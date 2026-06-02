/*
 * Copyright (c) 2025 Vitor Pamplona
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the
 * Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.vitorpamplona.amethyst.service.relayClient.eoseManagers

import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import java.util.concurrent.ConcurrentHashMap

/**
 * Backward `until`+`limit` pagination cursor, tracked **independently per relay** (and per [K], e.g.
 * per account or per conversation).
 *
 * The time-window model can't tell "this relay is empty" from "this is a gap" — a `since`/`until`
 * slice that returns nothing might just be a quiet stretch with older messages beneath it. Paging by
 * `until`+`limit` removes that ambiguity: a relay returns its N newest events older than `until`,
 * **skipping gaps**, so an empty page can only mean there is nothing older.
 *
 * Stop signal (per relay): an **empty page followed by EOSE** ([onEose] with no events) marks that
 * relay [done][isDone]. A relay that returns anything — even fewer than the requested limit, since a
 * relay may cap results below what we asked — is *not* done; its cursor advances to one second below
 * the oldest event it sent and it is asked again. Globally, the owner decides "exhausted" from
 * [roundEventCount]: a whole round that advanced no relay (every relay empty-EOSE'd or only answered
 * CLOSED) means nothing more is reachable.
 *
 * Not internally synchronized: per-relay counters are touched on the relay IO threads (one relay's
 * callbacks are serialized) and read on the owning scope after the load settles; fields are volatile.
 */
class UntilLimitPager<K> {
    private class RelayCursor {
        // The `until` for this relay's next page; null until the first page (caller supplies the start).
        @Volatile var until: Long? = null

        // Set once the relay answered an empty page with EOSE: there is nothing older on it.
        @Volatile var done: Boolean = false

        // Set once the relay has rejected us [GIVE_UP_AFTER_CLOSES] times in a row without ever
        // answering (CLOSED, e.g. "auth-required" for authors we can't authenticate). It is NOT done —
        // we just can't read its window — but it must be excluded so it doesn't block exhaustion forever.
        @Volatile var givenUp: Boolean = false

        // Consecutive CLOSEDs since this relay last actually answered (event or EOSE). Reset on contact.
        @Volatile var closedStreak: Int = 0

        // Per-round tallies, reset by [beginRound]: how many events arrived and the oldest among them.
        @Volatile var roundCount: Int = 0

        @Volatile var roundOldest: Long = Long.MAX_VALUE
    }

    private val perKey = ConcurrentHashMap<K, ConcurrentHashMap<NormalizedRelayUrl, RelayCursor>>()

    private fun cursorsFor(key: K) = perKey.getOrPut(key) { ConcurrentHashMap() }

    private fun cursor(
        key: K,
        relay: NormalizedRelayUrl,
    ) = cursorsFor(key).getOrPut(relay) { RelayCursor() }

    /** The `until` to request next from [relay], or [start] if it has not been paged yet. */
    fun untilFor(
        key: K,
        relay: NormalizedRelayUrl,
        start: Long,
    ): Long = cursor(key, relay).until ?: start

    /** True once [relay] answered an empty page with EOSE — nothing older to ask it for. */
    fun isDone(
        key: K,
        relay: NormalizedRelayUrl,
    ): Boolean = cursor(key, relay).done

    /** Resets the per-round tallies for the relays a fresh round is about to request. */
    fun beginRound(
        key: K,
        relays: Collection<NormalizedRelayUrl>,
    ) = relays.forEach {
        val c = cursor(key, it)
        c.roundCount = 0
        c.roundOldest = Long.MAX_VALUE
    }

    /** Records one event for [relay] in the current round. */
    fun onEvent(
        key: K,
        relay: NormalizedRelayUrl,
        createdAt: Long,
    ) {
        val c = cursor(key, relay)
        c.closedStreak = 0
        c.roundCount++
        if (createdAt < c.roundOldest) c.roundOldest = createdAt
    }

    /**
     * Finalizes [relay] for the round on its EOSE: an empty page marks it [done]; otherwise its cursor
     * advances to just below the oldest event it returned (exclusive, so the next page makes progress
     * and the relay can eventually reach an empty page).
     */
    fun onEose(
        key: K,
        relay: NormalizedRelayUrl,
    ) {
        val c = cursor(key, relay)
        c.closedStreak = 0
        if (c.roundCount == 0) {
            c.done = true
        } else {
            c.until = c.roundOldest - 1
        }
    }

    /**
     * Records a CLOSED (rejection) from [relay]. After [GIVE_UP_AFTER_CLOSES] in a row with no answer in
     * between — i.e. the relay keeps rejecting us and auth can't fix it — the relay is [given up][givenUp]
     * so it stops blocking exhaustion. Returns true if this CLOSED tipped it into given-up.
     */
    fun onClosed(
        key: K,
        relay: NormalizedRelayUrl,
    ): Boolean {
        val c = cursor(key, relay)
        if (c.givenUp || c.done) return false
        c.closedStreak++
        if (c.closedStreak >= GIVE_UP_AFTER_CLOSES) {
            c.givenUp = true
            return true
        }
        return false
    }

    /** Total events received across [relays] in the round just finished. Zero ⇒ nothing more is reachable. */
    fun roundEventCount(
        key: K,
        relays: Collection<NormalizedRelayUrl>,
    ): Int = relays.sumOf { cursor(key, it).roundCount }

    /**
     * Relays from [all] that still have older history to ask for: not yet empty-EOSE'd ([done]) and not
     * abandoned as unreadable ([givenUp]).
     */
    fun activeRelays(
        key: K,
        all: Collection<NormalizedRelayUrl>,
    ): List<NormalizedRelayUrl> =
        all.filterNot {
            val c = cursor(key, it)
            c.done || c.givenUp
        }

    /**
     * The oldest point reached across [relays] — the minimum cursor (how far back paging has gone).
     * Relays not yet paged count as [start]. Null when [relays] is empty.
     */
    fun deepestUntil(
        key: K,
        relays: Collection<NormalizedRelayUrl>,
        start: Long,
    ): Long? = relays.takeIf { it.isNotEmpty() }?.minOf { cursor(key, it).until ?: start }

    companion object {
        // Consecutive CLOSEDs (with no answer in between) before a relay is abandoned as unreadable.
        // Allows for the pool's auth handshake + a retry or two before concluding auth can't succeed.
        private const val GIVE_UP_AFTER_CLOSES = 3
    }
}
