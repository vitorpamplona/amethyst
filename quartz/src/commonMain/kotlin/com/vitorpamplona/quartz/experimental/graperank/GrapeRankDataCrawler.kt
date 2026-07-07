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
package com.vitorpamplona.quartz.experimental.graperank

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.crypto.verify
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.AdaptiveRelayLimiter
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.DrainFailure
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.classifyDrainFailure
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.SubscriptionListener
import com.vitorpamplona.quartz.nip01Core.relay.client.single.newSubId
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.store.IEventStore
import com.vitorpamplona.quartz.nip02FollowList.ContactListEvent
import com.vitorpamplona.quartz.nip09Deletions.DeletionEvent
import com.vitorpamplona.quartz.nip51Lists.muteList.MuteListEvent
import com.vitorpamplona.quartz.nip56Reports.ReportEvent
import com.vitorpamplona.quartz.nip65RelayList.AdvertisedRelayListEvent
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.concurrent.ConcurrentMap
import com.vitorpamplona.quartz.utils.concurrent.ConcurrentSet
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.coroutines.coroutineContext
import kotlin.time.TimeSource

/**
 * Crawls the Nostr follow/mute/report graph outward from an observer and streams
 * the contact lists it finds into a [TrustGraphBuilder], so [GrapeRank] can score
 * the whole reachable network from that observer's point of view.
 *
 * It uses the outbox model: each user's kind:10002 write relays are located
 * first, then their kind:3 / kind:10000 / kind:1984 events are fetched from
 * *their own* relays. The crawl is exhaustive — no user cap; it keeps going until
 * every discovered user's outbox has been checked and their contact list pulled
 * (an unreachable outbox is retried a few times), bounded only by [Config.maxHops]
 * (follow-graph distance) and the [Config.maxRounds] safety backstop.
 *
 * Every event it fetches (contact lists, mute lists, reports, relay lists, and
 * the report deletions it looks up) is verified and persisted to [store], so the
 * caller can materialize mutes + reports (honouring NIP-09 retractions) from the
 * store afterwards. Only the contact lists are streamed into the [TrustGraphBuilder]
 * during the crawl — the compact int-CSR structure keeps the whole network in
 * memory without holding millions of kind:3 objects.
 *
 * The crawler is transport-agnostic within quartz: it takes a [NostrClient], an
 * [IEventStore], and the shared [AdaptiveRelayLimiter] (which must already be
 * registered as a connection listener on the client so its ladders react to
 * NOTICE/CLOSED frames). Relay *policy* — which aggregators know kind:10002, which
 * general relays might hold content — is injected via [Config], because those
 * defaults live in application code, not the protocol library. Operator progress
 * is emitted through [log]; a headless caller routes it to stderr, a UI ignores it.
 */
