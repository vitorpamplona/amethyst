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
package com.vitorpamplona.amethyst.cli.commands

import com.vitorpamplona.amethyst.cli.Args
import com.vitorpamplona.amethyst.cli.Context
import com.vitorpamplona.amethyst.cli.DataDir
import com.vitorpamplona.amethyst.cli.DrainFailure
import com.vitorpamplona.amethyst.cli.Output
import com.vitorpamplona.amethyst.commons.defaults.Constants
import com.vitorpamplona.amethyst.commons.defaults.DefaultIndexerRelayList
import com.vitorpamplona.quartz.experimental.graperank.GrapeRank
import com.vitorpamplona.quartz.experimental.graperank.GrapeRankParams
import com.vitorpamplona.quartz.experimental.graperank.TrustGraphBuilder
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip02FollowList.ContactListEvent
import com.vitorpamplona.quartz.nip51Lists.muteList.MuteListEvent
import com.vitorpamplona.quartz.nip56Reports.ReportEvent
import com.vitorpamplona.quartz.nip65RelayList.AdvertisedRelayListEvent
import com.vitorpamplona.quartz.nip85TrustedAssertions.list.TrustProviderListEvent
import com.vitorpamplona.quartz.nip85TrustedAssertions.list.serviceProviders
import com.vitorpamplona.quartz.nip85TrustedAssertions.list.tags.ProviderTypes
import com.vitorpamplona.quartz.nip85TrustedAssertions.list.tags.ServiceProviderTag
import com.vitorpamplona.quartz.nip85TrustedAssertions.list.tags.ServiceType
import com.vitorpamplona.quartz.nip85TrustedAssertions.users.ContactCardEvent
import com.vitorpamplona.quartz.nip85TrustedAssertions.users.tags.RankTag
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.coroutineContext
import kotlin.math.roundToInt

/**
 * `amy graperank [OBSERVER] [flags]` — compute GrapeRank web-of-trust scores.
 *
 * GrapeRank assigns every user reachable in the follow/mute/report graph a
 * subjective trust score in `[0, 1]` from the observer's point of view (the
 * observer has full self-trust). It crawls the follow graph outward using the
 * outbox model — each user's kind:10002 write relays are located first, then
 * their kind:3 / kind:10000 / kind:1984 events are fetched from *their own*
 * relays. The crawl is exhaustive: it keeps going, with no user cap, until every
 * discovered user's outbox has been checked and their contact list pulled (an
 * unreachable outbox is retried a few times), then runs the scoring engine in
 * `commons/wot`.
 *
 * Prints a ranked list (text, or one JSON object under `--json`). With
 * `--publish`, results are also published as NIP-85 kind:30382 `ContactCardEvent`
 * trusted assertions (one per scored user, `rank = round(score*100)`).
 *
 * Sub-verbs complete the NIP-85 provider experience — the discovery layer that
 * lets clients find and consume those assertions:
 *  - `amy graperank register` — advertise a `30382:rank` provider in the
 *    account's kind:10040 [TrustProviderListEvent] (defaults to self, so a
 *    provider publishing ranks announces where to find them).
 *  - `amy graperank providers [USER]` — list a user's trusted providers.
 */
object GrapeRankCommand {
    // Authors per REQ filter — keeps individual subscriptions within relay limits.
    private const val AUTHORS_PER_FILTER = 300

    // Concurrent publishes when writing NIP-85 cards.
    private const val PUBLISH_CONCURRENCY = 16

    // Times we re-query an unreachable user's outbox before giving up on it, so
    // the crawl still terminates on a finite graph.
    private const val MAX_OUTBOX_ATTEMPTS = 3

    // Users whose outboxes we fetch in a single drain. Draining thousands of
    // distinct outbox relays at once saturates connections and times out
    // (empirically ~250 users/drain succeeds, ~17k fails); keep the fan-out small.
    private const val USER_BATCH = 256

    // Global content-drain fan-out — how many outbox batches we drain at once.
    // This is a GLOBAL bound (memory / open sockets); the per-relay concurrency
    // limit is enforced separately and adaptively by [AdaptiveRelayLimiter]
    // (drains run with gatePerRelay=true), which starts every relay at 100
    // concurrent subs and demotes only the ones that complain (100 → 20 → 10).
    // The two compose: at fan-out 24 a well-behaved relay runs at up to 24
    // concurrent subs, while a relay that pushes back is cut to 20 then 10 —
    // below the global bound, so the ladder actually bites. A higher global
    // fan-out (measured at 48) *re-floods* the busy hubs faster than demotion
    // catches up ("max concurrent subscription count reached" spikes) and
    // regressed wall-time, so keep the global bound moderate and let the
    // per-relay cap do the targeting.
    private const val DRAIN_CONCURRENCY = 24

    // Sharded backbone sweep: instead of asking every popular relay for the
    // same full author list (N× redundant), split the still-missing authors
    // into SHARD_RELAYS lists and send each to ONE of the top relays. Authors a
    // relay doesn't have rotate onto a different relay next pass, up to
    // SHARD_ROTATIONS times, so over a few passes each author is tried on
    // several popular relays. Once the remaining set drops below
    // SHARD_BROADCAST_THRESHOLD it's cheap to just ask them all at once.
    private const val SHARD_RELAYS = 10
    private const val SHARD_ROTATIONS = 6
    private const val SHARD_BROADCAST_THRESHOLD = 2000

    // The small-remainder broadcast (once a sweep is under the threshold) goes to
    // this many top live relays, not just the SHARD_RELAYS the rotation used —
    // a user's kind:3 is often mirrored on a busy relay ranked below the top 10,
    // which is where the old last-mile pass found its stragglers.
    private const val BROADCAST_RELAYS = 60

