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
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.SubscriptionListener
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.server.NostrServer
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebsocketBuilder
import com.vitorpamplona.quartz.nip01Core.relay.sockets.okhttp.BasicOkHttpWebSocket
import com.vitorpamplona.quartz.nip01Core.store.IEventStore
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.concurrent.atomic.AtomicLong

/**
 * One upstream relay this relay mirrors, from the `[[mirror]]` config.
 *
 * [trusted] is the relay-to-relay trust switch: events streamed from this
 * upstream skip Schnorr signature verification on ingest. The trusted
 * identity is [url] — the address *this* relay dialed (TLS-authenticated
 * for `wss://`), never anything the peer claims — so the skip can't be
 * hijacked by an inbound client.
 */
class MirrorUpstream(
    val url: NormalizedRelayUrl,
    val trusted: Boolean,
    /** How far back the initial REQ reaches. 0 = live-only from connect. */
    val backfillSeconds: Long = 0L,
    /**
     * Optional scope for this upstream (strfry-router's per-stream
     * `filter`). Used twice: it shapes the REQ sent upstream, and every
     * delivered event is re-checked against it before ingest — so even a
     * [trusted] upstream can only inject events inside the declared
     * scope. Its `since`/`limit` are ignored ([backfillSeconds] owns the
     * time window; the subscription is unbounded). `null` mirrors
     * everything.
     */
    val filter: Filter? = null,
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

    private val okhttp: OkHttpClient? = if (websocketBuilder == null) OkHttpClient.Builder().build() else null

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
            // The operator's filter scopes the REQ; the mirror owns the
            // time window (since) and never bounds the result (limit).
            val reqFilter =
                (up.filter ?: Filter()).copy(
                    since = since - up.backfillSeconds,
                    limit = null,
                )
            client.subscribe(
                subId = "geode-mirror-$i",
                filters = mapOf(up.url to listOf(reqFilter)),
                listener = listener,
            )
        }
        client.connect()
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
        runCatching { client.close() }
        inbound.close()
        scope.cancel()
        okhttp?.dispatcher?.executorService?.shutdown()
        okhttp?.connectionPool?.evictAll()
    }
}
