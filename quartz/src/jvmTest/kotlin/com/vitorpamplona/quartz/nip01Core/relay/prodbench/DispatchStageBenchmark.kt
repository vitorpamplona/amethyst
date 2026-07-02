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
package com.vitorpamplona.quartz.nip01Core.relay.prodbench

import com.vitorpamplona.geode.fixtures.SyntheticEvents
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.EventCollector
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.PoolRequests
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.SubscriptionListener
import com.vitorpamplona.quartz.nip01Core.relay.client.single.IRelayClient
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.EventMessage
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.normalizeRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebSocket
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebSocketListener
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebsocketBuilder
import kotlinx.coroutines.channels.Channel
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.Test

/**
 * Microbenchmark for the receiver's DISPATCH stage: everything that happens to
 * a message AFTER the JSON parse and BEFORE signature verification —
 *
 *   NostrClient.onIncomingMessage
 *     ├─ PoolRequests.onIncomingMessage   (spin lock + sub state machine + sub listener)
 *     ├─ PoolCounts / PoolEventOutbox     (no-ops for EVENT frames)
 *     └─ connection listeners             (EventCollector → dedup → handoff to verify)
 *
 * The goal is to find the arrangement of this stage that maximizes events/s,
 * since this is the code that decides how fast frames leave the per-relay
 * receiver coroutine once verification is moved off it. Offline and synthetic
 * on purpose: this stage's cost is pure CPU + locks and doesn't depend on
 * relay behavior. Duplicate factors are modeled on what the production runs
 * measured (each event delivered on S overlapping subs and again by F relays;
 * see ProductionReceiverBenchmark: 14–57% duplicates).
 *
 * Variants:
 *  - dispatch-only          floor: full NostrClient dispatch, no-op collector
 *  - dedup                  + ConcurrentHashMap id dedup (the verify gatekeeper)
 *  - dedup+chan             + per-event Channel handoff for unique events
 *  - dedup+batch64          + batched handoff (64/batch, IngestQueue-style)
 *  - early-dedup+batch64    dedup BEFORE NostrClient dispatch: duplicate frames
 *                           skip the whole dispatch machinery (models checking
 *                           seen-ids right after parse)
 *
 * Each variant runs with 1 feeder thread (one busy relay) and with
 * min(4, cores) feeder threads (several relays bursting at once, which is what
 * exposes the PoolRequests spin lock). A PoolRequests-only pass attributes how
 * much of the stage is the subscription state machine itself.
 *
 * Run with: ./gradlew :quartz:jvmTest --tests "*.DispatchStageBenchmark"
 */
class DispatchStageBenchmark {
    companion object {
        const val UNIQUE_EVENTS = 30_000
        const val SUBS_PER_RELAY = 2 // overlapping subs: same event delivered once per sub
        val MULTI_FEEDERS = minOf(4, Runtime.getRuntime().availableProcessors())
        const val BATCH_SIZE = 64
    }

    // ------------------------------------------------------------------
    // Fakes: a socket that swallows everything, so NostrClient/PoolRequests
    // run their real logic with no network and no reconnect churn.
    // ------------------------------------------------------------------

    class NoopWebSocket : WebSocket {
        override fun needsReconnect() = false

        override fun connect() {}

        override fun disconnect() {}

        override fun send(msg: String) = true
    }

    class NoopBuilder : WebsocketBuilder {
        override fun build(
            url: NormalizedRelayUrl,
            out: WebSocketListener,
        ): WebSocket = NoopWebSocket()
    }

    private val noopSubListener = object : SubscriptionListener {}

    private val events: List<Event> by lazy {
        (1..UNIQUE_EVENTS).map { SyntheticEvents.fakeEvent(idSeed = it, kind = 1, pubKey = SyntheticEvents.hexId(it % 500 + 1)) }
    }

    // ------------------------------------------------------------------
    // Harness
    // ------------------------------------------------------------------

    class Handoff(
        val batchSize: Int?,
    ) {
        val channel: Channel<List<Event>> = Channel(Channel.UNLIMITED)
        val handedOff = AtomicLong(0)

        // one batch buffer per feeder thread; flushed when full
        private val buffer = ThreadLocal.withInitial { ArrayList<Event>(batchSize ?: 1) }

        fun offer(event: Event) {
            if (batchSize == null) {
                channel.trySend(listOf(event))
                handedOff.incrementAndGet()
            } else {
                val buf = buffer.get()
                buf.add(event)
                if (buf.size >= batchSize) {
                    channel.trySend(ArrayList(buf))
                    handedOff.addAndGet(buf.size.toLong())
                    buf.clear()
                }
            }
        }
    }

    class VariantResult(
        val name: String,
        val feeders: Int,
        val deliveries: Long,
        val uniques: Long,
        val wallNanos: Long,
    ) {
        override fun toString(): String =
            "  %-24s feeders=%d  %,10.0f deliveries/s  %,10.0f uniques/s  wall=%.1fms".format(
                name,
                feeders,
                deliveries * 1e9 / wallNanos,
                uniques * 1e9 / wallNanos,
                wallNanos / 1e6,
            )
    }

