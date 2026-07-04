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
package com.vitorpamplona.geode.mirror

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.OptimizedJsonMapper
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.SubscriptionListener
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.EventMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.ReqCmd
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.server.NostrServer
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebsocketBuilder
import com.vitorpamplona.quartz.nip01Core.relay.sockets.okhttp.BasicOkHttpWebSocket
import com.vitorpamplona.quartz.nip01Core.relay.sockets.okhttp.TcpNoDelaySocketFactory
import com.vitorpamplona.quartz.nip01Core.store.IEventStore
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.time.Duration
import java.util.concurrent.atomic.AtomicLong

/**
 * Which way events flow between this relay and one upstream —
 * strfry-router's per-stream `dir`.
 */
enum class MirrorDirection {
    /** Pull: subscribe to the upstream and ingest what it sends. */
    DOWN,

    /** Push: publish this relay's matching events to the upstream. */
    UP,

    /** Pull and push. Echo suppression keeps the two from ping-ponging. */
    BOTH,
    ;

    companion object {
        /** Parses strfry's `"down"` / `"up"` / `"both"`; null if unknown. */
        fun parse(value: String): MirrorDirection? =
            when (value.lowercase()) {
                "down" -> DOWN
                "up" -> UP
                "both" -> BOTH
                else -> null
            }
    }
}

/**
 * One upstream relay this relay mirrors, from the `[[mirror]]` config.
 *
 * [trusted] is the relay-to-relay trust switch: events streamed from this
 * upstream skip Schnorr signature verification on ingest. The trusted
 * identity is [url] — the address *this* relay dialed (TLS-authenticated
 * for `wss://`), never anything the peer claims — so the skip can't be
 * hijacked by an inbound client. Only meaningful for [MirrorDirection.DOWN]
 * / [MirrorDirection.BOTH]; the up direction never verifies (the upstream
 * does its own gatekeeping).
 */
class MirrorUpstream(
    val url: NormalizedRelayUrl,
    val trusted: Boolean,
    /** How far back the initial replay reaches, in BOTH directions. 0 = live-only from connect. */
    val backfillSeconds: Long = 0L,
    /**
     * Optional scope for this upstream (strfry-router's per-stream
     * `filter`). Applied symmetrically: down, it shapes the REQ sent
     * upstream AND every delivered event is re-checked before ingest —
     * so even a [trusted] upstream can only inject events inside the
     * declared scope; up, it selects which local events are pushed. Its
     * `since`/`limit` are ignored ([backfillSeconds] owns the time
     * window; the subscriptions are unbounded). `null` mirrors
     * everything.
     */
    val filter: Filter? = null,
    /** Flow direction — strfry-router's `dir`. Defaults to pull-only. */
    val direction: MirrorDirection = MirrorDirection.DOWN,
)

/**
 * Streams events from configured upstream relays into the local relay —
 * geode's equivalent of `strfry router` in the "down" direction.
 *
 * One [NostrClient] holds every upstream connection; the client owns
 * reconnects, exponential backoff, and re-sending the REQ after a drop.
 * Each upstream gets its own subscription over an open filter
 * (`since = now - backfill`), and every EVENT that arrives is handed to
 * [NostrServer.ingest] — the same group-commit writer and live fanout a
 * client publish takes — with `skipVerify` set for [MirrorUpstream.trusted]
 * upstreams (the upstream already verified its ingest; re-verifying here
 * only burns CPU — Schnorr verify profiles at ~8% of busy ingest CPU).
 *
 * After a reconnect the upstream replays everything since the boot-time
 * `since`; replayed duplicates are rejected by the store's unique id
 * constraint and only show up in [rejected].
 *
 * Listener callbacks can't suspend, so events funnel through an unbounded
 * [inbound] channel into one consumer coroutine whose [NostrServer.ingest]
 * call suspends on the ingest queue's backpressure. The buffer is unbounded
 * for the same reason the client's receive channels are (see
 * `BasicOkHttpWebSocket`): blocking the socket reader parks the backlog on
 * infrastructure that isn't ours.
 */
