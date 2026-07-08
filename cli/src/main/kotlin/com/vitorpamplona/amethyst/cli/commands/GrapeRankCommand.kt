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
import com.vitorpamplona.amethyst.cli.Output
import com.vitorpamplona.amethyst.commons.defaults.Constants
import com.vitorpamplona.amethyst.commons.defaults.DefaultIndexerRelayList
import com.vitorpamplona.quartz.experimental.graperank.GrapeRank
import com.vitorpamplona.quartz.experimental.graperank.GrapeRankDataCrawler
import com.vitorpamplona.quartz.experimental.graperank.GrapeRankParams
import com.vitorpamplona.quartz.experimental.graperank.GrapeRankPublisher
import com.vitorpamplona.quartz.experimental.graperank.TrustGraphBuilder
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip02FollowList.ContactListEvent
import com.vitorpamplona.quartz.nip09Deletions.DeletionEvent
import com.vitorpamplona.quartz.nip09Deletions.DeletionIndex
import com.vitorpamplona.quartz.nip51Lists.muteList.MuteListEvent
import com.vitorpamplona.quartz.nip56Reports.ReportEvent
import com.vitorpamplona.quartz.nip85TrustedAssertions.list.TrustProviderListEvent
import com.vitorpamplona.quartz.nip85TrustedAssertions.list.serviceProviders
import com.vitorpamplona.quartz.nip85TrustedAssertions.list.tags.ProviderTypes
import com.vitorpamplona.quartz.nip85TrustedAssertions.list.tags.ServiceProviderTag
import com.vitorpamplona.quartz.nip85TrustedAssertions.list.tags.ServiceType
import com.vitorpamplona.quartz.nip85TrustedAssertions.users.ContactCardEvent
import com.vitorpamplona.quartz.nip85TrustedAssertions.users.tags.RankTag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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
 * The crawl and the computation are separable, because the crawl persists every
 * event it fetches to the store and the score is a pure function over it:
 *  - `amy graperank sync [OBSERVER]` — network only: crawl the reachable graph's
 *    kind 3/10000/1984/10002 into the local store. Idempotent and cumulative, so
 *    run it a few times to make sure everything is loaded. Scores nothing.
 *  - `amy graperank score [OBSERVER]` — local only: build the graph from the store
 *    and score (same as bare `--offline`). Instant and param-tunable; repeat with
 *    different `--rigor`/`--attenuation`/cutoffs without re-crawling.
 *  - bare `amy graperank [OBSERVER]` — the convenience combo: sync then score.
 *
 * Sub-verbs complete the NIP-85 provider experience — the discovery layer that
 * lets clients find and consume those assertions:
 *  - `amy graperank register` — advertise a `30382:rank` provider in the
 *    account's kind:10040 [TrustProviderListEvent] (defaults to self, so a
 *    provider publishing ranks announces where to find them).
 *  - `amy graperank providers [USER]` — list a user's trusted providers.
 */
object GrapeRankCommand {
    // Broad, big general relays that carry kind:10002 for many users, added to the
    // crawler's discovery set to raise the odds of resolving a stranger's outbox.
    private val EXTRA_DISCOVERY_RELAYS: Set<NormalizedRelayUrl> =
        listOf(
            "wss://relay.damus.io",
            "wss://relay.snort.social",
            "wss://offchain.pub",
            "wss://nostr.land",
            "wss://eden.nostr.land",
        ).mapNotNull { RelayUrlNormalizer.normalizeOrNull(it) }.toSet()

    // Network-wide aggregators that scrape and hold kind:3 for users whose own
    // outbox lacks it. The crawler queries these for a straggler's CONTENT (kind:3),
    // not just their kind:10002 relay list. Measured on observer 460c25e6, the distinct
    // missing authors whose kind:3 each holds: kindpag.es 369, yabu 126, oxtr.dev 76,
    // nos.lol 72, ditto 56, nostr1 29, momostr 11, mostr 3. So beyond the profile
    // indexers (kindpag/purplepag/coracle/yabu/nostr1) and the ActivityPub bridges
    // (ditto/momostr/mostr, which host bridged users' lists), two big general relays --
    // nostr.oxtr.dev and nos.lol -- carry ~150 more that no indexer has.
    private val CONTENT_AGGREGATOR_RELAYS: Set<NormalizedRelayUrl> =
        DefaultIndexerRelayList +
            listOf(
                "wss://relay.ditto.pub",
                "wss://relay.momostr.pink",
                "wss://relay.mostr.pub",
                "wss://nostr.oxtr.dev",
                "wss://nos.lol",
            ).mapNotNull { RelayUrlNormalizer.normalizeOrNull(it) }.toSet()