    /**
     * Runs one variant: [feeders] threads, each acting as one relay's consumer
     * coroutine, delivering every event on [SUBS_PER_RELAY] subs through the
     * real NostrClient dispatch path.
     */
    private fun runVariant(
        name: String,
        feeders: Int,
        earlyDedup: Boolean,
        sink: (Event, Handoff?, MutableSet<String>?) -> Unit,
        handoffBatch: Int? = null,
        useDedup: Boolean = false,
    ): VariantResult {
        val client = NostrClient(NoopBuilder())
        try {
            val relayUrls = (1..feeders).map { "ws://bench-relay-$it.local".normalizeRelayUrl() }
            val subIds = (1..SUBS_PER_RELAY).map { "bench-sub-$it" }

            // register subs on every relay so PoolRequests has real state to update
            subIds.forEach { subId ->
                client.subscribe(subId, relayUrls.associateWith { listOf(Filter(kinds = listOf(1))) }, noopSubListener)
            }

            val seen: MutableSet<String>? = if (useDedup || earlyDedup) ConcurrentHashMap.newKeySet(UNIQUE_EVENTS * 2) else null
            val handoff = handoffBatch?.let { Handoff(if (it == 0) null else it) }

            val collector =
                EventCollector(client) { event, _ ->
                    sink(event, handoff, seen)
                }

            val relayClients = relayUrls.map { client.getOrCreateRelay(it) }

            val threads =
                relayClients.map { relayClient ->
                    Thread {
                        for (event in events) {
                            if (earlyDedup && !seen!!.add(event.id)) {
                                // duplicate frame: skip the whole dispatch stage,
                                // exactly what a post-parse id check would do
                                continue
                            }
                            for (subId in subIds) {
                                client.onIncomingMessage(relayClient, "", EventMessage(subId, event))
                            }
                        }
                    }
                }

            val start = System.nanoTime()
            threads.forEach { it.start() }
            threads.forEach { it.join() }
            val wall = System.nanoTime() - start

            handoff?.channel?.close()

            // every feeder sees every event on every sub; early-dedup "skips"
            // still count as frames the receiver got past (that's the point)
            val deliveries = feeders.toLong() * UNIQUE_EVENTS * SUBS_PER_RELAY
            val uniqueCount = seen?.size?.toLong() ?: UNIQUE_EVENTS.toLong()
            return VariantResult(name, feeders, deliveries, uniqueCount, wall).also {
                collector.destroy()
            }
        } finally {
            client.close()
        }
    }

    // sinks -------------------------------------------------------------

    private val sinkNoop: (Event, Handoff?, MutableSet<String>?) -> Unit = { _, _, _ -> }

    private val sinkDedup: (Event, Handoff?, MutableSet<String>?) -> Unit = { event, _, seen ->
        seen!!.add(event.id)
    }

    private val sinkDedupHandoff: (Event, Handoff?, MutableSet<String>?) -> Unit = { event, handoff, seen ->
        if (seen!!.add(event.id)) handoff!!.offer(event)
    }

    private val sinkHandoffOnly: (Event, Handoff?, MutableSet<String>?) -> Unit = { event, handoff, _ ->
        handoff!!.offer(event)
    }

    // ------------------------------------------------------------------
    // PoolRequests in isolation: attributes the sub-state-machine + spin-lock
    // share of the stage.
    // ------------------------------------------------------------------

    private fun runPoolRequestsOnly(feeders: Int): VariantResult {
        val pool = PoolRequests()
        val client = NostrClient(NoopBuilder())
        try {
            val relayUrls = (1..feeders).map { "ws://bench-relay-$it.local".normalizeRelayUrl() }
            val subIds = (1..SUBS_PER_RELAY).map { "bench-sub-$it" }
            subIds.forEach { subId ->
                pool.addOrUpdate(subId, relayUrls.associateWith { listOf(Filter(kinds = listOf(1))) }, noopSubListener)
            }
            val relayClients: List<IRelayClient> = relayUrls.map { client.getOrCreateRelay(it) }

            val threads =
                relayClients.map { relayClient ->
                    Thread {
                        for (event in events) {
                            for (subId in subIds) {
                                pool.onIncomingMessage(relayClient, EventMessage(subId, event))
                            }
                        }
                    }
                }

            val start = System.nanoTime()
            threads.forEach { it.start() }
            threads.forEach { it.join() }
            val wall = System.nanoTime() - start

            val deliveries = feeders.toLong() * UNIQUE_EVENTS * SUBS_PER_RELAY
            return VariantResult("PoolRequests-only", feeders, deliveries, UNIQUE_EVENTS.toLong(), wall)
        } finally {
            client.close()
            pool.destroy()
        }
    }

    // ------------------------------------------------------------------

    private fun runAllVariants(print: Boolean) {
        for (feeders in listOf(1, MULTI_FEEDERS)) {
            val results =
                listOf(
                    runPoolRequestsOnly(feeders),
                    runVariant("dispatch-only", feeders, earlyDedup = false, sink = sinkNoop),
                    runVariant("dedup", feeders, earlyDedup = false, sink = sinkDedup, useDedup = true),
                    runVariant("dedup+chan", feeders, earlyDedup = false, sink = sinkDedupHandoff, handoffBatch = 0, useDedup = true),
                    runVariant("dedup+batch$BATCH_SIZE", feeders, earlyDedup = false, sink = sinkDedupHandoff, handoffBatch = BATCH_SIZE, useDedup = true),
                    runVariant("early-dedup+batch$BATCH_SIZE", feeders, earlyDedup = true, sink = sinkHandoffOnly, handoffBatch = BATCH_SIZE),
                )
            if (print) {
                println("\n--- feeders=$feeders (each delivering ${UNIQUE_EVENTS} events x $SUBS_PER_RELAY subs) ---")
                results.forEach { println(it) }
            }
        }
    }

    @Test
    fun dispatchStageBenchmark() {
        println("=== DISPATCH STAGE BENCHMARK (post-parse, pre-verify) ===")
        println("cores=${Runtime.getRuntime().availableProcessors()} uniqueEvents=$UNIQUE_EVENTS subsPerRelay=$SUBS_PER_RELAY")

        // warmup pass (JIT), then the measured pass
        runAllVariants(print = false)
        runAllVariants(print = true)
    }
}