@OptIn(ExperimentalAtomicApi::class)
class GrapeRankDataCrawler(
    private val client: NostrClient,
    private val store: IEventStore,
    private val limiter: AdaptiveRelayLimiter,
    private val config: Config,
    private val log: (String) -> Unit = {},
) {
    // Crawl-wide timing, accumulated across every drainGated consumer (24 run at
    // once). Nanoseconds spent verifying signatures vs. spent in the store write,
    // plus how many verified events reached the store. Surfaced in [Stats] so a
    // caller can see whether a from-scratch crawl is verify-, write-, or (by
    // subtraction from wall time) network-bound. Reset at the top of each [crawl].
    private val verifyNanos = AtomicLong(0)
    private val insertNanos = AtomicLong(0)
    private val eventsStored = AtomicLong(0)

    /**
     * Relay policy + crawl bounds. The relay sets come from the caller because the
     * aggregator/bootstrap defaults live outside quartz.
     *
     * @param relayListDiscoveryRelays where to look up a stranger's kind:10002 —
     *   the index/discovery aggregators (purplepag.es, coracle, …) plus general
     *   defaults that carry kind:10002 for most of the network.
     * @param contentFallbackRelays best-effort general relays that *might* hold a
     *   user's kind:3/10000/1984 when their outbox is unknown or unreachable.
     * @param maxRounds safety backstop on freshness passes (default: run to convergence).
     * @param maxHops follow-graph distance from the observer to crawl (Brainstorm uses 8).
     * @param timeoutMs the FAST per-drain timeout that gates a round's progression.
     *   A relay that reaches EOSE/CLOSED inside it resolves its authors this round;
     *   one still streaming is not cut but PARKED (see [parkTimeoutMs]) so the round
     *   moves on without waiting for it. Keep this short — it is the round cadence.
     * @param parkTimeoutMs how long a parked (slow-but-alive) relay is allowed to
     *   keep delivering after it blew [timeoutMs]. Its late events are persisted and
     *   its late contact lists folded into the graph in a later round, so the crawl
     *   waits for slow relays for completeness WITHOUT paying that wait in the
     *   round's wall-clock. Parked sockets are bounded by the slow-relay population,
     *   not the whole fan-out. Set `<= timeoutMs` to disable parking.
     * @param diagnose log a breakdown of slow/unreachable relays on each drain timeout.
     * @param insertBatchSize how many verified events to group-commit per
     *   [IEventStore.batchInsert]. The outbox model streams the same events from
     *   many relays through a single SQLite writer, so batching amortizes the
     *   per-transaction + writer-mutex cost across the batch (coerced to `>= 1`).
     * @param drainConcurrency how many outbox batches drain at once (the worker
     *   pool size). A GLOBAL bound (memory / open sockets); the per-relay
     *   concurrent-sub cap is enforced separately by [AdaptiveRelayLimiter]. Keep it
     *   moderate: a higher global fan-out re-floods busy hubs faster than demotion
     *   catches up (an A/B at 64 ran ~2x slower with more dead relays), so 24 is the
     *   validated default and raising it is a probe, not a speedup.
     */
    class Config(
        val relayListDiscoveryRelays: Set<NormalizedRelayUrl>,
        val contentFallbackRelays: Set<NormalizedRelayUrl>,
        val maxRounds: Int = Int.MAX_VALUE,
        val maxHops: Int = Int.MAX_VALUE,
        val timeoutMs: Long = 10_000,
        val parkTimeoutMs: Long = 40_000,
        val diagnose: Boolean = false,
        val insertBatchSize: Int = 500,
        val drainConcurrency: Int = 24,
    )

    /** What the crawl fetched — the counters the caller reports and the graph is built from. */
    class Stats(
        val rounds: Int,
        val contactListsFed: Int,
        val relaysContacted: Int,
        /** Users bucketed by follow-graph distance from the observer (hop -> count), ascending. */
        val hopHistogram: Map<Int, Int>,
        val downloadMs: Long,
        /** Wall time verifying signatures, summed across the concurrent consumers. */
        val verifyMs: Long,
        /** Wall time in the store write path, summed across the concurrent consumers. */
        val insertMs: Long,
        /** Verified events handed to the store (duplicates included — the write path dedups). */
        val eventsStored: Long,
    )

    /**
     * Crawl from [observer], streaming discovered contact lists into [builder]
     * (follows only — mutes/reports land in the store for the caller to
     * materialize). Returns the crawl [Stats].
     */
    suspend fun crawl(
        observer: HexKey,
        builder: TrustGraphBuilder,
    ): Stats {
        verifyNanos.store(0)
        insertNanos.store(0)
        eventsStored.store(0)
        return CrawlRun(observer, builder).run()
    }

    /**
     * Holds all per-crawl mutable state. Graph state (done/hopOf/builder/
     * writeRelayFreq/liveRelays/relaysContacted) is single-writer by construction
     * — Phase A and the Phase-B consumer never run concurrently, and routeByOutbox
     * (the only Phase-B producer write, to writeRelayFreq) touches a disjoint field
     * — so those stay plain collections. The frontier IS [hopOf]'s key set: a user
     * is "discovered" iff it has a hop stamp. Only the state genuinely shared across
     * the producer / consumer / drain-worker coroutines is concurrent: relayHints,
     * attempts, deadRelays, relayStrikes.
     */
    private inner class CrawlRun(
        val observer: HexKey,
        val builder: TrustGraphBuilder,
    ) {
        // hop distance per discovered user; the observer seeds it at 0. Its key set
        // is the discovered frontier — no separate `discovered` set to keep in sync.
        val hopOf = hashMapOf(observer to 0)
        val done = hashSetOf<HexKey>()
        val relaysContacted = hashSetOf<NormalizedRelayUrl>()
        val writeRelayFreq = HashMap<NormalizedRelayUrl, Int>()
        val liveRelays = hashSetOf<NormalizedRelayUrl>()

        // Concurrent: touched by more than one of producer/consumer/drain-workers.
        val relayHints = ConcurrentMap<HexKey, ConcurrentSet<NormalizedRelayUrl>>()
        val attempts = ConcurrentMap<HexKey, Int>()
        val deadRelays = ConcurrentSet<NormalizedRelayUrl>()
        val relayStrikes = ConcurrentMap<NormalizedRelayUrl, Int>()

        // Crawl-wide dedup of event ids, shared across all concurrent drains and
        // every round. The outbox model mirrors the SAME event (especially kind:10002
        // relay lists) across many relays, indexers, and rounds; a per-drain set only
        // catches the copies within one drain, so without this the majority of events
        // would be re-verified + re-inserted (hitting the store's UNIQUE constraint)
        // in a later drain. An id is added only AFTER it verifies, so a forged copy
        // (valid id, bad signature) delivered first can't suppress the genuine one.
        val seenIds = ConcurrentSet<HexKey>()

        // Per-user relays that answered (EOSE'd) without holding this user's kind:3,
        // so re-querying them for this user is guaranteed-empty waste. routeByOutbox
        // subtracts these from a user's candidate relays, so a straggler is retried
        // only against relays that could plausibly still have it (never-asked, or
        // ones that timed out — which unlike a clean EOSE might just be slow).
        val askedEmpty = ConcurrentMap<HexKey, ConcurrentSet<NormalizedRelayUrl>>()

        // Contact lists delivered LATE by parked (slow-but-alive) relays. A parked
        // unit persists its events, then pushes any kind:3 it found here; the round
        // loop (the single graph-writer) folds these into hopOf/done/builder between
        // rounds, so a slow relay's follows still expand the frontier — just a round
        // or two later than the fast ones. Unbounded: parked delivery must never
        // block on the round loop draining it.
        val lateHarvest = Channel<Pair<NormalizedRelayUrl, Event>>(Channel.UNLIMITED)

        // Parked units still streaming. The crawl isn't done until this hits 0 (and
        // the frontier is empty), so we wait for slow relays' completeness without
        // gating each round on them. Incremented when a unit parks, decremented when
        // it finishes (or its park window elapses).
        val parkedInFlight = AtomicLong(0)

        // Background scope owning the parked subscriptions (and Tier-2 relay-list
        // sweeps). Set in [run]; cancelled once the crawl converges.
        var bgScope: CoroutineScope? = null

        var rounds = 0
        var contactListsFed = 0

        /**
         * A relay that HARD-failed (bad domain, TLS misconfig, dead HTTP code) is
         * dropped on the first strike: it will not fix itself. A TRANSIENT failure
         * (refused/reset/unreachable, or a 429/5xx) might clear, so it takes
         * MAX_DEAD_STRIKES before we give up. Pure timeouts never reach here — the
         * drain treats them as busy-retry and does not report them dead at all.
         */
        fun recordDead(failed: Map<NormalizedRelayUrl, DrainFailure>) {
            for ((r, kind) in failed) {
                when (kind) {
                    DrainFailure.HARD -> deadRelays.add(r)
                    DrainFailure.TRANSIENT ->
                        if (relayStrikes.merge(r, 1) { a, b -> a + b } >= MAX_DEAD_STRIKES) deadRelays.add(r)
                }
            }
        }

        /** The busiest live relays we've learned, excluding the dead ones. */
        fun topLiveRelays(cap: Int): List<NormalizedRelayUrl> =
            writeRelayFreq.entries
                .asSequence()
                .filter { it.key in liveRelays && it.key !in deadRelays }
                .sortedByDescending { it.value }
                .take(cap)
                .map { it.key }
                .toList()

        /**
         * Feed a user's contact list into the graph, harvest relay hints, stamp
         * the hop distance of newly-seen follows, and add them to the frontier.
         * Called once per user (guarded by `done`). Returns the count of
         * newly-discovered users.
         */
        fun ingest(
            source: HexKey,
            contacts: ContactListEvent,
        ): Int {
            val nextHop = (hopOf[source] ?: 0) + 1
            val follows = ArrayList<HexKey>()
            var fresh = 0
            for (tag in contacts.follows()) {
                follows.add(tag.pubKey)
                tag.relayUri?.let { relayHints.getOrPut(tag.pubKey) { ConcurrentSet() }.add(it) }
                if (tag.pubKey !in hopOf) {
                    hopOf[tag.pubKey] = nextHop
                    fresh++
                }
            }
            builder.addFollows(source, follows)
            contactListsFed++
            return fresh
        }

        /**
         * Feed into the graph the contact lists a drain just returned (deduped by
         * author; the store's canonical latest wins), marking fed authors done.
         * Only the authors we actually received are touched — no scan over the
         * whole still-missing set. Returns the count newly fed.
         */
        suspend fun harvest(events: List<Pair<NormalizedRelayUrl, Event>>): Int {
            var got = 0
            for ((_, ev) in events) {
                if (ev !is ContactListEvent) continue
                val pk = ev.pubKey
                if (pk in done) continue
                val contacts = contactsOf(pk) ?: continue
                done += pk
                ingest(pk, contacts)
                got++
            }
            return got
        }

        /**
         * Sharded backbone sweep (see SHARD_RELAYS). Splits the missing authors
         * across the top live relays — one shard per relay, so no relay gets the
         * same list twice — drains all shards concurrently, then rotates whoever's
         * still missing onto a different relay for up to SHARD_ROTATIONS passes.
         * Once the remainder is small it's cheap to broadcast it to every top relay
         * at once. Returns lists fed.
         */
        suspend fun shardedSweep(authors: Collection<HexKey>): Int {
            val top = topLiveRelays(SHARD_RELAYS)
            if (top.isEmpty()) return 0
            val n = top.size
            var missing = authors.filter { it !in done && contactsOf(it) == null }
            var got = 0
            var rotation = 0
            while (missing.size > SHARD_BROADCAST_THRESHOLD && rotation < SHARD_ROTATIONS) {
                val shards = Array(n) { ArrayList<HexKey>() }
                for (pk in missing) {
                    val base = ((pk.hashCode() % n) + n) % n
                    shards[(base + rotation) % n].add(pk)
                }
                val results =
                    coroutineScope {
                        top
                            .mapIndexedNotNull { i, relay ->
                                val shard = shards[i]
                                if (shard.isEmpty()) {
                                    null
                                } else {
                                    // Each drain gets its own dead-set — the concurrent
                                    // drains must not share a mutable HashMap.
                                    async {
                                        val dead = HashMap<NormalizedRelayUrl, DrainFailure>()
                                        val filters =
                                            mapOf(relay to shard.chunked(AUTHORS_PER_FILTER).map { Filter(kinds = FETCH_KINDS, authors = it) })
                                        drainGated(filters, dead) to dead
                                    }
                                }
                            }.awaitAll()
                    }
                for ((_, dead) in results) recordDead(dead)
                relaysContacted += top
                val flat = results.flatMap { it.first }
                for ((relay, _) in flat) liveRelays.add(relay)
                got += harvest(flat)
                missing = missing.filter { it !in done }
                rotation++
            }
            // Once the remainder is small it's cheap to ask every top relay for it
            // at once. If the rotations bailed with a still-large set, those authors
            // just aren't on the popular relays — leave them to the caller's outbox
            // pass rather than broadcast a huge list.
            if (missing.isNotEmpty() && missing.size <= SHARD_BROADCAST_THRESHOLD) {
                // Broadcast the small remainder to a wider set of busy relays than
                // the rotation used — recovers users whose list is only on a relay
                // ranked below the top SHARD_RELAYS.
                val live = topLiveRelays(BROADCAST_RELAYS)
                if (live.isNotEmpty()) {
                    val dead = HashMap<NormalizedRelayUrl, DrainFailure>()
                    val filters =
                        live.associateWith { missing.chunked(AUTHORS_PER_FILTER).map { Filter(kinds = FETCH_KINDS, authors = it) } }
                    val events = drainGated(filters, dead)
                    recordDead(dead)
                    relaysContacted += live
                    for ((relay, _) in events) liveRelays.add(relay)
                    got += harvest(events)
                }
            }
            return got
        }

        /**
         * Fetch kind:10002 relay lists for any [pubkeys] we don't already know, so
         * [routeByOutbox] can route their content query to their own write relays.
         *
         * Tier 1 queries the bounded relay-list discovery set (indexers + general
         * defaults), which aggregate kind:10002 for the whole network. Blocking,
         * because this round's routing needs the result.
         *
         * Tier 2 is a completeness net for the stragglers the indexers don't cover:
         * cast the widest net — every relay we've seen deliver events. Fired
         * fire-and-forget on [bgScope]: a stray 10002 might sit on any one relay, so
         * we don't skip any, but we can't block the crawl on a fan-out that large.
         * The results land in the store and improve routing for later rounds.
         */
        suspend fun ensureRelayLists(
            pubkeys: Set<HexKey>,
            allLiveRelays: Set<NormalizedRelayUrl>,
            bgScope: CoroutineScope,
        ) {
            val missing = pubkeys.filter { relaysOf(it) == null }
            if (missing.isEmpty()) return

            suspend fun query(
                authors: List<HexKey>,
                relays: Set<NormalizedRelayUrl>,
            ) {
                if (relays.isEmpty() || authors.isEmpty()) return
                val filters =
                    relays.associateWith {
                        authors.chunked(AUTHORS_PER_FILTER).map { chunk ->
                            Filter(kinds = listOf(AdvertisedRelayListEvent.KIND), authors = chunk)
                        }
                    }
                drainGated(filters, null)
            }

            val discovery = config.relayListDiscoveryRelays
            query(missing, discovery)

            val stillMissing = missing.filter { relaysOf(it) == null }
            val wide = allLiveRelays - discovery
            if (stillMissing.isNotEmpty() && wide.isNotEmpty()) {
                bgScope.launch { query(stillMissing, wide) }
            }
        }

        /**
         * Fetch NIP-09 kind:5 deletion requests that retract any report we gathered.
         * A reporter can delete their own kind:1984 report — a deletion valid only
         * from the reporter's own key, published to the reporter's outbox. So we
         * group report ids by their author and ask each author's write relays for
         * kind:5 events that cite those ids (`#e`), pulling only the deletions that
         * touch our reports. The events land in the store for the caller to apply.
         */
        suspend fun fetchReportDeletions(backbone: Set<NormalizedRelayUrl>) {
            val idsByAuthor = HashMap<HexKey, MutableList<HexKey>>()
            for (ev in store.query<Event>(Filter(kinds = listOf(ReportEvent.KIND)))) {
                if (ev is ReportEvent) idsByAuthor.getOrPut(ev.pubKey) { ArrayList() }.add(ev.id)
            }
            if (idsByAuthor.isEmpty()) return

            // Route each reporter to their own write relays (fallback: backbone).
            val perRelayAuthors = HashMap<NormalizedRelayUrl, MutableSet<HexKey>>()
            for (author in idsByAuthor.keys) {
                val write = relaysOf(author)?.writeRelaysNorm()?.takeIf { it.isNotEmpty() } ?: backbone
                for (relay in write) if (relay !in deadRelays) perRelayAuthors.getOrPut(relay) { HashSet() }.add(author)
            }
            if (perRelayAuthors.isEmpty()) return

            val filters =
                perRelayAuthors.mapValues { (_, authors) ->
                    buildList {
                        for (authorChunk in authors.chunked(AUTHORS_PER_FILTER)) {
                            // Scope #e to this author-chunk's own report ids, chunked to
                            // respect REQ limits. Any over-match (a filter pairing an
                            // author with another author's id) is harmless — the
                            // deleter-must-be-author check the caller runs rejects it.
                            val chunkIds = authorChunk.flatMap { idsByAuthor[it].orEmpty() }
                            for (idChunk in chunkIds.chunked(AUTHORS_PER_FILTER)) {
                                add(Filter(kinds = listOf(DeletionEvent.KIND), authors = authorChunk, tags = mapOf("e" to idChunk)))
                            }
                        }
                    }
                }
            drainGated(filters, null)
        }

        /**
         * Group [pubkeys] by the relays we should query for their events:
         *  - first try: the user's own kind:10002 write relays (the outbox model);
         *  - a retry (`attempts[pk] > 0`, its outbox already failed): outbox +
         *    [backbone] — the known-good relays other people write to;
         *  - no outbox at all: harvested hints + backbone + the general fallback.
         *
         * Also tallies each user's write relays into [writeRelayFreq] so the
         * backbone can be learned from the crawl. Authors are chunked per relay.
         */
        suspend fun routeByOutbox(
            pubkeys: Set<HexKey>,
            backbone: Set<NormalizedRelayUrl>,
        ): Map<NormalizedRelayUrl, List<Filter>> {
            val fallback = config.contentFallbackRelays
            val perRelay = HashMap<NormalizedRelayUrl, MutableSet<HexKey>>()

            for (pk in pubkeys) {
                val write = relaysOf(pk)?.writeRelaysNorm()?.takeIf { it.isNotEmpty() }
                write?.forEach { writeRelayFreq[it] = (writeRelayFreq[it] ?: 0) + 1 }
                val relays =
                    when {
                        write == null -> relayHints[pk]?.snapshot().orEmpty() + backbone + fallback
                        (attempts[pk] ?: 0) > 0 -> write + backbone
                        else -> write
                    }
                // Skip relays proven dead (routing to them only burns the drain
                // timeout) and relays that already EOSE'd without this user's list
                // (re-querying them for this user is guaranteed-empty waste).
                val emptied = askedEmpty[pk]
                for (relay in relays) {
                    if (relay in deadRelays) continue
                    if (emptied != null && relay in emptied) continue
                    perRelay.getOrPut(relay) { HashSet() }.add(pk)
                }
            }

            return perRelay.mapValues { (_, authors) ->
                authors.chunked(AUTHORS_PER_FILTER).map { chunk ->
                    Filter(kinds = FETCH_KINDS, authors = chunk)
                }
            }
        }

        /**
         * Dedup (crawl-wide [seenIds]), verify, and group-commit a unit's events,
         * returning the newly-stored ones tagged by relay. Safe to call concurrently
         * from many fast drain units AND parked coroutines: an id is added to
         * [seenIds] only AFTER a good signature (so a forged copy delivered first
         * can't suppress the genuine one), and [ConcurrentSet.add] is an atomic
         * test-and-set — two relays mirroring the same event race on it and only the
         * winner stores it, so a duplicate never reaches the store's UNIQUE constraint.
         * The store serializes the actual writes behind its own single-writer mutex.
         */
        private suspend fun persist(events: List<Pair<NormalizedRelayUrl, Event>>): List<Pair<NormalizedRelayUrl, Event>> {
            if (events.isEmpty()) return emptyList()
            val flushAt = config.insertBatchSize.coerceAtLeast(1)
            val fresh = ArrayList<Pair<NormalizedRelayUrl, Event>>()
            val buffer = ArrayList<Event>(flushAt)

            suspend fun flush() {
                if (buffer.isEmpty()) return
                val mark = TimeSource.Monotonic.markNow()
                store.batchInsert(buffer)
                insertNanos.addAndFetch(mark.elapsedNow().inWholeNanoseconds)
                eventsStored.addAndFetch(buffer.size.toLong())
                buffer.clear()
            }

            for ((relay, event) in events) {
                if (event.id in seenIds) continue
                val vMark = TimeSource.Monotonic.markNow()
                val ok = event.verify()
                verifyNanos.addAndFetch(vMark.elapsedNow().inWholeNanoseconds)
                if (!ok) {
                    Log.w("GrapeRankDataCrawler") { "dropped event ${event.id.take(8)} kind=${event.kind} — bad signature" }
                    continue
                }
                if (!seenIds.add(event.id)) continue // lost the race to a mirror; it stores it
                fresh.add(relay to event)
                buffer.add(event)
                if (buffer.size >= flushAt) flush()
            }
            flush()
            return fresh
        }

        /**
         * Wait for a subscription's terminal ([done]: EOSE/CLOSED/cannot), resetting
         * the [idleMs] window every time an event pings [activity]. So the wait ends
         * with "timeout" only after [idleMs] of actual SILENCE — a relay that keeps
         * streaming (however long its result set) is never cut mid-flight; only a
         * genuinely stalled one is. Used for the patient park window.
         */
        private suspend fun awaitTerminalOrIdle(
            done: CompletableDeferred<String>,
            activity: Channel<Unit>,
            idleMs: Long,
        ): String {
            while (true) {
                val r =
                    withTimeoutOrNull(idleMs) {
                        select {
                            done.onAwait { it }
                            activity.onReceive { ACTIVITY }
                        }
                    }
                when (r) {
                    null -> return "timeout" // idleMs elapsed with no event and no terminal
                    ACTIVITY -> Unit // an event arrived — reset the idle window and keep waiting
                    else -> return r // terminal reason
                }
            }
        }

        /**
         * Fold one late-delivered event from a parked relay into the graph. Only the
         * round loop calls this (directly or via [foldLateHarvest]), so graph state
         * stays single-writer. Returns true if it fed a new contact list.
         */
        private suspend fun ingestLate(
            relay: NormalizedRelayUrl,
            ev: Event,
        ): Boolean {
            liveRelays.add(relay)
            if (ev !is ContactListEvent) return false
            val pk = ev.pubKey
            // Only authors we actually crawled (in hopOf) and haven't fed yet. A late
            // list for an unknown author would get a wrong hop stamp from ingest.
            if (pk in done || pk !in hopOf) return false
            val contacts = contactsOf(pk) ?: return false
            done += pk
            ingest(pk, contacts)
            return true
        }

        /** Drain whatever parked relays have delivered so far. Returns lists fed. */
        private suspend fun foldLateHarvest(): Int {
            var got = 0
            while (true) {
                val (relay, ev) = lateHarvest.tryReceive().getOrNull() ?: break
                if (ingestLate(relay, ev)) got++
            }
            return got
        }

        /**
         * Subscribe each relay to its filters behind [limiter] and drain them. A relay
         * that reaches a terminal (EOSE/CLOSED/cannot-connect) within the FAST
         * [Config.timeoutMs] has its events persisted and returned so this round can
         * resolve the authors it was asked for. A relay still streaming when the fast
         * timeout elapses is not cut but PARKED: it hands its open subscription to
         * [bgScope] (releasing its limiter permit so the fast pool moves on) and keeps
         * receiving for up to [Config.parkTimeoutMs] more; whatever it eventually
         * delivers is persisted and its contact lists pushed to [lateHarvest] for the
         * round loop to fold in — so slow relays add completeness without holding up
         * the round. Each relay's filters are split into REQ-sized groups so a popular
         * relay routed thousands of authors doesn't emit a frame most relays reject.
         * Hard connect failures (fast into [deadOut], parked straight to [recordDead])
         * are marked dead. Returns only the FAST events, tagged by relay.
         */
        private suspend fun drainGated(
            filters: Map<NormalizedRelayUrl, List<Filter>>,
            deadOut: MutableMap<NormalizedRelayUrl, DrainFailure>?,
            answeredOut: MutableSet<NormalizedRelayUrl>? = null,
        ): List<Pair<NormalizedRelayUrl, Event>> {
            if (filters.isEmpty()) return emptyList()

            // Split each relay's filters into REQ-sized groups. A REQ frame carries ALL
            // its filters at once, so a popular relay routed thousands of authors would
            // otherwise produce a multi-MB frame that most relays reject ("message too
            // large"). Grouping by total entry count keeps each REQ under the 256KB cap.
            val units = ArrayList<Pair<NormalizedRelayUrl, List<Filter>>>()
            for ((relay, relayFilters) in filters) {
                var group = ArrayList<Filter>()
                var entries = 0
                for (f in relayFilters) {
                    val fe = filterEntries(f)
                    if (group.isNotEmpty() && entries + fe > MAX_REQ_ENTRIES) {
                        units.add(relay to group)
                        group = ArrayList()
                        entries = 0
                    }
                    group.add(f)
                    entries += fe
                }
                if (group.isNotEmpty()) units.add(relay to group)
            }

            // Per-relay failure classification (HARD wins over TRANSIENT); which relays
            // stalled past the fast window; and which did NOT cleanly EOSE (timed out,
            // parked, closed, or couldn't connect) — a relay absent from that set
            // answered definitively, so an author it didn't return is one it lacks.
            val failures = ConcurrentMap<NormalizedRelayUrl, DrainFailure>()
            val timedOut = ConcurrentSet<NormalizedRelayUrl>()
            val notAnswered = ConcurrentSet<NormalizedRelayUrl>()

            fun classify(
                reason: String,
                relay: NormalizedRelayUrl,
                into: ConcurrentMap<NormalizedRelayUrl, DrainFailure>,
            ) {
                classifyDrainFailure(reason)?.let { kind ->
                    into.merge(relay, kind) { a, b ->
                        if (a == DrainFailure.HARD || b == DrainFailure.HARD) DrainFailure.HARD else DrainFailure.TRANSIENT
                    }
                }
            }

            fun logSlow(
                relay: NormalizedRelayUrl,
                reason: String,
                elapsedMs: Long,
                groupFilters: List<Filter>,
            ) {
                if (!config.diagnose) return
                val authors = groupFilters.flatMap { it.authors.orEmpty() }
                val kinds = groupFilters.flatMap { it.kinds.orEmpty() }.distinct()
                log(
                    "[slow-relay] ${relay.url} $reason in ${elapsedMs}ms | kinds=$kinds authors=${authors.size}: " +
                        authors.take(30).joinToString(",") + (if (authors.size > 30) ",…" else ""),
                )
            }

            val fast =
                coroutineScope {
                    units
                        .map { (subRelay, groupFilters) ->
                            async {
                                limiter.withPermit(subRelay) {
                                    val subId = newSubId()
                                    val done = CompletableDeferred<String>()
                                    val unitEvents = Channel<Pair<NormalizedRelayUrl, Event>>(Channel.UNLIMITED)
                                    // Liveness signal for the parked idle timeout: every event pings
                                    // this (conflated, so bursts collapse to one) and resets the park
                                    // window, so a relay actively streaming is never cut mid-flight.
                                    val activity = Channel<Unit>(Channel.CONFLATED)
                                    val listener =
                                        object : SubscriptionListener {
                                            override fun onEvent(
                                                event: Event,
                                                isLive: Boolean,
                                                relay: NormalizedRelayUrl,
                                                forFilters: List<Filter>?,
                                            ) {
                                                unitEvents.trySend(relay to event)
                                                activity.trySend(Unit)
                                            }

                                            override fun onEose(
                                                relay: NormalizedRelayUrl,
                                                forFilters: List<Filter>?,
                                            ) {
                                                done.complete("eose")
                                            }

                                            override fun onClosed(
                                                message: String,
                                                relay: NormalizedRelayUrl,
                                                forFilters: List<Filter>?,
                                            ) {
                                                done.complete("closed:$message")
                                            }

                                            override fun onCannotConnect(
                                                relay: NormalizedRelayUrl,
                                                message: String,
                                                forFilters: List<Filter>?,
                                            ) {
                                                done.complete("cannot:$message")
                                            }
                                        }
                                    client.subscribe(subId, mapOf(subRelay to groupFilters), listener)
                                    val mark = TimeSource.Monotonic.markNow()
                                    val reason = withTimeoutOrNull(config.timeoutMs) { done.await() }
                                    if (reason != null) {
                                        // Terminal within the fast window — resolve this round.
                                        val elapsedMs = mark.elapsedNow().inWholeMilliseconds
                                        if (reason != "eose") notAnswered.add(subRelay)
                                        classify(reason, subRelay, failures)
                                        if (elapsedMs > SLOW_DRAIN_LOG_MS) logSlow(subRelay, reason, elapsedMs, groupFilters)
                                        unitEvents.close()
                                        client.unsubscribe(subId)
                                        persist(buildList { for (e in unitEvents) add(e) })
                                    } else {
                                        // Still streaming — hand off and let the round move on.
                                        notAnswered.add(subRelay)
                                        timedOut.add(subRelay)
                                        val scope = bgScope
                                        if (scope != null && config.parkTimeoutMs > config.timeoutMs) {
                                            parkedInFlight.addAndFetch(1)
                                            scope.launch {
                                                try {
                                                    // Idle timeout, not absolute: only cut after parkTimeoutMs
                                                    // of SILENCE (no event, no terminal), so a relay still
                                                    // streaming a large result set is never chopped mid-flight.
                                                    val late = awaitTerminalOrIdle(done, activity, config.parkTimeoutMs)
                                                    logSlow(subRelay, "parked→$late", mark.elapsedNow().inWholeMilliseconds, groupFilters)
                                                    // A parked relay that ends in a hard/transient failure (not a
                                                    // clean EOSE) is reported dead the same way a fast one would be.
                                                    val lateDead = ConcurrentMap<NormalizedRelayUrl, DrainFailure>()
                                                    classify(late, subRelay, lateDead)
                                                    recordDead(lateDead.snapshot())
                                                    unitEvents.close()
                                                    for (pair in persist(buildList { for (e in unitEvents) add(e) })) lateHarvest.trySend(pair)
                                                } finally {
                                                    client.unsubscribe(subId)
                                                    parkedInFlight.addAndFetch(-1)
                                                }
                                            }
                                        } else {
                                            logSlow(subRelay, "timeout", mark.elapsedNow().inWholeMilliseconds, groupFilters)
                                            unitEvents.close()
                                            client.unsubscribe(subId)
                                        }
                                        emptyList()
                                    }
                                }
                            }
                        }.awaitAll()
                        .flatten()
                }

            if (config.diagnose && timedOut.size() > 0) {
                log("[drain] parked ${timedOut.size()} slow relay(s) past ${config.timeoutMs}ms")
            }
            deadOut?.putAll(failures.snapshot())
            answeredOut?.addAll(filters.keys.filter { it !in notAnswered })
            return fast
        }

        suspend fun run(): Stats {
            val crawlMark = TimeSource.Monotonic.markNow()
            // Scope owning parked (slow-relay) subscriptions and the fire-and-forget
            // Tier-2 relay-list sweeps. SupervisorJob so one failure never cancels the
            // others; cancelled once the crawl converges. Published to [bgScope] so
            // drainGated can hand slow subs to it.
            val scope = CoroutineScope(coroutineContext + SupervisorJob())
            bgScope = scope

            while (rounds < config.maxRounds) {
                // Fold in whatever the parked (slow-but-alive) relays have delivered
                // since the last round — their late contact lists expand the frontier
                // a round or two behind the fast ones (single-writer: only here).
                foldLateHarvest()

                // Only crawl users within the hop budget; deeper users still appear
                // in the graph as follow targets, we just don't fetch their lists.
                val pending = hopOf.keys.filter { it !in done && (hopOf[it] ?: 0) < config.maxHops }
                if (pending.isEmpty()) {
                    // Frontier drained. If no slow relay is still streaming, a final
                    // fold catches any last-moment delivery and we're done; otherwise
                    // wait for a parked relay to deliver (completeness) and loop.
                    if (parkedInFlight.load() == 0L) {
                        if (foldLateHarvest() == 0) break else continue
                    }
                    withTimeoutOrNull(PARK_POLL_MS) { lateHarvest.receive() }?.let { ingestLate(it.first, it.second) }
                    continue
                }
                rounds++

                // Refresh the warm pool to this round's busiest relays and keep that
                // subscription open — reusing the same subId just updates the
                // desired-relay set, so these sockets stay up across the round.
                topLiveRelays(WARM_POOL_SIZE).takeIf { it.isNotEmpty() }?.let { warm ->
                    client.subscribe(WARM_SUB_ID, warm.associateWith { WARM_FILTERS }, null)
                }

                val discoveredBefore = hopOf.size
                val fedBefore = contactListsFed

                // Phase A — bulk-fetch from the busiest relays via the sharded sweep.
                // Most users' kind:3 lives on the big popular relays, so this clears
                // the majority cheaply (early rounds no-op until a backbone is learned).
                shardedSweep(pending)

                // Phase B — whoever the popular relays didn't have (niche outboxes):
                // resolve their kind:10002, then fetch from their own write relays,
                // drained a few at a time and skipping dead relays.
                val stragglers = pending.filter { it !in done }
                if (stragglers.isNotEmpty()) {
                    val backbone = topLiveRelays(BACKBONE_SIZE).toSet()
                    // Snapshot of every relay we've seen work, for the wide Tier-2
                    // sweep (taken now, before the Phase-B workers mutate liveRelays).
                    val allLive = liveRelays.filterTo(HashSet()) { it !in deadRelays }
                    ensureRelayLists(stragglers.toSet(), allLive, scope)

                    // Continuous worker pool instead of chunked awaitAll barriers, so
                    // no worker waits on a slow sibling and hot relays stay connected.
                    // Shared graph state stays single-writer: routeByOutbox runs only
                    // on the producer (keeps writeRelayFreq serial) and ingest runs
                    // only on the consumer (keeps done/builder/hopOf serial), now
                    // overlapped with draining instead of blocked behind each batch.
                    val routed = Channel<Pair<List<HexKey>, Map<NormalizedRelayUrl, List<Filter>>>>(config.drainConcurrency * 2)
                    val drainedOut = Channel<DrainedBatch>(Channel.UNLIMITED)
                    coroutineScope {
                        // Producer: route each batch by outbox (serial), backpressured
                        // by the bounded `routed` channel.
                        val producer =
                            launch {
                                for (batch in stragglers.chunked(USER_BATCH)) {
                                    val filters = routeByOutbox(batch.toSet(), backbone)
                                    routed.send(batch to filters)
                                }
                                routed.close()
                            }
                        // Drain workers: pure network, no shared graph-state writes
                        // except recordDead (concurrent-safe). Each captures the relays
                        // that cleanly EOSE'd, so the consumer can tell "answered empty"
                        // from "timed out" per user.
                        val workers =
                            List(config.drainConcurrency) {
                                launch {
                                    for ((batch, filters) in routed) {
                                        val dead = HashMap<NormalizedRelayUrl, DrainFailure>()
                                        val answered = HashSet<NormalizedRelayUrl>()
                                        val events = drainGated(filters, dead, answered)
                                        recordDead(dead)
                                        drainedOut.send(DrainedBatch(batch, filters, answered, events))
                                    }
                                }
                            }
                        // Consumer: single-writer ingest, overlapped with draining.
                        val consumer =
                            launch {
                                for (d in drainedOut) {
                                    relaysContacted += d.filters.keys
                                    // Any relay that gave us an event is proven live + useful.
                                    for ((relay, _) in d.events) liveRelays.add(relay)

                                    // Per user, record relays that answered (EOSE'd) but did
                                    // not return their kind:3, so they aren't re-queried there.
                                    val returnedByRelay = HashMap<NormalizedRelayUrl, MutableSet<HexKey>>()
                                    for ((relay, ev) in d.events) {
                                        if (ev is ContactListEvent) returnedByRelay.getOrPut(relay) { HashSet() }.add(ev.pubKey)
                                    }
                                    for (relay in d.answered) {
                                        val asked = d.filters[relay]?.flatMapTo(HashSet()) { it.authors.orEmpty() } ?: continue
                                        val returned = returnedByRelay[relay].orEmpty()
                                        for (pk in asked) {
                                            if (pk !in returned) askedEmpty.getOrPut(pk) { ConcurrentSet() }.add(relay)
                                        }
                                    }

                                    for (pk in d.batch) {
                                        if (pk in done) continue
                                        val contacts = contactsOf(pk)
                                        if (contacts != null) {
                                            done += pk
                                            ingest(pk, contacts)
                                        } else {
                                            val tries = (attempts[pk] ?: 0) + 1
                                            attempts[pk] = tries
                                            if (tries >= MAX_OUTBOX_ATTEMPTS) done += pk
                                        }
                                    }
                                }
                            }
                        producer.join()
                        workers.joinAll()
                        drainedOut.close()
                        consumer.join()
                    }
                }

                log(
                    "[graperank] round $rounds: pending=${pending.size}, " +
                        "gotList=${contactListsFed - fedBefore}, newUsers=${hopOf.size - discoveredBefore}, " +
                        "discovered=${hopOf.size}, done=${done.size}, dead=${deadRelays.size()}",
                )
            }

            // Crawl done — drop the warm pool.
            client.unsubscribe(WARM_SUB_ID)

            // Reports can be retracted. Ask each reporter's outbox for NIP-09 kind:5
            // deletions that cite the reports we gathered (#e-filtered to our report
            // ids). The events land in the store; the caller decides which reports
            // they actually retract. Run before cancelling [scope] so it can still
            // park slow relays.
            fetchReportDeletions(topLiveRelays(BACKBONE_SIZE).toSet())

            // Stop any parked subscriptions + Tier-2 relay-list sweeps still in flight
            // (whatever they fetched already landed in the store).
            scope.cancel()

            val hopHistogram =
                hopOf.values
                    .groupingBy { it }
                    .eachCount()
                    .toList()
                    .sortedBy { it.first }
                    .toMap()
            val downloadMs = crawlMark.elapsedNow().inWholeMilliseconds
            val verifyMs = verifyNanos.load() / 1_000_000
            val insertMs = insertNanos.load() / 1_000_000
            val stored = eventsStored.load()
            log(
                "[graperank] crawl complete: ${hopOf.size} discovered, $contactListsFed contact lists fed, " +
                    "${relaysContacted.size} relays contacted, ${deadRelays.size()} dead, $rounds rounds in $downloadMs ms; " +
                    "by hop: " + hopHistogram.entries.joinToString(" ") { "${it.key}=${it.value}" },
            )
            log(
                "[graperank] write path: $stored events stored, verify ${verifyMs}ms + insert ${insertMs}ms " +
                    "(summed across all drains, batch=${config.insertBatchSize})",
            )
            return Stats(
                rounds = rounds,
                contactListsFed = contactListsFed,
                relaysContacted = relaysContacted.size,
                hopHistogram = hopHistogram,
                downloadMs = downloadMs,
                verifyMs = verifyMs,
                insertMs = insertMs,
                eventsStored = stored,
            )
        }
    }

    /**
     * One Phase-B batch after draining: the users asked for, the relay->filters map
     * they were routed through, the relays that cleanly EOSE'd ([answered]), and the
     * fresh events. Carries enough for the consumer to attribute "answered but
     * empty" per user without re-deriving the routing.
     */
    private class DrainedBatch(
        val batch: List<HexKey>,
        val filters: Map<NormalizedRelayUrl, List<Filter>>,
        val answered: Set<NormalizedRelayUrl>,
        val events: List<Pair<NormalizedRelayUrl, Event>>,
    )

    /** Latest known kind:3 contact list for [pubKey] from the local store, or null. */
    private suspend fun contactsOf(pubKey: HexKey): ContactListEvent? =
        store
            .query<Event>(Filter(authors = listOf(pubKey), kinds = listOf(ContactListEvent.KIND), limit = 1))
            .firstOrNull() as? ContactListEvent

    /** Latest known kind:10002 advertised relay list for [pubKey] from the store, or null. */
    private suspend fun relaysOf(pubKey: HexKey): AdvertisedRelayListEvent? =
        store
            .query<Event>(Filter(authors = listOf(pubKey), kinds = listOf(AdvertisedRelayListEvent.KIND), limit = 1))
            .firstOrNull() as? AdvertisedRelayListEvent

    companion object {
        // Authors per REQ filter — keeps individual subscriptions within relay limits.
        private const val AUTHORS_PER_FILTER = 300

        // Max total "entries" (authors + ids + tag values) in a single REQ frame.
        // Each entry is a ~67-byte hex string, so 2500 ≈ 167KB — under the 256KB
        // message cap most relays enforce. drainGated groups filters to stay within.
        private const val MAX_REQ_ENTRIES = 2500

        // --diagnose: a REQ that takes longer than this to reach a terminal (EOSE or
        // timeout) is logged with its relay + filter, so slow relays can be replayed.
        private const val SLOW_DRAIN_LOG_MS = 4000L

        // Once the frontier is empty but parked relays are still streaming, how long
        // to block waiting for one of them to deliver before re-checking convergence.
        private const val PARK_POLL_MS = 2000L

        // Sentinel returned by the park idle-wait's select when an event arrived
        // (resets the window). A control string that can't collide with a relay's
        // CLOSED/cannot message, which are the only other select results.
        private const val ACTIVITY = " activity"

        // Times we re-query an unreachable user's outbox before giving up, so the
        // crawl still terminates on a finite graph.
        private const val MAX_OUTBOX_ATTEMPTS = 3

        // Users whose outboxes we fetch in a single drain. Draining thousands of
        // distinct outbox relays at once saturates connections and times out
        // (~250/drain succeeds, ~17k fails); keep the fan-out small.
        private const val USER_BATCH = 256

        // Sharded backbone sweep: split the still-missing authors into SHARD_RELAYS
        // lists, one per top relay, rotating up to SHARD_ROTATIONS times; once the
        // remainder drops below SHARD_BROADCAST_THRESHOLD, broadcast it at once.
        private const val SHARD_RELAYS = 10
        private const val SHARD_ROTATIONS = 6
        private const val SHARD_BROADCAST_THRESHOLD = 2000

        // The small-remainder broadcast goes to this many top live relays — a user's
        // kind:3 is often mirrored on a busy relay ranked below the top 10.
        private const val BROADCAST_RELAYS = 60

        // A relay that fails to CONNECT this many times is treated as dead. Kept
        // above 1 so a single transient connect blip doesn't evict a relay.
        private const val MAX_DEAD_STRIKES = 3

        // Most-used write relays kept as the known-good backbone for retrying users.
        private const val BACKBONE_SIZE = 30

        // Warm pool: hold a do-nothing subscription open to the busiest relays for
        // the whole crawl, so the connections we reuse every round survive the
        // between-round routing gaps. The filter matches an impossible event id, so
        // the relay EOSEs immediately and streams nothing — it only keeps sockets warm.
        private const val WARM_POOL_SIZE = 20
        private const val WARM_SUB_ID = "graperank-warm"
        private val WARM_FILTERS = listOf(Filter(ids = listOf("0".repeat(64))))

        // Kinds requested from relays during the crawl: the graph edges (contact
        // lists, mute lists, reports) PLUS the user's own kind:10002. A user's outbox
        // holds the freshest copy of their relay list, so folding 10002 into the same
        // query keeps routing current. The store keeps newest-by-created_at for the
        // replaceable 10002, so the freshest always wins regardless of source relay.
        private val FETCH_KINDS =
            listOf(ContactListEvent.KIND, MuteListEvent.KIND, ReportEvent.KIND, AdvertisedRelayListEvent.KIND)

        /** Count the size-driving entries in a filter: authors, ids, and tag values. */
        private fun filterEntries(f: Filter): Int =
            (f.authors?.size ?: 0) +
                (f.ids?.size ?: 0) +
                (f.tags?.values?.sumOf { it.size } ?: 0) +
                (f.tagsAll?.values?.sumOf { it.size } ?: 0)
    }
}
