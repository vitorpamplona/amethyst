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
 * per account or per conversation), and advanced **on demand** — one page at a time, only when the
 * owner calls [advance].
 *
 * The time-window model can't tell "this relay is empty" from "this is a gap" — a `since`/`until`
 * slice that returns nothing might just be a quiet stretch with older messages beneath it. Paging by
 * `until`+`limit` removes that ambiguity: a relay returns its N newest events older than `until`,
 * **skipping gaps**, so an empty page can only mean there is nothing older.
 *
 * Two cursors are kept per relay, deliberately decoupled so a relay never pages further than it was
 * asked to:
 *  - [requestedUntilFor] — the `until` the relay's REQ currently carries. Moves **only** in [advance].
 *    Leaving it untouched on EOSE is what makes paging demand-driven: a relay that finished a page just
 *    parks at the same filter (no re-REQ) until the owner advances it again.
 *  - reached (see [reachedUntilFor]) — the oldest `created_at` the relay has actually delivered. Moves
 *    on EOSE. This is what the in-stream markers sit at; [advance] starts the next page just below it.
 *
 * Stop signal (per relay): an **empty page followed by EOSE** ([onEose] with no events) marks that
 * relay [done][isDone]. A relay that returns anything — even fewer than the requested limit, since a
 * relay may cap results below what we asked — is not done.
 *
 * Not internally synchronized: per-relay counters are touched on the relay IO threads (one relay's
 * callbacks are serialized) and read on the owning scope; fields are volatile.
 */
class UntilLimitPager<K> {
    private class RelayCursor {
        // The `until` the REQ carries; null until the relay is first advanced. Moves only in advance().
        @Volatile var requestedUntil: Long? = null

        // The oldest created_at this relay has delivered; null until its first non-empty page. Moves on
        // EOSE. The marker sits here and the next page starts just below it.
        @Volatile var reachedUntil: Long? = null

        // Set once the relay answered an empty page with EOSE: there is nothing older on it.
        @Volatile var done: Boolean = false

        // Per-page tallies, reset by [advance]: how many events arrived and the oldest among them.
        @Volatile var pageCount: Int = 0

        @Volatile var pageOldest: Long = Long.MAX_VALUE
    }

    private val perKey = ConcurrentHashMap<K, ConcurrentHashMap<NormalizedRelayUrl, RelayCursor>>()

    private fun cursorsFor(key: K) = perKey.getOrPut(key) { ConcurrentHashMap() }

    private fun cursor(
        key: K,
        relay: NormalizedRelayUrl,
    ) = cursorsFor(key).getOrPut(relay) { RelayCursor() }

    /** True once [relay] has been [advance]d at least once (so its REQ should be issued). */
    fun isArmed(
        key: K,
        relay: NormalizedRelayUrl,
    ): Boolean = cursor(key, relay).requestedUntil != null

    /** The `until` [relay]'s REQ currently carries. Only meaningful once [isArmed]. */
    fun requestedUntilFor(
        key: K,
        relay: NormalizedRelayUrl,
    ): Long? = cursor(key, relay).requestedUntil

    /** The oldest point [relay] has reached (its marker depth), or [start] if it hasn't delivered yet. */
    fun reachedUntilFor(
        key: K,
        relay: NormalizedRelayUrl,
        start: Long,
    ): Long = cursor(key, relay).reachedUntil ?: start

    /** True once [relay] answered an empty page with EOSE — nothing older to ask it for. */
    fun isDone(
        key: K,
        relay: NormalizedRelayUrl,
    ): Boolean = cursor(key, relay).done

    /**
     * Steps [relay] to its next, older page: points its REQ just below the oldest event it has delivered
     * (or [start] for its very first page) and clears the page tally. No-op (returns false) if the relay
     * has already paged to the bottom ([done]). The owner re-issues the REQ after this (invalidateFilters).
     */
    fun advance(
        key: K,
        relay: NormalizedRelayUrl,
        start: Long,
    ): Boolean {
        val c = cursor(key, relay)
        if (c.done) return false
        c.requestedUntil =
            if (c.requestedUntil == null) {
                start
            } else {
                (c.reachedUntil ?: start) - 1
            }
        c.pageCount = 0
        c.pageOldest = Long.MAX_VALUE
        return true
    }

    /** Records one event for [relay] in the current page. */
    fun onEvent(
        key: K,
        relay: NormalizedRelayUrl,
        createdAt: Long,
    ) {
        val c = cursor(key, relay)
        c.pageCount++
        if (createdAt < c.pageOldest) c.pageOldest = createdAt
    }

    /**
     * Finalizes [relay] for the page on its EOSE: an empty page marks it [done]; otherwise the reached
     * cursor drops to the oldest event the page returned. The requested cursor is left alone so the relay
     * parks until [advance] is called again.
     */
    fun onEose(
        key: K,
        relay: NormalizedRelayUrl,
    ) {
        val c = cursor(key, relay)
        if (c.pageCount == 0) {
            c.done = true
        } else {
            c.reachedUntil = c.pageOldest
        }
    }

    /** Relays from [all] that still have older history to ask for: not yet empty-EOSE'd ([done]). */
    fun activeRelays(
        key: K,
        all: Collection<NormalizedRelayUrl>,
    ): List<NormalizedRelayUrl> = all.filterNot { cursor(key, it).done }

    /** Relays from [all] that have been armed (advanced at least once) and are not yet [done]. */
    fun armedRelays(
        key: K,
        all: Collection<NormalizedRelayUrl>,
    ): List<NormalizedRelayUrl> =
        all.filter {
            val c = cursor(key, it)
            c.requestedUntil != null && !c.done
        }

    /**
     * The oldest point reached across [relays] — the minimum reached cursor (how far back paging has
     * gone). Relays that haven't delivered count as [start]. Null when [relays] is empty.
     */
    fun deepestReached(
        key: K,
        relays: Collection<NormalizedRelayUrl>,
        start: Long,
    ): Long? = relays.takeIf { it.isNotEmpty() }?.minOf { cursor(key, it).reachedUntil ?: start }
}
