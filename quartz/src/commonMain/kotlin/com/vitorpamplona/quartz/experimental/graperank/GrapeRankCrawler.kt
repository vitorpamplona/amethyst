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
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.fetchAllPages
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.SubscriptionListener
import com.vitorpamplona.quartz.nip01Core.relay.client.single.newSubId
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.concurrent.Volatile
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.AtomicReference
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
class GrapeRankCrawler(
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

    // Store-path attribution the summed [insertNanos] can't give: [insertNanos]
    // counts every concurrent caller's wall INCLUDING time queued on the store's
    // single-writer mutex, so at high fan-out it wildly overstates write cost.
    // Batches that started with no other insert in flight ([insertInFlight] == 1)
    // approximate the UNCONTENDED write rate; reads get their own count + wall so
    // a point-query pathology (thousands of tiny queries) is visible at a glance.
    private val insertInFlight = AtomicLong(0)
    private val insertSoloNanos = AtomicLong(0)
    private val insertSoloEvents = AtomicLong(0)
    private val queryCount = AtomicLong(0)
    private val queryNanos = AtomicLong(0)

    /**
     * Relay policy + crawl bounds. The relay sets come from the caller because the
     * aggregator/bootstrap defaults live outside quartz.
     *
     * @param relayListDiscoveryRelays where to look up a stranger's kind:10002 —
     *   the index/discovery aggregators (purplepag.es, coracle, …) plus general
     *   defaults that carry kind:10002 for most of the network.
     * @param contentFallbackRelays best-effort general relays that *might* hold a
     *   user's kind:3/10000/1984 when their outbox is unknown or unreachable.
     * @param contentAggregatorRelays index/aggregator relays that hold a network-wide
     *   copy of kind:3, mined for stragglers by [recoverStragglersFromAggregators]
     *   after the crawl converges. The outbox model asks "where does this user write?"
     *   — but a large tail of users have no kind:3 on their own advertised outbox (it's
     *   dead, or they never published one there), while a network-wide aggregator
     *   (kindpag.es, …) scraped and holds it. Those aggregators are queried only for
     *   kind:10002 in [ensureRelayLists]; the dedicated recovery pass asks them for the
     *   kind:3 itself — patiently and kind:3-only, since a multi-kind filter makes the
     *   big aggregators time out. Empty disables the pass.
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
     *   concurrent-sub cap is enforced separately by [AdaptiveRelayLimiter]. An
     *   early A/B that made 64 look ~2x slower than 24 was an artifact of the crawl
     *   running on a single-threaded event loop (more coroutines on one starved
     *   thread); re-run on the multithreaded dispatcher, 48 beat 24 twice at
     *   identical completeness (hop-3 cold: 579s→559s and 474s→396s), so 48 is the
     *   validated default.
     * @param timeoutEvictStrikes evict a relay after this many drains that timed out
     *   (connect timeout or park idle-cut) having delivered NOTHING. Unlike
     *   [classifyDrainFailure] — which never marks a timeout dead, since one slow
     *   answer shouldn't drop a relay — this catches the connect-but-silent / dead
     *   endpoints that are otherwise re-tried through every straggler's outbox for the
     *   rest of the crawl. A clean EOSE or any delivered event clears a relay's count,
     *   so only never-productive relays are evicted. `<= 0` disables it.
     */
    class Config(
        val relayListDiscoveryRelays: Set<NormalizedRelayUrl>,
        val contentFallbackRelays: Set<NormalizedRelayUrl>,
        val contentAggregatorRelays: Set<NormalizedRelayUrl> = emptySet(),
        val maxRounds: Int = Int.MAX_VALUE,
        val maxHops: Int = Int.MAX_VALUE,
        val timeoutMs: Long = 10_000,
        val parkTimeoutMs: Long = 40_000,
        val diagnose: Boolean = false,
        val insertBatchSize: Int = 500,
        val drainConcurrency: Int = 48,
        val timeoutEvictStrikes: Int = 3,
        /**
         * Also skip proven-dead relays in the kind:10002 discovery sweep
         * ([ensureRelayLists]). Without it the discovery/backbone set is queried
         * every round regardless of deadRelays, so a refusing indexer (snort,
         * nostr.band…) is re-hammered every round. Benchmarked win — default on.
         */
        val shedDeadDiscovery: Boolean = true,
        /**
         * Rotation passes in the sharded backbone sweep (each is an awaitAll
         * barrier). Benchmarked: 2 clears the backbone bulk at ~40% of the
         * 6-rotation wall cost with no completeness loss (1 clears too little).
         */
        val shardRotations: Int = 2,
        /**
         * Optional cheap reachability pre-probe. Given a relay, returns false if it
         * is definitely unreachable from here — a raw TCP connect (one round trip)
         * that failed fast. A background culler runs it over the cold tail of learned
         * relays and drops the unreachable ones into [deadHosts] BEFORE the expensive
         * WS path pays the full 7s connectTimeout on them. It only ever marks dead
         * and defers to the WS verdict: a host already proven live/dead is skipped.
         * A tight TCP timeout is safe where a tight WS timeout is not — a busy-but-
         * alive relay accepts the SYN instantly (kernel-level) and only stalls at the
         * app layer, so TCP-reachability separates "unreachable" from "slow". Null
         * disables pre-probing.
         */
        val reachabilityProbe: (suspend (NormalizedRelayUrl) -> Boolean)? = null,
        /**
         * Whether this client can reach .onion relays (has a Tor transport). When
         * false, every .onion relay is unreachable and [isDead] skips it on sight —
         * no socket, no wasted connect attempt.
         */
        val torEnabled: Boolean = false,
        /**
         * Relays a prior run (or another monitor) proved unreachable within the
         * reachability cache's TTL — seeded into [deadRelays] before the crawl starts
         * so we don't re-pay their connect timeouts. Per-URL (not per-authority): a
         * TTL'd "skip for now", re-probed once the record ages out, so it never
         * permanently ignores an author's advertised home. See RelayReachabilityStore.
         */
        val knownDeadRelays: Set<NormalizedRelayUrl> = emptySet(),
        /**
         * Relays a prior run (or another monitor) proved REACHABLE within the
         * reachability cache's TTL. Seeded into the crawl-wide warm pool at start, so
         * their DNS + TCP + TLS + WS-upgrade cost is paid ONCE, in one parallel
         * connection storm, before the first round — instead of serially inside each
         * drain that first routes to them. See [preconnectCap].
         */
        val knownLiveRelays: Set<NormalizedRelayUrl> = emptySet(),
        /**
         * Cap on the crawl-wide warm pool — the mass pre-connect. The client tears a
         * relay's socket down ~300ms after its last subscription closes, so without a
         * warm hold every round re-pays the full connect latency per outbox relay.
         * The warm pool keeps a do-nothing subscription (never-matching filter, EOSEs
         * instantly, streams nothing) open to up to this many relays for the whole
         * crawl: busiest proven relays first, then next-round outbox candidates, then
         * [knownLiveRelays]. Each warm relay holds one socket (one file descriptor)
         * and one dormant sub — size against the process FD budget, and keep it above
         * the transport's concurrent-handshake cap only if FDs allow. `<= 0` falls
         * back to warming just the busiest [WARM_POOL_SIZE] relays (the old behavior).
         */
        val preconnectCap: Int = 2500,
    )

    /** What the crawl fetched — the counters the caller reports and the graph is built from. */
    class Stats(
        val rounds: Int,
        val contactListsFed: Int,
        val relaysContacted: Int,
        /** Users bucketed by follow-graph distance from the observer (hop -> count), ascending. */
        val hopHistogram: Map<Int, Int>,
        /**
         * Contact lists successfully recovered, bucketed by the fed user's hop
         * (hop -> count), ascending. Divide by [hopHistogram] at the same hop for
         * per-hop completeness (fraction of discovered users we pulled a kind:3 for).
         */
        val contactsFedByHop: Map<Int, Int>,
        val downloadMs: Long,
        /** Wall time verifying signatures, summed across the concurrent consumers. */
        val verifyMs: Long,
        /** Wall time in the store write path, summed across the concurrent consumers. */
        val insertMs: Long,
        /** Verified events handed to the store (duplicates included — the write path dedups). */
        val eventsStored: Long,
        /**
         * Relays this run actually OBSERVED, for the caller to flush into the
         * reachability cache (kind:30166): [deadRelays] = relays newly proven
         * unreachable this run (a connect-establishment failure we paid), [liveRelays]
         * = relays that served ≥1 event. Seeded known-dead relays (skipped, never
         * dialed) are deliberately EXCLUDED from [deadRelays] — re-writing them would
         * refresh their TTL without a re-probe and blacklist a recovered relay forever;
         * their original record must age out so the next run re-probes them.
         */
        val deadRelays: Set<NormalizedRelayUrl>,
        val liveRelays: Set<NormalizedRelayUrl>,
    )

    /**
     * Crawl from [observer], streaming discovered contact lists into [builder]
     * (follows only — mutes/reports land in the store for the caller to
     * materialize). Pass `null` for a persist-only *sync*: every event still lands
     * in the store, the frontier still expands off each contact list, but no graph
     * is assembled in memory (the caller scores later from the store). Returns [Stats].
     */
    suspend fun crawl(
        observer: HexKey,
        builder: TrustGraphBuilder?,
    ): Stats {
        verifyNanos.store(0)
        insertNanos.store(0)
        eventsStored.store(0)
        insertInFlight.store(0)
        insertSoloNanos.store(0)
        insertSoloEvents.store(0)
        queryCount.store(0)
        queryNanos.store(0)
        // Hop off the caller's dispatcher: a CLI calls this from runBlocking's
        // SINGLE-THREADED event loop, and Phase B runs thousands of concurrent
        // drain-unit coroutines (each with a timeout timer, a channel, REQ JSON
        // encoding, signature verifies and store writes). On one thread those
        // timers fire late and batch walls inflate ~5x — measured 111 users/s
        // with the lone thread pegged at 100% while 3 cores idled. IO (not
        // Default): the store's blocking SQLite calls must not starve the
        // small cores-sized pool.
        return withContext(Dispatchers.IO) { CrawlRun(observer, builder).run() }
    }

    /**
     * Holds all per-crawl mutable state. Graph state (done/hopOf/builder/
     * relaysContacted) is single-writer by construction — Phase A and the Phase-B
     * consumer never run concurrently — so those stay plain collections. The frontier
     * IS [hopOf]'s key set: a user is "discovered" iff it has a hop stamp. State
     * genuinely shared across the producer / consumer / drain-worker coroutines is
     * concurrent: relayHints, attempts, deadRelays. [writeRelayFreq] and [liveRelays]
     * are also concurrent because the background reachability culler reads them (to
     * find candidates and skip already-live authorities) while the crawl writes them.
     */
    private inner class CrawlRun(
        val observer: HexKey,
        val builder: TrustGraphBuilder?,
    ) {
        // hop distance per discovered user; the observer seeds it at 0. Its key set
        // is the discovered frontier — no separate `discovered` set to keep in sync.
        val hopOf = hashMapOf(observer to 0)
        val done = hashSetOf<HexKey>()

        // Users already offered to the sharded backbone sweep. The top-relay set and
        // a user's answer don't change between adjacent rounds, so re-sweeping the
        // same straggler set every round only re-pays the rotation barriers
        // (measured: ~20s of Phase A per straggler round for ~zero yield). New users
        // (frontier growth) still sweep on their first pending round; the outbox,
        // broadcast and aggregator paths keep covering the swept-but-missing rest.
        // Single-writer: only the round loop's [shardedSweep] touches it.
        val shardSwept = hashSetOf<HexKey>()

        // Users we've already run the STATIC-relay outbox-discovery sweep for (the
        // indexer set in [ensureRelayLists]). That relay set never changes, so re-asking
        // it for the same never-had-a-10002 user each round it recirculates is pure waste
        // — the round-8 profile showed this as ~144k slow kind:10002 drains.
        // Single-writer: only the round loop's [ensureRelayLists] touches it.
        val relayListDiscoverySwept = hashSetOf<HexKey>()

        // Relays the WIDE (every-live-relay) recovery pass in [ensureRelayLists] has
        // already asked. Unlike the discovery set the wide net GROWS as the crawl learns
        // relays, so gating that pass on the swept-user set would never re-ask an old
        // straggler for a home relay discovered after its first sweep. Tracking the asked
        // relay set instead lets a late-appearing relay still surface an old straggler's
        // 10002 while never asking the same (user, relay) pair twice.
        // Single-writer: only the round loop's [ensureRelayLists] touches it.
        val wideRelaysSwept = hashSetOf<NormalizedRelayUrl>()
        val relaysContacted = hashSetOf<NormalizedRelayUrl>()
        val writeRelayFreq = ConcurrentMap<NormalizedRelayUrl, Int>()
        val liveRelays = ConcurrentSet<NormalizedRelayUrl>()

        // Per-relay outcome/latency/yield accounting, written from every drain unit
        // (fast + parked) across every round. Dumped at crawl end; the raw signal a
        // future adaptive controller reads to size filters / cap / strangle per relay.
        val telemetry = RelayTelemetry()

        // Per-round Phase-B batch-wall distribution (diagnose only): reset by the
        // round loop, recorded by the drain workers, dumped after each round.
        val batchWalls = BatchWallStats()

        // Phase-B churn the done-counter can't see: users VISITED (every batch member
        // processed by the consumer, found or not) and batches completed. The ticker
        // derives rates from these so a retry round over list-less users reads as
        // "working, zero yield" instead of "stalled".
        val usersVisited = AtomicLong(0)
        val batchesDone = AtomicLong(0)

        // Concurrent: touched by more than one of producer/consumer/drain-workers.
        val relayHints = ConcurrentMap<HexKey, ConcurrentSet<NormalizedRelayUrl>>()
        val attempts = ConcurrentMap<HexKey, Int>()

        // Seeded from the reachability cache (relays proven dead within its TTL) so the
        // WS path never re-pays their connect timeouts; the crawl still adds/removes
        // more as it goes and flushes the union back at the end.
        val deadRelays = ConcurrentSet<NormalizedRelayUrl>().apply { config.knownDeadRelays.forEach { add(it) } }

        // --diagnose [slow-relay] lines emitted per authority, for the suppression cap
        // in logSlow (the telemetry table keeps the complete per-relay counts).
        val slowLogCounts = ConcurrentMap<String, Int>()

        // Unproductive-TIMEOUT strikes, keyed by relay AUTHORITY (host[:port]), not the
        // full URL. [classifyDrainFailure] treats a READ timeout or a park idle-cut — the
        // relay answered the handshake but is slow — as "busy, retry" and never dead,
        // because one slow answer shouldn't evict a relay. But in a crawl the same
        // unresponsive server is
        // routed through every straggler's outbox, every round, each visit burning the
        // full timeout + park window for zero data. Keying by authority is what defeats
        // the outbox-model's per-user path fragmentation: a paid/dead host like
        // `filter.nostr.wine` is advertised as hundreds of distinct per-user URLs
        // (`filter.nostr.wine/npubA?broadcast=true`, …), so a per-URL counter never
        // reaches the threshold on any single one — but they are one server, and it
        // times out on all of them. We strike the authority and, past
        // [Config.timeoutEvictStrikes], mark it dead in [deadHosts] so every URL under
        // it is skipped. A clean EOSE or any delivered event records the authority in
        // [producedHosts] (see [clearTimeoutStrikes]), and [isDead] treats an authority
        // as dead ONLY while it is in [deadHosts] AND NOT in [producedHosts] — so a host
        // that ever produces is never evicted, even if concurrent strikes from the
        // 24-worker fan-out raced it into [deadHosts] at the same instant it EOSE'd.
        // Only the connect-but-silent / dead-endpoint class stays evicted.
        val deadTimeoutStrikes = ConcurrentMap<String, Int>()
        val deadHosts = ConcurrentSet<String>()

        // Authorities that ever produced (EOSE or a delivered event) this run. Membership
        // here overrides [deadHosts] in [isDead], making the "ever produces ⇒ never
        // evicted" invariant race-free: the strike path can lose to a clear and still add
        // to [deadHosts], but the gate consults this set and lets the proven host run.
        val producedHosts = ConcurrentSet<String>()

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

        // ── Saturation / latency instrumentation (diagnose only) ─────────────────────
        // Are we resource-bound or waiting-on-relays? These answer it without a profiler.
        // activeWorkers: Phase-B drain workers busy right now (vs drainConcurrency) — if
        //   rarely full, adding workers won't help; the producer/relays are the limit.
        // throttled: drains that got a relay rate-limit (429/too-many-*) — the EXTERNAL
        //   ceiling; if it climbs when we push harder, more concurrency backfires.
        // The latency sums split a drain's wall time into time-to-first-event vs the
        //   EOSE-wait AFTER the relay's last event (pure waiting on a done-but-slow relay)
        //   — a large eose-wait fraction is the case for a shorter/adaptive fast window.
        // burnedFastWindow: drains that blew timeoutMs and had to park.
        val activeWorkers = AtomicLong(0)
        val throttled = AtomicLong(0)
        val firstEventSumMs = AtomicLong(0)
        val eoseWaitSumMs = AtomicLong(0)
        val drainWallSumMs = AtomicLong(0)
        val drainSamples = AtomicLong(0)
        val burnedFastWindow = AtomicLong(0)

        // Background scope owning the parked subscriptions (and Tier-2 relay-list
        // sweeps). Set in [run]; cancelled once the crawl converges.
        var bgScope: CoroutineScope? = null

        var rounds = 0
        var contactListsFed = 0

        // Contact lists successfully recovered, bucketed by the fed user's hop
        // distance from the observer. Paired with [hopOf]'s histogram (users
        // DISCOVERED per hop), this gives per-hop completeness: how many of the
        // users found at each hop we actually pulled a kind:3 for. Single-writer:
        // only [ingest] touches it, and ingest runs only on the round loop /
        // Phase-B consumer, never concurrently.
        val contactsFedByHop = HashMap<Int, Int>()

        // Live-progress context the heartbeat ticker reads (plain vars set only by the
        // single round-loop coroutine; the ticker's reads are benign racy int/bool
        // reads — a stale value just shows in one progress line). progTarget/progBase
        // frame the CURRENT round so the ticker can show a real "X of Y (Z%)" for it.
        var progRound = 0
        var progTarget = 0
        var progBaseDone = 0
        var progConverging = false

        /**
         * A relay [classifyDrainFailure] flagged [DrainFailure.DEAD] won't serve us
         * this run (bad domain, TLS misconfig, dead/gated HTTP code, refused/reset,
         * connect that never opened), so it is dropped on the first strike. Read
         * timeouts and alive 429 rate-limits never reach here — the drain treats them
         * as busy-retry and does not report them dead at all.
         */
        fun recordDead(failed: Map<NormalizedRelayUrl, DrainFailure>) {
            for ((r, _) in failed) deadRelays.add(r)
        }

        /**
         * A relay's drain unit timed out (connect timeout or park idle-cut) having
         * delivered nothing. Count the strike against its AUTHORITY and, once it
         * reaches [Config.timeoutEvictStrikes], give up on the whole host — a
         * connect-but-silent or dead endpoint that would otherwise be re-tried through
         * every straggler's outbox for the rest of the crawl. Disabled when the
         * threshold is <= 0.
         */
        fun strikeUnproductiveTimeout(relay: NormalizedRelayUrl) {
            val limit = config.timeoutEvictStrikes
            if (limit <= 0) return
            val authority = authorityOf(relay.url)
            if (authority in producedHosts) return // proven productive — never evict on timeouts
            if (authority in deadHosts) return
            if (deadTimeoutStrikes.merge(authority, 1) { a, b -> a + b } >= limit) deadHosts.add(authority)
        }

        /**
         * A relay just proved its host can produce — a clean EOSE or an actual event —
         * so record the authority in [producedHosts] (permanently protecting it from
         * timeout eviction for the rest of the run) and wipe any timeout strikes it
         * accrued. Prevents an occasionally-slow but useful host (a busy backbone hub, or
         * a multi-path relay where some paths are slow) from accumulating its way to
         * eviction across a long crawl — and, via the [isDead] gate, un-evicts one that a
         * concurrent strike already pushed into [deadHosts] at the same instant.
         */
        fun clearTimeoutStrikes(relay: NormalizedRelayUrl) {
            val authority = authorityOf(relay.url)
            producedHosts.add(authority)
            // ConcurrentMap exposes no remove; reset the count to 0 atomically (0 is
            // below any positive eviction threshold, so it reads as "unstruck"). Guard
            // on a prior entry so we don't insert a 0 for every host that ever answers.
            if (deadTimeoutStrikes[authority] != null) deadTimeoutStrikes.merge(authority, 0) { _, _ -> 0 }
        }

        /**
         * A relay is out of the routing pool if it hard/transient-failed (per-URL
         * [deadRelays]) or its whole authority was timeout-evicted ([deadHosts]) and has
         * not since proven productive ([producedHosts] wins, so a slow-but-live host is
         * never permanently evicted); a .onion relay is dead on sight unless we have a
         * Tor transport, since every connect to it would only hang and fail.
         */
        fun isDead(relay: NormalizedRelayUrl): Boolean {
            val authority = authorityOf(relay.url)
            return relay in deadRelays ||
                (authority in deadHosts && authority !in producedHosts) ||
                (!config.torEnabled && RelayUrlNormalizer.isOnion(relay.url))
        }

        /** The busiest live relays we've learned, excluding the dead ones. */
        fun topLiveRelays(cap: Int): List<NormalizedRelayUrl> =
            writeRelayFreq
                .snapshot()
                .entries
                .asSequence()
                .filter { it.key in liveRelays && !isDead(it.key) }
                .sortedByDescending { it.value }
                .take(cap)
                .map { it.key }
                .toList()

        /**
         * Refresh the crawl-wide warm pool — the mass pre-connect. Re-subscribing the
         * same [WARM_SUB_ID] with a new relay set just updates the desired-relay set:
         * relays already warm stay connected, new ones start their handshake NOW (in
         * parallel, in the background), so by the time a drain routes to them the
         * socket is already open. Priority under [Config.preconnectCap]: busiest
         * proven-live relays, then advertised-but-not-yet-contacted outboxes (exactly
         * the relays the next rounds will dial), then the prior run's known-live
         * universe. Dead relays are always excluded so the pool never redials them.
         */
        fun refreshWarmPool() {
            val massCap = config.preconnectCap
            val cap = if (massCap > 0) massCap else WARM_POOL_SIZE
            val warm = LinkedHashSet<NormalizedRelayUrl>(cap * 2)
            warm.addAll(topLiveRelays(cap))
            if (massCap > 0) {
                if (warm.size < cap) {
                    // Advertised write relays not yet proven live — next drains' targets.
                    val advertised = writeRelayFreq.snapshot().entries.sortedByDescending { it.value }
                    for ((relay, _) in advertised) {
                        if (warm.size >= cap) break
                        if (!isDead(relay)) warm.add(relay)
                    }
                }
                if (warm.size < cap) {
                    for (relay in config.knownLiveRelays) {
                        if (warm.size >= cap) break
                        if (!isDead(relay)) warm.add(relay)
                    }
                }
            }
            if (warm.isEmpty()) return
            client.subscribe(WARM_SUB_ID, warm.associateWith { WARM_FILTERS }, null)
        }

        /**
         * Background reachability culler. Cheaply TCP-probes the relays we've learned —
         * COLD TAIL FIRST — and drops the unreachable ones into [deadHosts] so the WS
         * path never pays the 7s connectTimeout on a dead host. It only ever marks dead
         * and probes each authority once. Any host the WS path already resolved is
         * skipped: dead ones via [isDead], and hosts already proven LIVE ([liveRelays])
         * are filtered out up front so we never waste a probe — or a needless TCP hit —
         * on a working relay we depend on. Combined with the cold-tail ordering, the
         * probe stays off the hot relays the crawl is actively dialing, and the WS
         * verdict always wins ("if the websocket gets there first, let it run"). Runs on
         * [bgScope] until the crawl cancels it.
         */
        private suspend fun cullUnreachable(probe: suspend (NormalizedRelayUrl) -> Boolean) {
            val probed = HashSet<String>() // authorities; only ever touched by this coroutine's loop
            val gate = Semaphore(PROBE_CONCURRENCY)
            while (currentCoroutineContext().isActive) {
                // Never probe a host the WS path already proved live — wasted work and
                // a needless TCP hit on the hot relays we depend on.
                val liveAuthorities = liveRelays.snapshot().mapTo(HashSet()) { authorityOf(it.url) }
                // Least-written relays are the niche/dead long tail the WS path reaches
                // last — probing them first buys the most head start with the least
                // contention against the busy relays already being connected.
                val batch =
                    writeRelayFreq
                        .snapshot()
                        .entries
                        .asSequence()
                        .filter {
                            val authority = authorityOf(it.key.url)
                            authority !in probed && authority !in liveAuthorities && !isDead(it.key)
                        }.sortedBy { it.value }
                        .map { it.key }
                        .toList()
                if (batch.isEmpty()) {
                    delay(PROBE_IDLE_MS)
                    continue
                }
                coroutineScope {
                    for (relay in batch) {
                        val authority = authorityOf(relay.url)
                        if (!probed.add(authority)) continue
                        gate.acquire()
                        launch {
                            try {
                                // Re-check: the WS path may have resolved it while queued.
                                if (!isDead(relay) && !probe(relay)) deadHosts.add(authority)
                            } finally {
                                gate.release()
                            }
                        }
                    }
                }
            }
        }

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
            builder?.addFollows(source, follows)
            contactListsFed++
            val sourceHop = hopOf[source] ?: 0
            contactsFedByHop[sourceHop] = (contactsFedByHop[sourceHop] ?: 0) + 1
            return fresh
        }

        /**
         * Feed into the graph the contact lists a drain just returned (deduped by
         * author; the store's canonical latest wins), marking fed authors done.
         * Only the authors we actually received are touched — no scan over the
         * whole still-missing set. Returns the count newly fed.
         */
        suspend fun harvest(events: List<Pair<NormalizedRelayUrl, Event>>): Int {
            val authors = LinkedHashSet<HexKey>()
            for ((_, ev) in events) {
                if (ev is ContactListEvent && ev.pubKey !in done) authors.add(ev.pubKey)
            }
            // The store's canonical latest for each delivered author wins over the
            // delivered copy itself — same rule as before, now one query per chunk.
            return harvestFromStore(authors)
        }

        /**
         * Mark done any still-pending user whose kind:3 is already in the store — a
         * previous round's [ensureRelayLists] co-fetch, a late parked delivery, or a
         * prior run's data — folding it into the graph so Phase B never spends an outbox
         * drain re-pulling a contact list we already hold. Single-writer: called only
         * from the round loop at Phase-A time, before the drain workers start.
         *
         * BATCHED: one chunked author query per [AUTHORS_PER_FILTER] users, folding as
         * each chunk streams so peak memory is one chunk's lists. The obvious
         * per-user `contactsOf` point query costs ~8ms each on a multi-GB store under
         * concurrent writers — 100k pending users made that serial scan dominate a
         * round's Phase A wall (measured minutes), so batching here is structural.
         * Returns the count newly fed.
         */
        suspend fun harvestFromStore(authors: Collection<HexKey>): Int {
            var got = 0
            val pending = authors.filterTo(ArrayList()) { it !in done }
            for (chunk in pending.chunked(AUTHORS_PER_FILTER)) {
                for ((pk, contacts) in latestContactsFor(chunk)) {
                    if (pk in done) continue
                    done += pk
                    ingest(pk, contacts)
                    got++
                }
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
         *
         * Precondition: the caller ran [harvestFromStore] over [authors] first, so
         * `!in done` alone means "no kind:3 in the store" — no per-author store
         * point-queries here (they cost ~8ms each at crawl scale).
         */
        suspend fun shardedSweep(authors: Collection<HexKey>): Int {
            val top = topLiveRelays(SHARD_RELAYS)
            if (top.isEmpty()) return 0
            val n = top.size
            var missing = authors.filter { it !in done && it !in shardSwept }
            // Only remember the sweep once the backbone is mature (full shard width):
            // users swept against round 2's two-relay proto-backbone deserve a re-ask
            // when the real top-10 exists by round 3.
            if (n >= SHARD_RELAYS) shardSwept.addAll(missing)
            var got = 0
            var rotation = 0
            while (missing.size > SHARD_BROADCAST_THRESHOLD && rotation < config.shardRotations) {
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
            suspend fun query(
                authors: List<HexKey>,
                relays: Set<NormalizedRelayUrl>,
                kinds: List<Int>,
                background: Boolean = false,
            ) {
                if (relays.isEmpty() || authors.isEmpty()) return
                val filters =
                    relays.associateWith {
                        authors.chunked(AUTHORS_PER_FILTER).map { chunk ->
                            Filter(kinds = kinds, authors = chunk)
                        }
                    }
                drainGated(filters, null, background = background)
            }

            val discovery =
                if (config.shedDeadDiscovery) {
                    config.relayListDiscoveryRelays.filterTo(HashSet()) { it !in deadRelays }
                } else {
                    config.relayListDiscoveryRelays
                }

            // First-time DISCOVERY sweep on the static indexer set: a user only needs it
            // once (re-asking a static set can't find a 10002 we already missed). Co-fetch
            // the contact list in the same REQ — an indexer holding a user's 10002 often
            // holds their kind:3, a cheap byproduct of a round-trip we already pay.
            val freshlyMissing = pubkeys.filter { relaysOf(it) == null && it !in relayListDiscoverySwept }
            val freshlyMissingSet = freshlyMissing.toHashSet()
            relayListDiscoverySwept.addAll(freshlyMissing)
            query(freshlyMissing, discovery, listOf(AdvertisedRelayListEvent.KIND, ContactListEvent.KIND))

            // WIDE recovery sweep for kind:10002 ONLY (co-fetching kind:3 across thousands
            // of relays inflates this fire-and-forget sweep, which the finishing drain then
            // waits on — measured +300s at hop-3). The wide net grows every round, so it is
            // gated on the asked-RELAY set, not the swept-user set:
            //  - a user first swept this round is asked the whole current wide net;
            //  - a user swept earlier and still missing is asked ONLY the relays that
            //    appeared since — so its home relay, discovered late, still surfaces.
            // No (user, relay) pair is asked twice; every straggler eventually sees every
            // live relay. The added olderStillMissing×newWide work self-limits: newWide
            // shrinks toward zero as the relay universe is exhausted.
            val wide = allLiveRelays - discovery
            val newWide = wide - wideRelaysSwept
            wideRelaysSwept.addAll(wide)

            val freshStillMissing = freshlyMissing.filter { relaysOf(it) == null }
            val olderStillMissing = pubkeys.filter { it !in freshlyMissingSet && relaysOf(it) == null }
            val hasWork =
                (freshStillMissing.isNotEmpty() && wide.isNotEmpty()) ||
                    (olderStillMissing.isNotEmpty() && newWide.isNotEmpty())
            if (hasWork) {
                bgScope.launch {
                    // background=true: these sweeps' parked units must never gate
                    // round convergence — they are cancelled at crawl end instead,
                    // and incremental persist keeps whatever they delivered.
                    query(freshStillMissing, wide, listOf(AdvertisedRelayListEvent.KIND), background = true)
                    query(olderStillMissing, newWide, listOf(AdvertisedRelayListEvent.KIND), background = true)
                }
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
            val stageMark = TimeSource.Monotonic.markNow()
            val idsByAuthor = HashMap<HexKey, MutableList<HexKey>>()
            for (ev in queryTimed(Filter(kinds = listOf(ReportEvent.KIND)))) {
                if (ev is ReportEvent) idsByAuthor.getOrPut(ev.pubKey) { ArrayList() }.add(ev.id)
            }
            if (idsByAuthor.isEmpty()) return

            // Route each reporter to their own write relays (fallback: backbone).
            val perRelayAuthors = HashMap<NormalizedRelayUrl, MutableSet<HexKey>>()
            for (author in idsByAuthor.keys) {
                val write = relaysOf(author)?.writeRelaysNorm()?.takeIf { it.isNotEmpty() } ?: backbone
                for (relay in write) if (!isDead(relay)) perRelayAuthors.getOrPut(relay) { HashSet() }.add(author)
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
            log(
                "[graperank] report deletions: ${idsByAuthor.size} reporters via ${perRelayAuthors.size} relays " +
                    "in ${stageMark.elapsedNow().inWholeMilliseconds}ms",
            )
        }

        /**
         * Group [pubkeys] by the relays we should query for their events:
         *  - first try: the user's own kind:10002 write relays (the outbox model);
         *  - a retry (`attempts[pk] > 0`, its outbox already failed): outbox +
         *    [backbone] — the known-good relays other people write to;
         *  - no outbox at all: harvested hints + backbone + the general fallback.
         *
         * The content aggregators are deliberately NOT mixed in here: this path's
         * multi-kind [FETCH_KINDS] query loses their kind:3 to their per-REQ result
         * cap (a big indexer fills the response with the abundant kind:10002 and
         * returns no kind:3), so recovering from them is done separately — kind:3-only,
         * once and patiently — in [recoverStragglersFromAggregators].
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
                write?.forEach { writeRelayFreq.merge(it, 1) { a, b -> a + b } }
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
                    if (isDead(relay)) continue
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
         * Final patient pass for the stragglers the outbox model couldn't resolve.
         * A large tail of reachable users have no kind:3 on their own advertised
         * outbox — it's dead, or they never published one there — while a
         * network-wide aggregator ([Config.contentAggregatorRelays], e.g.
         * kindpag.es) scraped and holds it. Mixing those aggregators into the
         * competitive Phase-B fan-out doesn't work: there they'd be asked for the
         * multi-kind [FETCH_KINDS] filter (which times them out) and would race
         * thousands of outbox sockets, getting cut before a big aggregator finishes.
         * So once the frontier is drained we ask the aggregators for the remaining
         * stragglers' kind:3 ALONE: a handful of relays drained kind:3-only with the
         * patient park window, not competing with the fan-out. Recovered contact
         * lists are folded into the graph and persisted for a later `score`.
         */
        private suspend fun recoverStragglersFromAggregators() {
            // Query EVERY configured aggregator, even ones the main crawl evicted.
            // During the competitive crawl an indexer like user.kindpag.es is only ever
            // asked for kind:10002 in bulk and kind:[3,10000,1984,10002] one author at a
            // time; the latter parks and times out (60–80s each), striking the host until
            // it's timeout-evicted (isDead). It is never asked for a clean bulk kind:3 —
            // the one thing it actually serves fast (≈19 lists per 300 authors in a few
            // seconds). This deliberate patient pass IS that clean query, so eviction from
            // the fan-out must not disqualify it here. [drainGated] subscribes to whatever
            // filter map we hand it (it does not re-check isDead), and a genuinely dead
            // endpoint just costs one shared park window since the units run concurrently.
            val aggregators = config.contentAggregatorRelays.toHashSet()
            if (aggregators.isEmpty()) return
            // Wipe any timeout strikes the fan-out accrued so a partially-struck host
            // starts this pass clean and a fast EOSE here keeps it healthy.
            for (agg in aggregators) clearTimeoutStrikes(agg)
            // Stragglers = crawled users we still have no kind:3 for. Most are already
            // in `done` (their outbox attempts were exhausted), which is exactly why
            // [harvest]/[ingestLate] can't be reused — they skip `done` users — so we
            // fold these directly. Batched: the candidate set is EVERY discovered user
            // within the hop budget (hundreds of thousands), so a per-user store point
            // query here would take tens of minutes on its own.
            val candidates = hopOf.keys.filterTo(ArrayList()) { (hopOf[it] ?: 0) < config.maxHops }
            val stragglers = HashSet<HexKey>(candidates.size)
            for (chunk in candidates.chunked(AUTHORS_PER_FILTER)) {
                val have = latestContactsFor(chunk).keys
                for (pk in chunk) if (pk !in have) stragglers.add(pk)
            }
            if (stragglers.isEmpty()) return
            val before = contactListsFed
            val stageMark = TimeSource.Monotonic.markNow()
            log("[graperank] aggregator recovery: ${stragglers.size} stragglers via ${aggregators.size} aggregators")

            // Build the query against the full straggler set BEFORE any folding (the
            // filter lists are materialized here, so later mutation of `stragglers` is
            // safe). Ask ONLY for kind:3 — the contact list we're missing. A multi-kind
            // filter is useless against the big indexers: user.kindpag.es caps its
            // response at ~100 events per REQ (it ignores our limit), so a
            // kinds=[3,10000,1984,10002] query comes back 100× kind:10002 and 0×
            // kind:3 — the abundant relay lists crowd the contact lists out entirely.
            // Asked for kind:3 alone it returns them in a few seconds. Their kind:10002
            // is already fetched in bulk by [ensureRelayLists]; mutes/reports still come
            // from the outbox model. The aggregator's job here is only the lists.
            val filters =
                aggregators.associateWith {
                    stragglers.chunked(AUTHORS_PER_FILTER).map { chunk -> Filter(kinds = listOf(ContactListEvent.KIND), authors = chunk) }
                }

            // Fold one delivered contact list per straggler, exactly once.
            suspend fun foldAgg(events: List<Pair<NormalizedRelayUrl, Event>>) {
                for ((relay, ev) in events) {
                    liveRelays.add(relay)
                    if (ev !is ContactListEvent) continue
                    val pk = ev.pubKey
                    if (pk !in stragglers) continue
                    val contacts = contactsOf(pk) ?: continue
                    stragglers.remove(pk)
                    done += pk
                    ingest(pk, contacts)
                }
            }

            relaysContacted += aggregators
            // Fast deliveries fold immediately; a slow aggregator parks and its late
            // kind:3 arrives on [lateHarvest]. Fold that trickle until the channel
            // stays QUIET for one full park window: our own parked units' idle
            // discipline guarantees anything still coming lands within it. This
            // deliberately does NOT wait for parkedInFlight to reach zero — that
            // inherited the lifetime of every old round-park still draining in the
            // background (measured 222s of pure waiting at hop-3) while this pass's
            // own relays were long done.
            foldAgg(drainGated(filters, null))
            while (true) {
                val late = withTimeoutOrNull(config.parkTimeoutMs) { lateHarvest.receive() } ?: break
                foldAgg(listOf(late))
            }
            log(
                "[graperank] aggregator recovery: +${contactListsFed - before} contact lists " +
                    "in ${stageMark.elapsedNow().inWholeMilliseconds}ms",
            )
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
                // Solo = no other insert was in flight when this one started, so its
                // wall is (approximately) pure write, not writer-mutex queueing.
                val solo = insertInFlight.addAndFetch(1) == 1L
                val mark = TimeSource.Monotonic.markNow()
                try {
                    store.batchInsert(buffer)
                    val ns = mark.elapsedNow().inWholeNanoseconds
                    insertNanos.addAndFetch(ns)
                    if (solo) {
                        insertSoloNanos.addAndFetch(ns)
                        insertSoloEvents.addAndFetch(buffer.size.toLong())
                    }
                } finally {
                    insertInFlight.addAndFetch(-1)
                }
                eventsStored.addAndFetch(buffer.size.toLong())
                buffer.clear()
            }

            for ((relay, event) in events) {
                if (event.id in seenIds) continue
                val vMark = TimeSource.Monotonic.markNow()
                val ok = event.verify()
                verifyNanos.addAndFetch(vMark.elapsedNow().inWholeNanoseconds)
                if (!ok) {
                    Log.w("GrapeRankCrawler") { "dropped event ${event.id.take(8)} kind=${event.kind} — bad signature" }
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
         * Heartbeat so a long round never goes silent: every [PROGRESS_INTERVAL_MS]
         * emit a one-liner with the CURRENT round's completion (a real X/Y % — the
         * round's pending set is a known target), a rolling fetch rate + rough ETA for
         * it, and live counts (events stored, slow relays parked, live/dead relays).
         * Runs for the whole crawl on the background scope; cancelled when it ends.
         */
        private suspend fun progressTicker() {
            var lastFed = 0
            var lastVisited = 0L
            var lastBatches = 0L
            var lastMark = TimeSource.Monotonic.markNow()
            while (true) {
                delay(PROGRESS_INTERVAL_MS)
                val nowMark = TimeSource.Monotonic.markNow()
                val dtMs = (nowMark - lastMark).inWholeMilliseconds.coerceAtLeast(1)
                lastMark = nowMark
                val fed = contactListsFed
                val rate = (fed - lastFed) * 1000L / dtMs // lists/sec over this interval
                lastFed = fed
                // Churn rates the done-counter can't see: a retry round over list-less
                // users shows 0 lists/s while visiting thousands of users per second.
                val visited = usersVisited.load()
                val visitedRate = (visited - lastVisited) * 1000L / dtMs
                lastVisited = visited
                val batches = batchesDone.load()
                val batchRate10 = (batches - lastBatches) * 10_000L / dtMs // batches/s ×10 (one decimal)
                lastBatches = batches
                val events = eventsStored.load()
                val parked = parkedInFlight.load()
                when {
                    progConverging ->
                        log(
                            "[graperank] finishing · ${human(fed.toLong())} lists · ${human(events)} events" +
                                (if (parked > 0) " · $parked slow relay(s) still delivering" else " · draining"),
                        )
                    progTarget > 0 -> {
                        val roundDone = (done.size - progBaseDone).coerceAtLeast(0)
                        val pct = (100L * roundDone / progTarget).coerceIn(0, 100)
                        val remaining = (progTarget - roundDone).coerceAtLeast(0)
                        val eta = if (rate > 0) etaFmt(remaining / rate) else "…"
                        // Saturation tail: workers busy / cap, and rate-limit hits so far — is
                        // the pool full (raise concurrency) or starved (producer/relays bound)?
                        val sat =
                            if (config.diagnose) {
                                " · ${activeWorkers.load()}/${config.drainConcurrency}w · ${throttled.load()} rl" +
                                    " · ${visitedRate}v/s ${batchRate10 / 10}.${batchRate10 % 10}b/s"
                            } else {
                                ""
                            }
                        log(
                            "[graperank] round $progRound · ${human(roundDone.toLong())}/${human(progTarget.toLong())} ($pct%)" +
                                " · $rate/s · ~$eta · ${human(events)} ev · $parked slow · ${deadRelays.size()} dead$sat",
                        )
                    }
                }
            }
        }

        /**
         * A drain unit's page came back at the [FULL_PAGE_THRESHOLD] — it may have been
         * truncated by the relay's per-REQ cap. Continue the SAME query in the
         * background with `until` cursors ([fetchAllPages], starting at the page's
         * oldest event, inclusive) to drain whatever the cap hid, streaming the extra
         * events to [lateHarvest] just like a parked slow relay. Tracked by
         * [parkedInFlight] so the round waits for it — unless [background], in which
         * case (like every background unit) it must never gate convergence; gated by
         * [limiter] and dropped if we have no [bgScope]. The boundary second is
         * re-fetched and its already-seen events are dropped by [persist]'s crawl-wide
         * dedup, so nothing double-counts. A no-op unless the page hit
         * [FULL_PAGE_THRESHOLD], so only dense units pay for it.
         */
        private fun paginateIfCapped(
            relay: NormalizedRelayUrl,
            groupFilters: List<Filter>,
            pageSize: Int,
            pageOldest: Long,
            background: Boolean,
        ) {
            if (pageSize < FULL_PAGE_THRESHOLD) return
            val scope = bgScope ?: return
            val contFilters = groupFilters.map { it.copy(until = pageOldest) }
            if (!background) parkedInFlight.addAndFetch(1)
            scope.launch {
                try {
                    val more = ArrayList<Pair<NormalizedRelayUrl, Event>>()
                    limiter.withPermit(relay) {
                        client.fetchAllPages(relay, contFilters, config.parkTimeoutMs) { ev -> more.add(relay to ev) }
                    }
                    for (pair in persist(more)) lateHarvest.trySend(pair)
                } finally {
                    if (!background) parkedInFlight.addAndFetch(-1)
                }
            }
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
            /**
             * A fire-and-forget completeness sweep (Tier-2 relay-list recovery), not
             * work a round resolves on. Its parked units do NOT count toward
             * [parkedInFlight] — a hop-3 crawl measured ~500s (of 594s total!) just
             * waiting out background parks' idle windows at convergence — and are
             * simply cancelled at crawl end; incremental persist (see the park loop)
             * means everything they delivered up to that instant is already stored.
             */
            background: Boolean = false,
            /**
             * Diagnose hook: updated with the unit that took longest to resolve its
             * FAST outcome (terminal, park handoff, or bare timeout), so a slow batch
             * can name the relay that held its awaitAll open.
             */
            slowestOut: AtomicReference<SlowestUnit?>? = null,
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

            // Per-relay failure classification (HARD wins over TRANSIENT), and which
            // relays did NOT cleanly EOSE (timed out, parked, closed, or couldn't
            // connect) — a relay absent from that set answered definitively, so an
            // author it didn't return is one it lacks.
            val failures = ConcurrentMap<NormalizedRelayUrl, DrainFailure>()
            val notAnswered = ConcurrentSet<NormalizedRelayUrl>()

            fun classify(
                reason: String,
                relay: NormalizedRelayUrl,
                into: ConcurrentMap<NormalizedRelayUrl, DrainFailure>,
            ) {
                classifyDrainFailure(reason)?.let { kind -> into[relay] = kind }
            }

            // A relay asking us to slow down — the external concurrency ceiling.
            fun isRateLimit(reason: String): Boolean {
                val m = reason.lowercase()
                return "429" in m || "too many" in m || "rate" in m || "throttl" in m
            }

            fun logSlow(
                relay: NormalizedRelayUrl,
                reason: String,
                elapsedMs: Long,
                groupFilters: List<Filter>,
            ) {
                if (!config.diagnose) return
                // The tail re-visits the same dead authorities hundreds of times; after
                // a few examples per host these lines are pure log bloat (a hop-8 log
                // was ~1MB of them). The per-relay telemetry table keeps the full
                // counts; here we keep the first few occurrences as samples.
                val n = slowLogCounts.merge(authorityOf(relay.url), 1) { a, b -> a + b }
                if (n > SLOW_LOG_MAX_PER_AUTHORITY) return
                val suffix = if (n == SLOW_LOG_MAX_PER_AUTHORITY) " (further lines for this host suppressed)" else ""
                val authors = groupFilters.flatMap { it.authors.orEmpty() }
                val kinds = groupFilters.flatMap { it.kinds.orEmpty() }.distinct()
                log(
                    "[slow-relay] ${relay.url} $reason in ${elapsedMs}ms | kinds=$kinds authors=${authors.size}: " +
                        authors.take(3).joinToString(",") + (if (authors.size > 3) ",…" else "") + suffix,
                )
            }

            val fast =
                coroutineScope {
                    units
                        .map { (subRelay, groupFilters) ->
                            async {
                                // Queue time for this relay's limiter slot (permit + rate
                                // gate) — spent BEFORE any drain timer starts, so it is
                                // invisible to every latency metric unless ledgered here.
                                val permitMark = TimeSource.Monotonic.markNow()
                                limiter.withPermit(subRelay) {
                                    telemetry.recordPermitWait(subRelay, permitMark.elapsedNow().inWholeMilliseconds)
                                    val subId = newSubId()
                                    val done = CompletableDeferred<String>()
                                    val unitEvents = Channel<Pair<NormalizedRelayUrl, Event>>(Channel.UNLIMITED)
                                    // Liveness signal for the parked idle timeout: every event pings
                                    // this (conflated, so bursts collapse to one) and resets the park
                                    // window, so a relay actively streaming is never cut mid-flight.
                                    val activity = Channel<Unit>(Channel.CONFLATED)
                                    // Latency breakdown: elapsed-since-[mark] of the first and last
                                    // event, so the EOSE-wait AFTER the relay's last event (pure
                                    // waiting on a done-but-slow relay) is separable from fetch time.
                                    val mark = TimeSource.Monotonic.markNow()
                                    val firstEvt = AtomicLong(-1)
                                    val lastEvt = AtomicLong(-1)
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
                                                val e = mark.elapsedNow().inWholeMilliseconds
                                                firstEvt.compareAndSet(-1, e)
                                                lastEvt.store(e)
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
                                    val reason = withTimeoutOrNull(config.timeoutMs) { done.await() }
                                    if (reason != null) {
                                        // Terminal within the fast window — resolve this round.
                                        val elapsedMs = mark.elapsedNow().inWholeMilliseconds
                                        bumpSlowest(slowestOut, subRelay, elapsedMs, reason)
                                        if (reason != "eose") notAnswered.add(subRelay)
                                        classify(reason, subRelay, failures)
                                        if (isRateLimit(reason)) throttled.addAndFetch(1)
                                        // Split the wall time: fetch (to first event) vs EOSE-wait
                                        // (after the last event) — only for drains that got events.
                                        val firstE = firstEvt.load()
                                        if (firstE >= 0) {
                                            firstEventSumMs.addAndFetch(firstE)
                                            eoseWaitSumMs.addAndFetch((elapsedMs - lastEvt.load()).coerceAtLeast(0))
                                            drainWallSumMs.addAndFetch(elapsedMs)
                                            drainSamples.addAndFetch(1)
                                        }
                                        if (elapsedMs > SLOW_DRAIN_LOG_MS) logSlow(subRelay, reason, elapsedMs, groupFilters)
                                        unitEvents.close()
                                        client.unsubscribe(subId)
                                        val drained = buildList { for (e in unitEvents) add(e) }
                                        telemetry.record(subRelay, RelayTelemetry.outcomeOf(reason, parked = false), elapsedMs, authorsIn(groupFilters), drained.size)
                                        val persisted = persist(drained)
                                        // A full page from a clean EOSE may be the relay's cap, not the
                                        // whole answer — background-paginate the remainder into lateHarvest.
                                        if (reason == "eose" && drained.isNotEmpty()) {
                                            paginateIfCapped(subRelay, groupFilters, drained.size, drained.minOf { it.second.createdAt }, background)
                                        }
                                        // Alive if it EOSE'd or handed us anything; a connect-timeout that
                                        // gave nothing (classifyDrainFailure leaves it retryable forever)
                                        // earns a strike toward eviction instead.
                                        if (reason == "eose" || drained.isNotEmpty()) {
                                            clearTimeoutStrikes(subRelay)
                                        } else if (isTimeoutReason(reason)) {
                                            strikeUnproductiveTimeout(subRelay)
                                        }
                                        persisted
                                    } else {
                                        // Still streaming — hand off and let the round move on.
                                        burnedFastWindow.addAndFetch(1)
                                        bumpSlowest(slowestOut, subRelay, mark.elapsedNow().inWholeMilliseconds, "parked")
                                        notAnswered.add(subRelay)
                                        val scope = bgScope
                                        if (scope != null && config.parkTimeoutMs > config.timeoutMs) {
                                            if (!background) parkedInFlight.addAndFetch(1)
                                            scope.launch {
                                                // Incremental drain: persist + hand to lateHarvest whatever
                                                // has queued so far. Called on every activity ping and again
                                                // (NonCancellable) on the way out, so a park cancelled at
                                                // crawl end loses nothing it already received.
                                                var lateCount = 0
                                                var lateOldest = Long.MAX_VALUE

                                                suspend fun drainChunk() {
                                                    val chunk = ArrayList<Pair<NormalizedRelayUrl, Event>>()
                                                    while (true) chunk.add(unitEvents.tryReceive().getOrNull() ?: break)
                                                    if (chunk.isEmpty()) return
                                                    lateCount += chunk.size
                                                    for ((_, ev) in chunk) if (ev.createdAt < lateOldest) lateOldest = ev.createdAt
                                                    for (pair in persist(chunk)) lateHarvest.trySend(pair)
                                                }
                                                try {
                                                    // Idle timeout, not absolute: only cut after parkTimeoutMs
                                                    // of SILENCE (no event, no terminal), so a relay still
                                                    // streaming a large result set is never chopped mid-flight.
                                                    var late = "timeout"
                                                    while (true) {
                                                        val r =
                                                            withTimeoutOrNull(config.parkTimeoutMs) {
                                                                select {
                                                                    done.onAwait { it }
                                                                    activity.onReceive { ACTIVITY }
                                                                }
                                                            }
                                                        drainChunk()
                                                        when {
                                                            r == null -> break // idle window elapsed in silence
                                                            r == ACTIVITY -> continue // streamed — reset the window
                                                            else -> {
                                                                late = r
                                                                break
                                                            }
                                                        }
                                                    }
                                                    val lateMs = mark.elapsedNow().inWholeMilliseconds
                                                    logSlow(subRelay, "parked→$late", lateMs, groupFilters)
                                                    // A parked relay that ends in a hard/transient failure (not a
                                                    // clean EOSE) is reported dead the same way a fast one would be.
                                                    val lateDead = ConcurrentMap<NormalizedRelayUrl, DrainFailure>()
                                                    classify(late, subRelay, lateDead)
                                                    recordDead(lateDead.snapshot())
                                                    telemetry.record(subRelay, RelayTelemetry.outcomeOf(late, parked = true), lateMs, authorsIn(groupFilters), lateCount)
                                                    // A full parked page from a clean EOSE may also be capped —
                                                    // paginate its remainder in the background, same as the fast path.
                                                    if (late == "eose" && lateCount > 0) {
                                                        paginateIfCapped(subRelay, groupFilters, lateCount, lateOldest, background)
                                                    }
                                                    // Same liveness rule as the fast path: a park that ended
                                                    // in a clean EOSE or delivered anything clears the relay;
                                                    // one that idle-cut ("timeout") with nothing strikes it.
                                                    if (late == "eose" || lateCount > 0) {
                                                        clearTimeoutStrikes(subRelay)
                                                    } else if (late == "timeout" || isTimeoutReason(late)) {
                                                        strikeUnproductiveTimeout(subRelay)
                                                    }
                                                } finally {
                                                    withContext(NonCancellable) {
                                                        drainChunk() // keep whatever arrived since the last ping
                                                        unitEvents.close()
                                                        client.unsubscribe(subId)
                                                        if (!background) parkedInFlight.addAndFetch(-1)
                                                    }
                                                }
                                            }
                                            // Parked: events are persisted into lateHarvest by the
                                            // coroutine above, so this round contributes nothing here.
                                            emptyList()
                                        } else {
                                            // Parking disabled (no bgScope, or parkTimeoutMs <= timeoutMs):
                                            // drain and persist whatever streamed during the fast window
                                            // instead of dropping it, then return it so the round ingests
                                            // it exactly like the fast path — the other two branches persist,
                                            // this one must too or those events are lost and re-queried.
                                            val toMs = mark.elapsedNow().inWholeMilliseconds
                                            bumpSlowest(slowestOut, subRelay, toMs, "timeout")
                                            logSlow(subRelay, "timeout", toMs, groupFilters)
                                            unitEvents.close()
                                            client.unsubscribe(subId)
                                            val drained = buildList { for (e in unitEvents) add(e) }
                                            telemetry.record(subRelay, RelayTelemetry.Outcome.FAST_TIMEOUT, toMs, authorsIn(groupFilters), drained.size)
                                            // Nothing delivered → same unproductive-timeout signal, strike it;
                                            // anything delivered proves the host productive and clears it.
                                            if (drained.isEmpty()) strikeUnproductiveTimeout(subRelay) else clearTimeoutStrikes(subRelay)
                                            persist(drained)
                                        }
                                    }
                                }
                            }
                        }.awaitAll()
                        .flatten()
                }

            deadOut?.putAll(failures.snapshot())
            answeredOut?.addAll(filters.keys.filter { it !in notAnswered })
            return fast
        }

        /**
         * Emit the per-relay classification table: for every relay we touched, our
         * LIVE/DEAD verdict and the concurrency/rate limits we settled on, joined
         * with the evidence (attempts + outcome counts + yield + latency) that drove
         * it. One `[relay-class]` line per relay (tab-separated) plus a `[relay-class-sum]`
         * summary, so a later test can re-probe each relay and check the verdict/limit.
         */
        fun dumpRelayClassification() {
            val rows = telemetry.rows.snapshot()
            if (rows.isEmpty()) return
            log(
                "[relay-class-hdr] url\tclass\tconc_cap\trate_ms\tattempts\teose\ttimeout\t" +
                    "cannot\tratelim\tauth\tyield_pct\tmean_lat_ms\tmax_lat_ms",
            )
            var unreachable = 0
            var throttled = 0
            var live = 0
            val capHist = HashMap<Int, Int>()
            for ((relay, r) in rows) {
                val cap = limiter.concurrencyCapOf(relay)
                val rate = limiter.rateDelayOf(relay)
                capHist[cap] = (capHist[cap] ?: 0) + 1
                // UNREACHABLE: we gave up on it (dead set, connect-failure driven).
                // THROTTLED: alive, but it pushed back so we capped/rate-limited it.
                // LIVE: alive at default limits.
                val klass =
                    when {
                        relay in deadRelays -> "UNREACHABLE".also { unreachable++ }
                        limiter.isThrottled(relay) -> "THROTTLED".also { throttled++ }
                        else -> "LIVE".also { live++ }
                    }
                val att = r.attempts.load()
                val eose = r.count(RelayTelemetry.Outcome.FAST_EOSE) + r.count(RelayTelemetry.Outcome.SLOW_EOSE)
                val to = r.count(RelayTelemetry.Outcome.FAST_TIMEOUT) + r.count(RelayTelemetry.Outcome.PARK_TIMEOUT)
                val ask = r.authorsAsked.load()
                val yieldPct = if (ask > 0) r.eventsReturned.load() * 100 / ask else 0
                val meanLat = if (att > 0) r.latSumMs.load() / att else 0
                log(
                    "[relay-class] ${relay.url}\t$klass\t$cap\t$rate\t$att\t$eose\t$to\t" +
                        "${r.count(RelayTelemetry.Outcome.CANNOT)}\t${r.count(RelayTelemetry.Outcome.CLOSED_RATE)}\t" +
                        "${r.count(RelayTelemetry.Outcome.CLOSED_AUTH)}\t$yieldPct\t$meanLat\t${r.latMaxMs.load()}",
                )
            }
            val capsStr = capHist.entries.sortedBy { it.key }.joinToString(", ", "{", "}") { "${it.key}=${it.value}" }
            log("[relay-class-sum] relays=${rows.size} live=$live throttled=$throttled unreachable=$unreachable concurrency_caps=$capsStr")
        }

        /**
         * The round loop: expand the frontier until every in-budget user is done (or
         * [Config.maxRounds] hits). RE-ENTRANT: the terminal aggregator recovery calls
         * it again when its folded lists reveal users the previous rounds never saw.
         */
        private suspend fun runRounds() {
            // Background scope for parked handoffs + Tier-2 sweeps; set by [run]
            // before the first call and alive until the crawl's very end.
            val scope = checkNotNull(bgScope) { "runRounds requires bgScope" }
            while (rounds < config.maxRounds) {
                // Fold in whatever the parked (slow-but-alive) relays have delivered
                // since the last round — their late contact lists expand the frontier
                // a round or two behind the fast ones (single-writer: only here).
                foldLateHarvest()

                // Only crawl users within the hop budget; deeper users still appear
                // in the graph as follow targets, we just don't fetch their lists.
                val pending = hopOf.keys.filter { it !in done && (hopOf[it] ?: 0) < config.maxHops }
                if (pending.isEmpty()) {
                    // Frontier drained. Parked relays still streaming can no longer
                    // change this loop's outcome: pending empty means every in-budget
                    // user is done, and parked filters only ever asked for in-budget
                    // users, so ingestLate would skip 100% of their late lists. Their
                    // events still persist incrementally to the store, where the
                    // aggregator pass re-reads them — blocking here on parkedInFlight
                    // only serialized that wait (measured 222s at hop-3) in front of
                    // the final passes. A last fold catches any already-queued
                    // delivery; if it (unexpectedly) fed someone, loop once more.
                    progConverging = true
                    if (foldLateHarvest() == 0) break else continue
                }
                rounds++
                // Frame this round for the heartbeat ticker: its target is the pending
                // set, its baseline is how many users were already done going in.
                progRound = rounds
                progTarget = pending.size
                progBaseDone = done.size
                progConverging = false

                // Refresh the warm pool for this round and keep that subscription open —
                // reusing the same subId just updates the desired-relay set, so warm
                // sockets survive across rounds and newly-learned outbox relays start
                // connecting in the background before Phase B routes to them.
                refreshWarmPool()

                val discoveredBefore = hopOf.size
                val fedBefore = contactListsFed
                val roundMark = TimeSource.Monotonic.markNow()

                // Phase A — fold any kind:3 already sitting in the store FIRST (a prior
                // round's ensureRelayLists co-fetch, a late parked delivery, or a
                // previous run), batched by author chunk; then bulk-fetch the genuinely
                // missing rest from the busiest relays via the sharded sweep (which per
                // its precondition trusts `done` instead of re-checking the store).
                val fedBeforeA = contactListsFed
                harvestFromStore(pending)
                shardedSweep(pending)
                val phaseAMs = roundMark.elapsedNow().inWholeMilliseconds
                val phaseAFed = contactListsFed - fedBeforeA

                // Phase B — whoever the popular relays didn't have (niche outboxes):
                // resolve their kind:10002, then fetch from their own write relays,
                // drained a few at a time and skipping dead relays.
                if (config.diagnose) batchWalls.reset()
                val stragglers = pending.filter { it !in done }
                if (stragglers.isNotEmpty()) {
                    val backbone = topLiveRelays(BACKBONE_SIZE).toSet()
                    // Snapshot of every relay we've seen work, for the wide Tier-2
                    // sweep (taken now, before the Phase-B workers mutate liveRelays).
                    val allLive = liveRelays.snapshot().filterTo(HashSet()) { !isDead(it) }
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
                                        activeWorkers.addAndFetch(1)
                                        try {
                                            val dead = HashMap<NormalizedRelayUrl, DrainFailure>()
                                            val answered = HashSet<NormalizedRelayUrl>()
                                            val slowest = if (config.diagnose) AtomicReference<SlowestUnit?>(null) else null
                                            val batchMark = TimeSource.Monotonic.markNow()
                                            val events = drainGated(filters, dead, answered, slowestOut = slowest)
                                            recordDead(dead)
                                            val drained =
                                                DrainedBatch(
                                                    batch,
                                                    filters,
                                                    answered,
                                                    events,
                                                    wallMs = batchMark.elapsedNow().inWholeMilliseconds,
                                                    slowest = slowest?.load(),
                                                )
                                            if (config.diagnose) batchWalls.record(drained)
                                            drainedOut.send(drained)
                                        } finally {
                                            activeWorkers.addAndFetch(-1)
                                        }
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

                                    usersVisited.addAndFetch(d.batch.size.toLong())
                                    batchesDone.addAndFetch(1)

                                    // One chunked store query for the whole batch (256
                                    // authors) instead of a point query per user.
                                    val stored = latestContactsFor(d.batch.filter { it !in done })
                                    for (pk in d.batch) {
                                        if (pk in done) continue
                                        val contacts = stored[pk]
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

                val roundMs = roundMark.elapsedNow().inWholeMilliseconds
                log(
                    "[graperank] round $rounds: pending=${pending.size}, " +
                        "gotList=${contactListsFed - fedBefore}, newUsers=${hopOf.size - discoveredBefore}, " +
                        "discovered=${hopOf.size}, done=${done.size}, dead=${deadRelays.size()}, " +
                        "time=${roundMs}ms (phaseA=${phaseAMs}ms fed=$phaseAFed, phaseB=${roundMs - phaseAMs}ms fed=${contactListsFed - fedBefore - phaseAFed})",
                )
                if (config.diagnose) {
                    // Batch-wall distribution vs the fast window that supposedly bounds
                    // it — the direct measure of pre-drain queueing — plus the limiter's
                    // running demotion counts, so relay push-back is visible per round.
                    batchWalls.dump(log, config.timeoutMs)
                    val snap = limiter.snapshot()
                    log(
                        "[graperank] limiter: capped=${snap["concurrency_capped_relays"]} " +
                            "rate_limited=${snap["rate_limited_relays"]}",
                    )
                }
            }
        }

        suspend fun run(): Stats {
            val crawlMark = TimeSource.Monotonic.markNow()
            // Scope owning parked (slow-relay) subscriptions and the fire-and-forget
            // Tier-2 relay-list sweeps. SupervisorJob so one failure never cancels the
            // others; cancelled once the crawl converges. Published to [bgScope] so
            // drainGated can hand slow subs to it.
            val scope = CoroutineScope(coroutineContext + SupervisorJob())
            bgScope = scope

            // Heartbeat: keeps a long, silent round feeling alive with live % + ETA.
            // Runs on [scope], so scope.cancel() at crawl end stops it.
            scope.launch { progressTicker() }

            // Background reachability culler: cheaply TCP-probes the cold tail of
            // learned relays and drops the unreachable ones into deadHosts before the
            // WS path pays the full connectTimeout on them. Runs on [scope], stopped
            // by scope.cancel() at crawl end.
            config.reachabilityProbe?.let { probe -> scope.launch { cullUnreachable(probe) } }

            // Mass pre-connect: start the handshake to every relay a prior run proved
            // live NOW, in one parallel storm, so the connect wait is paid once —
            // concurrently, before the first drain — instead of serially inside each
            // drain that first routes to a relay. The subscribe returns immediately;
            // sockets ramp in the background while round 1's discovery queries run.
            if (config.preconnectCap > 0 && config.knownLiveRelays.isNotEmpty()) {
                log(
                    "[graperank] pre-connecting up to ${config.preconnectCap} of " +
                        "${config.knownLiveRelays.size} known-live relays",
                )
                refreshWarmPool()
            }

            runRounds()

            // Patient final passes, run CONCURRENTLY — they are independent (one
            // folds stragglers' kind:3 into the graph, the other lands kind:5
            // retractions in the store; graph state stays single-writer inside the
            // aggregator pass), so serially they cost sum instead of max (report
            // deletions alone measured 137s at hop-3).
            //
            // Aggregator pass: recover the stragglers the outbox model couldn't
            // resolve by asking the content aggregators for their kind:3 ALONE, no
            // longer racing the full fan-out (which cut the aggregators short during
            // the rounds). Runs before [scope] is cancelled so slow aggregators park.
            //
            // Report deletions: reports can be retracted. Ask each reporter's outbox
            // for NIP-09 kind:5 deletions that cite the reports we gathered
            // (#e-filtered to our report ids). The events land in the store; the
            // caller decides which reports they actually retract. Runs before
            // [scope] is cancelled so it can still park slow relays.
            coroutineScope {
                launch { recoverStragglersFromAggregators() }
                launch { fetchReportDeletions(topLiveRelays(BACKBONE_SIZE).toSet()) }
            }

            // FIXPOINT: recovery discoveries must be CRAWLED, not just counted. A
            // recovered straggler's list reveals users the rounds never saw; without
            // re-entering the round loop those users dead-end, making deep-hop
            // completeness hostage to WHEN a list arrived (in-round = crawlable,
            // terminal recovery = ignored). Measured: one weak backbone hour at
            // round 5 shifted ~30k lists to the terminal pass and silently
            // amputated ~220k hop-6/7 users vs an otherwise-identical run. So:
            // while recovery keeps revealing in-budget pending users, run more
            // rounds and recover again — each pass folds each straggler at most
            // once and the hop budget bounds depth, so this converges (and
            // maxRounds backstops it). The warm pool is still up, so resumed
            // rounds start on warm sockets.
            var extraRounds = false
            while (rounds < config.maxRounds &&
                hopOf.keys.any { it !in done && (hopOf[it] ?: 0) < config.maxHops }
            ) {
                extraRounds = true
                log("[graperank] recovery expanded the frontier — resuming rounds")
                runRounds()
                recoverStragglersFromAggregators()
            }
            // Extra rounds can gather new kind:1984 reports whose deletions the
            // first pass never saw; top up (store-deduped, so re-asking is cheap).
            // Skipped entirely on the common no-extra-rounds path.
            if (extraRounds) fetchReportDeletions(topLiveRelays(BACKBONE_SIZE).toSet())

            // Crawl done — drop the warm pool.
            client.unsubscribe(WARM_SUB_ID)

            // Stop any parked subscriptions + Tier-2 relay-list sweeps still in flight
            // (whatever they fetched already landed in the store).
            scope.cancel()

            // Per-relay outcome/latency/yield table. Totals + worst time-sinks always;
            // the full per-relay dump (thousands of lines) only under --diagnose.
            telemetry.dump(log, full = config.diagnose)

            // Machine-readable per-relay CLASSIFICATION table: our live/dead verdict
            // and the concurrency/rate limits we settled on, joined with the evidence
            // (outcome counts + yield) so it can be checked against an independent
            // re-probe later. Emitted only under --diagnose (one line per relay).
            if (config.diagnose) dumpRelayClassification()

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
                    "${relaysContacted.size} relays contacted, ${deadRelays.size()} dead + ${deadHosts.size()} timeout-evicted hosts, " +
                    "$rounds rounds in $downloadMs ms; " +
                    "by hop: " + hopHistogram.entries.joinToString(" ") { "${it.key}=${it.value}" },
            )
            val soloEvents = insertSoloEvents.load()
            val soloUsPerEvent = if (soloEvents > 0) insertSoloNanos.load() / 1_000 / soloEvents else 0
            log(
                "[graperank] write path: $stored events stored, verify ${verifyMs}ms; insert wall ${insertMs}ms " +
                    "summed across concurrent drains — writer-mutex wait INCLUDED " +
                    "(uncontended writes: $soloEvents events ≈ ${soloUsPerEvent}µs/event; batch=${config.insertBatchSize})",
            )
            log("[graperank] store reads: ${queryCount.load()} queries in ${queryNanos.load() / 1_000_000}ms")
            if (config.diagnose) {
                val n = drainSamples.load().coerceAtLeast(1)
                val wall = drainWallSumMs.load().coerceAtLeast(1)
                val meanFirst = firstEventSumMs.load() / n
                val meanEose = eoseWaitSumMs.load() / n
                val eosePct = 100 * eoseWaitSumMs.load() / wall
                log(
                    "[graperank] latency breakdown (drains with events, n=${drainSamples.load()}): " +
                        "mean time-to-first-event ${meanFirst}ms, mean EOSE-wait-after-last-event ${meanEose}ms " +
                        "($eosePct% of drain wall spent waiting for EOSE after the relay's last event); " +
                        "${burnedFastWindow.load()} drains blew the ${config.timeoutMs}ms fast window and parked; " +
                        "${throttled.load()} rate-limit responses. " +
                        "High EOSE-wait % → a shorter/adaptive fast window is the lever, not more concurrency.",
                )
            }
            return Stats(
                rounds = rounds,
                contactListsFed = contactListsFed,
                relaysContacted = relaysContacted.size,
                hopHistogram = hopHistogram,
                contactsFedByHop = contactsFedByHop.toList().sortedBy { it.first }.toMap(),
                downloadMs = downloadMs,
                verifyMs = verifyMs,
                insertMs = insertMs,
                eventsStored = stored,
                // Only relays we actually dialed this run — exclude the seeded
                // known-dead (skipped, not re-probed) so their original TTL stands.
                deadRelays = deadRelays.snapshot() - config.knownDeadRelays,
                liveRelays = liveRelays.snapshot(),
            )
        }
    }

    /**
     * One Phase-B batch after draining: the users asked for, the relay->filters map
     * they were routed through, the relays that cleanly EOSE'd ([answered]), the
     * fresh events, and (diagnose) how long the drain held its worker plus the unit
     * that resolved last. Carries enough for the consumer to attribute "answered but
     * empty" per user without re-deriving the routing.
     */
    private class DrainedBatch(
        val batch: List<HexKey>,
        val filters: Map<NormalizedRelayUrl, List<Filter>>,
        val answered: Set<NormalizedRelayUrl>,
        val events: List<Pair<NormalizedRelayUrl, Event>>,
        val wallMs: Long = 0,
        val slowest: SlowestUnit? = null,
    )

    /** The drain unit that took longest to reach its fast outcome within one drainGated call. */
    class SlowestUnit(
        val relay: NormalizedRelayUrl,
        val elapsedMs: Long,
        val reason: String,
    )

    /**
     * Per-round Phase-B batch-wall statistics (diagnose only). A batch's wall is the
     * awaitAll over its units, so its distribution — against the fast window that
     * supposedly bounds it — is the direct measure of pre-drain queueing (permits,
     * rate gates, dispatcher). Reset by the round loop before Phase B; recorded
     * concurrently by the drain workers; dumped at round end with the slowest
     * batches' last-resolving units named.
     */
    private class BatchWallStats {
        private val seq = AtomicLong(0)

        // Swapped whole on reset (ConcurrentMap exposes no clear); reset only runs
        // from the round loop between rounds, when no drain worker is recording.
        @Volatile private var samples = ConcurrentMap<Long, DrainedBatch>()

        fun reset() {
            samples = ConcurrentMap()
        }

        fun record(batch: DrainedBatch) {
            samples[seq.addAndFetch(1)] = batch
        }

        fun dump(
            log: (String) -> Unit,
            fastWindowMs: Long,
        ) {
            val snap = samples.snapshot().values.toList()
            if (snap.isEmpty()) return
            val walls = snap.map { it.wallMs }.sorted()

            fun pct(p: Int) = walls[(walls.size - 1) * p / 100]
            val over = walls.count { it > fastWindowMs }
            val slowest = snap.sortedByDescending { it.wallMs }.take(3)
            val detail =
                slowest.joinToString(" ; ") { b ->
                    "${b.wallMs}ms ${b.filters.size}relays ${b.batch.size}authors" +
                        (b.slowest?.let { " last=${it.relay.url} ${it.elapsedMs}ms(${it.reason})" } ?: "")
                }
            log(
                "[batch-walls] n=${walls.size} p50=${pct(50)}ms p90=${pct(90)}ms max=${walls.last()}ms · " +
                    "$over over the ${fastWindowMs}ms fast window | slowest: $detail",
            )
        }
    }

    /**
     * Per-relay outcome + latency + yield accounting, accumulated across the whole
     * crawl from every drain unit (fast and parked). This is the ground truth for
     * "which relays are worth talking to": for each relay we track how every REQ
     * ended, how long it took, how many authors we asked it for, and how many
     * events it actually returned. A relay that eats a 27s connect on every REQ and
     * returns nothing is a pure time sink; one that EOSEs fast with a high
     * events/authors yield is gold. The dump at crawl end sorts by wasted time so
     * the worst offenders are obvious, and it's the signal source a future adaptive
     * controller uses to size filters / cap concurrency / strangle per relay.
     *
     * Fully concurrent: many drain workers and parked coroutines record at once, so
     * every counter is atomic and the row map is a [ConcurrentMap].
     */
    class RelayTelemetry {
        enum class Outcome {
            /** EOSE within the fast window — the good case. */
            FAST_EOSE,

            /** EOSE, but only after parking (blew the fast window, delivered late). */
            SLOW_EOSE,

            /** Blew the fast window and never parked (parking off / no bg scope). */
            FAST_TIMEOUT,

            /** Parked, then the park idle window elapsed with no terminal. */
            PARK_TIMEOUT,

            /** Could not open the socket at all (offline / DNS / TLS / refused). */
            CANNOT,

            /** CLOSED with a rate-limit / too-many-subs / burst complaint. */
            CLOSED_RATE,

            /** CLOSED demanding NIP-42 auth we don't provide. */
            CLOSED_AUTH,

            /** CLOSED blocked / restricted / banned. */
            CLOSED_BLOCKED,

            /** CLOSED for any other reason. */
            CLOSED_OTHER,
        }

        class Row {
            val byOutcome = ConcurrentMap<Outcome, AtomicLong>()
            val attempts = AtomicLong(0)
            val authorsAsked = AtomicLong(0)
            val eventsReturned = AtomicLong(0)
            val latSumMs = AtomicLong(0)
            val latMaxMs = AtomicLong(0)

            /**
             * Wall time units spent QUEUED for this relay's limiter slot (concurrency
             * permit + rate gate) BEFORE their drain timer starts. Invisible in every
             * latency number above — [latSumMs] starts after the permit is acquired —
             * yet it is exactly where a hot relay serializes the whole fan-out, so it
             * gets its own ledger.
             */
            val permitWaitMs = AtomicLong(0)

            fun bump(outcome: Outcome) = byOutcome.getOrPut(outcome) { AtomicLong(0) }.addAndFetch(1)

            fun count(outcome: Outcome): Long = byOutcome[outcome]?.load() ?: 0
        }

        val rows = ConcurrentMap<NormalizedRelayUrl, Row>()

        /** Add [waitMs] of pre-drain limiter queueing (permit + rate gate) to [relay]'s row. */
        fun recordPermitWait(
            relay: NormalizedRelayUrl,
            waitMs: Long,
        ) {
            if (waitMs <= 0) return
            rows.getOrPut(relay) { Row() }.permitWaitMs.addAndFetch(waitMs)
        }

        fun record(
            relay: NormalizedRelayUrl,
            outcome: Outcome,
            latencyMs: Long,
            authorsAsked: Int,
            eventsReturned: Int,
        ) {
            val row = rows.getOrPut(relay) { Row() }
            row.attempts.addAndFetch(1)
            row.bump(outcome)
            row.authorsAsked.addAndFetch(authorsAsked.toLong())
            row.eventsReturned.addAndFetch(eventsReturned.toLong())
            row.latSumMs.addAndFetch(latencyMs)
            while (true) {
                val cur = row.latMaxMs.load()
                if (latencyMs <= cur || row.latMaxMs.compareAndSet(cur, latencyMs)) break
            }
        }

        /** Total wall time we spent waiting on a relay that gave us nothing useful. */
        private fun wastedMs(r: Row): Long {
            // Time in the two no-yield terminal classes; a slow EOSE that DID return
            // events isn't "wasted", so only count the pure sinks.
            val sinkAttempts = r.count(Outcome.FAST_TIMEOUT) + r.count(Outcome.PARK_TIMEOUT) + r.count(Outcome.CANNOT)
            val total = r.attempts.load()
            if (total == 0L) return 0
            return r.latSumMs.load() * sinkAttempts / total
        }

        /**
         * Dump the per-relay table via [log]. Totals + the worst time-sinks always;
         * the FULL per-relay table (every relay we touched, sorted worst-first) only
         * when [full] — thousands of lines, so gate it behind --diagnose.
         */
        fun dump(
            log: (String) -> Unit,
            full: Boolean,
        ) {
            val snap = rows.snapshot()
            if (snap.isEmpty()) return

            val totals = HashMap<Outcome, Long>()
            var authors = 0L
            var events = 0L
            for ((_, r) in snap) {
                for (o in Outcome.entries) totals[o] = (totals[o] ?: 0) + r.count(o)
                authors += r.authorsAsked.load()
                events += r.eventsReturned.load()
            }
            log(
                "[relay-telemetry] ${snap.size} relays · outcomes " +
                    Outcome.entries.filter { (totals[it] ?: 0) > 0 }.joinToString(" ") { "${it.name.lowercase()}=${totals[it]}" },
            )
            log("[relay-telemetry] asked $authors author-slots, got $events events (yield ${if (authors > 0) events * 100 / authors else 0}%)")

            fun line(
                url: String,
                r: Row,
            ): String {
                val a = r.attempts.load()
                val meanLat = if (a > 0) r.latSumMs.load() / a else 0
                val ask = r.authorsAsked.load()
                val yield = if (ask > 0) r.eventsReturned.load() * 100 / ask else 0
                return "  $url att=$a eose=${r.count(Outcome.FAST_EOSE)}/${r.count(Outcome.SLOW_EOSE)} " +
                    "to=${r.count(Outcome.FAST_TIMEOUT) + r.count(Outcome.PARK_TIMEOUT)} cannot=${r.count(Outcome.CANNOT)} " +
                    "rate=${r.count(Outcome.CLOSED_RATE)} auth=${r.count(Outcome.CLOSED_AUTH)} " +
                    "ask=$ask got=${r.eventsReturned.load()} yield=$yield% lat=$meanLat/${r.latMaxMs.load()}ms " +
                    "pwait=${r.permitWaitMs.load()}ms wasted=${wastedMs(r)}ms"
            }

            // Total pre-drain limiter queueing and the single worst offender — the
            // signal that a hot relay's permit gate is serializing the fan-out.
            val totalPermitWait = snap.values.sumOf { it.permitWaitMs.load() }
            if (totalPermitWait > 0) {
                val worst = snap.entries.maxByOrNull { it.value.permitWaitMs.load() }
                log(
                    "[relay-telemetry] permit/rate-gate wait total ${totalPermitWait}ms" +
                        (worst?.let { " (worst: ${it.key.url} ${it.value.permitWaitMs.load()}ms)" } ?: ""),
                )
            }

            val ordered = snap.entries.sortedByDescending { wastedMs(it.value) }
            log("[relay-telemetry] top time-sinks (by wasted ms):")
            for ((relay, r) in ordered.take(25)) log(line(relay.url, r))

            if (full) {
                val byPermitWait =
                    snap.entries
                        .filter { it.value.permitWaitMs.load() > 0 }
                        .sortedByDescending { it.value.permitWaitMs.load() }
                        .take(10)
                if (byPermitWait.isNotEmpty()) {
                    log("[relay-telemetry] top permit/rate-gate queues:")
                    for ((relay, r) in byPermitWait) log(line(relay.url, r))
                }
                log("[relay-telemetry] FULL per-relay table (${snap.size} relays, worst-first):")
                for ((relay, r) in ordered) log(line(relay.url, r))
            }
        }

        companion object {
            /** Map a drain terminal reason (see [drainGated]) to an [Outcome]. */
            fun outcomeOf(
                reason: String,
                parked: Boolean,
            ): Outcome =
                when {
                    reason == "eose" -> if (parked) Outcome.SLOW_EOSE else Outcome.FAST_EOSE
                    reason == "timeout" -> if (parked) Outcome.PARK_TIMEOUT else Outcome.FAST_TIMEOUT
                    reason.startsWith("cannot") -> Outcome.CANNOT
                    reason.startsWith("closed:") -> {
                        val m = reason.removePrefix("closed:").lowercase()
                        when {
                            "rate" in m || "too many" in m || "burst" in m || "slow down" in m || "subscription" in m -> Outcome.CLOSED_RATE
                            "auth" in m -> Outcome.CLOSED_AUTH
                            "block" in m || "restrict" in m || "ban" in m -> Outcome.CLOSED_BLOCKED
                            else -> Outcome.CLOSED_OTHER
                        }
                    }
                    else -> Outcome.CLOSED_OTHER
                }
        }
    }

    /** [IEventStore.query] with crawl-wide count + wall-time accounting. */
    private suspend fun queryTimed(filter: Filter): List<Event> {
        val mark = TimeSource.Monotonic.markNow()
        val result = store.query<Event>(filter)
        queryNanos.addAndFetch(mark.elapsedNow().inWholeNanoseconds)
        queryCount.addAndFetch(1)
        return result
    }

    /** Latest known kind:3 contact list for [pubKey] from the local store, or null. */
    private suspend fun contactsOf(pubKey: HexKey): ContactListEvent? =
        queryTimed(Filter(authors = listOf(pubKey), kinds = listOf(ContactListEvent.KIND), limit = 1))
            .firstOrNull() as? ContactListEvent

    /**
     * Latest stored kind:3 per author for a CHUNK of authors, in ONE store query.
     * The batched sibling of [contactsOf]: a point query costs ~8ms on a multi-GB
     * store under concurrent writers, so every crawl path that resolves contact
     * lists for thousands of users at once must go through this instead. Newest
     * `created_at` per author wins, matching the replaceable-event rule.
     * Callers chunk to [AUTHORS_PER_FILTER] to keep the SQL parameter list sane.
     */
    private suspend fun latestContactsFor(authors: Collection<HexKey>): Map<HexKey, ContactListEvent> {
        if (authors.isEmpty()) return emptyMap()
        val latest = HashMap<HexKey, ContactListEvent>()
        for (ev in queryTimed(Filter(kinds = listOf(ContactListEvent.KIND), authors = authors.toList()))) {
            if (ev !is ContactListEvent) continue
            val cur = latest[ev.pubKey]
            if (cur == null || ev.createdAt > cur.createdAt) latest[ev.pubKey] = ev
        }
        return latest
    }

    /** Latest known kind:10002 advertised relay list for [pubKey] from the store, or null. */
    private suspend fun relaysOf(pubKey: HexKey): AdvertisedRelayListEvent? =
        queryTimed(Filter(authors = listOf(pubKey), kinds = listOf(AdvertisedRelayListEvent.KIND), limit = 1))
            .firstOrNull() as? AdvertisedRelayListEvent

    companion object {
        // Authors per REQ filter — keeps individual subscriptions within relay limits.
        private const val AUTHORS_PER_FILTER = 300

        // Concurrent TCP reachability probes in the background culler. Raw sockets are
        // cheap and short-lived; the per-relay WS limiter is unaffected (this never
        // opens a REQ), so this only bounds file descriptors during the cull.
        private const val PROBE_CONCURRENCY = 128

        // Re-scan interval for the culler when it has probed everything learned so far
        // and is waiting for new relays to be discovered.
        private const val PROBE_IDLE_MS = 2000L

        // A single REQ can match up to authors×kinds events; a relay that caps its
        // response below that silently drops the tail (measured: user.kindpag.es
        // returns at most ~100 events per REQ and ignores our limit). Any page that
        // comes back with at least this many events is treated as possibly-capped and
        // paginated with `until` cursors to drain the rest. Set at the smallest page
        // cap we've observed, so it catches every relay that caps at or above it while
        // sparing the common under-cap page an extra REQ.
        private const val FULL_PAGE_THRESHOLD = 100

        // Max total "entries" (authors + ids + tag values) in a single REQ frame.
        // Each entry is a ~67-byte hex string, so 2500 ≈ 167KB — under the 256KB
        // message cap most relays enforce. drainGated groups filters to stay within.
        private const val MAX_REQ_ENTRIES = 2500

        // --diagnose: a REQ that takes longer than this to reach a terminal (EOSE or
        // timeout) is logged with its relay + filter, so slow relays can be replayed.
        private const val SLOW_DRAIN_LOG_MS = 4000L

        // --diagnose: [slow-relay] sample lines kept per authority before suppression.
        private const val SLOW_LOG_MAX_PER_AUTHORITY = 3

        /**
         * Is a drain terminal reason a connect/read TIMEOUT — the class
         * [classifyDrainFailure] leaves retryable forever? The reason shape is
         * `cannot:<message>` and the message now carries the exception class name
         * (see BasicRelayClient), so a SocketTimeoutException surfaces as "timed
         * out"/"timeout". Used to drive unproductive-timeout eviction.
         */
        private fun isTimeoutReason(reason: String): Boolean {
            if (!reason.startsWith("cannot")) return false
            val m = reason.removePrefix("cannot:").lowercase()
            return "timeout" in m || "timed out" in m
        }

        /**
         * The authority (host[:port]) of a normalized relay URL — the segment between
         * the `wss://` / `ws://` scheme and the first `/`. This is the key the
         * timeout-eviction counts on, so the many per-user path URLs the outbox model
         * mints for one server (`filter.nostr.wine/npubA`, `filter.nostr.wine/npubB`, …)
         * collapse to a single evictable host. A bare host is its own authority, so this
         * is a no-op for the common no-path relay. Deliberately host-only: it must NOT
         * fold `filter.nostr.wine` into `nostr.wine` — those are different servers with
         * different behaviour (the bare host may read fine while the filter host stalls).
         */
        fun authorityOf(url: String): String {
            val afterScheme =
                when {
                    url.startsWith("wss://") -> url.substring(6)
                    url.startsWith("ws://") -> url.substring(5)
                    else -> url
                }
            val slash = afterScheme.indexOf('/')
            return if (slash >= 0) afterScheme.substring(0, slash) else afterScheme
        }

        // Once the frontier is empty but parked relays are still streaming, how long
        // to block waiting for one of them to deliver before re-checking convergence.
        private const val PARK_POLL_MS = 2000L

        // How often the heartbeat ticker emits a live-progress line.
        private const val PROGRESS_INTERVAL_MS = 3000L

        /** Compact human count: 1234 -> "1.2k", 1_500_000 -> "1.5M". */
        private fun human(n: Long): String =
            when {
                n >= 1_000_000 -> "${n / 1_000_000}.${(n % 1_000_000) / 100_000}M"
                n >= 1_000 -> "${n / 1_000}.${(n % 1_000) / 100}k"
                else -> n.toString()
            }

        /** Seconds as "45s" or "3m20s". */
        private fun etaFmt(secs: Long): String = if (secs >= 60) "${secs / 60}m${secs % 60}s" else "${secs}s"

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

        // Most-used write relays kept as the known-good backbone for retrying users.
        private const val BACKBONE_SIZE = 30

        // Warm pool: hold a do-nothing subscription open to relays for the whole
        // crawl, so their connections survive the between-round routing gaps. The
        // filter matches an impossible event id, so the relay EOSEs immediately and
        // streams nothing — it only keeps sockets warm. With Config.preconnectCap > 0
        // the pool covers the whole candidate universe (mass pre-connect); this
        // constant is the busiest-relays fallback size when that is disabled.
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

        /** Total author slots across a unit's filters — what we asked a relay for. */
        private fun authorsIn(filters: List<Filter>): Int = filters.sumOf { it.authors?.size ?: 0 }

        /** CAS-update [out] with this unit if it beats the current slowest. Null out = diagnose off. */
        private fun bumpSlowest(
            out: AtomicReference<SlowestUnit?>?,
            relay: NormalizedRelayUrl,
            elapsedMs: Long,
            reason: String,
        ) {
            if (out == null) return
            while (true) {
                val cur = out.load()
                if (cur != null && cur.elapsedMs >= elapsedMs) return
                if (out.compareAndSet(cur, SlowestUnit(relay, elapsedMs, reason))) return
            }
        }

        /** Count the size-driving entries in a filter: authors, ids, and tag values. */
        private fun filterEntries(f: Filter): Int =
            (f.authors?.size ?: 0) +
                (f.ids?.size ?: 0) +
                (f.tags?.values?.sumOf { it.size } ?: 0) +
                (f.tagsAll?.values?.sumOf { it.size } ?: 0)
    }
}
