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
import com.vitorpamplona.quartz.utils.Log
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
    /** A covered `created_at` interval, inclusive at both ends. */
    data class Span(
        val min: Long,
        val max: Long,
    ) {
        fun widen(other: Span) = Span(minOf(min, other.min), maxOf(max, other.max))
    }

    /**
     * What is already covered for one (relay, filter) pair.
     *
     * [spans] is PER KIND, and that is the whole point of it. A band used to
     * hold one interval for the entire filter, which is a claim no multi-kind
     * walk can support: ask for `kinds: [0, 30382]`, see profiles back to 2020
     * and score cards only from 2025, and the band reads 2020..2026 — so the
     * next run skips 2020..2025 for BOTH, and the score cards in that interior
     * are never asked for again. A long-lived kind vouched for a short-lived
     * one. Per kind, each carries only the evidence actually collected for it.
     *
     * Filters that name no kinds at all cannot be split, so they keep a single
     * span under [ALL_KINDS] — the same claim as before, correctly scoped to
     * the case where it is the only claim available.
     *
     * [complete] is the difference between "we walked this span" (a paged
     * fetch) and "we are in sync below this point" (a finished negentropy
     * reconcile, which compared the whole range). Only a complete band may
     * skip its older leg. It is a property of the BAND rather than of a span:
     * a reconcile compares the filter's whole id set at once, so it either
     * covers every kind in it or none.
     *
     * [fullAt] is when the last pass that started from nothing finished — the
     * clock for the periodic re-walk.
     */
    data class Band(
        val spans: Map<Int, Span>,
        val complete: Boolean = false,
        val fullAt: Long = 0,
    ) {
        /** The outer edges across every kind — for logging and for the file's compatibility fields. */
        val minCreatedAt: Long get() = spans.values.minOfOrNull { it.min } ?: 0
        val maxCreatedAt: Long get() = spans.values.maxOfOrNull { it.max } ?: 0

        /** Widen each kind by its counterpart, keeping kinds only one side knows. */
        fun widen(other: Band): Band {
            val merged = spans.toMutableMap()
            for ((kind, span) in other.spans) merged[kind] = merged[kind]?.widen(span) ?: span
            return Band(merged, complete || other.complete, fullAt)
        }
    }

    private val bands = ConcurrentMap<String, Band>()

    // filter -> its canonical json. Filter.toJson() runs to tens of thousands
    // of characters for author-scoped filters, and a fan-out keys once per
    // relay per cycle over the SAME handful of filter instances. Filter
    // compares by identity, so this map is an identity cache — and an
    // identity cache retains every distinct instance it is handed. A caller
    // that rebuilds its filter each cycle would grow it forever, so past
    // MAX_FINGERPRINTS new instances key correctly but are not cached.
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
        floor: Long? = null,
    ): List<Filter> {
        val band = bands[key(url, filter)] ?: return listOf(filter)
        // Time for another full pass: relays gain old events, and without
        // this the band's claim is never re-tested.
        if (isStale(band)) return listOf(filter)
        if (band.spans.isEmpty()) return listOf(filter)

        val kinds = filter.kinds
        if (kinds.isNullOrEmpty()) {
            // Nothing to split by. One span, exactly as before.
            return windows(filter, band.spans[ALL_KINDS], band.complete, floor)
                .map { (since, until) -> filter.copy(since = since, until = until) }
        }

        // Per kind, then REGROUPED by the windows each one wants. Kinds whose
        // coverage agrees — the overwhelmingly common case, and the only case
        // at all until they diverge — collapse back into one ask, so a filter
        // that used to produce two legs still produces two rather than two per
        // kind. Only a kind whose evidence genuinely differs earns its own.
        val byWindows = LinkedHashMap<List<Pair<Long?, Long?>>, MutableList<Int>>()
        for (kind in kinds) {
            // ALL_KINDS as the fallback: a band written before coverage was
            // tracked per kind, restored from such a file. It carries the old,
            // wider claim for every kind — the behaviour this replaces — and
            // self-corrects on the first paged walk that reports per kind.
            val span = band.spans[kind] ?: band.spans[ALL_KINDS]
            byWindows.getOrPut(windows(filter, span, band.complete, floor)) { mutableListOf() }.add(kind)
        }
        return byWindows.flatMap { (windows, group) ->
            // toList(): `group` is the mutable accumulator above, and handing
            // the same instance to every Filter in the group would publish it
            // through a public return value. Filters are treated as immutable
            // everywhere else; this keeps that true by construction.
            val kindsForGroup = group.toList()
            windows.map { (since, until) -> filter.copy(kinds = kindsForGroup, since = since, until = until) }
        }
    }

    /**
     * The `(since, until)` pairs still outstanding for ONE span — the leg
     * arithmetic, with the filter's own bounds applied and nothing else.
     * A null [span] means no evidence at all, so the whole filter is wanted.
     */
    private fun windows(
        filter: Filter,
        span: Span?,
        complete: Boolean,
        floor: Long?,
    ): List<Pair<Long?, Long?>> {
        if (span == null) return listOf(filter.since to filter.until)
        val out = mutableListOf<Pair<Long?, Long?>>()

        // Older: up to and including the span's floor, but not past the
        // filter's (or, when the filter has no `since`, the caller's [floor] —
        // a sync window the filter itself must not carry, or it would change
        // the band's key every run). A complete band compared its whole range
        // already, but only down to the floor it ran against: a caller now
        // reaching deeper — a raised backfill window — re-opens the span below.
        val since = filter.since ?: floor
        val wantsOlder =
            if (complete) {
                since != null && since < span.min
            } else {
                since == null || span.min >= since
            }
        if (wantsOlder) out.add(filter.since to minOf(span.min, filter.until ?: Long.MAX_VALUE))

        // Newer: from the span's ceiling on, but not past the filter's.
        if (filter.until == null || span.max <= filter.until) {
            out.add(maxOf(span.max, filter.since ?: Long.MIN_VALUE) to filter.until)
        }
        return out
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
        observedByKind: Map<Int, Span>? = null,
    ) {
        if (reconciledThrough != null) {
            // A reconcile compares the filter's whole id set in one pass, so
            // the span it earns is the same for every kind the filter names —
            // no per-kind evidence needed or possible.
            val span = Span(observedMin ?: reconciledThrough, reconciledThrough)
            put(url, filter, kindsOf(filter).associateWith { span }, complete = true)
            return
        }
        if (!paged) return

        // Read ONCE. `now` is a clock call, and this was invoking it twice per
        // entry — so a 40-kind map took 80 readings, and worse, a span's floor
        // and ceiling were judged against two different instants.
        val at = now()
        if (observedByKind != null) {
            // Guarded per span for the same reason the aggregate is below.
            val plausible =
                observedByKind.filterValues {
                    isPlausible(it.min, at) && isPlausible(it.max, at)
                }
            if (plausible.isEmpty()) return
            val named = filter.kinds
            val spans =
                if (named.isNullOrEmpty()) {
                    // A filter naming no kinds cannot be split, so [legs] reads
                    // ALL_KINDS and nothing else. Storing what the walk saw per
                    // kind would record a band no lookup can ever reach — it
                    // would exist and do nothing. Collapse to the union, which
                    // is the only claim such a filter can make.
                    mapOf(ALL_KINDS to plausible.values.reduce { a, b -> a.widen(b) })
                } else {
                    // Only kinds the filter NAMES. A relay may answer with more
                    // than it was asked for, and a caller whose containment
                    // check runs against a different filter than the band is
                    // keyed by passes those straight through. Keeping them
                    // would be inert for [legs] — which looks up the filter's
                    // own kinds — but NOT for [Band.minCreatedAt], which the
                    // state file writes as its rollback-compat `min`/`max`. An
                    // off-filter kind seen further back would widen those past
                    // anything the filter's kinds support, so a binary from
                    // before per-kind spans would read that file and
                    // over-claim: this fix undone through the compat path.
                    plausible.filterKeys { it in named }
                }
            if (spans.isEmpty()) return
            put(url, filter, spans, complete = false)
            return
        }

        // No per-kind evidence. For a filter naming one kind (or none) the
        // aggregate IS the per-kind answer and nothing is lost. For a filter
        // naming several it is not: attributing one interval to all of them is
        // exactly the over-claim [Band.spans] exists to stop, and a band that
        // over-claims skips events silently — strictly worse than re-reading
        // them. So record nothing and say why, once. The caller resumes as if
        // it had no band, which is where it was before bands existed.
        val kinds = kindsOf(filter)
        if (kinds.size > 1) {
            if (!warnedAboutUnattributed) {
                warnedAboutUnattributed = true
                Log.w("SyncCoverage") {
                    "paged record for a ${kinds.size}-kind filter with no per-kind spans — no band recorded, so this " +
                        "walk will not resume. Pass observedByKind (see SyncCoverage.observe) to earn one."
                }
            }
            return
        }
        // Guarded even though callers should filter with [isPlausible] per
        // event: a 1970 floor or a far-future ceiling would make the band
        // claim the whole timeline, and the leg outside it would ask for a
        // range nothing can be in, forever.
        if (observedMin == null || observedMax == null) return
        if (!isPlausible(observedMin, at) || !isPlausible(observedMax, at)) return
        put(url, filter, kinds.associateWith { Span(observedMin, observedMax) }, complete = false)
    }

    /** The kinds a band is keyed by: the filter's, or [ALL_KINDS] when it names none. */
    private fun kindsOf(filter: Filter): List<Int> = filter.kinds?.takeIf { it.isNotEmpty() } ?: listOf(ALL_KINDS)

    /**
     * Widen (or reset) the band. A pass that ran because the previous band
     * had gone stale REPLACES it: it re-walked the whole filter, so its own
     * span is the complete picture and [Band.fullAt] restarts from here.
     */
    private fun put(
        url: NormalizedRelayUrl,
        filter: Filter,
        spans: Map<Int, Span>,
        complete: Boolean,
    ) {
        val fresh = Band(spans, complete, now())
        bands.merge(key(url, filter), fresh) { old, new ->
            if (isStale(old)) new else old.widen(new)
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
            // Nothing outside its band: this relay asks nothing of the
            // snapshot at all — the best case must not widen the window.
            if (legs.isEmpty()) continue
            // More than one leg means an older gap this relay still wants, so
            // the snapshot cannot start above the filter's own floor.
            val only = legs.singleOrNull() ?: return filter
            val legSince = only.since ?: return filter
            since = minOf(since, legSince)
        }
        // Every relay fully covered: any window would do; the unnarrowed
        // filter is merely safe, and callers usually skip the sync entirely.
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
    ): String {
        val fingerprint =
            fingerprints[filter]
                ?: filter.toJson().also {
                    // Bounded: stable callers hit the cache at any size a real
                    // config produces; a caller minting fresh instances just
                    // pays the toJson each time instead of growing the heap.
                    if (fingerprints.size() < MAX_FINGERPRINTS) fingerprints[filter] = it
                }
        return "${url.url} $fingerprint"
    }

    // One line per process, not per walk: the point is to tell a caller it has
    // not been migrated, and repeating it every leg would bury the log it is
    // trying to be read in.
    private var warnedAboutUnattributed = false

    companion object {
        /**
         * The span key for a filter that names no kinds, and the fallback for
         * a band restored from a file written before spans were per kind.
         * Negative because NIP-01 kinds are not.
         */
        const val ALL_KINDS = -1

        /**
         * Widen [into] with one event's stamp, so a caller can accumulate the
         * per-kind evidence [record] wants as events arrive:
         *
         *     val seen = mutableMapOf<Int, SyncCoverage.Span>()
         *     ... onEvent { SyncCoverage.observe(seen, it.kind, it.createdAt) }
         *     coverage.record(url, filter, …, paged = true, observedByKind = seen)
         *
         * Implausible stamps are dropped here rather than by each caller —
         * per EVENT, never over a leg's aggregate, because one misdated event
         * among hundreds of thousands would otherwise discard the whole band.
         *
         * Not synchronized: it replaces a pair of plain `var`s at each call
         * site and is meant for the same single-consumer callback.
         */
        fun observe(
            into: MutableMap<Int, Span>,
            kind: Int,
            createdAt: Long,
            now: Long = TimeUtils.now(),
        ) {
            if (!isPlausible(createdAt, now)) return
            val one = Span(createdAt, createdAt)
            into[kind] = into[kind]?.widen(one) ?: one
        }

        // More filter instances than any deliberate configuration holds; only
        // a caller rebuilding filters per cycle ever reaches it.
        private const val MAX_FINGERPRINTS = 1_000

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
