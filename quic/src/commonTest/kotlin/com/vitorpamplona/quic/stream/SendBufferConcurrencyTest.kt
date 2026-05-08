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
package com.vitorpamplona.quic.stream

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Concurrent producer/consumer regression tests for [SendBuffer].
 *
 * The original suite was entirely single-threaded: every test enqueued
 * and then took chunks on the same coroutine, so torn state across
 * threads stayed invisible.
 *
 * In production [SendBuffer] is hit by two distinct paths:
 *   - the application's writer coroutine calling [SendBuffer.enqueue]
 *     (e.g. via `WtPeerStreamDemux`'s per-stream send callback);
 *   - the [com.vitorpamplona.quic.connection.QuicConnectionDriver] send loop calling
 *     [SendBuffer.takeChunk] under the connection mutex, which is
 *     NOT held by the producer.
 *
 * Without internal synchronisation the writer would see
 * `pendingBytes > 0` (set by an in-flight `enqueue`) before the
 * matching `chunks.addLast` became visible, fall into the
 * head-peel branch, and crash with
 * `NoSuchElementException: ArrayDeque is empty.` from
 * `chunks.first()`. These tests reliably reproduce that scenario on
 * the unsynchronised version and validate ordering / accounting on
 * the fixed one.
 */
class SendBufferConcurrencyTest {
    @Test
    fun concurrent_enqueue_and_takeChunk_does_not_throw() =
        runBlocking {
            // Multi-thread dispatcher is load-bearing — runTest's virtual
            // scheduler is single-threaded and would never expose the race.
            withContext(Dispatchers.Default) {
                repeat(REPEATS) {
                    val buf = SendBuffer()
                    coroutineScope {
                        // Producers race against a consumer that drains as
                        // fast as it can.
                        val producers =
                            List(PRODUCERS) {
                                async {
                                    repeat(WRITES_PER_PRODUCER) {
                                        buf.enqueue(byteArrayOf(0x42))
                                    }
                                }
                            }
                        val consumer =
                            async {
                                var taken = 0
                                while (taken < PRODUCERS * WRITES_PER_PRODUCER) {
                                    val chunk = buf.takeChunk(maxBytes = MAX_BYTES) ?: continue
                                    taken += chunk.data.size
                                }
                                taken
                            }
                        producers.awaitAll()
                        // Drain whatever the consumer didn't pick up before
                        // producers finished. Without internal synchronisation
                        // either side could throw before reaching this.
                        consumer.await()
                    }
                    assertEquals(0, buf.readableBytes, "all bytes should be drained at the end of round")
                    assertEquals((PRODUCERS * WRITES_PER_PRODUCER).toLong(), buf.sentOffset)
                }
            }
        }

    @Test
    fun concurrent_takeChunk_callers_never_double_drain_a_chunk() =
        runBlocking {
            withContext(Dispatchers.Default) {
                repeat(REPEATS) {
                    val buf = SendBuffer()
                    repeat(PRODUCERS * WRITES_PER_PRODUCER) {
                        buf.enqueue(byteArrayOf(0x55))
                    }
                    val expectedTotal = PRODUCERS * WRITES_PER_PRODUCER
                    coroutineScope {
                        // Multiple consumers racing for the same buffer mirrors
                        // the (unlikely but possible) case of overlapping driver
                        // ticks; the byte total must stay exact.
                        val takers =
                            List(CONSUMERS) {
                                async {
                                    var localTaken = 0
                                    while (true) {
                                        val chunk = buf.takeChunk(maxBytes = MAX_BYTES) ?: break
                                        localTaken += chunk.data.size
                                    }
                                    localTaken
                                }
                            }
                        val totalTaken = takers.awaitAll().sum()
                        assertEquals(expectedTotal, totalTaken, "each byte must be handed out to exactly one consumer")
                        assertTrue(buf.readableBytes == 0, "buffer must end empty after all consumers stop")
                    }
                }
            }
        }

    @Test
    fun concurrent_finish_with_inflight_enqueue_emits_correct_fin() =
        runBlocking {
            withContext(Dispatchers.Default) {
                repeat(REPEATS) {
                    val buf = SendBuffer()
                    coroutineScope {
                        val producer =
                            async {
                                repeat(WRITES_PER_PRODUCER) {
                                    buf.enqueue(byteArrayOf(0x77))
                                }
                                buf.finish()
                            }
                        val consumer =
                            async {
                                var sawFin = false
                                var bytes = 0
                                while (!sawFin) {
                                    val chunk = buf.takeChunk(maxBytes = MAX_BYTES) ?: continue
                                    bytes += chunk.data.size
                                    if (chunk.fin) sawFin = true
                                }
                                bytes
                            }
                        producer.await()
                        val drained = consumer.await()
                        assertEquals(WRITES_PER_PRODUCER, drained, "FIN must come AFTER every enqueued byte")
                        assertTrue(buf.finSent)
                    }
                }
            }
        }

    private companion object {
        // Tuned to reliably trigger the original race on a multi-core
        // host (~6 ms per round on 8 cores) without ballooning CI time.
        const val REPEATS = 50
        const val PRODUCERS = 4
        const val CONSUMERS = 4
        const val WRITES_PER_PRODUCER = 200
        const val MAX_BYTES = 64
    }
}
