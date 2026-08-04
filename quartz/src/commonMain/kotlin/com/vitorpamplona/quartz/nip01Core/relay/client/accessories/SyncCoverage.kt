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
package com.vitorpamplona.quartz.nip01Core.relay.client.accessories

import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.utils.TimeUtils
import com.vitorpamplona.quartz.utils.concurrent.ConcurrentMap

/**
 * How much of a filter's history has already been pulled from one relay, so a
 * restart does not pull it again.
 *
 * A negentropy relay needs none of this — reconciliation downloads only the
 * diff. Most relays lack NIP-77, and a paged fetch ([fetchAllPages]) has no
 * memory: it re-downloads everything it walked last time, every restart,
 * forever. So for those, remember the band of `created_at` covered per
 * (relay, filter), and the next run asks only for the two legs outside it:
 *
 *     stored band:        |<-------- covered -------->|
 *     next fetch:  <------|                           |------>
 *
 * Keyed by the WHOLE filter deliberately: any edit to a filter is a new key
 * with no band, so the next run starts over — the safe direction to be wrong
 * in, and the intended way to force a re-walk.
 *
 * A band does not guarantee completeness (a truncating relay, an event
 * back-dated into a walked span). The trade is deliberate: re-reading a
 * corpus on every restart is a certain daily cost, while both holes are
 * occasional and self-heal on the next filter change or full re-walk.
 *
 * Persistence is the caller's: [export] the map on a schedule and [restore]
 * it at startup. [onChange] fires whenever a band changes, so a persistence
 * layer can mark itself dirty without polling.
 *
 * Not to be confused with the `relay.client.paging` package: its
 * `RelayLoadingCursors` are in-memory POSITIONS for demand-driven UI paging
 * within one session, while these are persistent INTERVALS — a claim about
 * coverage that outlives the process and licenses skipping work.
 */