    // A relay that fails to CONNECT this many times is treated as dead and
    // dropped from routing, so we stop paying the drain timeout on it. Kept above
    // 1 so a single transient connect blip doesn't evict a relay for the run.
    private const val MAX_DEAD_STRIKES = 3

    // Broad, big general relays that carry kind:10002 for many users, added to the
    // discovery set to raise the odds of resolving a stranger's outbox. Every entry
    // is NIP-11 liveness-checked — dead relays only add timeouts.
    private val EXTRA_DISCOVERY_RELAYS: Set<NormalizedRelayUrl> =
        listOf(
            "wss://relay.damus.io",
            "wss://relay.snort.social",
            "wss://offchain.pub",
            "wss://nostr.land",
            "wss://eden.nostr.land",
        ).mapNotNull { RelayUrlNormalizer.normalizeOrNull(it) }.toSet()

    // How many of the most-used write relays (learned from everyone's kind:10002)
    // to keep as the known-good backbone for retrying users we couldn't reach.
    private const val BACKBONE_SIZE = 30

    // Warm pool: hold a persistent, do-nothing subscription open to the busiest
    // WARM_POOL_SIZE relays for the whole crawl, so the connections we reuse
    // every round survive the between-round routing gaps (and niche-relay churn)
    // instead of being dropped ~300ms after a wave ends and reconnected next
    // round. The filter matches an impossible event id, so the relay EOSEs
    // immediately and streams nothing — it only keeps the socket warm.
    private const val WARM_POOL_SIZE = 20
    private const val WARM_SUB_ID = "graperank-warm"
    private val WARM_FILTERS = listOf(Filter(ids = listOf("0".repeat(64))))

    suspend fun dispatch(
        dataDir: DataDir,
        tail: Array<String>,
    ): Int =
        // Sub-verbs are explicit words; anything else (npub / hex / nprofile /
        // NIP-05, or nothing) is the OBSERVER positional for a score computation.
        when (tail.firstOrNull()) {
            "register" -> register(dataDir, tail.drop(1).toTypedArray())
            "providers" -> providers(dataDir, tail.drop(1).toTypedArray())
            else -> run(dataDir, tail)
        }

