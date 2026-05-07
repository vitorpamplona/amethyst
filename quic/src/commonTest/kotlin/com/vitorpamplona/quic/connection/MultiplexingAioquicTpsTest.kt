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

import com.vitorpamplona.quic.frame.StreamFrame
import com.vitorpamplona.quic.tls.InProcessTlsServer
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Reproduce the aioquic-2026-05-07 multiplex-on-the-wire failure in
 * a unit test. Server qlog showed:
 *   - 1406 packets with exactly 1 STREAM frame, 1 with 3, never more
 *   - first stream packets at 1665 / 1709ms (one RTT apart, NOT burst)
 *   - per-RTT cadence throughout
 *
 * Hypothesis: writer drains 1 stream per packet despite many being
 * queued — under conditions specific to aioquic's TPs:
 *   - initial_max_data = 1MB (matches qlog)
 *   - initial_max_stream_data_bidi_remote = 1MB (matches qlog)
 *   - initial_max_streams_bidi = 128 (matches qlog)
 *
 * MultiplexingCoalescingTest passes with `initial_max_data = 100MB`
 * — which is 100x more credit than aioquic gives. This test mirrors
 * the exact aioquic TPs to see if the smaller credit triggers the
 * 1-stream-per-packet shape.
 *
 * If this test asserts ≤6 packets and PASSES, the bug is NOT in the
 * synchronous drain path — it's in the live driver flow (which
 * MultiplexingCoalescingTest doesn't exercise).
 *
 * If it FAILS with one stream per packet, we have a deterministic
 * reproduction and can fix the writer.
 */
class MultiplexingAioquicTpsTest {
    @Test
    fun multiplex_64_streams_with_aioquic_TPs_should_still_coalesce() =
        runBlocking {
            val client = handshakedClientMatchingAioquic()

            // ~80-byte HTTP/3 HEADERS frame payload — what
            // Http3GetClient actually emits per stream after QPACK
            // encoding. Smaller payloads (50 bytes) under-stress the
            // coalescing path.
            val n = 64
            val payloadPerStream = 80
            val streams =
                client.openBidiStreamsBatch((0 until n).toList()) { stream, i ->
                    stream.send.enqueue(ByteArray(payloadPerStream) { (i and 0xff).toByte() })
                    stream.send.finish()
                    stream
                }
            assertEquals(n, streams.size)

            val packets = mutableListOf<ByteArray>()
            while (true) {
                val pkt = drainOutbound(client, nowMillis = 1L) ?: break
                packets += pkt
            }

            val totalBytes = packets.sumOf { it.size }
            val streamsPerPacket = n.toDouble() / packets.size.coerceAtLeast(1)
            println(
                "[MultiplexingAioquicTpsTest] 64 streams of $payloadPerStream bytes " +
                    "drained into ${packets.size} packets " +
                    "(${(streamsPerPacket * 10).toInt() / 10.0} streams/pkt, " +
                    "totalBytes=$totalBytes)",
            )

            // Same threshold as MultiplexingCoalescingTest. 64 streams
            // × ~110 bytes (80 payload + ~30 overhead) ≈ 7 KB; at
            // ~1100-byte payload budget per packet that's 6-7 packets.
            assertTrue(
                packets.size <= 8,
                "expected ≤8 packets, got ${packets.size} — " +
                    "writer regressed to one-stream-per-packet under aioquic TPs",
            )
            assertTrue(
                streamsPerPacket >= 8.0,
                "expected ≥8 streams/packet, got $streamsPerPacket",
            )
        }

    private fun handshakedClientMatchingAioquic(): QuicConnection =
        runBlocking {
            val client =
                QuicConnection(
                    serverName = "example.test",
                    config =
                        QuicConnectionConfig(
                            // Mirror our actual local TPs from the qlog.
                            initialMaxData = 16L * 1024 * 1024,
                            initialMaxStreamsBidi = 100,
                            initialMaxStreamsUni = 10000,
                            initialMaxStreamDataBidiLocal = 1L * 1024 * 1024,
                            initialMaxStreamDataBidiRemote = 1L * 1024 * 1024,
                            initialMaxStreamDataUni = 1L * 1024 * 1024,
                        ),
                    tlsCertificateValidator =
                        com.vitorpamplona.quic.tls
                            .PermissiveCertificateValidator(),
                )
            val serverScid = ConnectionId.random(8)
            val tlsServer =
                InProcessTlsServer(
                    transportParameters =
                        TransportParameters(
                            // Mirror aioquic-qns 2026-05-07 from the qlog.
                            initialMaxData = 1L * 1024 * 1024,
                            initialMaxStreamDataBidiLocal = 1L * 1024 * 1024,
                            initialMaxStreamDataBidiRemote = 1L * 1024 * 1024,
                            initialMaxStreamDataUni = 1L * 1024 * 1024,
                            initialMaxStreamsBidi = 128,
                            initialMaxStreamsUni = 128,
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
            client
        }
}
