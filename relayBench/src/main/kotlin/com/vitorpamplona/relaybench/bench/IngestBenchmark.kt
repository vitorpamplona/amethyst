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
package com.vitorpamplona.relaybench.bench

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.OptimizedJsonMapper
import com.vitorpamplona.relaybench.NostrSocket
import com.vitorpamplona.relaybench.Percentiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import java.util.concurrent.atomic.AtomicInteger

/**
 * Write-path metrics.
 *
 * **Visibility latency** answers the exact question "how long from the
 * relay *receiving* an EVENT until a REQ can return it": one connection
 * publishes, and from the same send-instant a second connection
 * hammer-polls `REQ {ids:[id]}` until the event comes back. The polled
 * REQ round-trip on loopback (~sub-ms) bounds the probe's resolution.
 * The OK-ack latency and whether the event was readable no later than
 * its OK (i.e. does the ack imply queryable — NIP-20 semantics) are
 * recorded alongside.
 *
 * **Throughput** replays the corpus over N connections with a bounded
 * number of unacked EVENTs in flight per connection, timing wall clock
 * from first send to last OK.
 */
object IngestBenchmark {
    private val mapper = jacksonObjectMapper()

    data class VisibilityResult(
        val okLatency: Percentiles,
        val visibleLatency: Percentiles,
        val visibleByOkTime: Double,
        val samples: Int,
        val rejected: Int,
    )

    data class ThroughputResult(
        val events: Int,
        val accepted: Int,
        val rejected: Int,
        val wallMs: Double,
        val eventsPerSec: Double,
        val connections: Int,
    )

    /** (okNanos, visibleNanos, visibleBeforeOk) for one accepted event, or null if rejected. */
    private class Sample(
        val okNanos: Long,
        val visibleNanos: Long,
        val visibleByOk: Boolean,
    )

    suspend fun visibility(
        wsUrl: String,
        samples: List<Event>,
        http: OkHttpClient,
        log: (String) -> Unit,
    ): VisibilityResult {
        val publisher = NostrSocket.connect(http, wsUrl)
        val prober = NostrSocket.connect(http, wsUrl)
        val okNanos = ArrayList<Long>(samples.size)
        val visibleNanos = ArrayList<Long>(samples.size)
        var byOkHits = 0
        var rejected = 0
        var timeouts = 0

        try {
            for ((i, event) in samples.withIndex()) {
                if (timeouts >= 5) {
                    log("    ! aborting visibility probe after $timeouts timeouts")
                    break
                }
                val sample =
                    try {
                        probeOne(publisher, prober, event, i)
                    } catch (_: TimeoutCancellationException) {
                        timeouts++
                        continue
                    }
                if (sample == null) {
                    rejected++
                } else {
                    okNanos += sample.okNanos
                    visibleNanos += sample.visibleNanos
                    if (sample.visibleByOk) byOkHits++
                }
            }
        } finally {
            publisher.disconnect()
            prober.disconnect()
        }
        if (rejected > 0) log("    ! $rejected visibility samples were rejected by the relay")
        return VisibilityResult(
            okLatency = Percentiles.ofNanos(okNanos),
            visibleLatency = Percentiles.ofNanos(visibleNanos),
            visibleByOkTime = if (visibleNanos.isEmpty()) 0.0 else byOkHits.toDouble() / visibleNanos.size,
            samples = visibleNanos.size,
            rejected = rejected,
        )
    }

    private suspend fun probeOne(
        publisher: NostrSocket,
        prober: NostrSocket,
        event: Event,
        index: Int,
    ): Sample? =
        withTimeout(30_000) {
            coroutineScope {
                val start = System.nanoTime()

                val okAt =
                    async {
                        for (raw in publisher.incoming) {
                            val node = runCatching { mapper.readTree(raw) }.getOrNull() ?: continue
                            if (node[0]?.asText() == "OK" && node[1]?.asText() == event.id) {
                                return@async Pair(System.nanoTime(), node[2]?.asBoolean() == true)
                            }
                        }
                        Pair(0L, false)
                    }

                val visibleAt =
                    async {
                        var polls = 0
                        while (true) {
                            val subId = "v$index-${polls++}"
                            prober.req(subId, """{"ids":["${event.id}"]}""")
                            var foundAt = 0L
                            for (raw in prober.incoming) {
                                val node = runCatching { mapper.readTree(raw) }.getOrNull() ?: continue
                                if (node[1]?.asText() != subId) continue
                                when (node[0]?.asText()) {
                                    "EVENT" -> foundAt = System.nanoTime()
                                    "EOSE", "CLOSED" -> {
                                        prober.close(subId)
                                        if (foundAt != 0L) return@async foundAt
                                        break
                                    }
                                }
                            }
                        }
                        @Suppress("UNREACHABLE_CODE")
                        0L
                    }

                check(publisher.publish(OptimizedJsonMapper.toJson(event))) { "publish failed" }

                val (okTime, accepted) = okAt.await()
                if (!accepted) {
                    visibleAt.cancelAndJoin()
                    return@coroutineScope null
                }
                val visibleTime = visibleAt.await()
                Sample(okTime - start, visibleTime - start, visibleTime <= okTime)
            }
        }

    suspend fun throughput(
        wsUrl: String,
        events: List<Event>,
        connections: Int,
        window: Int,
        http: OkHttpClient,
    ): ThroughputResult {
        val accepted = AtomicInteger()
        val rejected = AtomicInteger()
        val slices = List(connections) { c -> events.filterIndexed { i, _ -> i % connections == c } }

        val start = System.nanoTime()
        withContext(Dispatchers.IO) {
            slices
                .filter { it.isNotEmpty() }
                .map { slice -> async { publishSlice(wsUrl, slice, window, http, accepted, rejected) } }
                .awaitAll()
        }
        val wallMs = (System.nanoTime() - start) / 1_000_000.0
        return ThroughputResult(
            events = events.size,
            accepted = accepted.get(),
            rejected = rejected.get(),
            wallMs = wallMs,
            eventsPerSec = events.size / (wallMs / 1000.0),
            connections = connections,
        )
    }

    private suspend fun publishSlice(
        wsUrl: String,
        slice: List<Event>,
        window: Int,
        http: OkHttpClient,
        accepted: AtomicInteger,
        rejected: AtomicInteger,
    ) {
        val socket = NostrSocket.connect(http, wsUrl)
        try {
            coroutineScope {
                val inFlight = Semaphore(window)
                val reader =
                    launch {
                        var acked = 0
                        for (raw in socket.incoming) {
                            // Fast-path: only OK frames matter here.
                            if (!raw.startsWith("[\"OK\"")) continue
                            val node = runCatching { mapper.readTree(raw) }.getOrNull() ?: continue
                            if (node[2]?.asBoolean() == true) accepted.incrementAndGet() else rejected.incrementAndGet()
                            inFlight.release()
                            if (++acked == slice.size) break
                        }
                    }
                for (event in slice) {
                    inFlight.acquire()
                    check(socket.publish(OptimizedJsonMapper.toJson(event))) { "publish failed (socket closed?)" }
                }
                reader.join()
            }
        } finally {
            socket.disconnect()
        }
    }
}