    suspend fun dispatch(
        dataDir: DataDir,
        tail: Array<String>,
    ): Int =
        // Sub-verbs are explicit words; anything else (npub / hex / nprofile /
        // NIP-05, or nothing) is the OBSERVER positional for a score computation.
        when (tail.firstOrNull()) {
            "register" -> register(dataDir, tail.drop(1).toTypedArray())
            "providers" -> providers(dataDir, tail.drop(1).toTypedArray())
            "operator" -> operator(dataDir, tail.drop(1).toTypedArray())
            "sync" -> sync(dataDir, tail.drop(1).toTypedArray())
            "score" -> run(dataDir, tail.drop(1).toTypedArray(), forceOffline = true)
            else -> run(dataDir, tail)
        }

    suspend fun run(
        dataDir: DataDir,
        rest: Array<String>,
        forceOffline: Boolean = false,
    ): Int {
        val args = Args(rest)
        val observerArg = args.positionalOrNull(0)
        // Crawl to full convergence by default (every reachable user's outbox
        // checked). --max-rounds is only a safety backstop; --max-hops bounds the
        // follow-graph distance from the observer that we crawl (Brainstorm uses 8).
        val limit = args.intFlag("limit", 100)
        val minScore = args.flag("min-score")?.toDoubleOrNull() ?: 0.0
        // `graperank score` forces the local (no-network) path; `--offline` does the
        // same on the bare command. Either way we build + score from the store only.
        val offline = forceOffline || args.bool("offline")
        // Crawl tuning (--max-rounds/--max-hops/--timeout/--diagnose/--drain-concurrency)
        // is read straight from args by [newCrawler]; only these two are surfaced in
        // the result JSON, so keep local copies for that.
        val parkTimeoutMs = args.longFlag("park-timeout", 40L) * 1000
        val insertBatch = args.intFlag("insert-batch", 500)
        val doPublish = args.bool("publish")
        // Publish cutoff: only cards with rank >= this are published; existing
        // cards for targets below it (or gone from the graph) are retracted. Rank
        // is round(score*100), so 2 drops the ~0.015-and-below barely-trusted tail.
        val minRank = args.intFlag("min-rank", 2)
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

            // Contact lists stream straight into a compact int-CSR structure as the
            // crawl finds them and the Event is discarded, so the whole network fits
            // in memory without holding millions of kind:3 objects.
            val builder = TrustGraphBuilder()
            var contactListsFed = 0
            // Wall time to read + deserialize the contact lists out of the store
            // (offline path only; online streams them in during the crawl).
            var storeLoadMs: Long? = null
            // Crawl telemetry (online path only): rounds, relays contacted, the
            // per-hop histogram, and the network-bound download time that dominates a
            // from-scratch run. Null on the offline path.
            var crawlStats: GrapeRankDataCrawler.Stats? = null

            if (!offline) {
                val stats = newCrawler(ctx, args).crawl(observer, builder)
                crawlStats = stats
                contactListsFed = stats.contactListsFed
                reportRelayFeedback(ctx)
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
            val reportsDeleted = materializeReports(ctx, builder)

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

            val hopHistogram = crawlStats?.hopHistogram.orEmpty()
            val result =
                linkedMapOf<String, Any?>(
                    "observer" to observer,
                    "crawl_rounds" to (crawlStats?.rounds ?: 0),
                    "relays_contacted" to (crawlStats?.relaysContacted ?: 0),
                    "relay_feedback" to if (ctx.relayDiagnostics.hadFeedback()) ctx.relayDiagnostics.snapshot() else null,
                    "relay_throttling" to if (ctx.relayLimiter.hadThrottling()) ctx.relayLimiter.snapshot() else null,
                    "max_hop_reached" to (hopHistogram.keys.maxOrNull() ?: 0),
                    "users_by_hop" to hopHistogram.mapKeys { it.key.toString() },
                    "contact_lists_by_hop" to crawlStats?.contactsFedByHop.orEmpty().mapKeys { it.key.toString() },
                    "graph_users" to graph.nodeCount,
                    "graph_edges" to graph.edgeCount(),
                    "reports_deleted" to reportsDeleted,
                    "users_scored" to rankedIds.size,
                    "download_ms" to crawlStats?.downloadMs,
                    "verify_ms" to crawlStats?.verifyMs,
                    "insert_ms" to crawlStats?.insertMs,
                    "events_stored" to crawlStats?.eventsStored,
                    "insert_batch" to insertBatch,
                    "park_timeout_ms" to parkTimeoutMs,
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
                // The cards for THIS observer are signed by a dedicated, stable
                // per-observer service key derived from the machine's operator
                // master (see OperatorKeys) — not the account key. Same key across
                // runs means re-signing a card replaces the addressable prior one.
                val opKeys = ctx.dataDir.operatorKeys()
                val serviceKey = opKeys.serviceKey(observer)
                val serviceSigner = NostrSignerInternal(serviceKey)
                val providerPubkey = serviceKey.pubKey.toHexKey()
                result["provider_pubkey"] = providerPubkey

                // Cards go to the operator's own relay(s), where the whole
                // trusted-assertion set lives; --publish-relay overrides.
                val relays =
                    publishRelaysArg
                        ?.split(",")
                        ?.mapNotNull { RelayUrlNormalizer.normalizeOrNull(it.trim()) }
                        ?.toSet()
                        ?.takeIf { it.isNotEmpty() }
                        ?: opKeys.operatorRelays()

                if (relays.isEmpty()) {
                    result["published"] = 0
                    result["publish_error"] = "no operator relay configured — run `amy graperank operator relay <url>` or pass --publish-relay"
                } else {
                    // The scorer's desired card set: every user at or above the rank
                    // cutoff, as (target, rank). GrapeRankPublisher reconciles this
                    // against what this provider key already published and upserts /
                    // retracts the difference.
                    val publishable =
                        rankedIds
                            .filter { rankOf(scores[it]) >= minRank }
                            .map { graph.pubkeyOf(it) to rankOf(scores[it]) }

                    val publisher = GrapeRankPublisher(ctx.store) { event, to -> ctx.publish(event, to) }
                    val pub =
                        publisher.reconcileAndPublish(
                            providerSigner = serviceSigner,
                            providerPubkey = providerPubkey,
                            scored = publishable,
                            relays = relays,
                            publishLimit = publishLimit,
                        )

                    result["skipped_unchanged"] = pub.skippedUnchanged
                    if (pub.truncated > 0) result["publish_truncated"] = pub.truncated
                    result["published"] = pub.published
                    result["publish_rejected"] = pub.publishRejected
                    result["deleted"] = pub.deleted
                    result["delete_rejected"] = pub.deleteRejected
                    result["published_kind"] = ContactCardEvent.KIND
                    result["published_to"] = relays.map { it.url }

                    // Help the observer point clients at this provider: publish their
                    // kind:10040 (30382:rank -> providerPubkey @ operator relay) to
                    // their outbox — but only when we actually hold their key.
                    maybePublishObserverProviderList(ctx, observer, providerPubkey, relays.first())?.let {
                        result["observer_10040"] = it
                    }
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
     * Configure the outbox-model crawler from the crawl flags on [args] plus the
     * account's relay policy. Shared by the bare command and `graperank sync`.
     * Relay policy — where a stranger's kind:10002 is found (index/discovery
     * aggregators + general defaults) and best-effort general relays that might
     * hold content when an outbox is unknown — lives in app code, so the quartz
     * crawler takes it injected.
     */
    private suspend fun newCrawler(
        ctx: Context,
        args: Args,
    ): GrapeRankDataCrawler {
        val discoveryRelays =
            ctx.bootstrapRelays() + Constants.eventFinderRelays + DefaultIndexerRelayList + EXTRA_DISCOVERY_RELAYS
        val contentFallback = ctx.bootstrapRelays() + Constants.eventFinderRelays
        // Aggregator kind:3 recovery for stragglers is on by default; --no-aggregators
        // disables it for A/B comparison.
        val aggregators = if (args.bool("no-aggregators")) emptySet() else CONTENT_AGGREGATOR_RELAYS
        return GrapeRankDataCrawler(
            client = ctx.client,
            store = ctx.store,
            limiter = ctx.relayLimiter,
            config =
                GrapeRankDataCrawler.Config(
                    relayListDiscoveryRelays = discoveryRelays,
                    contentFallbackRelays = contentFallback,
                    contentAggregatorRelays = aggregators,
                    maxRounds = args.intFlag("max-rounds", Int.MAX_VALUE),
                    maxHops = args.intFlag("max-hops", Int.MAX_VALUE),
                    timeoutMs = args.longFlag("timeout", 10L) * 1000,
                    parkTimeoutMs = args.longFlag("park-timeout", 40L) * 1000,
                    diagnose = args.bool("diagnose"),
                    insertBatchSize = args.intFlag("insert-batch", 500),
                    drainConcurrency = args.intFlag("drain-concurrency", 24),
                    timeoutEvictStrikes = args.intFlag("timeout-evict", 3),
                    // shedDeadDiscovery / shardRotations keep their benchmarked-best
                    // Config defaults.
                ),
            log = { System.err.println(it) },
        )
    }

    /** Echo any relay NOTICE/CLOSED feedback + adaptive throttling the crawl saw. */
    private fun reportRelayFeedback(ctx: Context) {
        if (ctx.relayDiagnostics.hadFeedback()) {
            System.err.println("[graperank] relay feedback: ${ctx.relayDiagnostics.snapshot()}")
        }
        if (ctx.relayLimiter.hadThrottling()) {
            System.err.println("[graperank] relay throttling: ${ctx.relayLimiter.snapshot()}")
        }
    }

    /**
     * `amy graperank sync [OBSERVER]` — network-only WoT data sync. Crawls the
     * reachable follow/mute/report graph into the local store (kind 3/10000/1984/
     * 10002) and reports what it loaded, WITHOUT scoring. Idempotent + cumulative:
     * run it a few times to make sure everything is loaded, then `graperank score`.
     */
    private suspend fun sync(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val observerArg = args.positionalOrNull(0)
        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val observer = observerArg?.let { ctx.requireUserHex(it) } ?: ctx.identity.pubKeyHex
            // Persist-only crawl: no in-memory graph (null builder); every event
            // still lands in the store for a later `score`.
            val stats = newCrawler(ctx, args).crawl(observer, null)
            reportRelayFeedback(ctx)
            Output.emit(
                linkedMapOf<String, Any?>(
                    "observer" to observer,
                    "crawl_rounds" to stats.rounds,
                    "relays_contacted" to stats.relaysContacted,
                    "relay_feedback" to if (ctx.relayDiagnostics.hadFeedback()) ctx.relayDiagnostics.snapshot() else null,
                    "relay_throttling" to if (ctx.relayLimiter.hadThrottling()) ctx.relayLimiter.snapshot() else null,
                    "max_hop_reached" to (stats.hopHistogram.keys.maxOrNull() ?: 0),
                    "users_by_hop" to stats.hopHistogram.mapKeys { it.key.toString() },
                    "contact_lists_by_hop" to stats.contactsFedByHop.mapKeys { it.key.toString() },
                    "users_discovered" to stats.hopHistogram.values.sum(),
                    "contact_lists_fed" to stats.contactListsFed,
                    "download_ms" to stats.downloadMs,
                    "verify_ms" to stats.verifyMs,
                    "insert_ms" to stats.insertMs,
                    "events_stored" to stats.eventsStored,
                ),
            )
        }
        return 0
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
     * `amy graperank operator [status | relay <url>… | providers]`
     *
     * Manage the machine's operator keys used to sign trusted-assertion cards.
     *  - `status` (default): master pubkey, configured relay(s), provider count.
     *  - `relay <url>…`: set the operator relay(s) the cards + retractions publish
     *    to; creates the operator master on first use.
     *  - `providers`: the observer -> provider-pubkey mapping learned so far.
     */
    private fun operator(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val opKeys = dataDir.operatorKeys()
        return when (rest.firstOrNull()) {
            "relay" -> {
                val urls = rest.drop(1).filter { it.isNotBlank() }
                val normalized = urls.mapNotNull { RelayUrlNormalizer.normalizeOrNull(it) }
                if (normalized.isEmpty()) return Output.error("bad_args", "usage: amy graperank operator relay <wss://…> [<wss://…> …]")
                opKeys.setRelays(urls)
                Output.emit(mapOf("master_pubkey" to opKeys.masterPubKey(), "relays" to normalized.map { it.url }))
                0
            }

            "providers" -> {
                Output.emit(
                    mapOf(
                        "master_pubkey" to if (opKeys.exists()) opKeys.masterPubKey() else null,
                        "providers" to opKeys.providers().map { (observer, rec) -> mapOf("observer" to observer, "provider_pubkey" to rec.providerPubKey) },
                    ),
                )
                0
            }

            null, "status" -> {
                if (!opKeys.exists()) {
                    Output.emit(mapOf("initialized" to false))
                } else {
                    Output.emit(
                        mapOf(
                            "initialized" to true,
                            "master_pubkey" to opKeys.masterPubKey(),
                            "relays" to opKeys.operatorRelays().map { it.url },
                            "providers" to opKeys.providers().size,
                        ),
                    )
                }
                0
            }

            else -> Output.error("bad_args", "unknown operator subcommand '${rest.first()}' (status | relay | providers)")
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
     * Feed reports into [builder], dropping any that a valid NIP-09 deletion has
     * retracted. Uses quartz's [DeletionIndex] — the same indexer the Android
     * app's LocalCache runs — which keys each deletion under the DELETER's pubkey,
     * so `hasBeenDeleted(report)` is true only when the report's own author
     * deleted it (NIP-09: a deletion is authoritative only from the event's
     * author). It also honours created_at ordering. Returns how many were dropped.
     */
    private suspend fun materializeReports(
        ctx: Context,
        builder: TrustGraphBuilder,
    ): Int {
        val reports = ctx.store.query<Event>(Filter(kinds = listOf(ReportEvent.KIND))).filterIsInstance<ReportEvent>()
        if (reports.isEmpty()) return 0

        // Everything in the store already passed verifyAndStore, so mark the
        // deletions as verified and skip the redundant signature check.
        val deletions = DeletionIndex()
        for (ev in ctx.store.query<Event>(Filter(kinds = listOf(DeletionEvent.KIND)))) {
            if (ev is DeletionEvent) deletions.add(ev, wasVerified = true)
        }

        var dropped = 0
        for (r in reports) {
            if (deletions.hasBeenDeleted(r)) {
                dropped++
                continue
            }
            builder.addReports(r.pubKey, r.reportedAuthor().map { it.pubkey })
        }
        if (dropped > 0) System.err.println("[graperank] dropped $dropped retracted reports (NIP-09 deletions)")
        return dropped
    }

    /**
     * If the active account IS the observer (so we hold their key), publish/refresh
     * their kind:10040 declaring `30382:rank` -> [providerPubkey] at [relay], to
     * their own outbox relays — the NIP-85 pointer a client follows to find these
     * cards. Returns the 10040 event id, or null when we don't hold the key (a
     * third-party observer must add the provider to their 10040 out-of-band).
     */
    private suspend fun maybePublishObserverProviderList(
        ctx: Context,
        observer: HexKey,
        providerPubkey: HexKey,
        relay: NormalizedRelayUrl,
    ): String? {
        if (observer != ctx.identity.pubKeyHex) return null
        val service = ProviderTypes.rank
        val outbox = ctx.outboxRelays()
        val latest = fetchLatestProviderList(ctx, observer, outbox, 8_000)
        val alreadyListed =
            latest?.serviceProviders()?.any {
                it.service == service && it.pubkey == providerPubkey && it.relayUrl == relay
            } ?: false
        if (alreadyListed) return latest?.id

        val tag = ServiceProviderTag(service, providerPubkey, relay)
        val event =
            if (latest == null) {
                TrustProviderListEvent.create(tag, isPrivate = false, signer = ctx.signer)
            } else {
                TrustProviderListEvent.add(latest, tag, isPrivate = false, signer = ctx.signer)
            }
        ctx.publish(event, outbox)
        return event.id
    }
}
