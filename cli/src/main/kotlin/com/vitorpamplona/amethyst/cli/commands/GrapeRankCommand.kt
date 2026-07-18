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
import com.vitorpamplona.quartz.experimental.graperank.FollowerCrawler
import com.vitorpamplona.quartz.experimental.graperank.GrapeRank
import com.vitorpamplona.quartz.experimental.graperank.GrapeRankCrawler
import com.vitorpamplona.quartz.experimental.graperank.GrapeRankParams
import com.vitorpamplona.quartz.experimental.graperank.GrapeRankPublisher
import com.vitorpamplona.quartz.experimental.graperank.GrapeRankUpdater
import com.vitorpamplona.quartz.experimental.graperank.TrustGraphBuilder
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip02FollowList.ContactListEvent
import com.vitorpamplona.quartz.nip09Deletions.DeletionEvent
import com.vitorpamplona.quartz.nip09Deletions.DeletionIndex
import com.vitorpamplona.quartz.nip51Lists.muteList.MuteListEvent
import com.vitorpamplona.quartz.nip56Reports.ReportEvent
import com.vitorpamplona.quartz.nip65RelayList.AdvertisedRelayListEvent
import com.vitorpamplona.quartz.nip66RelayMonitor.discovery.RelayDiscoveryEvent
import com.vitorpamplona.quartz.nip66RelayMonitor.reachability.RelayReachabilityStore
import com.vitorpamplona.quartz.nip85TrustedAssertions.list.TrustProviderListEvent
import com.vitorpamplona.quartz.nip85TrustedAssertions.list.serviceProviders
import com.vitorpamplona.quartz.nip85TrustedAssertions.list.tags.ProviderTypes
import com.vitorpamplona.quartz.nip85TrustedAssertions.list.tags.ServiceProviderTag
import com.vitorpamplona.quartz.nip85TrustedAssertions.list.tags.ServiceType
import com.vitorpamplona.quartz.nip85TrustedAssertions.users.ContactCardEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import java.util.concurrent.Executors
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
 * The pipeline is three separable stages, each with its own verb, and the local
 * store is the source of truth between them (the crawl persists every event it
 * fetches; the score is a pure function over the store; every score run persists
 * its result as locally-signed NIP-85 kind:30382 cards):
 *  - `amy graperank crawl [OBSERVER]` — network only: crawl the reachable graph's
 *    kind 3/10000/1984/10002 into the local store. Idempotent and cumulative, so
 *    run it a few times to make sure everything is loaded. Scores nothing.
 *  - `amy graperank status` — read-only inventory of all of the above: WoT record
 *    counts, reachability-cache freshness, operator state, persisted card sets.
 *    Answers "do I need to crawl again?" with no network and no signing.
 *  - `amy graperank score [OBSERVER]` — local only: build the graph from the store,
 *    score (same as bare `--offline`), and ALWAYS reconcile the result into the
 *    store as kind:30382 [ContactCardEvent] cards signed by the observer's
 *    per-observer service key (`rank = round(score*100)`, cutoff `--min-rank`):
 *    changed ranks are re-signed, unchanged ones skipped, dropped targets
 *    retracted with a kind:5. That persisted card set is what `publish` and
 *    `rank` reuse — scores are never ephemeral.
 *  - `amy graperank publish [OBSERVER]` — transport only: make the operator
 *    relay(s) converge to the local card set via a NIP-77 up-only reconcile
 *    (nothing is re-signed or re-scored), and refresh the observer's kind:10040
 *    pointer when we hold their key.
 *  - `amy relay probe` — the relay census: mass-connect every relay the store
 *    knows and record live/dead + measured RTT into the reachability cache, so the
 *    next crawl skips the dead and pre-connects the living in one parallel storm.
 *    Lives in [RelayCommands] (`graperank probe` is kept as an alias).
 *  - bare `amy graperank [OBSERVER]` — the convenience combo: crawl then score.
 *
 * Sub-verbs complete the NIP-85 experience — discovery and consumption:
 *  - `amy graperank rank USER` — read the kind:30382 cards about USER (local
 *    store first, `--refresh` to drain providers' relays): the consumer side.
 *  - `amy graperank register` — advertise a `30382:rank` provider in the
 *    account's kind:10040 [TrustProviderListEvent] (defaults to self, so a
 *    provider publishing ranks announces where to find them);
 *    `amy graperank unregister PROVIDER` removes entries again.
 *  - `amy graperank providers [USER]` — list a user's trusted providers.
 */
object GrapeRankCommand {
    val USAGE: String =
        """
        |amy graperank — GrapeRank web-of-trust: crawl + score + publish NIP-85 cards
        |
        |  graperank [OBSERVER]                       crawl + score: subjective trust (0..1) over the
        |    [--min-rank N] [--offline]                follow/mute/report graph, then persist the result
        |    [--limit N] [--min-score X]               as local NIP-85 kind:30382 cards (ranks >=
        |    [--rigor X] [--attenuation X]             --min-rank, default 2). --offline skips the crawl.
        |    [--max-hops N] [--diagnose]               OBSERVER: npub|nprofile|hex|name@domain (self).
        |  graperank crawl [OBSERVER]                 network only: crawl the graph (kind 3/10000/1984/
        |    [--max-hops N] [--max-rounds N]           10002) into the local store, no scoring.
        |    [--no-preconnect] [--preconnect-cap N]    Idempotent — run a few times to load everything.
        |  graperank followers [OBSERVER]             reverse crawl: pull kind:3 lists that #p-tag the
        |    [--relay URL[,URL…]] [--max N]            observer from every relay the store knows, so
        |    [--timeout SECS] [--relay-concurrency N]  every follower becomes a graph edge for `score`.
        |    [--insert-batch N]
        |  graperank score [OBSERVER]                 local only: score from the store + persist cards
        |                                              (= bare --offline; same flags). No network.
        |  graperank publish [OBSERVER]               push local cards to the operator relay(s) via a
        |    [--relay URL[,URL…]] [--timeout SECS]     NIP-77 up-sync (nothing re-scored), and refresh
        |    [--relay-concurrency N]                   the observer's kind:10040 when we hold their key.
        |  graperank rank USER [--provider PUBKEY]    read the kind:30382 cards about USER, one rank per
        |    [--refresh] [--timeout SECS]              provider; --refresh drains relays on a miss.
        |  graperank status                           read-only local inventory: record counts, cache
        |                                              freshness, operator state, cards per observer.
        |  graperank refresh [--down] [--up]          re-sync known authors' records (kind 0/3/10002/
        |    [--relay-concurrency N] [--author-chunk N] 1984) from their outboxes via NIP-77, so score
        |    [--min-authors N] [--report-limit N]       runs on current data. (`update` is a deprecated
        |    [--no-sync-deletions] [--timeout SECS]     alias.)
        |  graperank register [PROVIDER]              declare a NIP-85 provider in your kind:10040
        |    [--service KIND:TAG] [--relay URL]        (default: self as 30382:rank at your 1st outbox).
        |    [--private]
        |  graperank unregister PROVIDER              remove matching entries from your kind:10040;
        |    [--service KIND:TAG] [--relay URL]        --service/--relay narrow, else all for that key.
        |  graperank providers [USER] [--refresh]     list a user's declared NIP-85 providers.
        |    [--timeout SECS]
        |  graperank operator                         operator keys (~/.amy/operator/): `relay URL…`
        |    [status | relay URL… | keys]              sets the publish target; `keys` maps observer
        |                                              -> service-key. (default: status)
        |  graperank probe                            deprecated alias for `relay probe` (relay census).
        """.trimMargin()

