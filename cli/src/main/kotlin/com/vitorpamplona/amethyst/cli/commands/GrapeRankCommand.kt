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

    // Users whose outboxes we fetch in a single drain. Draining thousands of
    // distinct outbox relays at once saturates connections and times out
    // (empirically ~250 users/drain succeeds, ~17k fails); keep the fan-out small.
    private const val USER_BATCH = 256

    // Concurrent content drains. Bounded so total open connections stay sane.
    private const val DRAIN_CONCURRENCY = 8

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

    // Last-mile sweep: after the outbox crawl gives up on the users whose own
    // relays never answered, we take one more run at them against the WHOLE
    // known-good relay pool — the busiest live relays we learned from everyone
    // else's lists (they include the big aggregators). LAST_MILE_RELAYS caps that
    // pool; LAST_MILE_PASSES bounds how many times we re-sweep as recovered lists
    // reveal a few more reachable users.
    private const val LAST_MILE_RELAYS = 80
    private const val LAST_MILE_PASSES = 2

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

        val params =
            GrapeRankParams(
                attenuation = args.flag("attenuation")?.toDoubleOrNull() ?: GrapeRankParams().attenuation,
                rigor = args.flag("rigor")?.toDoubleOrNull() ?: GrapeRankParams().rigor,
            )

        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val observer = observerArg?.let { ctx.requireUserHex(it) } ?: ctx.identity.pubKeyHex

            val graphKinds = listOf(ContactListEvent.KIND, MuteListEvent.KIND, ReportEvent.KIND)

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

            val hopOf = HashMap<HexKey, Int>()
            if (!offline) {
                val discovered = hashSetOf(observer)
                hopOf[observer] = 0
                // Per-user relay hints harvested from the `p`-tag relay hints in the
                // contact lists we crawl (A's follow of B says where B writes) — a
                // discovery tier below each user's kind:10002 outbox.
                val relayHints = HashMap<HexKey, MutableSet<NormalizedRelayUrl>>()
                // Users we're finished with this run: we fed their latest kind:3, or
                // ran out of retry attempts on an unreachable outbox.
                val done = hashSetOf<HexKey>()
                val attempts = HashMap<HexKey, Int>()
                val relaysContacted = hashSetOf<NormalizedRelayUrl>()
                // Known-good relay pool, learned from the crawl itself: how often each
                // relay appears as someone's write relay, and which relays actually
                // delivered events (so we know they connect and work). The most-common
                // live relays become the `backbone` we retry unreachable users against.
                val writeRelayFreq = HashMap<NormalizedRelayUrl, Int>()
                val liveRelays = hashSetOf<NormalizedRelayUrl>()

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
                        tag.relayUri?.let { relayHints.getOrPut(tag.pubKey) { HashSet() }.add(it) }
                        if (discovered.add(tag.pubKey)) {
                            hopOf[tag.pubKey] = nextHop
                            fresh++
                        }
                    }
                    builder.addFollows(source, follows)
                    contactListsFed++
                    return fresh
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

                    var gotList = 0
                    var newUsers = 0

                    // The known-good backbone this round: the most-used write relays
                    // that have actually delivered events. Retried / outbox-less users
                    // are also queried here — these are relays we know work, learned
                    // from everyone else's lists.
                    val backbone =
                        writeRelayFreq.entries
                            .asSequence()
                            .filter { it.key in liveRelays }
                            .sortedByDescending { it.value }
                            .take(BACKBONE_SIZE)
                            .map { it.key }
                            .toSet()

                    // Resolve kind:10002 outboxes in bulk (indexers aggregate them),
                    // then fetch content in small batches drained a few at a time — one
                    // giant drain over thousands of outbox relays saturates connections
                    // and times out. Routing (store reads) is serial; only the drains
                    // run concurrently, which is safe: inserts serialize on the store
                    // write lock.
                    ensureRelayLists(ctx, pending.toSet(), backbone, timeoutMs, diagnose)
                    for (group in pending.chunked(USER_BATCH).chunked(DRAIN_CONCURRENCY)) {
                        val prepared = group.map { batch -> batch to routeByOutbox(ctx, batch.toSet(), relayHints, backbone, attempts, writeRelayFreq, graphKinds) }
                        val drained =
                            coroutineScope {
                                prepared
                                    .map { (batch, filters) ->
                                        async {
                                            val events = ctx.drain(filters, timeoutMs, diagnose)
                                            Triple(batch, filters.keys, events)
                                        }
                                    }.awaitAll()
                            }
                        for ((batch, relays, events) in drained) {
                            relaysContacted += relays
                            // Any relay that gave us an event is proven live + useful.
                            for ((relay, _) in events) liveRelays.add(relay)
                            for (pk in batch) {
                                val contacts = ctx.contactsOf(pk)
                                if (contacts != null) {
                                    done += pk
                                    gotList++
                                    newUsers += ingest(pk, contacts)
                                } else {
                                    val tries = (attempts[pk] ?: 0) + 1
                                    attempts[pk] = tries
                                    if (tries >= MAX_OUTBOX_ATTEMPTS) done += pk
                                }
                            }
                        }
                    }

                    System.err.println(
                        "[graperank] round $rounds: fetched=${pending.size}, gotList=$gotList, " +
                            "newUsers=$newUsers, discovered=${discovered.size}, done=${done.size}",
                    )
                }

                // Last-mile sweep. The outbox crawl leaves a tail of users whose own
                // relays never answered (dead/misconfigured outboxes). Their contact
                // lists very likely still exist — on the big aggregators and busy
                // relays everyone else writes to. So instead of asking each straggler's
                // broken outbox again, ask the WHOLE known-good pool at once: the
                // busiest live relays learned from the crawl, plus the discovery set.
                val goodPool =
                    (
                        writeRelayFreq.entries
                            .asSequence()
                            .filter { it.key in liveRelays }
                            .sortedByDescending { it.value }
                            .take(LAST_MILE_RELAYS)
                            .map { it.key }
                            .toSet() + relayListDiscoveryRelays(ctx)
                    ).toList()
                if (goodPool.isNotEmpty()) {
                    for (pass in 1..LAST_MILE_PASSES) {
                        val missing = discovered.filter { (hopOf[it] ?: 0) < maxHops && ctx.contactsOf(it) == null }
                        if (missing.isEmpty()) break

                        var recovered = 0
                        var newUsers = 0
                        for (group in missing.chunked(USER_BATCH).chunked(DRAIN_CONCURRENCY)) {
                            val drained =
                                coroutineScope {
                                    group
                                        .map { batch ->
                                            val filters =
                                                goodPool.associateWith {
                                                    batch.chunked(AUTHORS_PER_FILTER).map { chunk ->
                                                        Filter(kinds = graphKinds, authors = chunk)
                                                    }
                                                }
                                            async {
                                                val events = ctx.drain(filters, timeoutMs, diagnose)
                                                batch to events
                                            }
                                        }.awaitAll()
                                }
                            for ((batch, events) in drained) {
                                relaysContacted += goodPool
                                for ((relay, _) in events) liveRelays.add(relay)
                                for (pk in batch) {
                                    val contacts = ctx.contactsOf(pk)
                                    if (contacts != null) {
                                        recovered++
                                        done += pk
                                        newUsers += ingest(pk, contacts)
                                    }
                                }
                            }
                        }
                        System.err.println(
                            "[graperank] last-mile pass $pass: swept=${missing.size}, recovered=$recovered, " +
                                "newUsers=$newUsers, discovered=${discovered.size}, still-missing=${discovered.count { (hopOf[it] ?: 0) < maxHops && ctx.contactsOf(it) == null }}",
                        )
                        if (recovered == 0) break
                    }
                }

                relaysContactedCount = relaysContacted.size
                val perHop =
                    hopOf.values
                        .groupingBy { it }
                        .eachCount()
                        .toSortedMap()
                System.err.println(
                    "[graperank] crawl complete: ${discovered.size} discovered, $contactListsFed contact lists fed, " +
                        "$relaysContactedCount relays contacted, $rounds rounds; " +
                        "by hop: " + perHop.entries.joinToString(" ") { "${it.key}=${it.value}" },
                )
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

            // Live scoring progress: the worklist visits each reachable user once per
            // relaxation; report every SCORE_PROGRESS_STEP visits so a large graph shows
            // movement instead of hanging silently.
            val scoreStart = System.nanoTime()
            val scores =
                GrapeRank(params).compute(graph, observer) { visited, queued ->
                    if (visited % SCORE_PROGRESS_STEP == 0L) {
                        System.err.println("[graperank] scoring: $visited visited, $queued queued")
                    }
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
                    "store_load_ms" to storeLoadMs,
                    "graph_build_ms" to buildMs,
                    "scoring_ms" to scoringMs,
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
        fallbackRelays: Set<NormalizedRelayUrl>,
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
            ctx.drain(filters, timeoutMs, diagnose)
        }

        val discovery = relayListDiscoveryRelays(ctx)
        query(missing, discovery)

        // Tier 2: whoever the aggregators still don't have, ask the relays the
        // rest of the graph actually writes to.
        val stillMissing = missing.filter { ctx.relaysOf(it) == null }
        query(stillMissing, fallbackRelays - discovery)
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
