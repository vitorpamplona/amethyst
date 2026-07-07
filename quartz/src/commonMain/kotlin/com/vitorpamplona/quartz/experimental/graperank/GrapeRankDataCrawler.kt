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
     * @param timeoutMs per-drain timeout.
     * @param diagnose log a breakdown of slow/unreachable relays on each drain timeout.
     * @param insertBatchSize how many verified events to group-commit per
     *   [IEventStore.batchInsert]. The outbox model streams the same events from
     *   many relays through a single SQLite writer, so batching amortizes the
     *   per-transaction + writer-mutex cost across the batch (coerced to `>= 1`).
     */
    class Config(
        val relayListDiscoveryRelays: Set<NormalizedRelayUrl>,
        val contentFallbackRelays: Set<NormalizedRelayUrl>,
        val maxRounds: Int = Int.MAX_VALUE,
        val maxHops: Int = Int.MAX_VALUE,
        val timeoutMs: Long = 10_000,
        val diagnose: Boolean = false,
        val insertBatchSize: Int = 500,
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
                                        drainGated(filters, dead, seenIds) to dead
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
                    val events = drainGated(filters, dead, seenIds)
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
                drainGated(filters, null, seenIds)
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
            drainGated(filters, null, seenIds)
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

        suspend fun run(): Stats {
            val crawlMark = TimeSource.Monotonic.markNow()
            // Scope for fire-and-forget relay-list discovery (see ensureRelayLists
            // Tier 2). SupervisorJob so one failing sweep never cancels the others;
            // cancelled when the crawl finishes.
            val bgScope = CoroutineScope(coroutineContext + SupervisorJob())

            while (rounds < config.maxRounds) {
                // Only crawl users within the hop budget; deeper users still appear
                // in the graph as follow targets, we just don't fetch their lists.
                val pending = hopOf.keys.filter { it !in done && (hopOf[it] ?: 0) < config.maxHops }
                if (pending.isEmpty()) break
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
                    ensureRelayLists(stragglers.toSet(), allLive, bgScope)

                    // Continuous worker pool instead of chunked awaitAll barriers, so
                    // no worker waits on a slow sibling and hot relays stay connected.
                    // Shared graph state stays single-writer: routeByOutbox runs only
                    // on the producer (keeps writeRelayFreq serial) and ingest runs
                    // only on the consumer (keeps done/builder/hopOf serial), now
                    // overlapped with draining instead of blocked behind each batch.
                    val routed = Channel<Pair<List<HexKey>, Map<NormalizedRelayUrl, List<Filter>>>>(DRAIN_CONCURRENCY * 2)
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
                            List(DRAIN_CONCURRENCY) {
                                launch {
                                    for ((batch, filters) in routed) {
                                        val dead = HashMap<NormalizedRelayUrl, DrainFailure>()
                                        val answered = HashSet<NormalizedRelayUrl>()
                                        val events = drainGated(filters, dead, seenIds, answered)
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

            // Crawl done — drop the warm pool and stop any background relay-list
            // sweeps still in flight (their results are already in the store).
            client.unsubscribe(WARM_SUB_ID)
            bgScope.cancel()

            // Reports can be retracted. Ask each reporter's outbox for NIP-09 kind:5
            // deletions that cite the reports we gathered (#e-filtered to our report
            // ids). The events land in the store; the caller decides which reports
            // they actually retract.
            fetchReportDeletions(topLiveRelays(BACKBONE_SIZE).toSet())

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
                    "(summed across ${DRAIN_CONCURRENCY} consumers, batch=${config.insertBatchSize})",
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

    /**
     * Subscribe each relay to its filters behind [limiter], drain until every
     * relay's subscription is terminal or the timeout elapses, verify+store the
     * events, and return them tagged by relay. Each relay gets its own gated
     * subscription so we never exceed its adaptive concurrent-subscription cap; a
     * relay's filters are split into REQ-sized groups so a popular relay routed
     * thousands of authors doesn't produce a multi-MB frame that most relays
     * reject outright. Hard connect failures are reported into [deadOut]. Events
     * whose id is already in the crawl-wide [seen] set are dropped before the
     * expensive verify+store; verified ids are added to it so later drains skip them.
     */
    private suspend fun drainGated(
        filters: Map<NormalizedRelayUrl, List<Filter>>,
        deadOut: MutableMap<NormalizedRelayUrl, DrainFailure>?,
        seen: ConcurrentSet<HexKey>,
        answeredOut: MutableSet<NormalizedRelayUrl>? = null,
    ): List<Pair<NormalizedRelayUrl, Event>> {
        if (filters.isEmpty()) return emptyList()
        val eventChannel = Channel<Pair<NormalizedRelayUrl, Event>>(Channel.UNLIMITED)

        // Split each relay's filters into REQ-sized groups. A REQ frame carries ALL
        // its filters at once, so a popular relay routed thousands of authors would
        // otherwise produce a multi-MB frame that most relays reject ("message too
        // large") — silently dropping every author in it. Grouping by total entry
        // count keeps each REQ well under the common 256KB cap.
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

        // Per-relay failure classification, HARD winning over TRANSIENT across a
        // relay's several REQ-groups; plus which relays stalled to a timeout.
        val failures = ConcurrentMap<NormalizedRelayUrl, DrainFailure>()
        val timedOut = ConcurrentSet<NormalizedRelayUrl>()
        // Relays that did NOT cleanly EOSE every group (timed out, closed, or
        // couldn't connect). A relay absent from this set answered definitively —
        // so an author it was asked for but didn't return is one it simply lacks.
        val notAnswered = ConcurrentSet<NormalizedRelayUrl>()

        val collected = mutableListOf<Pair<NormalizedRelayUrl, Event>>()
        coroutineScope {
            // Single consumer per drain: dedup against the crawl-wide [seen] set,
            // verify, and group-commit to the store. Duplicates (the same event from
            // another relay, drain, or round) are skipped BEFORE the expensive Schnorr
            // verify + store write. An id is added to [seen] only after it verifies, so
            // a forged copy (valid id, bad signature) delivered first can't suppress
            // the genuine one. Verified events are buffered and flushed via batchInsert
            // so the per-transaction + writer-mutex cost is paid once per
            // [insertBatchSize], not once per event. (A relay whose every event was
            // already seen won't be credited into `liveRelays` by this drain — that's
            // fine: it's a redundant mirror that added nothing new.)
            val consumer =
                launch {
                    val flushAt = config.insertBatchSize.coerceAtLeast(1)
                    val buffer = ArrayList<Event>(flushAt)

                    suspend fun flush() {
                        if (buffer.isEmpty()) return
                        val mark = TimeSource.Monotonic.markNow()
                        store.batchInsert(buffer)
                        insertNanos.addAndFetch(mark.elapsedNow().inWholeNanoseconds)
                        eventsStored.addAndFetch(buffer.size.toLong())
                        buffer.clear()
                    }

                    for ((relay, event) in eventChannel) {
                        if (event.id in seen) continue
                        val vMark = TimeSource.Monotonic.markNow()
                        val ok = event.verify()
                        verifyNanos.addAndFetch(vMark.elapsedNow().inWholeNanoseconds)
                        if (!ok) {
                            Log.w("GrapeRankDataCrawler") { "dropped event ${event.id.take(8)} kind=${event.kind} — bad signature" }
                            continue
                        }
                        seen.add(event.id)
                        collected.add(relay to event)
                        buffer.add(event)
                        if (buffer.size >= flushAt) flush()
                    }
                    flush()
                }
            // One gated subscription per (relay, REQ-group). The permit is held for
            // the group's whole life, so concurrent subs on a relay never exceed its
            // adaptive cap.
            units
                .map { (subRelay, groupFilters) ->
                    launch {
                        limiter.withPermit(subRelay) {
                            val subId = newSubId()
                            val done = CompletableDeferred<String>()
                            val groupListener =
                                object : SubscriptionListener {
                                    override fun onEvent(
                                        event: Event,
                                        isLive: Boolean,
                                        relay: NormalizedRelayUrl,
                                        forFilters: List<Filter>?,
                                    ) {
                                        eventChannel.trySend(relay to event)
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
                            client.subscribe(subId, mapOf(subRelay to groupFilters), groupListener)
                            try {
                                val reason = withTimeoutOrNull(config.timeoutMs) { done.await() } ?: "timeout"
                                if (reason == "timeout") timedOut.add(subRelay)
                                if (reason != "eose") notAnswered.add(subRelay)
                                classifyDrainFailure(reason)?.let { kind ->
                                    failures.merge(subRelay, kind) { a, b ->
                                        if (a == DrainFailure.HARD || b == DrainFailure.HARD) DrainFailure.HARD else DrainFailure.TRANSIENT
                                    }
                                }
                            } finally {
                                client.unsubscribe(subId)
                            }
                        }
                    }
                }.joinAll()
            // All subscriptions are torn down; no more events can arrive. Close the
            // channel so the consumer drains what's buffered and completes.
            eventChannel.close()
            consumer.join()
        }
        if (config.diagnose && timedOut.size() > 0) {
            val stalled = timedOut.snapshot()
            val eventsPer = collected.groupingBy { it.first }.eachCount()
            val detail = stalled.take(12).joinToString(", ") { "${it.url}(${eventsPer[it] ?: 0}ev)" }
            log("[drain] timeout ${config.timeoutMs}ms: ${stalled.size} slow(no EOSE)" + (if (detail.isNotEmpty()) " | slow: $detail" else ""))
        }
        deadOut?.putAll(failures.snapshot())
        answeredOut?.addAll(filters.keys.filter { it !in notAnswered })
        return collected
    }

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

        // Times we re-query an unreachable user's outbox before giving up, so the
        // crawl still terminates on a finite graph.
        private const val MAX_OUTBOX_ATTEMPTS = 3

        // Users whose outboxes we fetch in a single drain. Draining thousands of
        // distinct outbox relays at once saturates connections and times out
        // (~250/drain succeeds, ~17k fails); keep the fan-out small.
        private const val USER_BATCH = 256

        // Global content-drain fan-out — how many outbox batches we drain at once. A
        // GLOBAL bound (memory / open sockets); the per-relay concurrency limit is
        // enforced separately by AdaptiveRelayLimiter. A higher global fan-out
        // re-floods busy hubs faster than demotion catches up, so keep it moderate.
        private const val DRAIN_CONCURRENCY = 24

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
