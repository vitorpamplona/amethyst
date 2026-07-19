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
import com.vitorpamplona.amethyst.cli.commands.RawEventSupport
import com.vitorpamplona.amethyst.commons.defaults.Constants
import com.vitorpamplona.amethyst.commons.defaults.DefaultIndexerRelayList
import com.vitorpamplona.quartz.experimental.graperank.FollowerCrawler
import com.vitorpamplona.quartz.experimental.graperank.GrapeRankCrawler
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip65RelayList.AdvertisedRelayListEvent
import com.vitorpamplona.quartz.nip66RelayMonitor.discovery.RelayDiscoveryEvent
import com.vitorpamplona.quartz.nip66RelayMonitor.reachability.RelayReachabilityStore
import com.vitorpamplona.quartz.nip66RelayMonitor.reachability.TcpProber

/**
 * The network-facing graperank verbs: the outbox-model follow-graph crawl
 * (`graperank crawl`), the reverse follower crawl (`graperank followers`), and
 * the crawler configuration shared with the bare `graperank` combo in
 * [GrapeRankScore].
 */
object GrapeRankCrawl {
    /**
     * Configure the outbox-model crawler from the crawl flags on [args] plus the
     * account's relay policy. Shared by the bare command and `graperank crawl`.
     * Relay policy — where a stranger's kind:10002 is found (index/discovery
     * aggregators + general defaults) and best-effort general relays that might
     * hold content when an outbox is unknown — lives in app code, so the quartz
     * crawler takes it injected.
     */
    internal suspend fun newCrawler(
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
                    timeoutMs = args.timeoutMs(10),
                    parkTimeoutMs = args.longFlag("park-timeout", 40L) * 1000,
                    diagnose = args.bool("diagnose"),
                    insertBatchSize = args.intFlag(FLAG_INSERT_BATCH, INSERT_BATCH_DEFAULT),
                    drainConcurrency = args.intFlag("drain-concurrency", 48),
                    timeoutEvictStrikes = args.intFlag("timeout-evict", 3),
                    // Cheap TCP reachability pre-probe (--no-probe to disable). No Tor
                    // transport here, so .onion relays are skipped on sight.
                    reachabilityProbe = if (args.bool("no-probe")) null else TcpProber::tcpReachable,
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
    internal fun reportRelayFeedback(ctx: Context) {
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
    internal suspend fun flushReachability(
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
    suspend fun crawl(
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
    suspend fun followers(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val observerArg = args.positionalOrNull(0)
        // Strictly validated like every other `--relay` in amy — a malformed
        // entry is a bad_args failure, not a silent drop.
        val explicitRelays = RawEventSupport.relayFlag(args)
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
                explicitRelays.takeIf { it.isNotEmpty() }
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
                            timeoutMs = args.timeoutMs(15),
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
}
