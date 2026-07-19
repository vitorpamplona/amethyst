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
package com.vitorpamplona.amethyst.cli.commands.graperank

import com.vitorpamplona.amethyst.cli.Args
import com.vitorpamplona.amethyst.cli.Context
import com.vitorpamplona.amethyst.cli.DataDir
import com.vitorpamplona.amethyst.cli.Output
import com.vitorpamplona.quartz.experimental.graperank.GrapeRank
import com.vitorpamplona.quartz.experimental.graperank.GrapeRankCrawler
import com.vitorpamplona.quartz.experimental.graperank.GrapeRankParams
import com.vitorpamplona.quartz.experimental.graperank.GrapeRankPublisher
import com.vitorpamplona.quartz.experimental.graperank.TrustGraphBuilder
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip02FollowList.ContactListEvent
import com.vitorpamplona.quartz.nip09Deletions.DeletionEvent
import com.vitorpamplona.quartz.nip09Deletions.DeletionIndex
import com.vitorpamplona.quartz.nip51Lists.muteList.MuteListEvent
import com.vitorpamplona.quartz.nip56Reports.ReportEvent
import kotlin.math.roundToInt

/**
 * The scoring stage: the bare `amy graperank [OBSERVER]` combo (crawl + score)
 * and `amy graperank score [OBSERVER]` (the same pipeline with the crawl forced
 * off). Builds the trust graph, runs the GrapeRank engine, and ALWAYS persists
 * the result as locally-signed NIP-85 kind:30382 cards.
 */
object GrapeRankScore {
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
        // is read straight from args by [GrapeRankCrawl.newCrawler]; only these two are
        // surfaced in the result JSON, so keep local copies for that.
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
                val stats = GrapeRankCrawl.newCrawler(ctx, args).crawl(observer, builder)
                crawlStats = stats
                contactListsFed = stats.contactListsFed
                GrapeRankCrawl.flushReachability(ctx, args, stats)
                GrapeRankCrawl.reportRelayFeedback(ctx)
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
}
