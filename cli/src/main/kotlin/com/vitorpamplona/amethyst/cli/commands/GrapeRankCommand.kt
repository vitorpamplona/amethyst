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
import com.vitorpamplona.quartz.nip85TrustedAssertions.list.TrustProviderListEvent
import com.vitorpamplona.quartz.nip85TrustedAssertions.list.serviceProviders
import com.vitorpamplona.quartz.nip85TrustedAssertions.list.tags.ProviderTypes
import com.vitorpamplona.quartz.nip85TrustedAssertions.list.tags.ServiceProviderTag
import com.vitorpamplona.quartz.nip85TrustedAssertions.list.tags.ServiceType
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

    // Emit a scoring-progress line every this many worklist visits.
    private const val SCORE_PROGRESS_STEP = 5_000

    // Times we re-query an unreachable user's outbox before giving up on it, so
    // the crawl still terminates on a finite graph.
    private const val MAX_OUTBOX_ATTEMPTS = 3

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
        // checked). --max-rounds is only a safety backstop.
        val maxRounds = args.intFlag("max-rounds", Int.MAX_VALUE)
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

            var rounds = 0
            var relaysContactedCount = 0
            val events: List<Event>

            if (offline) {
                events = ctx.store.query(Filter(kinds = graphKinds))
                System.err.println("[graperank] offline: ${events.size} events from local store")
            } else {
                val collected = mutableListOf<Event>()
                val discovered = hashSetOf(observer)
                // Per-user relay hints harvested from the `p`-tag relay hints in the
                // contact lists we crawl (A's follow of B says where B writes) — a
                // discovery tier below each user's kind:10002 outbox.
                val relayHints = HashMap<HexKey, MutableSet<NormalizedRelayUrl>>()
                // Users we're finished with: their outbox was queried and we either
                // downloaded their kind:3 or ran out of retry attempts. Growing this
                // set toward `discovered` is what drives the crawl to completion.
                val done = hashSetOf<HexKey>()
                val attempts = HashMap<HexKey, Int>()
                // The pool of relays we actually route outbox queries to, grown as
                // more users' kind:10002 outboxes are discovered.
                val relaysContacted = hashSetOf<NormalizedRelayUrl>()

                // Loop until every discovered user has had their outbox checked and
                // their kind:3/10000/1984 pulled from it — no user cap. A user whose
                // outbox stays unreachable is dropped after MAX_OUTBOX_ATTEMPTS tries
                // so the crawl still terminates.
                while (rounds < maxRounds) {
                    val pending = discovered.filterNot { it in done }
                    if (pending.isEmpty()) break
                    rounds++

                    // 1. Resolve kind:10002 outboxes for pending users missing them.
                    ensureRelayLists(ctx, pending.toSet(), relayHints, timeoutMs)

                    // 2. Pull kind:3/10000/1984 from each pending user's own outbox
                    //    (hints + general relays only when the outbox is unknown).
                    val before = collected.size
                    val filters = routeByOutbox(ctx, pending.toSet(), relayHints, graphKinds)
                    relaysContacted += filters.keys
                    collected += ctx.drain(filters, timeoutMs).map { it.second }

                    // 3. Mark done / retry, harvest hints, expand the follow graph.
                    var downloaded = 0
                    var newUsers = 0
                    for (pk in pending) {
                        val contacts = ctx.contactsOf(pk)
                        if (contacts != null) {
                            done += pk
                            downloaded++
                            for (tag in contacts.follows()) {
                                tag.relayUri?.let { relayHints.getOrPut(tag.pubKey) { HashSet() }.add(it) }
                                if (discovered.add(tag.pubKey)) newUsers++
                            }
                        } else {
                            val tries = (attempts[pk] ?: 0) + 1
                            attempts[pk] = tries
                            // Give up once we've exhausted retries: either the user has
                            // no contact list, or their outbox is unreachable.
                            if (tries >= MAX_OUTBOX_ATTEMPTS) done += pk
                        }
                    }
                    System.err.println(
                        "[graperank] round $rounds: queried=${pending.size}, +events=${collected.size - before}, " +
                            "downloaded=$downloaded, newUsers=$newUsers, discovered=${discovered.size}, done=${done.size}",
                    )
                }

                relaysContactedCount = relaysContacted.size
                val unreached = discovered.count { it !in done || ctx.contactsOf(it) == null }
                System.err.println(
                    "[graperank] crawl complete: ${discovered.size} users discovered, " +
                        "${discovered.size - unreached} contact lists downloaded, $unreached without one, " +
                        "$relaysContactedCount relays contacted, $rounds rounds",
                )
                events = collected
            }

            val graph = TrustGraphBuilder.build(events)
            System.err.println(
                "[graperank] graph built: ${graph.users.size} users, ${graph.edgeCount()} edges from ${events.size} events; scoring…",
            )

            // Live scoring progress: the worklist visits each reachable user once
            // per relaxation; report every PROGRESS_STEP visits so a large graph
            // shows movement instead of hanging silently.
            val scores =
                GrapeRank(params).compute(graph, observer) { visited, scored, queued ->
                    if (visited % SCORE_PROGRESS_STEP == 0) {
                        System.err.println("[graperank] scoring: $visited visited, $scored scored, $queued queued")
                    }
                }
            System.err.println("[graperank] scored ${scores.size} users")

            fun rankOf(score: Double) = (score * 100).roundToInt()

            val ranked =
                scores.entries
                    .filter { it.value >= minScore }
                    .sortedByDescending { it.value }

            val result =
                linkedMapOf<String, Any?>(
                    "observer" to observer,
                    "crawl_rounds" to rounds,
                    "relays_contacted" to relaysContactedCount,
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
    private suspend fun relayListDiscoveryRelays(ctx: Context): Set<NormalizedRelayUrl> = ctx.bootstrapRelays() + Constants.eventFinderRelays + DefaultIndexerRelayList

    /**
     * Best-effort fallback relays for **content** (kind:3/10000/1984/0) when a
     * user's outbox is unknown or unreachable. Content lives on each user's own
     * outbox, so this is only general-purpose relays that *might* hold a copy —
     * bootstrap + event-finder. **No indexers**: they don't serve these kinds.
     */
    private suspend fun contentFallbackRelays(ctx: Context): Set<NormalizedRelayUrl> = ctx.bootstrapRelays() + Constants.eventFinderRelays

    /**
     * Fetch kind:10002 relay lists for any frontier member we don't already know,
     * so [routeByOutbox] can route their content query to their own write relays.
     * Queries the relay-list discovery set (incl. indexers) plus each user's
     * harvested relay [hints] — the CLI analog of the app's tiered
     * `pickRelaysToLoadUsers`.
     */
    private suspend fun ensureRelayLists(
        ctx: Context,
        pubkeys: Set<HexKey>,
        hints: Map<HexKey, Set<NormalizedRelayUrl>>,
        timeoutMs: Long,
    ) {
        val missing = pubkeys.filter { ctx.relaysOf(it) == null }
        if (missing.isEmpty()) return

        val base = relayListDiscoveryRelays(ctx)
        val perRelay = HashMap<NormalizedRelayUrl, MutableSet<HexKey>>()
        for (pk in missing) {
            for (relay in base + hints[pk].orEmpty()) perRelay.getOrPut(relay) { HashSet() }.add(pk)
        }
        if (perRelay.isEmpty()) return

        val filters =
            perRelay.mapValues { (_, authors) ->
                authors.chunked(AUTHORS_PER_FILTER).map { chunk ->
                    Filter(kinds = listOf(AdvertisedRelayListEvent.KIND), authors = chunk)
                }
            }
        ctx.drain(filters, timeoutMs)
    }

    /**
     * Group [pubkeys] by the relays we should query for their events: each user's
     * kind:10002 write relays (the outbox model); for users with no advertised
     * relay list, their harvested relay [hints] plus the broad discovery set.
     * Authors are chunked per relay to respect relay REQ limits.
     */
    private suspend fun routeByOutbox(
        ctx: Context,
        pubkeys: Set<HexKey>,
        hints: Map<HexKey, Set<NormalizedRelayUrl>>,
        kinds: List<Int>,
    ): Map<NormalizedRelayUrl, List<Filter>> {
        val fallback = contentFallbackRelays(ctx)
        val perRelay = HashMap<NormalizedRelayUrl, MutableSet<HexKey>>()

        for (pk in pubkeys) {
            val write = ctx.relaysOf(pk)?.writeRelaysNorm()?.takeIf { it.isNotEmpty() }
            val relays = write ?: (hints[pk].orEmpty() + fallback)
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