    private const val FLAG_INSERT_BATCH = "insert-batch"
    private const val FLAG_RELAY_CONCURRENCY = "relay-concurrency"
    private const val FLAG_CONCURRENCY = "concurrency"
    private const val INSERT_BATCH_DEFAULT = 500

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

    private const val PROBE_TIMEOUT_MS = 2000

    // Default score cutoff for counting a follower as "trusted" (the `followers`
    // tag). Matches NosFabrica Brainstorm's verifiedFollowersInfluenceCutoff.
    private const val DEFAULT_FOLLOWERS_THRESHOLD = 0.02

    // args.bool on a mistyped flag silently returns false, so the one flag read from
    // three different functions goes through a compile-time-checked name.
    private const val NO_REACHABILITY_CACHE_FLAG = "no-reachability-cache"

    // The probe does BLOCKING DNS + TCP connect, and dead-domain DNS lookups can hang
    // far past the connect timeout. On the shared Dispatchers.IO those hanging lookups
    // starve the crawl's own IO — measured +462s on the finishing drain at hop-3. Run
    // them on a dedicated, isolated daemon pool instead so the crawl's IO is untouched.
    private val probeDispatcher =
        Executors
            .newFixedThreadPool(128) { r -> Thread(r, "relay-probe").apply { isDaemon = true } }
            .asCoroutineDispatcher()

    /**
     * Cheap reachability pre-probe: a raw TCP connect (one round trip) with a tight
     * timeout. Returns false only when the port won't even accept a socket — a dead
     * dropper, refusal, or unroutable/onion/LAN host — which the crawler drops into
     * deadHosts before the WS path pays its 7s connectTimeout. A busy-but-alive relay
     * accepts the SYN instantly at the kernel level (its slowness is at the app layer),
     * so it passes here and is left for the real WS attempt. Unparseable host → true,
     * so an odd URL is never culled on a parse quirk — let the WS decide.
     */
    private suspend fun tcpReachable(relay: NormalizedRelayUrl): Boolean =
        withContext(probeDispatcher) {
            val hostPort = relayHostPort(relay) ?: return@withContext true
            try {
                Socket().use { it.connect(InetSocketAddress(hostPort.first, hostPort.second), PROBE_TIMEOUT_MS) }
                true
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                false
            }
        }

    private fun relayHostPort(relay: NormalizedRelayUrl): Pair<String, Int>? =
        try {
            val uri = URI(relay.url)
            val host = uri.host ?: return null
            val port =
                if (uri.port > 0) {
                    uri.port
                } else if (relay.url.startsWith("wss://", ignoreCase = true)) {
                    443
                } else {
                    80
                }
            host to port
        } catch (e: Exception) {
            null
        }

