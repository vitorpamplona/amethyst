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
package com.vitorpamplona.quic.connection

import com.vitorpamplona.quic.tls.InProcessTlsServer
import com.vitorpamplona.quic.tls.PermissiveCertificateValidator
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Throughput contract for the lock-split refactor (2026-05-08): opening
 * many parallel bidi streams + queueing requests must not be serialised
 * by a single connection-wide mutex. Phase 1 of the split (separate
 * `streamsLock` / `lifecycleLock` / per-level `levelLock`) targets the
 * multiplexing testcase that drove this refactor — see
 * `quic/plans/2026-05-08-lock-split-design.md`.
 *
 * The test stands up an in-memory client (no socket I/O), opens 1000
 * client-bidi streams concurrently, enqueues a small request body + FIN
 * on each, and asserts the operation completes within a generous wall-
 * clock budget. The number is deliberately loose: this is a contract
 * for "lock contention isn't pathological", not a microbenchmark.
 *
 * NOTE: the in-memory pipe doesn't drive a concurrent send loop, so
 * this test exercises the lock-acquisition cost of `openBidiStream`
 * itself rather than full multiplexing throughput. The interop runner
 * provides the end-to-end measurement.
 */
class MultiplexingThroughputTest {
    @Test
    fun open_1000_bidi_streams_completes_quickly() {
        runBlocking {
            val client =
                QuicConnection(
                    serverName = "example.test",
                    config =
                        QuicConnectionConfig(
                            initialMaxStreamsBidi = 2_000,
                            initialMaxStreamsUni = 2_000,
                            initialMaxData = 100_000_000,
                            initialMaxStreamDataBidiLocal = 100_000,
                            initialMaxStreamDataBidiRemote = 100_000,
                            initialMaxStreamDataUni = 100_000,
                        ),
                    tlsCertificateValidator = PermissiveCertificateValidator(),
                )
            val serverScid = ConnectionId.random(8)
            val tlsServer =
                InProcessTlsServer(
                    transportParameters =
                        TransportParameters(
                            initialMaxData = 100_000_000,
                            initialMaxStreamDataBidiLocal = 100_000,
                            initialMaxStreamDataBidiRemote = 100_000,
                            initialMaxStreamDataUni = 100_000,
                            initialMaxStreamsBidi = 2_000,
                            initialMaxStreamsUni = 2_000,
                            initialSourceConnectionId = serverScid.bytes,
                            originalDestinationConnectionId = client.destinationConnectionId.bytes,
                        ).encode(),
                )
            val pipe =
                InMemoryQuicPipe(
                    client = client,
                    initialDcid = client.destinationConnectionId.bytes,
                    serverScid = serverScid,
                    tlsServer = tlsServer,
                )
            client.start()
            pipe.drive(maxRounds = 16)
            assertEquals(QuicConnection.Status.CONNECTED, client.status)

            val request = ByteArray(50) { it.toByte() }
            val streamCount = 1_000

            // Open all streams in parallel — each launch contends for
            // streamsLock briefly. Pre-refactor this serialised against
            // any in-flight drainOutbound call; phase 1 keeps openBidi
            // contention scoped to streamsLock-only.
            val started =
                kotlin.time.TimeSource.Monotonic
                    .markNow()
            val opens =
                (0 until streamCount).map {
                    async {
                        val stream = client.openBidiStream()
                        stream.send.enqueue(request)
                        stream.send.finish()
                        stream.streamId
                    }
                }
            val ids = opens.awaitAll()
            val elapsed = started.elapsedNow()

            // Useful diagnostic for measuring future regressions: stdout
            // shows up in the test report so phase-1 vs phase-2 can be
            // compared against the same test.
            println(
                "[MultiplexingThroughputTest] opened $streamCount bidi streams in " +
                    "${elapsed.inWholeMilliseconds}ms " +
                    "(${(streamCount * 1000.0 / elapsed.inWholeMilliseconds.coerceAtLeast(1L)).toLong()} streams/sec)",
            )
            assertEquals(streamCount, ids.size)
            assertEquals(streamCount, ids.toSet().size, "stream ids must be unique")
            // Generous bound; in-process opens of 1000 streams should
            // complete in well under half a second on any developer
            // machine — pre-refactor this was minutes due to lock
            // contention against the (idle) send-loop drain. The looser
            // 2-second bound is still 100x what's expected on actual
            // hardware while accounting for slow CI workers.
            assertTrue(
                elapsed.inWholeMilliseconds < 2_000L,
                "1000 parallel openBidiStream calls took ${elapsed.inWholeMilliseconds}ms; expected <2000ms",
            )
        }
    }
}