    suspend fun run(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val observerArg = args.positionalOrNull(0)
        // Crawl to full convergence by default (every reachable user's outbox
        // checked). --max-rounds is only a safety backstop; --max-hops bounds the
        // follow-graph distance from the observer that we crawl (Brainstorm uses 8).
        val maxRounds = args.intFlag("max-rounds", Int.MAX_VALUE)
        val maxHops = args.intFlag("max-hops", Int.MAX_VALUE)
        val limit = args.intFlag("limit", 100)
        val minScore = args.flag("min-score")?.toDoubleOrNull() ?: 0.0
        val offline = args.bool("offline")
        val diagnose = args.bool("diagnose")
        val timeoutMs = args.longFlag("timeout", 10L) * 1000
        val doPublish = args.bool("publish")
        val minRank = args.intFlag("min-rank", 1)
        val publishLimit = args.intFlag("publish-limit", 500)
        val publishRelaysArg = args.flag("publish-relay")
        // Benchmark: build + sign one kind:30382 card per scored user (rank >=
        // --min-rank) with a throwaway key and time it, WITHOUT publishing.
        // Measures the id-hash + Schnorr-sign cost of emitting the full card set.
        val benchSign = args.bool("bench-sign")

        val params =
            GrapeRankParams(
                attenuation = args.flag("attenuation")?.toDoubleOrNull() ?: GrapeRankParams().attenuation,
                rigor = args.flag("rigor")?.toDoubleOrNull() ?: GrapeRankParams().rigor,
            )

        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val observer = observerArg?.let { ctx.requireUserHex(it) } ?: ctx.identity.pubKeyHex

            val graphKinds = listOf(ContactListEvent.KIND, MuteListEvent.KIND, ReportEvent.KIND)
            // Kinds requested from relays during the crawl: the graph edges PLUS the
            // user's own kind:10002. A user's outbox holds the freshest copy of their
            // relay list, so folding 10002 into the same query we send their outbox
            // keeps our routing current instead of trusting a possibly-stale indexer
            // copy. Safe to also pull from popular relays in the sweep — the store
            // keeps newest-by-created_at for the replaceable 10002, so the freshest
            // always wins regardless of which relay delivered it.
            val fetchKinds = graphKinds + AdvertisedRelayListEvent.KIND

            // The graph is built incrementally: contact lists stream straight into a
            // compact int-CSR structure and the Event is discarded, so the whole
            // network fits in memory without holding millions of kind:3 objects.
            val builder = TrustGraphBuilder()
            var rounds = 0
            var relaysContactedCount = 0
            var contactListsFed = 0
            // Wall time to read + deserialize the contact lists out of the store
            // (offline path only; online streams them in during the crawl). This
            // is the real pre-scoring cost — the int-CSR build afterwards is a
            // cheap in-memory pack.
            var storeLoadMs: Long? = null
            // Wall time to crawl + download the whole graph off the relays
            // (online path only) — rounds + last-mile sweep, i.e. everything up
            // to the point the graph is fully fetched. This is network-bound and
            // dominates a from-scratch run.
            var downloadMs: Long? = null

            val hopOf = HashMap<HexKey, Int>()
            if (!offline) {
                val crawlStart = System.nanoTime()
                // Scope for fire-and-forget relay-list discovery: the wide Tier-2
                // sweep (ensureRelayLists) casts kind:10002 queries across every relay
                // we know, but we don't block the crawl on it — its results just
                // enrich routing for later rounds. SupervisorJob so one failing sweep
                // never cancels the others; cancelled when the crawl finishes.
                val bgScope = CoroutineScope(coroutineContext + SupervisorJob())
                val discovered = hashSetOf(observer)
                hopOf[observer] = 0
                // Per-user relay hints harvested from the `p`-tag relay hints in the
                // contact lists we crawl (A's follow of B says where B writes) — a
                // discovery tier below each user's kind:10002 outbox. Concurrent:
                // the Phase-B producer reads these while the consumer's ingest writes
                // them (see the worker-pool below), so both map and inner sets are
                // thread-safe.
                val relayHints = ConcurrentHashMap<HexKey, MutableSet<NormalizedRelayUrl>>()
                // Users we're finished with this run: we fed their latest kind:3, or
                // ran out of retry attempts on an unreachable outbox.
                val done = hashSetOf<HexKey>()
                // Outbox retry counts. Concurrent: the producer reads (to widen a
                // retry's routing) while the consumer increments.
                val attempts = ConcurrentHashMap<HexKey, Int>()
                val relaysContacted = hashSetOf<NormalizedRelayUrl>()
                // Known-good relay pool, learned from the crawl itself: how often each
                // relay appears as someone's write relay, and which relays actually
                // delivered events (so we know they connect and work). The most-common
                // live relays become the `backbone` we retry unreachable users against.
                val writeRelayFreq = HashMap<NormalizedRelayUrl, Int>()
                val liveRelays = hashSetOf<NormalizedRelayUrl>()
                // Relays that failed to connect MAX_DEAD_STRIKES times — dropped
                // from all routing so a wave stops eating the timeout on them.
                // Concurrent: drain workers strike relays while the producer reads
                // deadRelays to prune routing.
                val deadRelays = ConcurrentHashMap.newKeySet<NormalizedRelayUrl>()
                val relayStrikes = ConcurrentHashMap<NormalizedRelayUrl, Int>()

                // A relay that HARD-failed (bad domain, TLS misconfig, dead HTTP
                // code — see DrainFailure) is dropped on the first strike: it will
                // not fix itself. A TRANSIENT failure (refused/reset/unreachable,
                // or a 429/5xx) might clear, so it takes MAX_DEAD_STRIKES before we
                // give up. Pure timeouts never reach here — the drain treats them as
                // busy-retry and does not report them dead at all.
                fun recordDead(failed: Map<NormalizedRelayUrl, DrainFailure>) {
                    for ((r, kind) in failed) {
                        when (kind) {
                            DrainFailure.HARD -> deadRelays.add(r)
                            DrainFailure.TRANSIENT ->
                                if (relayStrikes.merge(r, 1, Int::plus)!! >= MAX_DEAD_STRIKES) deadRelays.add(r)
                        }
                    }
                }

                // The busiest live relays we've learned, excluding the dead ones.
                fun topLiveRelays(cap: Int): List<NormalizedRelayUrl> =
                    writeRelayFreq.entries
                        .asSequence()
                        .filter { it.key in liveRelays && it.key !in deadRelays }
                        .sortedByDescending { it.value }
                        .take(cap)
                        .map { it.key }
                        .toList()

                // Feed a user's contact list into the graph, harvest relay hints, stamp
                // the hop distance of newly-seen follows, and add them to the frontier.
                // Called once per user (guarded by `done`). Returns the count of
                // newly-discovered users.
                fun ingest(
                    source: HexKey,
                    contacts: ContactListEvent,
                ): Int {
                    val nextHop = (hopOf[source] ?: 0) + 1
                    val follows = ArrayList<HexKey>()
                    var fresh = 0
                    for (tag in contacts.follows()) {
                        follows.add(tag.pubKey)
                        tag.relayUri?.let { relayHints.getOrPut(tag.pubKey) { ConcurrentHashMap.newKeySet() }.add(it) }
                        if (discovered.add(tag.pubKey)) {
                            hopOf[tag.pubKey] = nextHop
                            fresh++
                        }
                    }
                    builder.addFollows(source, follows)
                    contactListsFed++
                    return fresh
                }

                // Feed into the graph the contact lists a drain just returned
                // (deduped by author; the store's canonical latest wins), marking
                // fed authors done. Only the authors we actually received are
                // touched — no scan over the whole still-missing set. Returns the
                // count newly fed.
                suspend fun harvest(events: List<Pair<NormalizedRelayUrl, Event>>): Int {
                    var got = 0
                    for ((_, ev) in events) {
                        if (ev !is ContactListEvent) continue
                        val pk = ev.pubKey
                        if (pk in done) continue
                        val contacts = ctx.contactsOf(pk) ?: continue
                        done += pk
                        ingest(pk, contacts)
                        got++
                    }
                    return got
                }

                // Sharded backbone sweep (see SHARD_RELAYS). Splits the missing
                // authors across the top live relays — one shard per relay, so no
                // relay gets the same list twice — drains all shards concurrently,
                // then rotates whoever's still missing onto a different relay for up
                // to SHARD_ROTATIONS passes. Once the remainder is small it's cheap
                // to broadcast it to every top relay at once. Returns lists fed.
                suspend fun shardedSweep(authors: Collection<HexKey>): Int {
                    val top = topLiveRelays(SHARD_RELAYS)
                    if (top.isEmpty()) return 0
                    val n = top.size
                    var missing = authors.filter { it !in done && ctx.contactsOf(it) == null }
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
                                            // drains must not share a mutable HashSet.
                                            async {
                                                val dead = HashMap<NormalizedRelayUrl, DrainFailure>()
                                                val filters =
                                                    mapOf(relay to shard.chunked(AUTHORS_PER_FILTER).map { Filter(kinds = fetchKinds, authors = it) })
                                                ctx.drain(filters, timeoutMs, diagnose, dead, gatePerRelay = true) to dead
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
                    // Once the remainder is small it's cheap to ask every top relay
                    // for it at once. If the rotations bailed with a still-large set,
                    // those authors just aren't on the popular relays — leave them to
                    // the caller's outbox pass rather than broadcast a huge list.
                    if (missing.isNotEmpty() && missing.size <= SHARD_BROADCAST_THRESHOLD) {
                        // Broadcast the small remainder to a wider set of busy relays
                        // than the rotation used — recovers users whose list is only
                        // on a relay ranked below the top SHARD_RELAYS.
                        val live = topLiveRelays(BROADCAST_RELAYS)
                        if (live.isNotEmpty()) {
                            val dead = HashMap<NormalizedRelayUrl, DrainFailure>()
                            val filters =
                                live.associateWith { missing.chunked(AUTHORS_PER_FILTER).map { Filter(kinds = fetchKinds, authors = it) } }
                            val events = ctx.drain(filters, timeoutMs, diagnose, dead, gatePerRelay = true)
                            recordDead(dead)
                            relaysContacted += live
                            for ((relay, _) in events) liveRelays.add(relay)
                            got += harvest(events)
                        }
                    }
                    return got
                }

                // Crawl to full graph depth (no user cap; --max-hops bounds the follow
                // distance). Each run fetches every discovered user's LATEST
                // kind:3/10000/1984 once from their outbox (a freshness pass — grouped
                // by write relay in routeByOutbox), unless we already fetched it this
                // run (`done`). An unreachable outbox is retried up to
                // MAX_OUTBOX_ATTEMPTS then dropped so the crawl terminates.
                while (rounds < maxRounds) {
                    // Only crawl users within the hop budget; deeper users still appear
                    // in the graph as follow targets, we just don't fetch their lists.
                    val pending = discovered.filter { it !in done && (hopOf[it] ?: 0) < maxHops }
                    if (pending.isEmpty()) break
                    rounds++

                    // Refresh the warm pool to this round's busiest relays and keep
                    // that subscription open — reusing the same subId just updates the
                    // desired-relay set, so these sockets stay up across the round.
                    topLiveRelays(WARM_POOL_SIZE).takeIf { it.isNotEmpty() }?.let { warm ->
                        ctx.client.subscribe(WARM_SUB_ID, warm.associateWith { WARM_FILTERS }, null)
                    }

                    val discoveredBefore = discovered.size
                    val fedBefore = contactListsFed

                    // Phase A — bulk-fetch from the busiest relays via the sharded
                    // sweep. Most users' kind:3 lives on the big popular relays, so
                    // this clears the majority cheaply, without asking every relay for
                    // the same authors (early rounds no-op until a backbone is learned).
                    shardedSweep(pending)

                    // Phase B — whoever the popular relays didn't have (niche
                    // outboxes): resolve their kind:10002, then fetch from their own
                    // write relays, drained a few at a time and skipping dead relays.
                    val stragglers = pending.filter { it !in done }
                    if (stragglers.isNotEmpty()) {
                        val backbone = topLiveRelays(BACKBONE_SIZE).toSet()
                        // Snapshot of every relay we've seen work, for the wide Tier-2
                        // sweep (taken now, on this single coroutine, before the Phase-B
                        // workers start mutating liveRelays).
                        val allLive = (liveRelays - deadRelays).toSet()
                        ensureRelayLists(ctx, stragglers.toSet(), allLive, bgScope, timeoutMs, diagnose)

                        // Continuous worker pool instead of chunked awaitAll barriers.
                        // The old shape drained DRAIN_CONCURRENCY batches, waited for the
                        // SLOWEST (a dead relay's full timeout), ingested, then started
                        // the next group — so every batch's tail idled the whole pool.
                        // Here a fixed set of DRAIN_CONCURRENCY workers pulls batches off
                        // a queue and grabs the next the instant a drain returns, so no
                        // worker waits on a slow sibling and hot relays stay connected
                        // (some worker is always subscribed). Shared graph state stays
                        // single-writer: routeByOutbox runs only on the producer (keeps
                        // writeRelayFreq serial) and ingest runs only on the consumer
                        // (keeps discovered/done/builder/hopOf serial), now overlapped
                        // with draining instead of blocked behind each batch.
                        val routed = Channel<Pair<List<HexKey>, Map<NormalizedRelayUrl, List<Filter>>>>(DRAIN_CONCURRENCY * 2)
                        val drainedOut = Channel<Triple<List<HexKey>, Set<NormalizedRelayUrl>, List<Pair<NormalizedRelayUrl, Event>>>>(Channel.UNLIMITED)
                        coroutineScope {
                            // Producer: route each batch by outbox (serial), backpressured
                            // by the bounded `routed` channel so we don't precompute every
                            // filter map at once.
                            val producer =
                                launch {
                                    for (batch in stragglers.chunked(USER_BATCH)) {
                                        val filters = routeByOutbox(ctx, batch.toSet(), relayHints, backbone, attempts, writeRelayFreq, fetchKinds, deadRelays)
                                        routed.send(batch to filters)
                                    }
                                    routed.close()
                                }
                            // Drain workers: pure network, no shared graph-state writes
                            // except recordDead (concurrent-safe now).
                            val workers =
                                List(DRAIN_CONCURRENCY) {
                                    launch {
                                        for ((batch, filters) in routed) {
                                            val dead = HashMap<NormalizedRelayUrl, DrainFailure>()
                                            val events = ctx.drain(filters, timeoutMs, diagnose, dead, gatePerRelay = true)
                                            recordDead(dead)
                                            drainedOut.send(Triple(batch, filters.keys, events))
                                        }
                                    }
                                }
                            // Consumer: single-writer ingest, overlapped with draining.
                            val consumer =
                                launch {
                                    for ((batch, relays, events) in drainedOut) {
                                        relaysContacted += relays
                                        // Any relay that gave us an event is proven live + useful.
                                        for ((relay, _) in events) liveRelays.add(relay)
                                        for (pk in batch) {
                                            if (pk in done) continue
                                            val contacts = ctx.contactsOf(pk)
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

                    System.err.println(
                        "[graperank] round $rounds: pending=${pending.size}, " +
                            "gotList=${contactListsFed - fedBefore}, newUsers=${discovered.size - discoveredBefore}, " +
                            "discovered=${discovered.size}, done=${done.size}, dead=${deadRelays.size}",
                    )
                }

                // Crawl done — drop the warm pool and stop any background relay-list
                // sweeps still in flight (their results are already in the store).
                ctx.client.unsubscribe(WARM_SUB_ID)
                bgScope.cancel()

                // No separate last-mile pass: the per-round sharded sweep already
                // broadcasts the small remaining set to every top relay once it drops
                // below SHARD_BROADCAST_THRESHOLD, and the round loop only exits when
                // every reachable user within the hop budget is done.

                relaysContactedCount = relaysContacted.size
                val perHop =
                    hopOf.values
                        .groupingBy { it }
                        .eachCount()
                        .toSortedMap()
                downloadMs = (System.nanoTime() - crawlStart) / 1_000_000
                System.err.println(
                    "[graperank] crawl complete: ${discovered.size} discovered, $contactListsFed contact lists fed, " +
                        "$relaysContactedCount relays contacted, ${deadRelays.size} dead, $rounds rounds in $downloadMs ms; " +
                        "by hop: " + perHop.entries.joinToString(" ") { "${it.key}=${it.value}" },
                )
                if (ctx.relayDiagnostics.hadFeedback()) {
                    System.err.println("[graperank] relay feedback: ${ctx.relayDiagnostics.snapshot()}")
                }
                if (ctx.relayLimiter.hadThrottling()) {
                    System.err.println("[graperank] relay throttling: ${ctx.relayLimiter.snapshot()}")
                }
            } else {
                // Offline: stream contact lists from the local store into the graph.
                val loadStart = System.nanoTime()
                for (event in ctx.store.query<Event>(Filter(kinds = listOf(ContactListEvent.KIND)))) {
                    if (event is ContactListEvent) {
                        builder.addFollows(event.pubKey, event.verifiedFollowKeySet())
                        contactListsFed++
                    }
                }
                storeLoadMs = (System.nanoTime() - loadStart) / 1_000_000
                System.err.println("[graperank] offline: $contactListsFed contact lists from local store in $storeLoadMs ms")
            }

            // Mutes + reports come from the store (both paths). Far fewer than contact
            // lists, so materialising them is cheap.
            for (event in ctx.store.query<Event>(Filter(kinds = listOf(MuteListEvent.KIND)))) {
                if (event is MuteListEvent) builder.addMutes(event.pubKey, event.linkedPubKeys())
            }
            for (event in ctx.store.query<Event>(Filter(kinds = listOf(ReportEvent.KIND)))) {
                if (event is ReportEvent) builder.addReports(event.pubKey, event.reportedAuthor().map { it.pubkey })
            }

            val buildStart = System.nanoTime()
            val graph = builder.build()
            val buildMs = (System.nanoTime() - buildStart) / 1_000_000
            System.err.println("[graperank] graph built: ${graph.nodeCount} users, ${graph.edgeCount()} edges in $buildMs ms; scoring…")

            // Live scoring progress: fires once per Gauss-Seidel sweep with the
            // running node-update count and how many nodes still moved more than the
            // convergence delta this sweep — that second number trends to 0, so a
            // large graph shows convergence instead of hanging silently.
            val scoreStart = System.nanoTime()
            var sweeps = 0
            val scores =
                GrapeRank(params).compute(graph, observer) { visited, stillMoving ->
                    sweeps++
                    System.err.println("[graperank] scoring sweep $sweeps: $visited node-updates, $stillMoving still moving")
                }

            fun rankOf(score: Double) = (score * 100).roundToInt()

            val observerId = graph.idOf(observer)
            // Reachable users with positive trust at or above --min-score, high→low.
            val rankedIds = ArrayList<Int>()
            for (id in 0 until graph.nodeCount) {
                if (id != observerId && scores[id] > 0.0 && scores[id] >= minScore) rankedIds.add(id)
            }
            rankedIds.sortByDescending { scores[it] }
            val scoringMs = (System.nanoTime() - scoreStart) / 1_000_000
            System.err.println("[graperank] scored ${rankedIds.size} users in $scoringMs ms")

            val result =
                linkedMapOf<String, Any?>(
                    "observer" to observer,
                    "crawl_rounds" to rounds,
                    "relays_contacted" to relaysContactedCount,
                    "relay_feedback" to if (ctx.relayDiagnostics.hadFeedback()) ctx.relayDiagnostics.snapshot() else null,
                    "relay_throttling" to if (ctx.relayLimiter.hadThrottling()) ctx.relayLimiter.snapshot() else null,
                    "max_hop_reached" to (hopOf.values.maxOrNull() ?: 0),
                    "users_by_hop" to
                        hopOf.values
                            .groupingBy { it }
                            .eachCount()
                            .toSortedMap()
                            .mapKeys { it.key.toString() },
                    "graph_users" to graph.nodeCount,
                    "graph_edges" to graph.edgeCount(),
                    "users_scored" to rankedIds.size,
                    "download_ms" to downloadMs,
                    "store_load_ms" to storeLoadMs,
                    "graph_build_ms" to buildMs,
                    "scoring_ms" to scoringMs,
                    "scoring_sweeps" to sweeps,
                    "scores" to
                        rankedIds.take(limit).map {
                            mapOf("pubkey" to graph.pubkeyOf(it), "score" to scores[it], "rank" to rankOf(scores[it]))
                        },
                )

            if (doPublish) {
                val relays =
                    publishRelaysArg
                        ?.split(",")
                        ?.mapNotNull { RelayUrlNormalizer.normalizeOrNull(it.trim()) }
                        ?.toSet()
                        ?.takeIf { it.isNotEmpty() }
                        ?: ctx.outboxRelays()

                // Ranks we've already published (read back from the store, which
                // holds our own prior cards) — keyed by target, newest per target.
                // Lets us leave an unchanged card alone instead of churning it.
                val publishedRanks = publishedCardRanks(ctx)

                val candidates =
                    rankedIds
                        .filter { rankOf(scores[it]) >= minRank }
                        .map { graph.pubkeyOf(it) to rankOf(scores[it]) }
                val changed = candidates.filter { (target, rank) -> publishedRanks[target] != rank }
                val toPublish = changed.take(publishLimit)

                result["skipped_unchanged"] = candidates.size - changed.size
                if (changed.size > toPublish.size) {
                    result["publish_truncated"] = changed.size - toPublish.size
                }

                if (relays.isEmpty()) {
                    result["published"] = 0
                    result["publish_error"] = "no publish relays configured"
                } else {
                    val (ok, rejected) = publishCards(ctx, toPublish, relays)
                    result["published"] = ok
                    result["publish_rejected"] = rejected
                    result["published_kind"] = ContactCardEvent.KIND
                    result["published_to"] = relays.map { it.url }
                }
            }

            if (benchSign) {
                // Throwaway key — these cards are for timing only and never leave
                // the process, so no real identity signs them.
                val tempSigner = NostrSignerInternal(KeyPair())
                val cards =
                    rankedIds
                        .filter { rankOf(scores[it]) >= minRank }
                        .map { graph.pubkeyOf(it) to rankOf(scores[it]) }
                val signStart = System.nanoTime()
                val signed = signCards(cards, tempSigner)
                val signMs = (System.nanoTime() - signStart) / 1_000_000
                val perSec = if (signMs > 0) signed * 1000L / signMs else 0
                System.err.println("[graperank] signed $signed kind:30382 cards in $signMs ms ($perSec/s, temp key, not published)")
                result["bench_signed"] = signed
                result["bench_sign_ms"] = signMs
            }

            Output.emit(result)
            return 0
        }
    }

    /**
     * Build + sign one kind:30382 [ContactCardEvent] per (target, rank), fanned
     * out across CPU cores (id-hash + Schnorr sign is CPU-bound). The signed
     * events are discarded — this only exists to time card generation. Returns
     * the number signed.
     */
    private suspend fun signCards(
        cards: List<Pair<HexKey, Int>>,
        signer: NostrSigner,
    ): Int {
        if (cards.isEmpty()) return 0
        val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        val chunkSize = ((cards.size + cores - 1) / cores).coerceAtLeast(1)
        return coroutineScope {
            cards
                .chunked(chunkSize)
                .map { chunk ->
                    async(Dispatchers.Default) {
                        for ((target, rank) in chunk) {
                            ContactCardEvent.create(
                                targetUser = target,
                                signer = signer,
                                publicInitializer = { add(RankTag.assemble(rank)) },
                            )
                        }
                        chunk.size
                    }
                }.awaitAll()
                .sum()
        }
    }

    /**
     * `amy graperank register [PROVIDER] [--service KIND:TAG] [--relay URL] [--private]`
     *
     * Add a NIP-85 provider entry to the account's kind:10040
     * [TrustProviderListEvent] — the declaration a client reads to discover which
     * key publishes which assertion, and where. Defaults to declaring *self* as
     * the `30382:rank` provider at the account's first outbox relay, which is the
     * self-advertisement a GrapeRank provider makes so its followers can find the
     * cards it publishes. Fetches the freshest list first so existing providers
     * are preserved.
     */
    private suspend fun register(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val providerArg = args.positionalOrNull(0) ?: args.flag("provider")
        val serviceArg = args.flag("service")
        val relayArg = args.flag("relay")
        val isPrivate = args.bool("private")
        val timeoutMs = args.longFlag("timeout", 8L) * 1000

        val service =
            serviceArg?.let {
                ServiceType.parse(it) ?: return Output.error("bad_args", "--service must be KIND:TAG, e.g. 30382:rank")
            } ?: ProviderTypes.rank

        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val self = ctx.identity.pubKeyHex
            val provider = providerArg?.let { ctx.requireUserHex(it) } ?: self

            val outbox = ctx.outboxRelays()
            val relay =
                relayArg?.let { RelayUrlNormalizer.normalizeOrNull(it) }
                    ?: outbox.firstOrNull()
                    ?: return Output.error("no_relays", "no relay hint; pass --relay URL or configure outbox relays")

            val latest = fetchLatestProviderList(ctx, self, outbox, timeoutMs)
            val alreadyListed =
                latest?.serviceProviders()?.any {
                    it.service == service && it.pubkey == provider && it.relayUrl == relay
                } ?: false

            if (alreadyListed) {
                Output.emit(
                    mapOf(
                        "service" to service.toValue(),
                        "provider" to provider,
                        "relay" to relay.url,
                        "changed" to false,
                        "based_on" to latest?.id,
                    ),
                )
                return 0
            }

            val tag = ServiceProviderTag(service, provider, relay)
            val event =
                if (latest == null) {
                    TrustProviderListEvent.create(tag, isPrivate = isPrivate, signer = ctx.signer)
                } else {
                    TrustProviderListEvent.add(latest, tag, isPrivate = isPrivate, signer = ctx.signer)
                }

            val ack = ctx.publish(event, outbox)
            Output.emit(
                mapOf(
                    "service" to service.toValue(),
                    "provider" to provider,
                    "relay" to relay.url,
                    "private" to isPrivate,
                    "changed" to true,
                    "event_id" to event.id,
                    "based_on" to latest?.id,
                    "published_to" to ack.filterValues { it }.keys.map { it.url },
                    "rejected_by" to ack.filterValues { !it }.keys.map { it.url },
                ),
            )
            return 0
        }
    }

    /**
     * `amy graperank providers [USER] [--refresh] [--timeout SECS]`
     *
     * List the NIP-85 trusted providers a user declares in their kind:10040
     * (default: the active account). Cache-first; falls back to a relay drain on
     * a miss or with `--refresh`. For the active account, private (NIP-44)
     * provider entries are decrypted and included too.
     */
    private suspend fun providers(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val userArg = args.positionalOrNull(0)
        val refresh = args.bool("refresh")
        val timeoutMs = args.longFlag("timeout", 8L) * 1000

        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val user = userArg?.let { ctx.requireUserHex(it) } ?: ctx.identity.pubKeyHex
            val isSelf = user == ctx.identity.pubKeyHex

            var event = if (refresh) null else providerListOf(ctx, user)
            if (event == null) {
                ctx.drain(
                    (ctx.bootstrapRelays() + Constants.eventFinderRelays).associateWith {
                        listOf(Filter(kinds = listOf(TrustProviderListEvent.KIND), authors = listOf(user), limit = 1))
                    },
                    timeoutMs,
                )
                event = providerListOf(ctx, user)
            }

            if (event == null) {
                Output.emit(mapOf("user" to user, "found" to false, "providers" to emptyList<Any>()))
                return 0
            }

            val public = event.serviceProviders()
            val private = if (isSelf) event.privateTags(ctx.signer)?.serviceProviders().orEmpty() else emptyList()

            fun render(
                tag: ServiceProviderTag,
                scope: String,
            ) = mapOf(
                "service" to tag.service.toValue(),
                "provider" to tag.pubkey,
                "relay" to tag.relayUrl.url,
                "scope" to scope,
            )

            Output.emit(
                mapOf(
                    "user" to user,
                    "found" to true,
                    "event_id" to event.id,
                    "created_at" to event.createdAt,
                    "providers" to public.map { render(it, "public") } + private.map { render(it, "private") },
                ),
            )
            return 0
        }
    }

    /** Latest known kind:10040 provider list for [pubKey] from the local store. */
    private suspend fun providerListOf(
        ctx: Context,
        pubKey: HexKey,
    ): TrustProviderListEvent? =
        ctx.store
            .query<Event>(Filter(kinds = listOf(TrustProviderListEvent.KIND), authors = listOf(pubKey), limit = 1))
            .firstOrNull() as? TrustProviderListEvent

    /**
     * Fetch the freshest kind:10040 for [pubKey] from [relays] so a register
     * builds on top of the current provider set instead of clobbering it.
     */
    private suspend fun fetchLatestProviderList(
        ctx: Context,
        pubKey: HexKey,
        relays: Set<NormalizedRelayUrl>,
        timeoutMs: Long,
    ): TrustProviderListEvent? {
        if (relays.isEmpty()) return providerListOf(ctx, pubKey)
        val filter = Filter(kinds = listOf(TrustProviderListEvent.KIND), authors = listOf(pubKey), limit = 1)
        ctx.drain(relays.associateWith { listOf(filter) }, timeoutMs)
        return providerListOf(ctx, pubKey)
    }

    /**
     * Relays to query for **kind:10002 relay lists** — the account's own relays +
     * bootstrap defaults + event-finder relays + the **indexer relays**
     * (purplepag.es, coracle, …). Indexers aggregate kind:10002 (and kind:0) for
     * the whole network, so this is where a stranger's relay list is found. They
     * do NOT hold kind:3/10000/1984 — see [contentFallbackRelays].
     */
    private suspend fun relayListDiscoveryRelays(ctx: Context): Set<NormalizedRelayUrl> = ctx.bootstrapRelays() + Constants.eventFinderRelays + DefaultIndexerRelayList + EXTRA_DISCOVERY_RELAYS

    /**
     * Best-effort fallback relays for **content** (kind:3/10000/1984/0) when a
     * user's outbox is unknown or unreachable. Content lives on each user's own
     * outbox, so this is only general-purpose relays that *might* hold a copy —
     * bootstrap + event-finder. **No indexers**: they don't serve these kinds.
     */
    private suspend fun contentFallbackRelays(ctx: Context): Set<NormalizedRelayUrl> = ctx.bootstrapRelays() + Constants.eventFinderRelays

    /**
     * Fetch kind:10002 relay lists for any [pubkeys] we don't already know, so
     * [routeByOutbox] can route their content query to their own write relays.
     *
     * Tier 1 queries the bounded relay-list discovery set (indexers + general
     * defaults), which aggregate kind:10002 for the whole network — reliable in
     * bulk, unlike fanning out to thousands of per-user outboxes.
     *
     * Tier 2 is a completeness net for the stragglers the indexers don't cover:
     * a user publishes their own kind:10002 to their own write relays, and those
     * relays overlap heavily with [fallbackRelays] — the known-good backbone we
     * learned from the `r` tags in *everyone else's* 10002s. So after tier 1,
     * any pubkey still without a relay list is retried against that learned pool
     * (minus the tier-1 relays we already asked). Early rounds skip tier 2
     * harmlessly because the backbone is still empty; it kicks in once the crawl
     * has learned which relays actually carry 10002s.
     */
    private suspend fun ensureRelayLists(
        ctx: Context,
        pubkeys: Set<HexKey>,
        allLiveRelays: Set<NormalizedRelayUrl>,
        bgScope: CoroutineScope,
        timeoutMs: Long,
        diagnose: Boolean,
    ) {
        val missing = pubkeys.filter { ctx.relaysOf(it) == null }
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
            ctx.drain(filters, timeoutMs, diagnose, gatePerRelay = true)
        }

        // Tier 1: the index/discovery aggregators, which carry kind:10002 for most
        // of the network. Blocking, because this round's routing needs the result.
        val discovery = relayListDiscoveryRelays(ctx)
        query(missing, discovery)

        // Tier 2: whoever the aggregators still don't have, cast the widest net —
        // ask EVERY relay we've seen deliver events, not just the backbone. Fired
        // fire-and-forget on [bgScope]: a stray 10002 might sit on any one relay, so
        // we don't want to skip any, but we also can't block the crawl on a fan-out
        // that large. The results land in the store and improve routing for later
        // rounds; anyone still unresolved is handled by fallback routing meanwhile.
        val stillMissing = missing.filter { ctx.relaysOf(it) == null }
        val wide = allLiveRelays - discovery
        if (stillMissing.isNotEmpty() && wide.isNotEmpty()) {
            bgScope.launch { query(stillMissing, wide) }
        }
    }

    /**
     * Group [pubkeys] by the relays we should query for their events:
     *  - first try: the user's own kind:10002 write relays (the outbox model);
     *  - a retry (`attempts[pk] > 0`, its outbox already failed): outbox +
     *    [backbone] — the known-good relays other people write to, which likely
     *    hold a copy;
     *  - no outbox at all: harvested [hints] + backbone + the general fallback.
     *
     * Also tallies each user's write relays into [writeRelayFreq] so the backbone
     * can be learned from the crawl. Authors are chunked per relay to respect REQ
     * limits.
     */
    private suspend fun routeByOutbox(
        ctx: Context,
        pubkeys: Set<HexKey>,
        hints: Map<HexKey, Set<NormalizedRelayUrl>>,
        backbone: Set<NormalizedRelayUrl>,
        attempts: Map<HexKey, Int>,
        writeRelayFreq: MutableMap<NormalizedRelayUrl, Int>,
        kinds: List<Int>,
        deadRelays: Set<NormalizedRelayUrl>,
    ): Map<NormalizedRelayUrl, List<Filter>> {
        val fallback = contentFallbackRelays(ctx)
        val perRelay = HashMap<NormalizedRelayUrl, MutableSet<HexKey>>()

        for (pk in pubkeys) {
            val write = ctx.relaysOf(pk)?.writeRelaysNorm()?.takeIf { it.isNotEmpty() }
            write?.forEach { writeRelayFreq.merge(it, 1, Int::plus) }
            val relays =
                when {
                    write == null -> hints[pk].orEmpty() + backbone + fallback
                    (attempts[pk] ?: 0) > 0 -> write + backbone
                    else -> write
                }
            // Skip relays already proven dead — routing to them only burns the
            // drain timeout.
            for (relay in relays) if (relay !in deadRelays) perRelay.getOrPut(relay) { HashSet() }.add(pk)
        }

        return perRelay.mapValues { (_, authors) ->
            authors.chunked(AUTHORS_PER_FILTER).map { chunk ->
                Filter(kinds = kinds, authors = chunk)
            }
        }
    }

    /**
     * The rank we last published for each target, read from the active account's
     * own kind:30382 cards in the local store (newest card wins per target).
     * `ctx.publish` stores every card it sends, so on repeat runs this reflects
     * what's already out there and lets us skip targets whose rank is unchanged.
     */
    private suspend fun publishedCardRanks(ctx: Context): Map<HexKey, Int> {
        val self = ctx.identity.pubKeyHex
        return ctx.store
            .query<Event>(Filter(kinds = listOf(ContactCardEvent.KIND), authors = listOf(self)))
            .filterIsInstance<ContactCardEvent>()
            .groupBy { it.aboutUser() }
            .mapNotNull { (target, cards) ->
                val t = target ?: return@mapNotNull null
                val rank = cards.maxByOrNull { it.createdAt }?.rank() ?: return@mapNotNull null
                t to rank
            }.toMap()
    }

    /** Build + publish one NIP-85 kind:30382 card per user, bounded-concurrently. */
    private suspend fun publishCards(
        ctx: Context,
        cards: List<Pair<HexKey, Int>>,
        relays: Set<NormalizedRelayUrl>,
    ): Pair<Int, Int> {
        var published = 0
        var rejected = 0
        for (batch in cards.chunked(PUBLISH_CONCURRENCY)) {
            val acks =
                coroutineScope {
                    batch
                        .map { (pubkey, rank) ->
                            async {
                                val card =
                                    ContactCardEvent.create(
                                        targetUser = pubkey,
                                        signer = ctx.signer,
                                        publicInitializer = { add(RankTag.assemble(rank)) },
                                    )
                                ctx.publish(card, relays)
                            }
                        }.awaitAll()
                }
            for (ack in acks) {
                if (ack.values.any { it }) published++ else rejected++
            }
        }
        return published to rejected
    }
}