    suspend fun dispatch(
        dataDir: DataDir,
        tail: Array<String>,
    ): Int =
        // Sub-verbs are explicit words; anything else (npub / hex / nprofile /
        // NIP-05, or nothing) is the OBSERVER positional for a score computation.
        // The bare-positional default means route() can't be used here, so
        // handle --help explicitly before the fall-through.
        when (tail.firstOrNull()) {
            "--help", "-h", "help" -> {
                System.err.println(USAGE)
                0
            }
            "register" -> register(dataDir, tail.drop(1).toTypedArray())
            "unregister" -> unregister(dataDir, tail.drop(1).toTypedArray())
            "providers" -> providers(dataDir, tail.drop(1).toTypedArray())
            "operator" -> operator(dataDir, tail.drop(1).toTypedArray())
            "crawl" -> crawl(dataDir, tail.drop(1).toTypedArray())
            "followers" -> followers(dataDir, tail.drop(1).toTypedArray())
            "status" -> status(dataDir)
            // The relay census outgrew graperank (it feeds the shared NIP-66
            // reachability cache every command reads) and moved to `amy relay
            // probe`; this alias keeps the old spelling working.
            "probe" -> {
                System.err.println("[amy] `graperank probe` is deprecated — use `relay probe` (the relay census).")
                RelayCommands.probe(dataDir, tail.drop(1).toTypedArray())
            }
            // `refresh` is canonical (it refreshes the WoT record kinds from each
            // author's outbox); `update` is the pre-rename back-compat alias.
            "refresh" -> refresh(dataDir, tail.drop(1).toTypedArray())
            "update" -> {
                System.err.println("[amy] `graperank update` is deprecated — use `graperank refresh`.")
                refresh(dataDir, tail.drop(1).toTypedArray())
            }
            "score" -> run(dataDir, tail.drop(1).toTypedArray(), forceOffline = true)
            "publish" -> publish(dataDir, tail.drop(1).toTypedArray())
            "rank" -> rank(dataDir, tail.drop(1).toTypedArray())
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
        val insertBatch = args.intFlag(FLAG_INSERT_BATCH, INSERT_BATCH_DEFAULT)
        // Card cutoff: only scores with rank >= this get a local kind:30382 card;
        // existing cards for targets below it (or gone from the graph) are
        // retracted. Rank is round(score*100), so 2 drops the ~0.015-and-below
        // barely-trusted tail.
        val minRank = args.intFlag("min-rank", 2)
        // A "trusted follower" (the `followers` tag) is a follower whose own score is
        // at or above this. Mirrors Brainstorm's verifiedFollowersInfluenceCutoff
        // (0.02), which is the same 0.02 score == rank 2 line as the default min-rank.
        val followersThreshold = args.flag("followers-threshold")?.toDoubleOrNull() ?: DEFAULT_FOLLOWERS_THRESHOLD

        val params =
            GrapeRankParams(
                attenuation = args.flag("attenuation")?.toDoubleOrNull() ?: GrapeRankParams().attenuation,
                rigor = args.flag("rigor")?.toDoubleOrNull() ?: GrapeRankParams().rigor,
            )
        // Crawl-tuning flags are read later inside newCrawler/flushReachability
        // (and only on the online path), so whitelist them here.
        args.rejectUnknown(
            "max-rounds",
            "max-hops",
            "timeout",
            "diagnose",
            "drain-concurrency",
            "timeout-evict",
            "no-probe",
            "no-aggregators",
            "no-preconnect",
            "preconnect-cap",
            NO_REACHABILITY_CACHE_FLAG,
        )

        // Crawl never signs and score signs cards with the machine-level operator
        // key (`~/.amy/operator/`, independent of any account), so neither needs a
        // personal account — run anonymously when there is none, requiring an
        // explicit observer since there's no logged-in user to default to.
        Context.openOrAnonymous(dataDir).use { ctx ->
            ctx.prepare()
            if (ctx.anonymous && observerArg == null) {
                return Output.error("bad_args", "no account — pass an OBSERVER (npub / hex / nprofile / NIP-05)")
            }
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
            var crawlStats: GrapeRankCrawler.Stats? = null

            if (!offline) {
                val stats = newCrawler(ctx, args).crawl(observer, builder)
                crawlStats = stats
                contactListsFed = stats.contactListsFed
                flushReachability(ctx, args, stats)
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

            // Two derived per-user metrics persisted alongside the rank on each card:
            //  - trusted-follower count: how many of a user's followers score at or
            //    above the cutoff (Brainstorm's trustedFollowers).
            //  - hops: shortest follow-graph distance from the observer (1 = direct
            //    follow). Both are pure functions over the same graph + scores.
            val followerCounts = graph.trustedFollowerCounts(scores, followersThreshold)
            val hops = graph.hopsFrom(observer)

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
                    "followers_threshold" to followersThreshold,
                    "scores" to
                        rankedIds.take(limit).map {
                            mapOf(
                                "pubkey" to graph.pubkeyOf(it),
                                "score" to scores[it],
                                "rank" to rankOf(scores[it]),
                                "followers" to followerCounts[it],
                                "hops" to hops[it],
                            )
                        },
                )

            // Every score run persists its result: the desired card set (every user
            // at or above the rank cutoff) is reconciled into the LOCAL store as
            // kind:30382 cards — signed by a dedicated, stable per-observer service
            // key derived from the machine's operator master (see OperatorKeys),
            // not the account key. Changed ranks are re-signed (the addressable
            // card is replaced), unchanged ones skipped, dropped targets retracted
            // with a kind:5. `graperank publish` and `graperank rank` reuse this
            // set; no relay is touched here.
            val opKeys = ctx.dataDir.operatorKeys()
            val serviceKey = opKeys.serviceKey(observer)
            val providerPubkey = serviceKey.pubKey.toHexKey()
            val desiredCards =
                rankedIds
                    .filter { rankOf(scores[it]) >= minRank }
                    .map { id ->
                        GrapeRankPublisher.ScoredCard(
                            target = graph.pubkeyOf(id),
                            rank = rankOf(scores[id]),
                            followers = followerCounts[id],
                            // A scored user always has a follow path from the observer,
                            // so hops is ≥ 1; guard the UNREACHABLE sentinel just in case.
                            hops = hops[id].takeIf { it >= 1 },
                        )
                    }

            val cardsStart = System.nanoTime()
            val local =
                GrapeRankPublisher(ctx.store) { System.err.println(it) }
                    .reconcileLocal(
                        providerSigner = NostrSignerInternal(serviceKey),
                        providerPubkey = providerPubkey,
                        scored = desiredCards,
                    )
            val cardsMs = (System.nanoTime() - cardsStart) / 1_000_000
            System.err.println(
                "[graperank] local cards: ${local.signed} signed, ${local.unchanged} unchanged, " +
                    "${local.retracted} retracted in $cardsMs ms — `amy graperank publish` pushes them to the operator relay",
            )

            result["provider_pubkey"] = providerPubkey
            result["min_rank"] = minRank
            result["cards_total"] = desiredCards.size
            result["cards_signed"] = local.signed
            result["cards_unchanged"] = local.unchanged
            result["cards_retracted"] = local.retracted
            result["cards_ms"] = cardsMs

            Output.emit(result)
            return 0
        }
    }