class MirrorWorker(
    private val upstreams: List<MirrorUpstream>,
    private val server: NostrServer,
    /**
     * Transport override for tests (e.g. `InProcessRelays`). Defaults to
     * a real OkHttp WebSocket per upstream.
     */
    websocketBuilder: WebsocketBuilder? = null,
) : AutoCloseable {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * The ping interval matters on a long-running daemon: a half-open
     * upstream connection (network drop with no FIN) never fires
     * onDisconnected on its own, so without pings the client would
     * believe it is connected forever and stop mirroring silently. A
     * missed pong fails the socket, which routes into the normal
     * disconnect → backoff → re-dial path. Same value the Android app
     * uses for its relay pool.
     */
    private val okhttp: OkHttpClient? =
        if (websocketBuilder == null) {
            OkHttpClient
                .Builder()
                .socketFactory(TcpNoDelaySocketFactory)
                .pingInterval(Duration.ofSeconds(PING_INTERVAL_SECS))
                .build()
        } else {
            null
        }

    private val client =
        NostrClient(
            websocketBuilder = websocketBuilder ?: BasicOkHttpWebSocket.Builder { okhttp!! },
            parentScope = scope,
        )

    private class Inbound(
        val event: Event,
        val skipVerify: Boolean,
    )

    private val inbound = Channel<Inbound>(Channel.UNLIMITED)

    /** Events accepted into the local store (excludes duplicates). */
    val accepted = AtomicLong(0)

    /** Events the store rejected — mostly duplicate replays after a reconnect. */
    val rejected = AtomicLong(0)

    /**
     * Deliveries dropped by the [MirrorUpstream.filter] re-check before
     * ever reaching the store — an upstream sending these is answering
     * outside the REQ it was given.
     */
    val filtered = AtomicLong(0)

    /** Local events handed to the client's outbox for an up-direction upstream. */
    val sentUp = AtomicLong(0)

    /**
     * Recently exchanged event ids, one set per up-capable upstream —
     * the echo suppressor for [MirrorDirection.BOTH]. An event pulled
     * DOWN from an upstream must not be pushed straight back UP to it
     * (and one we pushed up must not be re-ingested when the upstream
     * fans it back on our down subscription). Bounded LRU: eviction only
     * costs a wasted round trip that the stores' unique-id constraints
     * absorb, so correctness never depends on it.
     */
    private class RecentIds(
        private val capacity: Int,
    ) {
        private val map =
            object : LinkedHashMap<String, Boolean>(capacity, 0.75f, true) {
                override fun removeEldestEntry(eldest: Map.Entry<String, Boolean>) = size > capacity
            }

        @Synchronized
        fun add(id: String) {
            map[id] = true
        }

        @Synchronized
        fun contains(id: String): Boolean = map.containsKey(id)
    }

    /** Open in-process sessions feeding the up direction; closed with the worker. */
    private val upSessions = mutableListOf<AutoCloseable>()

    /** Dials every upstream and starts streaming. Call once. */
    fun start() {
        scope.launch {
            for (msg in inbound) {
                try {
                    server.ingest(msg.event, msg.skipVerify) { outcome ->
                        when (outcome) {
                            IEventStore.InsertOutcome.Accepted -> accepted.incrementAndGet()
                            is IEventStore.InsertOutcome.Rejected -> {
                                rejected.incrementAndGet()
                                Log.d("MirrorWorker") { "rejected ${msg.event.id}: ${outcome.reason}" }
                            }
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    // A server shutting down closes its ingest queue while
                    // events may still be buffered here; an uncaught throw
                    // would leak into the scope's default handler (and
                    // poison unrelated runTest tests on CI). Stop pulling —
                    // the relay beneath us is going away.
                    Log.w("MirrorWorker") { "ingest failed, stopping mirror consumer: ${e.message}" }
                    break
                }
            }
        }

        val since = TimeUtils.now()
        upstreams.forEachIndexed { i, up ->
            // Echo suppression only matters when events can flow both
            // ways on the same upstream.
            val exchanged = if (up.direction == MirrorDirection.BOTH) RecentIds(EXCHANGED_IDS_CAPACITY) else null

            // The operator's filter scopes the subscriptions in both
            // directions; the mirror owns the time window (since) and
            // never bounds the result (limit).
            val scopedFilter =
                (up.filter ?: Filter()).copy(
                    since = since - up.backfillSeconds,
                    limit = null,
                )

            if (up.direction != MirrorDirection.UP) {
                startDown(i, up, scopedFilter, exchanged)
            }
            if (up.direction != MirrorDirection.DOWN) {
                startUp(up, scopedFilter, exchanged)
            }
        }
        client.connect()

        // Retry pump. NostrClient re-dials once on disconnect and then
        // relies on its 60s keep-alive — measured as a 61s mirror blackout
        // when an upstream restarts and the immediate re-dial races the
        // port rebind. Poking more often costs nothing: reconnectIfNeedsTo
        // skips connected relays, and each relay's exponential backoff
        // (1s doubling, 5min cap) still gates actual dial attempts, so a
        // long-dead upstream is not hammered — only a briefly-restarting
        // one is picked back up in seconds instead of a minute.
        scope.launch {
            while (true) {
                delay(RECONNECT_POKE_MS)
                client.reconnect(onlyIfChanged = true, ignoreRetryDelays = false)
            }
        }
    }

    /** Down direction: subscribe to the upstream, ingest what it sends. */
    private fun startDown(
        index: Int,
        up: MirrorUpstream,
        scopedFilter: Filter,
        exchanged: RecentIds?,
    ) {
        val listener =
            object : SubscriptionListener {
                override fun onEvent(
                    event: Event,
                    isLive: Boolean,
                    relay: NormalizedRelayUrl,
                    forFilters: List<Filter>?,
                ) {
                    // strfry-router parity: never take the upstream's
                    // word for what matched. Re-checking the configured
                    // scope here means even a trusted (skip-verify)
                    // upstream can only inject events the operator
                    // declared — the REQ shapes what we ask for, this
                    // shapes what we accept.
                    if (up.filter != null && !up.filter.match(event)) {
                        filtered.incrementAndGet()
                        Log.d("MirrorWorker") { "out-of-scope from ${relay.url}: ${event.id}" }
                        return
                    }
                    // BOTH: an event we just pushed up is fanned back on
                    // this subscription — it already exists locally.
                    if (exchanged?.contains(event.id) == true) return
                    exchanged?.add(event.id)
                    inbound.trySend(Inbound(event, up.trusted))
                }

                override fun onCannotConnect(
                    relay: NormalizedRelayUrl,
                    message: String,
                    forFilters: List<Filter>?,
                ) {
                    Log.w("MirrorWorker") { "cannot reach upstream ${relay.url}: $message" }
                }
            }
        client.subscribe(
            subId = "geode-mirror-$index",
            filters = mapOf(up.url to listOf(scopedFilter)),
            listener = listener,
        )
    }

    /**
     * Up direction: an in-process session on the LOCAL relay subscribes
     * with the same scoped filter — stored replay covers the backfill
     * window, the live tail covers everything after — and each matching
     * event is handed to the client's outbox for [MirrorUpstream.url].
     * The outbox owns delivery: it re-sends on reconnect until the
     * upstream OKs, and the upstream's own duplicate handling absorbs
     * replays. Going through a real session (not the store) means the
     * relay's policy chain gates what leaves, same as any client.
     */
    private fun startUp(
        up: MirrorUpstream,
        scopedFilter: Filter,
        exchanged: RecentIds?,
    ) {
        val session =
            server.connect { json ->
                if (!json.startsWith("[\"EVENT\"")) return@connect
                val event =
                    runCatching { (OptimizedJsonMapper.fromJsonToMessage(json) as? EventMessage)?.event }
                        .getOrNull() ?: return@connect
                // BOTH: don't push back what we just pulled down.
                if (exchanged?.contains(event.id) == true) return@connect
                exchanged?.add(event.id)
                client.publish(event, setOf(up.url))
                sentUp.incrementAndGet()
            }
        upSessions += AutoCloseable { session.close() }
        scope.launch {
            session.receive(OptimizedJsonMapper.toJson(ReqCmd("geode-mirror-up", listOf(scopedFilter))))
        }
    }

    /**
     * Stops pulling from every upstream. In-flight ingest submissions
     * drain through the server's queue; events still buffered in
     * [inbound] are dropped — the next boot's `since` overlaps only if
     * the operator configured a backfill window, which is the documented
     * trade-off of a live mirror.
     */
    override fun close() {
        // Close the client first so no listener callback races the
        // channel close below.
        upSessions.forEach { runCatching { it.close() } }
        runCatching { client.close() }
        inbound.close()
        scope.cancel()
        okhttp?.dispatcher?.executorService?.shutdown()
        okhttp?.connectionPool?.evictAll()
    }

    private companion object {
        /** Matches the Android app's relay-pool WebSocket ping interval. */
        const val PING_INTERVAL_SECS = 120L

        /** How often the retry pump nudges disconnected upstreams. */
        const val RECONNECT_POKE_MS = 5_000L

        /**
         * Per-upstream echo-suppression LRU size (BOTH direction only).
         * Covers the burst window between pulling an event down and the
         * up-session seeing its local fanout; eviction only costs a
         * duplicate round trip.
         */
        const val EXCHANGED_IDS_CAPACITY = 8_192
    }
}
