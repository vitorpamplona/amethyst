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
import com.vitorpamplona.amethyst.commons.wot.GrapeRank
import com.vitorpamplona.amethyst.commons.wot.GrapeRankParams
import com.vitorpamplona.amethyst.commons.wot.TrustGraphBuilder
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip02FollowList.ContactListEvent
import com.vitorpamplona.quartz.nip51Lists.muteList.MuteListEvent
import com.vitorpamplona.quartz.nip56Reports.ReportEvent
import com.vitorpamplona.quartz.nip65RelayList.AdvertisedRelayListEvent
import com.vitorpamplona.quartz.nip85TrustedAssertions.users.ContactCardEvent
import com.vitorpamplona.quartz.nip85TrustedAssertions.users.tags.RankTag
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
 * relays — until no new users appear (typically ~8 hops), then runs the scoring
 * engine in `commons/wot`.
 *
 * Prints a ranked list (text, or one JSON object under `--json`). With
 * `--publish`, results are also published as NIP-85 kind:30382 `ContactCardEvent`
 * trusted assertions (one per scored user, `rank = round(score*100)`).
 */
object GrapeRankCommand {
    // Authors per REQ filter — keeps individual subscriptions within relay limits.
    private const val AUTHORS_PER_FILTER = 300

    // Concurrent publishes when writing NIP-85 cards.
    private const val PUBLISH_CONCURRENCY = 16

    suspend fun run(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val observerArg = args.positionalOrNull(0)
        val maxDepth = args.intFlag("max-depth", 8)
        val maxUsers = args.intFlag("max-users", 50_000)
        val limit = args.intFlag("limit", 100)
        val minScore = args.flag("min-score")?.toDoubleOrNull() ?: 0.0
        val offline = args.bool("offline")
        val timeoutMs = args.longFlag("timeout", 10L) * 1000
        val doPublish = args.bool("publish")
        val minRank = args.intFlag("min-rank", 1)
        val publishLimit = args.intFlag("publish-limit", 500)
        val publishRelaysArg = args.flag("publish-relay")

        val params =
            GrapeRankParams(
                attenuation = args.flag("attenuation")?.toDoubleOrNull() ?: GrapeRankParams().attenuation,
                rigor = args.flag("rigor")?.toDoubleOrNull() ?: GrapeRankParams().rigor,
            )

        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val observer = observerArg?.let { ctx.requireUserHex(it) } ?: ctx.identity.pubKeyHex

            val graphKinds = listOf(ContactListEvent.KIND, MuteListEvent.KIND, ReportEvent.KIND)

            var depthReached = 0
            val events: List<Event>

            if (offline) {
                events = ctx.store.query(Filter(kinds = graphKinds))
                System.err.println("[graperank] offline: ${events.size} events from local store")
            } else {
                val collected = mutableListOf<Event>()
                val discovered = hashSetOf(observer)
                var frontier: Set<HexKey> = setOf(observer)

                for (hop in 0 until maxDepth) {
                    if (frontier.isEmpty()) break
                    depthReached = hop + 1

                    ensureRelayLists(ctx, frontier, timeoutMs)

                    val filters = routeByOutbox(ctx, frontier, graphKinds)
                    collected += ctx.drain(filters, timeoutMs).map { it.second }

                    val next = hashSetOf<HexKey>()
                    for (pk in frontier) {
                        ctx.contactsOf(pk)?.verifiedFollowKeySet()?.forEach { followed ->
                            if (discovered.size < maxUsers && discovered.add(followed)) next += followed
                        }
                    }
                    System.err.println("[graperank] hop ${hop + 1}: fetched frontier=${frontier.size}, new=${next.size}, total=${discovered.size}")

                    if (discovered.size >= maxUsers) {
                        System.err.println("[graperank] reached --max-users=$maxUsers cap; stopping crawl")
                        break
                    }
                    frontier = next
                }
                events = collected
            }

            val graph = TrustGraphBuilder.build(events)
            val scores = GrapeRank(params).compute(graph, observer)

            fun rankOf(score: Double) = (score * 100).roundToInt()

            val ranked =
                scores.entries
                    .filter { it.value >= minScore }
                    .sortedByDescending { it.value }

            val result =
                linkedMapOf<String, Any?>(
                    "observer" to observer,
                    "depth_reached" to depthReached,
                    "graph_users" to graph.users.size,
                    "graph_edges" to graph.edgeCount(),
                    "users_scored" to scores.size,
                    "scores" to
                        ranked.take(limit).map {
                            mapOf("pubkey" to it.key, "score" to it.value, "rank" to rankOf(it.value))
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
                    ranked
                        .filter { rankOf(it.value) >= minRank }
                        .map { it.key to rankOf(it.value) }
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

            Output.emit(result)
            return 0
        }
    }

    /**
     * Fetch kind:10002 relay lists for any frontier member we don't already know,
     * so [routeByOutbox] can route their content query to their own write relays.
     * Uses the broad bootstrap + event-finder relay set as the discovery seed —
     * the CLI analog of the app's tiered outbox lookup.
     */
    private suspend fun ensureRelayLists(
        ctx: Context,
        pubkeys: Set<HexKey>,
        timeoutMs: Long,
    ) {
        val missing = pubkeys.filter { ctx.relaysOf(it) == null }
        if (missing.isEmpty()) return

        val seedRelays = ctx.bootstrapRelays() + Constants.eventFinderRelays
        if (seedRelays.isEmpty()) return

        val filters =
            seedRelays.associateWith {
                missing.chunked(AUTHORS_PER_FILTER).map { chunk ->
                    Filter(kinds = listOf(AdvertisedRelayListEvent.KIND), authors = chunk)
                }
            }
        ctx.drain(filters, timeoutMs)
    }

    /**
     * Group [pubkeys] by the relays we should query for their events: each user's
     * kind:10002 write relays (the outbox model), falling back to the broad
     * event-finder set for users with no advertised relay list. Authors are
     * chunked per relay to respect relay REQ limits.
     */
    private suspend fun routeByOutbox(
        ctx: Context,
        pubkeys: Set<HexKey>,
        kinds: List<Int>,
    ): Map<NormalizedRelayUrl, List<Filter>> {
        val fallback = ctx.bootstrapRelays() + Constants.eventFinderRelays
        val perRelay = HashMap<NormalizedRelayUrl, MutableSet<HexKey>>()

        for (pk in pubkeys) {
            val write = ctx.relaysOf(pk)?.writeRelaysNorm()?.takeIf { it.isNotEmpty() }
            val relays = write ?: fallback
            for (relay in relays) perRelay.getOrPut(relay) { HashSet() }.add(pk)
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