    /**
     * Configure the outbox-model crawler from the crawl flags on [args] plus the
     * account's relay policy. Shared by the bare command and `graperank crawl`.
     * Relay policy — where a stranger's kind:10002 is found (index/discovery
     * aggregators + general defaults) and best-effort general relays that might
     * hold content when an outbox is unknown — lives in app code, so the quartz
     * crawler takes it injected.
     */
    private suspend fun newCrawler(
        ctx: Context,
        args: Args,
    ): GrapeRankCrawler {
        val discoveryRelays =
            ctx.bootstrapRelays() + Constants.eventFinderRelays + DefaultIndexerRelayList + EXTRA_DISCOVERY_RELAYS
        val contentFallback = ctx.bootstrapRelays() + Constants.eventFinderRelays
        // Aggregator kind:3 recovery for stragglers is on by default; --no-aggregators
        // disables it for A/B comparison.
        val aggregators = if (args.bool("no-aggregators")) emptySet() else CONTENT_AGGREGATOR_RELAYS
        // Seed the crawl from the reachability cache (--no-reachability-cache to skip):
        // proven-dead relays within the TTL are never dialed (their connect timeouts
        // aren't re-paid), and the proven-live universe is pre-connected in one
        // parallel storm at crawl start so the connect wait is paid once, up front.
        // The crawl's own final live/dead set is flushed back by the caller.
        val reachability =
            if (args.bool(NO_REACHABILITY_CACHE_FLAG)) null else ctx.reachability.snapshot()
        val knownDead = reachability?.dead ?: emptySet()
        // Mass pre-connect sizing: every warm socket is one FD, so the default is
        // derived from the process's ulimit (--preconnect-cap to override,
        // --no-preconnect to fall back to the top-20 warm pool).
        val preconnectCap =
            if (args.bool("no-preconnect")) 0 else args.intFlag("preconnect-cap", Context.defaultPreconnectCap)
        if (preconnectCap > 0 && Context.maxFileDescriptors < 4096) {
            System.err.println(
                "[graperank] open-files limit is ${Context.maxFileDescriptors} → pre-connect capped at " +
                    "$preconnectCap sockets; `ulimit -n 16384` before running unlocks a faster crawl",
            )
        }
        return GrapeRankCrawler(
            client = ctx.client,
            store = ctx.store,
            limiter = ctx.relayLimiter,
            config =
                GrapeRankCrawler.Config(
                    relayListDiscoveryRelays = discoveryRelays,
                    knownDeadRelays = knownDead,
                    knownLiveRelays = if (preconnectCap > 0) reachability?.live.orEmpty() else emptySet(),
                    preconnectCap = preconnectCap,
                    contentFallbackRelays = contentFallback,
                    contentAggregatorRelays = aggregators,
                    maxRounds = args.intFlag("max-rounds", Int.MAX_VALUE),
                    maxHops = args.intFlag("max-hops", Int.MAX_VALUE),
                    timeoutMs = args.longFlag("timeout", 10L) * 1000,
                    parkTimeoutMs = args.longFlag("park-timeout", 40L) * 1000,
                    diagnose = args.bool("diagnose"),
                    insertBatchSize = args.intFlag(FLAG_INSERT_BATCH, INSERT_BATCH_DEFAULT),
                    drainConcurrency = args.intFlag("drain-concurrency", 48),
                    timeoutEvictStrikes = args.intFlag("timeout-evict", 3),
                    // Cheap TCP reachability pre-probe (--no-probe to disable). No Tor
                    // transport here, so .onion relays are skipped on sight.
                    reachabilityProbe = if (args.bool("no-probe")) null else ::tcpReachable,
                    torEnabled = false,
                    // shedDeadDiscovery / shardRotations keep their benchmarked-best
                    // Config defaults.
                ),
            log = crawlLogger(),
        )
    }