class SyncCoverage(
    // How long a band may narrow work before the whole filter is walked
    // again. Everything a band claims is a claim about the past; this is how
    // long to trust it without re-testing.
    private val fullResyncSeconds: Long = DEFAULT_FULL_RESYNC_SECONDS,
    private val now: () -> Long = { TimeUtils.now() },
    private val onChange: () -> Unit = {},
) {
    /**
     * What is already covered for one (relay, filter) pair.
     *
     * [complete] is the difference between "we walked this span" (a paged
     * fetch) and "we are in sync below this point" (a finished negentropy
     * reconcile, which compared the whole range). Only a complete band may
     * skip its older leg.
     *
     * [fullAt] is when the last pass that started from nothing finished — the
     * clock for the periodic re-walk.
     */
    data class Band(
        val minCreatedAt: Long,
        val maxCreatedAt: Long,
        val complete: Boolean = false,
        val fullAt: Long = 0,
    )

    private val bands = ConcurrentMap<String, Band>()

    // filter -> its canonical json. Filter.toJson() runs to tens of thousands
    // of characters for author-scoped filters, and a fan-out keys once per
    // relay per cycle over the SAME handful of filter instances. Filter
    // compares by identity, so this map is an identity cache; an
    // equal-but-distinct filter still keys correctly, just without the cache.
    private val fingerprints = ConcurrentMap<Filter, String>()

    /**
     * The filters to actually run now, given what is already covered: the
     * whole filter when nothing is recorded (or the band went stale),
     * otherwise the legs outside the band, clamped to the filter's own
     * `since`/`until`.
     *
     * The legs are INCLUSIVE of the band's edges (`until = min`, not
     * `min - 1`): a page boundary can split a run of events sharing one
     * `created_at`, and excluding the edge would strand the rest of that
     * second in no leg at all. The cost is re-reading one second's worth of
     * events per leg, which a store rejects as duplicates.
     */
    fun legs(
        url: NormalizedRelayUrl,
        filter: Filter,
    ): List<Filter> {
        val band = bands[key(url, filter)] ?: return listOf(filter)
        // Time for another full pass: relays gain old events, and without
        // this the band's claim is never re-tested.
        if (isStale(band)) return listOf(filter)
        val legs = mutableListOf<Filter>()

        // Older: up to and including the band's floor, but not past the
        // filter's. A complete band has no older leg at all — the reconcile
        // already compared the whole range.
        if (!band.complete && (filter.since == null || band.minCreatedAt >= filter.since)) {
            legs.add(filter.copy(until = minOf(band.minCreatedAt, filter.until ?: Long.MAX_VALUE)))
        }

        // Newer: from the band's ceiling on, but not past the filter's.
        if (filter.until == null || band.maxCreatedAt <= filter.until) {
            legs.add(filter.copy(since = maxOf(band.maxCreatedAt, filter.since ?: Long.MIN_VALUE)))
        }
        return legs
    }

    /**
     * Widen the band for (url, filter) to include what a completed fetch saw.
     *
     * [paged] gates the mechanism: a negentropy sync needs no band, and
     * recording one would only risk narrowing a future reconciliation.
     * Nothing is recorded for a fetch that saw no events — an empty result
     * says nothing about what the relay holds.
     *
     * [reconciledThrough] is the strong case: a FINISHED reconcile compared
     * the filter's whole range, so the caller is in sync up to the instant
     * the sync STARTED — recorded against that instant rather than the newest
     * event seen, because "the relay had nothing newer" and "we never asked"
     * must not look alike.
     */
    fun record(
        url: NormalizedRelayUrl,
        filter: Filter,
        observedMin: Long?,
        observedMax: Long?,
        paged: Boolean,
        reconciledThrough: Long? = null,
    ) {
        if (reconciledThrough != null) {
            put(url, filter, observedMin ?: reconciledThrough, reconciledThrough, complete = true)
            return
        }
        if (!paged) return
        // Guarded even though callers should filter with [isPlausible] per
        // event: a 1970 floor or a far-future ceiling would make the band
        // claim the whole timeline, and the leg outside it would ask for a
        // range nothing can be in, forever.
        if (observedMin == null || observedMax == null) return
        if (!isPlausible(observedMin, now()) || !isPlausible(observedMax, now())) return
        put(url, filter, observedMin, observedMax, complete = false)
    }

    /**
     * Widen (or reset) the band. A pass that ran because the previous band
     * had gone stale REPLACES it: it re-walked the whole filter, so its own
     * span is the complete picture and [Band.fullAt] restarts from here.
     */
    private fun put(
        url: NormalizedRelayUrl,
        filter: Filter,
        min: Long,
        max: Long,
        complete: Boolean,
    ) {
        val fresh = Band(min, max, complete, now())
        bands.merge(key(url, filter), fresh) { old, new ->
            if (isStale(old)) {
                new
            } else {
                Band(
                    minOf(old.minCreatedAt, new.minCreatedAt),
                    maxOf(old.maxCreatedAt, new.maxCreatedAt),
                    old.complete || new.complete,
                    old.fullAt,
                )
            }
        }
        onChange()
    }

    private fun isStale(band: Band): Boolean = now() - band.fullAt >= fullResyncSeconds

    /**
     * The narrowest single filter that still covers what every one of [urls]
     * needs — the window a shared negentropy snapshot has to be taken over.
     *
     * In steady state every relay carries a complete band and this collapses
     * to `since = the oldest of their ceilings` — the difference between
     * snapshotting an id set of millions and one of a few thousand. One relay
     * that has never synced puts it back to the full filter, correctly: that
     * relay genuinely needs everything.
     */
    fun coveringWindow(
        urls: List<NormalizedRelayUrl>,
        filter: Filter,
    ): Filter {
        if (urls.isEmpty()) return filter
        var since = Long.MAX_VALUE
        for (url in urls) {
            val legs = legs(url, filter)
            // More than one leg means an older gap this relay still wants, so
            // the snapshot cannot start above the filter's own floor.
            val only = legs.singleOrNull() ?: return filter
            val legSince = only.since ?: return filter
            since = minOf(since, legSince)
        }
        return if (since == Long.MAX_VALUE) filter else filter.copy(since = since)
    }

    /** What is currently covered, for logging and tests. */
    fun band(
        url: NormalizedRelayUrl,
        filter: Filter,
    ): Band? = bands[key(url, filter)]

    fun size(): Int = bands.size()

    /** A point-in-time copy of every band, for a persistence layer to write out. */
    fun export(): Map<String, Band> = bands.snapshot()

    /** Load previously [export]ed bands, e.g. at startup. */
    fun restore(entries: Map<String, Band>) {
        for ((key, band) in entries) bands[key] = band
    }

    /**
     * The identity of one (relay, filter) pair. [Filter.toJson] is the
     * protocol's own canonical form, so two filters that mean the same thing
     * key the same way and any edit keys differently — exactly the "config
     * changed, start over" rule.
     */
    private fun key(
        url: NormalizedRelayUrl,
        filter: Filter,
    ): String = "${url.url} ${fingerprints.getOrPut(filter) { filter.toJson() }}"

    companion object {
        /**
         * A week. Long enough that the narrow path is the normal one, short
         * enough that anything a band is wrong about is wrong for days, not
         * forever.
         */
        const val DEFAULT_FULL_RESYNC_SECONDS = 7L * 24 * 60 * 60

        /**
         * 2020-01-01. Below this a `created_at` is a bug, not a date — the
         * protocol did not exist. Also the natural floor for measuring a
         * paged walk's progress when a filter names no `since`.
         */
        const val PLAUSIBLE_FLOOR = 1_577_836_800L

        // Clock skew a relay may legitimately be ahead by. Past this, a
        // created_at is the author's fiction rather than a time.
        private const val FUTURE_SKEW_SECONDS = 86_400L

        /**
         * Whether a `created_at` can be believed as evidence of coverage.
         * Filter with this per EVENT, not over a leg's aggregate: one
         * misdated event among hundreds of thousands would otherwise discard
         * the whole relay's band.
         */
        fun isPlausible(
            createdAt: Long,
            now: Long = TimeUtils.now(),
        ): Boolean = createdAt in PLAUSIBLE_FLOOR..(now + FUTURE_SKEW_SECONDS)
    }
}
