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
package com.vitorpamplona.quartz.nip01Core.relay.client.accessories

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.crypto.verify
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * Client-side batched Schnorr verification, off the relay receiver coroutine.
 *
 * Today the app verifies every new event INLINE on the per-relay consumer
 * coroutine (`CacheClientConnector` → `LocalCache.justConsume` →
 * `justVerify`): at ~75µs a verify (JVM; several hundred µs on phone cores)
 * a burst of a few thousand events serializes seconds of CPU behind that
 * relay's EOSEs, OKs and live events. Verification is embarrassingly
 * parallel — the relay-server side already exploits that
 * ([com.vitorpamplona.quartz.nip01Core.relay.server.backend.IngestQueue]'s
 * `parallelVerify`) — this is the same batching strategy for the client:
 *
 *  - [submit] is cheap (a channel send) and returns immediately, freeing the
 *    receiver coroutine for the next frame;
 *  - one drain coroutine pulls a batch greedily (up to [maxBatch]), fans the
 *    batch's verifies across [Dispatchers.Default], then dispatches
 *    [onVerified]/[onInvalid] in submission order (batching the fan-out
 *    beats per-event handoff: a per-event channel send measured ~180ns of
 *    overhead each, a 64-batch is ~free — see DispatchStageBenchmark);
 *  - [preVerified] lets the caller short-circuit events it already trusts
 *    (LocalCache-style dedup: an id already consumed doesn't need another
 *    verify) without paying the CPU;
 *  - the submit channel is bounded: a verify backlog beyond [capacity]
 *    blocks the submitting receiver coroutine, which backpressures the
 *    socket instead of growing the heap.
 *
 * Typical wiring (replacing an inline-verify EventCollector):
 * ```
 * val verifier = ParallelEventVerifier(scope, onVerified = { event, relay ->
 *     cache.justConsume(event, relay, wasVerified = true)
 * })
 * val collector = EventCollector(client) { event, relay ->
 *     if (!cache.hasConsumed(event.id)) verifier.submit(event, relay)
 * }
 * ```
 *
 * Ordering note: submission order is preserved END-TO-END for callbacks (the
 * drain loop dispatches each batch in order, batches are sequential), so
 * per-relay, per-subscription arrival order survives the parallel stage.
 */
class ParallelEventVerifier<C>(
    scope: CoroutineScope,
    private val maxBatch: Int = DEFAULT_MAX_BATCH,
    capacity: Int = DEFAULT_CAPACITY,
    private val parallelism: Int = DEFAULT_PARALLELISM,
    private val preVerified: (Event) -> Boolean = { false },
    private val onInvalid: ((Event, C) -> Unit)? = null,
    private val onVerified: (Event, C) -> Unit,
) : AutoCloseable {
    private class Pending<C>(
        val event: Event,
        val context: C,
    )

    private val incoming = Channel<Pending<C>>(capacity)

    var verifiedCount: Long = 0
        private set
    var invalidCount: Long = 0
        private set

    private val drainJob =
        scope.launch(Dispatchers.Default) {
            val batch = ArrayList<Pending<C>>(maxBatch)
            try {
                while (true) {
                    // Block for the first item, then drain greedily so
                    // back-to-back submissions coalesce into one parallel batch.
                    batch.add(incoming.receive())
                    while (batch.size < maxBatch) {
                        val next = incoming.tryReceive().getOrNull() ?: break
                        batch.add(next)
                    }
                    processBatch(batch)
                    batch.clear()
                }
            } catch (_: ClosedReceiveChannelException) {
                // normal shutdown via close()
            }
        }

    /**
     * Hands one event to the verify stage. Returns fast (a channel send);
     * blocks only when the verify backlog exceeds the capacity — that is the
     * backpressure path, by design.
     */
    fun submit(
        event: Event,
        context: C,
    ) {
        incoming.trySendBlocking(Pending(event, context))
    }

    private suspend fun processBatch(batch: List<Pending<C>>) {
        val results = BooleanArray(batch.size)
        if (batch.size == 1) {
            results[0] = verifyOne(batch[0].event)
        } else {
            // One worker per core-sized CHUNK, not per event: a per-event
            // async measured ~40µs/event end-to-end (scheduling overhead
            // swallowing the parallel gain) vs near-linear scaling with
            // chunked workers — same pattern as the offline verify benchmark.
            coroutineScope {
                val workers = minOf(parallelism, batch.size)
                val chunkSize = (batch.size + workers - 1) / workers
                (0 until workers)
                    .map { w ->
                        async(Dispatchers.Default) {
                            var i = w * chunkSize
                            val end = minOf(i + chunkSize, batch.size)
                            while (i < end) {
                                results[i] = verifyOne(batch[i].event)
                                i++
                            }
                        }
                    }.awaitAll()
            }
        }

        // Dispatch in submission order, outside the parallel section.
        for (i in batch.indices) {
            val pending = batch[i]
            try {
                if (results[i]) {
                    verifiedCount++
                    onVerified(pending.event, pending.context)
                } else {
                    invalidCount++
                    onInvalid?.invoke(pending.event, pending.context)
                }
            } catch (e: Throwable) {
                // a misbehaving callback must not kill the drain loop
                Log.w("ParallelEventVerifier") { "callback threw for ${pending.event.id}: ${e.message}" }
            }
        }
    }

    private fun verifyOne(event: Event): Boolean = preVerified(event) || event.verify()

    /**
     * Stops accepting submissions; the drain loop finishes the batches already
     * queued and ends. Cancelling the [CoroutineScope] passed to the
     * constructor stops everything immediately instead.
     */
    override fun close() {
        incoming.close()
    }

    /** Completed once the drain loop exits (all queued batches dispatched). */
    suspend fun join() = drainJob.join()

    companion object {
        /**
         * Measured sweet spot (see ParallelVerifyBenchmark + the plan doc):
         * the per-batch fork/join barrier costs real throughput at small
         * batches (64 → ~1.2× total speedup; 256 → ~2.2×; 1024 → ~3.2× on 4
         * cores) while batch size bounds callback latency (256 verifies ≈
         * 3ms on a JVM box, ~10ms on a phone). Note the batch only grows
         * when a backlog exists — a light trickle takes the single-event
         * fast path with minimal latency — so this cap matters exactly when
         * throughput matters.
         */
        const val DEFAULT_MAX_BATCH: Int = 256

        /** Submissions in flight before [submit] blocks the caller. */
        const val DEFAULT_CAPACITY: Int = 8192

        /**
         * Verify workers per batch. Dispatchers.Default caps actual CPU
         * parallelism at the core count anyway; workers beyond it just queue,
         * so a fixed value covering common phone/desktop core counts is fine
         * (commonMain has no portable core-count API). Callers that know
         * their hardware can pass an exact value.
         */
        const val DEFAULT_PARALLELISM: Int = 8
    }
}
