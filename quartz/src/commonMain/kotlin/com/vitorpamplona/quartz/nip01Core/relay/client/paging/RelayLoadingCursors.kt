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
package com.vitorpamplona.quartz.nip01Core.relay.client.paging

import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.utils.cache.LargeCache
import kotlin.concurrent.Volatile

/**
 * Backward `until`+`limit` pagination cursors for **one scope** (one account, or one conversation),
 * tracked **independently per relay** and advanced **on demand** — one page at a time, only when the
 * owner calls [advance].
 *
 * This is pure per-relay paging *state*, with no orchestration (no spinner / stall / exhaustion / live
 * flows — those are [BackwardRelayPager]'s job). It is meant to live on the owning domain object (a
 * `Chatroom` for a conversation, a `ChatroomList` for the account-level feeds), so the "how far each
 * relay has paged" it records shares the lifetime of the cached messages it describes and is dropped
 * with them. That is why it carries no key: the object graph *is* the partition.
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
 * callbacks are serialized) and read on the owning scope; fields are volatile and the relay map is a
 * thread-safe [LargeCache]. Keyed only by [NormalizedRelayUrl], which is `Comparable` and consistent
 * with `equals`, so the sorted cache identifies relays correctly.
 */
class RelayLoadingCursors {
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

    private val cursors = LargeCache<NormalizedRelayUrl, RelayCursor>()

    /**
     * The session-pinned history floor this scope pages down from (typically `now − liveTail`, set by the
     * owner on first advance). Kept here so it persists with the scope and does not drift forward on
     * recompute — an undelivered relay's marker sits at this floor, and a moving floor would re-trigger
     * its on-screen sentinel.
     */
    @Volatile
    var floor: Long? = null

    private fun cursor(relay: NormalizedRelayUrl) = cursors.getOrCreate(relay) { RelayCursor() }

    /** The `until` [relay]'s REQ currently carries. Only meaningful once it has been [advance]d. */
    fun requestedUntilFor(relay: NormalizedRelayUrl): Long? = cursor(relay).requestedUntil

    /** The oldest point [relay] has reached (its marker depth), or [start] if it hasn't delivered yet. */
    fun reachedUntilFor(
        relay: NormalizedRelayUrl,
        start: Long,
    ): Long = cursor(relay).reachedUntil ?: start

    /** True once [relay] answered an empty page with EOSE — nothing older to ask it for. */
    fun isDone(relay: NormalizedRelayUrl): Boolean = cursor(relay).done

    /**
     * Steps [relay] to its next, older page: points its REQ just below the oldest event it has delivered
     * (or [start] for its very first page) and clears the page tally. No-op (returns false) if the relay
     * has already paged to the bottom ([done]). The owner re-issues the REQ after this (invalidateFilters).
     */
    fun advance(
        relay: NormalizedRelayUrl,
        start: Long,
    ): Boolean {
        val c = cursor(relay)
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
        relay: NormalizedRelayUrl,
        createdAt: Long,
    ) {
        val c = cursor(relay)
        c.pageCount++
        if (createdAt < c.pageOldest) c.pageOldest = createdAt
    }

    /**
     * Finalizes [relay] for the page on its EOSE: an empty page marks it [done]; otherwise the reached
     * cursor drops to the oldest event the page returned. The requested cursor is left alone so the relay
     * parks until [advance] is called again.
     */
    fun onEose(relay: NormalizedRelayUrl) {
        val c = cursor(relay)
        if (c.pageCount == 0) {
            c.done = true
        } else {
            // The reached cursor must move strictly older every page (the next page asks `until =
            // reached - 1`). A relay that returns events but none older than we already have — a
            // misbehaving relay echoing the same newest events — would otherwise pin the cursor and the
            // on-screen sentinel would re-request the same window forever. Treat that as the bottom.
            val prev = c.reachedUntil
            if (prev == null || c.pageOldest < prev) {
                c.reachedUntil = c.pageOldest
            } else {
                c.done = true
            }
        }
    }

    /** Relays from [all] that have been armed (advanced at least once) and are not yet [done]. */
    fun armedRelays(all: Collection<NormalizedRelayUrl>): List<NormalizedRelayUrl> =
        all.filter {
            val c = cursor(it)
            c.requestedUntil != null && !c.done
        }

    /**
     * The oldest point reached across [relays] — the minimum reached cursor (how far back paging has
     * gone). Relays that haven't delivered count as [start]. Null when [relays] is empty.
     */
    fun deepestReached(
        relays: Collection<NormalizedRelayUrl>,
        start: Long,
    ): Long? = relays.takeIf { it.isNotEmpty() }?.minOf { cursor(it).reachedUntil ?: start }
}
