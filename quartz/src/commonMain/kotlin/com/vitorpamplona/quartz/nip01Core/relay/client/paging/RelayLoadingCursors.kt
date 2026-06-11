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
 * Per-relay `until`+`limit` pagination cursors for **one scope** (one account, or one conversation):
 * how far back each relay has been *asked* to load, and how far it has actually *delivered*.
 *
 * Pure paging state, no orchestration — the loading / stall / exhaustion / live status a caller shows
 * around paging is a separate concern, layered on top by a driver (in this library, `BackwardRelayPager`).
 * It carries no key on purpose: the caller holds one instance per scope on whatever object owns that
 * scope, so the cursors share that object's lifetime and the object graph is the partition — not a map
 * kept in here.
 *
 * Why `until`+`limit` and not a time window: a `since`/`until` slice that comes back empty can't tell
 * "nothing older here" from "just a quiet gap". `until`+`limit` returns the N newest events older than
 * `until`, skipping gaps — so an **empty page + EOSE is the gap-proof stop** ([isDone]). (A short page,
 * fewer than the limit, is the relay capping the response, not the bottom.)
 *
 * Two cursors per relay, kept apart so a relay never pages past what it was asked:
 *  - [requestedUntilFor] — the `until` its REQ carries; moves only in [advance]. Untouched on EOSE, so a
 *    finished page just parks (no re-REQ) until advanced again — this is what makes paging demand-driven.
 *  - reached ([reachedUntilFor]) — the oldest `created_at` it has delivered; moves on EOSE. This is the
 *    "loaded back to here" point, and the next [advance] starts just below it.
 *
 * No locks of its own: each relay's callbacks are serialized, the cursor fields are `@Volatile`, and the
 * relay map is the thread-safe [LargeCache] (keyed by [NormalizedRelayUrl], which orders consistently
 * with `equals`).
 */
class RelayLoadingCursors {
    private class RelayCursor {
        // The `until` the REQ carries; null until the relay is first advanced. Moves only in advance().
        @Volatile var requestedUntil: Long? = null

        // The oldest created_at this relay has delivered; null until its first non-empty page. Moves on
        // EOSE. This is the "loaded back to here" point; the next page starts just below it.
        @Volatile var reachedUntil: Long? = null

        // Set once the relay answered an empty page with EOSE: there is nothing older on it.
        @Volatile var done: Boolean = false

        // Per-page tallies, reset by [advance]: how many events arrived and the oldest among them.
        @Volatile var pageCount: Int = 0

        @Volatile var pageOldest: Long = Long.MAX_VALUE
    }

    private val cursors = LargeCache<NormalizedRelayUrl, RelayCursor>()

    /**
     * The history floor this scope pages down from (e.g. `now − liveTail`, pinned by the owner on first
     * advance). Kept here so it persists with the scope and doesn't drift on recompute — a relay that
     * hasn't delivered yet reports this floor as its reached point, and a moving floor would make a
     * demand-driven loader re-fire on it.
     */
    @Volatile
    var floor: Long? = null

    private fun cursor(relay: NormalizedRelayUrl) = cursors.getOrCreate(relay) { RelayCursor() }

    /** The `until` [relay]'s REQ currently carries. Only meaningful once it has been [advance]d. */
    fun requestedUntilFor(relay: NormalizedRelayUrl): Long? = cursor(relay).requestedUntil

    /** The oldest point [relay] has reached, or [start] if it hasn't delivered yet. */
    fun reachedUntilFor(
        relay: NormalizedRelayUrl,
        start: Long,
    ): Long = cursor(relay).reachedUntil ?: start

    /** True once [relay] answered an empty page with EOSE — nothing older to ask it for. */
    fun isDone(relay: NormalizedRelayUrl): Boolean = cursor(relay).done

    /**
     * Steps [relay] to its next, older page: points its REQ just below the oldest event it has delivered
     * (or [start] for its very first page) and clears the page tally. No-op (returns false) if the relay
     * has already paged to the bottom ([isDone]). The owner re-issues the relay's REQ after this.
     */
    fun advance(
        relay: NormalizedRelayUrl,
        start: Long,
    ): Boolean {
        val c = cursor(relay)
        if (c.done) return false
        val reached = c.reachedUntil
        c.requestedUntil =
            when {
                // Resume just below the oldest event already delivered. Covers both normal page-to-page
                // advance and a post-[rewindTo] resume (which un-arms the relay — requestedUntil back to
                // null — but keeps the rewound reached point, so the next page picks up at the boundary
                // instead of restarting at the floor and re-streaming the still-held tail).
                reached != null -> reached - 1
                // Very first page for this relay (nothing delivered, nothing requested yet).
                c.requestedUntil == null -> start
                // Armed but still mid-page (no EOSE yet) — keep asking from the same top.
                else -> start
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
     * Finalizes [relay] for the page on its EOSE: an empty page marks it [isDone]; otherwise the reached
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
            // misbehaving relay echoing the same newest events — would otherwise pin the cursor and a
            // demand-driven loader would re-request the same window forever. Treat that as the bottom.
            val prev = c.reachedUntil
            if (prev == null || c.pageOldest < prev) {
                c.reachedUntil = c.pageOldest
            } else {
                c.done = true
            }
        }
    }

    /**
     * Realigns the window after the cache prunes messages out of it: for each `relay → newestPrunedUntil`
     * entry, rewinds that relay so it no longer claims to hold anything at or below [newestPrunedUntil]
     * (the newest cursor-space `created_at` among the messages pruned from that relay — for gift wraps the
     * **outer-wrap** time, recovered from the rumor's [host][com.vitorpamplona.quartz.nip59Giftwrap.HostStub]).
     *
     * Without this, a relay that already paged past the pruned band — or reached `done` — would never
     * re-request the dropped messages: its [reachedUntil] still points below them, so the next [advance]
     * starts even older and skips the hole entirely.
     *
     * The rewind pulls [reachedUntil] back up to just above [newestPrunedUntil] (so the next page's
     * `until` re-includes it), clears [done] (there *is* older data to re-fetch again), and un-arms the
     * relay (requested cursor back to null) so paging stays demand-driven — the dropped band comes back
     * only when the on-screen marker advances the relay again, not eagerly on the next re-subscribe.
     *
     * Bounds:
     *  - A relay with no cursor yet (never paged) is skipped — there is no window position to misalign.
     *  - The rewind never moves [reachedUntil] above the pinned [floor] (history lives strictly below it;
     *    a pruned message newer than the floor is the live tail's concern, not this window's).
     *  - A relay whose reached point is already shallower than the pruned band needs no rewind.
     */
    fun rewindTo(newestPrunedUntil: Map<NormalizedRelayUrl, Long>) {
        val floorAt = floor ?: return
        newestPrunedUntil.forEach { (relay, prunedUntil) ->
            val c = cursors.get(relay) ?: return@forEach
            val reached = c.reachedUntil ?: return@forEach
            // Re-include the newest pruned event: the next page asks `until = reached - 1`, so reached must
            // sit one tick above it. Never climb above the floor.
            val target = minOf(prunedUntil + 1, floorAt)
            if (reached < target) {
                c.reachedUntil = target
                c.requestedUntil = null
                c.done = false
                c.pageCount = 0
                c.pageOldest = Long.MAX_VALUE
            }
        }
    }

    /** Relays from [all] that have been armed (advanced at least once) and are not yet [isDone]. */
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