    /**
     * Crawl progress logger with a `[t+SSSs]` elapsed prefix, so a saved log
     * attributes wall time to rounds/phases without external timestamps.
     */
    private fun crawlLogger(): (String) -> Unit {
        val start = System.nanoTime()
        return { line ->
            val secs = (System.nanoTime() - start) / 1_000_000_000
            System.err.println("[t+${secs}s] $line")
        }
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
     * Flush the crawl's final live/dead relay verdicts into the shared reachability
     * cache (NIP-66 kind:30166) so the next crawl and the WoT updater start warm and
     * skip proven-dead relays. Best-effort and behind `--no-reachability-cache`: a
     * cache write must never fail the crawl it is summarizing.
     */
    private suspend fun flushReachability(
        ctx: Context,
        args: Args,
        stats: GrapeRankCrawler.Stats,
    ) {
        if (args.bool(NO_REACHABILITY_CACHE_FLAG)) return
        runCatching {
            ctx.reachability.record(reachable = stats.liveRelays, dead = stats.deadRelays)
            System.err.println(
                "[graperank] reachability cache: recorded ${stats.liveRelays.size} live, ${stats.deadRelays.size} dead",
            )
        }.onFailure { System.err.println("[graperank] reachability cache flush failed: ${it.message}") }
    }

    /**
     * `amy graperank crawl [OBSERVER]` — network-only WoT data crawl. Crawls the
     * reachable follow/mute/report graph into the local store (kind
     * 3/10000/1984/10002) and reports what it loaded, WITHOUT scoring.
     * Idempotent + cumulative: run it a few times to make sure everything is loaded,
     * then `graperank score`.
     */
    private suspend fun crawl(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val observerArg = args.positionalOrNull(0)
        // Every crawl flag is read later inside newCrawler/flushReachability,
        // so whitelist the full set up front.
        args.rejectUnknown(
            "max-rounds",
            "max-hops",
            "timeout",
            "park-timeout",
            "diagnose",
            FLAG_INSERT_BATCH,
            "drain-concurrency",
            "timeout-evict",
            "no-probe",
            "no-aggregators",
            "no-preconnect",
            "preconnect-cap",
            NO_REACHABILITY_CACHE_FLAG,
        )
        // Crawl never signs, so no account is needed — run anonymously when there is
        // none, requiring an explicit observer (no logged-in user to default to).
        Context.openOrAnonymous(dataDir).use { ctx ->
            ctx.prepare()
            if (ctx.anonymous && observerArg == null) {
                return Output.error("bad_args", "no account — pass an OBSERVER (npub / hex / nprofile / NIP-05)")
            }
            val observer = observerArg?.let { ctx.requireUserHex(it) } ?: ctx.identity.pubKeyHex
            // Persist-only crawl: no in-memory graph (null builder); every event
            // still lands in the store for a later `score`.
            val stats = newCrawler(ctx, args).crawl(observer, null)
            flushReachability(ctx, args, stats)
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
     * `amy graperank followers [OBSERVER] [flags]` — the reverse crawl: find every
     * user who FOLLOWS the observer and persist their kind:3 contact lists.
     *
     * The outbox model (what `crawl` uses) walks follows *outward* and can't find
     * followers — you don't know a follower exists until you've seen their list, so
     * you can't route to their outbox first. This casts a wide net instead: it asks
     * as many relays as possible for kind:3 events that `#p`-tag the observer, paging
     * each relay past its per-REQ cap. The relay universe is "all possible relays":
     * the reachability-cache live set + every kind:10002 read/write relay and every
     * kind:30166 monitored relay in the store + the index/aggregator relays that hold
     * reverse-follow data for the network. Proven-dead relays are skipped
     * (`--no-reachability-cache` to query them anyway).
     *
     * Each follower's list lands in the store, so it also enriches the graph a later
     * `graperank score` builds — every follower becomes a FOLLOW edge into the
     * observer. Idempotent and cumulative; run it a few times for completeness.
     *
     * Flags: `--relay URL[,URL…]` (query only these instead of the whole universe),
     * `--max N` (cap followers pulled per relay; default: pull every follower each
     * relay holds), `--timeout SECS` (per-page EOSE watchdog, default 15),
     * `--relay-concurrency N` (relays paged at once, default 16), `--insert-batch N`
     * (events per store commit, default 500).
     */
    private suspend fun followers(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val observerArg = args.positionalOrNull(0)
        val relayArg = args.flag("relay")
        val relayConcurrency = args.intFlag(FLAG_RELAY_CONCURRENCY, args.intFlag(FLAG_CONCURRENCY, 16))
        // --max/--timeout/--insert-batch are read later inside the crawler
        // Config; --no-reachability-cache inside allKnownRelays.
        args.rejectUnknown("max", "timeout", FLAG_INSERT_BATCH, NO_REACHABILITY_CACHE_FLAG)

        // Read-only + never signs, so it runs anonymously — but then an OBSERVER
        // positional is required (there's no account to default to).
        Context.openOrAnonymous(dataDir).use { ctx ->
            ctx.prepare()
            if (ctx.anonymous && observerArg == null) {
                return Output.error("bad_args", "usage: amy graperank followers OBSERVER [flags] (no account — pass an observer)")
            }
            val observer = observerArg?.let { ctx.requireUserHex(it) } ?: ctx.identity.pubKeyHex

            val relays =
                relayArg
                    ?.split(",")
                    ?.mapNotNull { RelayUrlNormalizer.normalizeOrNull(it.trim()) }
                    ?.toSet()
                    ?.takeIf { it.isNotEmpty() }
                    ?: allKnownRelays(ctx, args)

            if (relays.isEmpty()) {
                return Output.error("no_relays", "no relays to query — run `amy relay probe` / `amy graperank crawl` first, or pass --relay")
            }

            System.err.println("[followers] querying ${relays.size} relays for kind:3 #p=${observer.take(8)}…")
            val crawler =
                FollowerCrawler(
                    client = ctx.client,
                    store = ctx.store,
                    config =
                        FollowerCrawler.Config(
                            relays = relays,
                            // Default null → pull EVERY follower each relay holds; --max
                            // N caps the total per relay for a quick spot check.
                            maxPerRelay = args.flag("max")?.toIntOrNull(),
                            timeoutMs = args.longFlag("timeout", 15L) * 1000,
                            maxConcurrentRelays = relayConcurrency,
                            insertBatchSize = args.intFlag(FLAG_INSERT_BATCH, INSERT_BATCH_DEFAULT),
                        ),
                    log = { System.err.println(it) },
                )
            val stats = crawler.crawl(observer)

            Output.emit(
                linkedMapOf<String, Any?>(
                    "observer" to observer,
                    "relays_queried" to stats.relaysQueried,
                    "relays_answered" to stats.relaysAnswered,
                    "followers_found" to stats.followersFound,
                    "events_stored" to stats.eventsStored,
                    "download_ms" to stats.downloadMs,
                ),
            )
        }
        return 0
    }

    /**
     * "All possible relays" for the follower crawl: the reachability-cache live set,
     * every kind:10002 read/write relay and every kind:30166 monitored relay the
     * store knows, and the index/aggregator relays that hold reverse-follow data —
     * minus the ones a prior probe proved dead (unless `--no-reachability-cache`).
     */
    private suspend fun allKnownRelays(
        ctx: Context,
        args: Args,
    ): Set<NormalizedRelayUrl> {
        // Read the reachability records (kind:30166) with a throwaway signer, NOT
        // ctx.reachability — the latter derives the monitor key and would CREATE the
        // operator master (a passphrase prompt) purely to read a cache. The follower
        // crawl never signs, so keep it truly account-and-key-free. snapshot() only
        // reads; the signer is used solely for writes. Same trick as `status`.
        val reach = RelayReachabilityStore(store = ctx.store, signer = NostrSignerInternal(KeyPair())).snapshot()
        val relays = LinkedHashSet<NormalizedRelayUrl>()
        relays.addAll(reach.live)
        // Every advertised outbox/inbox relay in the store — the homes of the users
        // whose followers we're hunting are exactly where those followers publish too.
        for (event in ctx.store.query<Event>(Filter(kinds = listOf(AdvertisedRelayListEvent.KIND)))) {
            if (event is AdvertisedRelayListEvent) relays.addAll(event.relaysNorm())
        }
        // Relays a NIP-66 monitor has ever seen (kind:30166) — the widest census.
        for (event in ctx.store.query<Event>(Filter(kinds = listOf(RelayDiscoveryEvent.KIND)))) {
            if (event is RelayDiscoveryEvent) event.relay()?.let { relays.add(it) }
        }
        // Aggregators/indexers hold reverse-follow data for users whose own outbox
        // we've never learned — the biggest completeness lever for followers.
        relays.addAll(CONTENT_AGGREGATOR_RELAYS)
        relays.addAll(EXTRA_DISCOVERY_RELAYS)
        relays.addAll(ctx.bootstrapRelays())
        relays.addAll(Constants.eventFinderRelays)

        if (!args.bool(NO_REACHABILITY_CACHE_FLAG)) relays.removeAll(reach.dead)
        return relays
    }

    /**
     * `amy graperank status` — read-only inventory of everything a GrapeRank run
     * depends on, straight from the local store: WoT record counts (the "do I
     * need to crawl again?" answer), reachability-cache size + freshness,
     * operator/service-key state, and the persisted card set per observer.
     * No network, no signing, no side effects.
     */
    private suspend fun status(dataDir: DataDir): Int {
        Context.openOrAnonymous(dataDir).use { ctx ->
            // Deliberately no ctx.prepare(): status must stay offline (nothing here
            // needs a relay connection or marmot state).
            suspend fun countKind(kind: Int) = ctx.store.count(Filter(kinds = listOf(kind)))

            // The store keeps only the newest replaceable event per author, so the
            // kind:3 count is "users whose follow list we hold" — the graph size a
            // `score` would see.
            val contactLists = countKind(ContactListEvent.KIND)

            // Read-only reachability view: ctx.reachability would lazily derive the
            // monitor key and thereby CREATE the operator master on a fresh machine;
            // a throwaway signer reads the same kind:30166 records without that
            // side effect (the signer is only used for writes).
            val reach = RelayReachabilityStore(store = ctx.store, signer = NostrSignerInternal(KeyPair())).snapshot()
            val newestReachRecord =
                ctx.store
                    .query<Event>(Filter(kinds = listOf(RelayDiscoveryEvent.KIND), limit = 1))
                    .firstOrNull()
                    ?.createdAt

            val opKeys = ctx.dataDir.operatorKeys()
            val cards =
                opKeys.providers().map { (observer, rec) ->
                    linkedMapOf<String, Any?>(
                        "observer" to observer,
                        "provider_pubkey" to rec.providerPubKey,
                        "cards" to ctx.store.count(Filter(kinds = listOf(ContactCardEvent.KIND), authors = listOf(rec.providerPubKey))),
                        "retractions" to ctx.store.count(Filter(kinds = listOf(DeletionEvent.KIND), authors = listOf(rec.providerPubKey))),
                    )
                }

            Output.emit(
                linkedMapOf<String, Any?>(
                    "store" to
                        linkedMapOf<String, Any?>(
                            "profiles" to countKind(MetadataEvent.KIND),
                            "contact_lists" to contactLists,
                            "mute_lists" to countKind(MuteListEvent.KIND),
                            "reports" to countKind(ReportEvent.KIND),
                            "relay_lists" to countKind(AdvertisedRelayListEvent.KIND),
                        ),
                    "reachability" to
                        linkedMapOf<String, Any?>(
                            "live" to reach.live.size,
                            "dead" to reach.dead.size,
                            "newest_record_age_s" to newestReachRecord?.let { (TimeUtils.now() - it).coerceAtLeast(0) },
                        ),
                    "operator" to
                        if (opKeys.exists()) {
                            linkedMapOf<String, Any?>(
                                "initialized" to true,
                                "master_pubkey" to opKeys.masterPubKey(),
                                "relays" to opKeys.operatorRelays().map { it.url },
                            )
                        } else {
                            linkedMapOf<String, Any?>("initialized" to false)
                        },
                    "cards" to cards,
                    "note" to if (contactLists == 0) "no contact lists in the local store — run `amy graperank crawl` first" else null,
                ),
            )
        }
        return 0
    }

    /**
     * `amy graperank refresh [flags]` (alias: `update`) — refresh every locally-known author's WoT
     * record kinds (0 / 3 / 10002 / 1984) straight from their own outbox, so the
     * next `graperank score` runs on current data without a full follow-graph crawl.
     *
     * Thin wrapper over quartz's [GrapeRankUpdater]: it reads every kind:10002 in the
     * store, inverts them into a `write-relay -> authors` map (the outbox model), and
     * runs one NIP-77 negentropy reconcile per write relay scoped to its authors —
     * bidirectional, settling deletions over the residual (its applyDown direction
     * downloads the relay's kind:5 when an uploaded record was rejected), and falling
     * back to a full paged download when a relay can't reconcile. This command only
     * parses flags and renders the [GrapeRankUpdater.Result] as text/JSON.
     *
     * Flags: `--timeout SECS` (per-group idle watchdog, default 30),
     * `--relay-concurrency N` (relays reconciled at once, default 4),
     * `--author-chunk N` (authors per reconcile filter, default 500),
     * `--min-authors N` (skip relays hosting fewer than N of our authors, default 1),
     * `--report-limit N` (per-relay rows in the JSON, default 50),
     * `--down` / `--up` / `--no-sync-deletions`.
     */
    private suspend fun refresh(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val reportLimit = args.intFlag("report-limit", 50).coerceAtLeast(0)
        // Default is bidirectional; a single --down/--up narrows to that direction.
        val downFlag = args.bool("down")
        val upFlag = args.bool("up")
        // The remaining flags are read later inside the updater Config.
        args.rejectUnknown(
            "no-sync-deletions",
            FLAG_RELAY_CONCURRENCY,
            FLAG_CONCURRENCY,
            "author-chunk",
            "min-authors",
            "timeout",
            NO_REACHABILITY_CACHE_FLAG,
        )

        Context.openOrAnonymous(dataDir).use { ctx ->
            ctx.prepare()

            // Skip relays a crawl/monitor proved dead within the cache's TTL — a dead
            // relay cannot serve its authors, so reconciling it only burns a timeout.
            // Live author-advertised relays are always synced (--no-reachability-cache
            // to reconcile every relay regardless).
            val knownDead =
                if (args.bool(NO_REACHABILITY_CACHE_FLAG)) emptySet() else ctx.reachability.snapshot().dead

            val updater =
                GrapeRankUpdater(
                    client = ctx.client,
                    store = ctx.store,
                    config =
                        GrapeRankUpdater.Config(
                            down = downFlag || !upFlag,
                            up = upFlag || !downFlag,
                            syncDeletions = !args.bool("no-sync-deletions"),
                            relayConcurrency = args.intFlag(FLAG_RELAY_CONCURRENCY, args.intFlag(FLAG_CONCURRENCY, 4)),
                            authorChunk = args.intFlag("author-chunk", 500),
                            minAuthors = args.intFlag("min-authors", 1),
                            idleTimeoutMs = args.longFlag("timeout", 30L) * 1000,
                            knownDead = knownDead,
                        ),
                    log = { System.err.println(it) },
                )

            val result = updater.update()

            if (result.relays == 0) {
                Output.emit(
                    linkedMapOf<String, Any?>(
                        "relay_lists_in_store" to result.relayListsInStore,
                        "authors_with_outbox" to result.authorsWithOutbox,
                        "relays" to 0,
                        "note" to "no kind:10002 write relays in the local store — run `graperank crawl` first",
                    ),
                )
                return 0
            }

            // Busiest relays first, capped so a many-thousand-relay run still emits a
            // bounded JSON object; totals below always cover every relay.
            val report =
                result.perRelay
                    .sortedByDescending { it.downloaded + it.uploaded }
                    .take(reportLimit)
                    .map {
                        linkedMapOf<String, Any?>(
                            "relay" to it.relay.url,
                            "authors" to it.authors,
                            "need" to it.need,
                            "have" to it.have,
                            "downloaded" to it.downloaded,
                            "uploaded" to it.uploaded,
                            "deletions_sent_up" to it.deletionsSentUp,
                            "deletions_applied_down" to it.deletionsAppliedDown,
                            "paged_fallback" to it.pagedFallback,
                            "error" to it.error,
                        )
                    }

            Output.emit(
                linkedMapOf<String, Any?>(
                    "kinds" to GrapeRankUpdater.DEFAULT_KINDS,
                    "relay_lists_in_store" to result.relayListsInStore,
                    "authors_with_outbox" to result.authorsWithOutbox,
                    "relays" to result.relays,
                    "relays_ok" to result.relaysOk,
                    "relays_failed" to result.relaysFailed,
                    "relays_paged_fallback" to result.relaysPagedFallback,
                    "downloaded" to result.downloaded,
                    "uploaded" to result.uploaded,
                    "deletions_sent_up" to result.deletionsSentUp,
                    "deletions_applied_down" to result.deletionsAppliedDown,
                    "report_limit" to reportLimit,
                    "per_relay" to report,
                ),
            )
            return 0
        }
    }

    /**
     * `amy graperank publish [OBSERVER] [--relay URL[,URL…]] [--relay-concurrency N] [--timeout SECS]`
     *
     * Transport only: make the operator relay(s) converge to the local card set
     * that `graperank score` persisted for OBSERVER (default: the active account).
     * One NIP-77 up-only reconcile per relay over the provider service key's
     * kind:30382 cards + kind:5 retractions — nothing is re-scored or re-signed,
     * and a card the relay lost is restored. A relay that can't reconcile gets the
     * full local set blast-published instead. Also refreshes the observer's
     * kind:10040 provider pointer when we hold their key.
     */
    private suspend fun publish(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val observerArg = args.positionalOrNull(0)
        val relayArg = args.flag("relay")
        // --relay-concurrency is canonical for "relays worked at once" across the
        // graperank verbs; --concurrency is accepted everywhere as its alias.
        val relayConcurrency = args.intFlag(FLAG_RELAY_CONCURRENCY, args.intFlag(FLAG_CONCURRENCY, 4))
        // Idle watchdog per relay reconcile (not a total budget), like `refresh`.
        val idleTimeoutMs = args.longFlag("timeout", 30L) * 1000
        args.rejectUnknown()

        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val observer = observerArg?.let { ctx.requireUserHex(it) } ?: ctx.identity.pubKeyHex
            val opKeys = ctx.dataDir.operatorKeys()
            val providerPubkey = opKeys.serviceKey(observer).pubKey.toHexKey()

            // Cards live on the operator's own relay(s); --relay overrides.
            val relays =
                relayArg
                    ?.split(",")
                    ?.mapNotNull { RelayUrlNormalizer.normalizeOrNull(it.trim()) }
                    ?.toSet()
                    ?.takeIf { it.isNotEmpty() }
                    ?: opKeys.operatorRelays()
            if (relays.isEmpty()) {
                return Output.error("no_relays", "no operator relay configured — run `amy graperank operator relay <url>` or pass --relay")
            }

            val publisher = GrapeRankPublisher(ctx.store) { System.err.println(it) }
            val sync =
                publisher.syncToRelays(
                    client = ctx.client,
                    providerPubkey = providerPubkey,
                    relays = relays,
                    relayConcurrency = relayConcurrency,
                    idleTimeoutMs = idleTimeoutMs,
                )

            if (sync.cards == 0 && sync.deletions == 0) {
                Output.emit(
                    linkedMapOf<String, Any?>(
                        "observer" to observer,
                        "provider_pubkey" to providerPubkey,
                        "cards" to 0,
                        "note" to "no local cards for this observer — run `amy graperank score` first",
                    ),
                )
                return 0
            }

            // Help the observer point clients at this provider: publish their
            // kind:10040 (30382:rank -> providerPubkey @ operator relay) to
            // their outbox — but only when we actually hold their key.
            val observer10040 = maybePublishObserverProviderList(ctx, observer, providerPubkey, relays.first())

            Output.emit(
                linkedMapOf<String, Any?>(
                    "observer" to observer,
                    "provider_pubkey" to providerPubkey,
                    "cards" to sync.cards,
                    "deletions" to sync.deletions,
                    "relays" to sync.perRelay.size,
                    "relays_ok" to sync.perRelay.count { it.ok },
                    "relays_failed" to sync.perRelay.count { !it.ok },
                    "uploaded" to sync.perRelay.sumOf { it.uploaded },
                    "fallback_published" to sync.perRelay.sumOf { it.fallbackPublished },
                    "per_relay" to
                        sync.perRelay.map {
                            linkedMapOf<String, Any?>(
                                "relay" to it.relay.url,
                                "uploaded" to it.uploaded,
                                "fallback_published" to it.fallbackPublished,
                                "fallback_rejected" to it.fallbackRejected,
                                "error" to it.error,
                            )
                        },
                    "observer_10040" to observer10040,
                ),
            )
            return 0
        }
    }

    /**
     * `amy graperank rank USER [--provider PUBKEY] [--refresh] [--timeout SECS]`
     *
     * The consumer side of NIP-85: read the kind:30382 cards about USER and print
     * one rank per provider (newest card each). Cache-first — a `graperank score`
     * run on this machine already left its cards in the store — falling back to a
     * relay drain on a miss or with `--refresh` (sources: the operator relays, the
     * relays declared in the account's kind:10040, and the bootstrap set).
     * `--provider` narrows to one provider key.
     */
    private suspend fun rank(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val userArg =
            args.positionalOrNull(0)
                ?: return Output.error("bad_args", "usage: amy graperank rank USER [--provider PUBKEY] [--refresh] [--timeout SECS]")
        val providerArg = args.flag("provider")
        val refresh = args.bool("refresh")
        val timeoutMs = args.longFlag("timeout", 8L) * 1000
        args.rejectUnknown()

        Context.openOrAnonymous(dataDir).use { ctx ->
            ctx.prepare()
            val user = ctx.requireUserHex(userArg)
            val provider = providerArg?.let { ctx.requireUserHex(it) }
            val cardFilter =
                Filter(
                    kinds = listOf(ContactCardEvent.KIND),
                    tags = mapOf("d" to listOf(user)),
                    authors = provider?.let { listOf(it) },
                )

            suspend fun localCards(): List<ContactCardEvent> = ctx.store.query<Event>(cardFilter).filterIsInstance<ContactCardEvent>()

            var cards = localCards()
            if (refresh || cards.isEmpty()) {
                val relays = rankSourceRelays(ctx, provider)
                if (relays.isNotEmpty()) {
                    ctx.drain(relays.associateWith { listOf(cardFilter.copy(limit = 50)) }, timeoutMs)
                    cards = localCards()
                }
            }

            // Newest card per provider key, strongest assertion first.
            val newest =
                cards
                    .groupBy { it.pubKey }
                    .mapNotNull { (_, list) -> list.maxByOrNull { it.createdAt } }
                    .sortedWith(compareByDescending<ContactCardEvent> { it.rank() ?: -1 }.thenByDescending { it.createdAt })

            // A provider key this machine's operator master derived maps back to
            // the observer whose subjective view the rank expresses.
            val providerToObserver =
                ctx.dataDir
                    .operatorKeys()
                    .providers()
                    .entries
                    .associate { (observer, rec) -> rec.providerPubKey to observer }

            Output.emit(
                linkedMapOf<String, Any?>(
                    "user" to user,
                    "found" to newest.isNotEmpty(),
                    "cards" to
                        newest.map { card ->
                            linkedMapOf<String, Any?>(
                                "provider" to card.pubKey,
                                "rank" to card.rank(),
                                "followers" to card.followerCount(),
                                "hops" to card.hops(),
                                "observer" to providerToObserver[card.pubKey],
                                "created_at" to card.createdAt,
                                "event_id" to card.id,
                            )
                        },
                ),
            )
            return 0
        }
    }

    /**
     * Relays worth draining for someone's kind:30382 cards: the machine's own
     * operator relay(s), every relay the account's kind:10040 declares for a
     * 30382 service (narrowed to [provider] when given), and the bootstrap set.
     */
    private suspend fun rankSourceRelays(
        ctx: Context,
        provider: HexKey?,
    ): Set<NormalizedRelayUrl> {
        val declared =
            if (!ctx.anonymous) {
                providerListOf(ctx, ctx.identity.pubKeyHex)
                    ?.serviceProviders()
                    ?.filter { it.service.kind == ContactCardEvent.KIND && (provider == null || it.pubkey == provider) }
                    ?.map { it.relayUrl }
                    .orEmpty()
            } else {
                emptyList()
            }
        return ctx.dataDir.operatorKeys().operatorRelays() + declared + ctx.bootstrapRelays() + Constants.eventFinderRelays
    }

    /**
     * `amy graperank operator [status | relay <url>… | keys]`
     *
     * Manage the machine's operator keys used to sign trusted-assertion cards.
     *  - `status` (default): master pubkey, configured relay(s), service-key count.
     *  - `relay <url>…`: set the operator relay(s) the cards + retractions publish
     *    to; creates the operator master on first use.
     *  - `keys` (alias: the pre-rename `providers`, which collided with
     *    `graperank providers`): the observer -> service-key mapping derived so
     *    far — what a third-party observer wires into their kind:10040.
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

            "keys", "providers" -> {
                Output.emit(
                    mapOf(
                        "master_pubkey" to if (opKeys.exists()) opKeys.masterPubKey() else null,
                        "keys" to opKeys.providers().map { (observer, rec) -> mapOf("observer" to observer, "provider_pubkey" to rec.providerPubKey) },
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
                            "keys" to opKeys.providers().size,
                        ),
                    )
                }
                0
            }

            else -> Output.error("bad_args", "unknown operator subcommand '${rest.first()}' (status | relay | keys)")
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
        args.rejectUnknown()

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
                        "based_on" to latest.id,
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
            RawEventSupport.publishGuard(ack, event.id)?.let { return it }
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
     * `amy graperank unregister PROVIDER [--service KIND:TAG] [--relay URL] [--timeout SECS]`
     *
     * The inverse of [register]: drop matching provider entries — public AND
     * private — from the account's kind:10040 [TrustProviderListEvent] and
     * re-publish it. PROVIDER is required; `--service` / `--relay` narrow the
     * match when the same key is listed for several services or relays — without
     * them, every entry for that provider key is removed. Fetches the freshest
     * list first so the removal applies to the current provider set.
     */
    private suspend fun unregister(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val providerArg =
            args.positionalOrNull(0)
                ?: args.flag("provider")
                ?: return Output.error("bad_args", "usage: amy graperank unregister PROVIDER [--service KIND:TAG] [--relay URL]")
        val serviceArg = args.flag("service")
        val relayArg = args.flag("relay")
        val timeoutMs = args.longFlag("timeout", 8L) * 1000
        args.rejectUnknown()

        val service =
            serviceArg?.let {
                ServiceType.parse(it) ?: return Output.error("bad_args", "--service must be KIND:TAG, e.g. 30382:rank")
            }
        val relay =
            relayArg?.let {
                RelayUrlNormalizer.normalizeOrNull(it) ?: return Output.error("bad_args", "--relay is not a valid relay URL")
            }

        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val provider = ctx.requireUserHex(providerArg)
            val outbox = ctx.outboxRelays()

            val latest =
                fetchLatestProviderList(ctx, ctx.identity.pubKeyHex, outbox, timeoutMs)
                    ?: return Output.error("not_found", "no kind:10040 provider list found for this account")

            fun matches(tag: ServiceProviderTag) =
                tag.pubkey == provider &&
                    (service == null || tag.service == service) &&
                    (relay == null || tag.relayUrl == relay)

            val publicMatches = latest.serviceProviders().filter(::matches)
            val privateMatches =
                latest
                    .privateTags(ctx.signer)
                    ?.serviceProviders()
                    .orEmpty()
                    .filter(::matches)
            val toRemove = (publicMatches + privateMatches).distinct()

            if (toRemove.isEmpty()) {
                Output.emit(
                    mapOf(
                        "provider" to provider,
                        "changed" to false,
                        "removed" to emptyList<Any>(),
                        "based_on" to latest.id,
                    ),
                )
                return 0
            }

            // remove() strips the tag from both the public and the private set,
            // re-signing each round; only the final version is published.
            var event = latest
            for (tag in toRemove) {
                event = TrustProviderListEvent.remove(event, tag, ctx.signer)
            }

            val ack = ctx.publish(event, outbox)
            RawEventSupport.publishGuard(ack, event.id)?.let { return it }
            Output.emit(
                mapOf(
                    "provider" to provider,
                    "changed" to true,
                    "removed" to toRemove.map { mapOf("service" to it.service.toValue(), "relay" to it.relayUrl.url) },
                    "event_id" to event.id,
                    "based_on" to latest.id,
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
        args.rejectUnknown()

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
        if (alreadyListed) return latest.id

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
