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
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pin the multiplexing throughput contract that the runner's
 * `multiplexing` testcase exercises.
 *
 * This is the fast in-memory version of what the Docker-based runner
 * does (open N bidi streams, send a GET on each, collect responses):
 * we drive a real handshake via [InMemoryQuicPipe] then enqueue request
 * bytes on N bidi streams in two batching shapes, drive the writer, and
 * count the resulting datagrams.
 *
 * The bug this test catches: pre-fix, the interop runner's multiplexing
 * test ran ~25 streams/sec because each application-side enqueue woke
 * the send loop while the OTHER coroutines hadn't yet queued bytes.
 * Result: one stream per packet, ~80-byte packets, useless coalescing.
 *
 * Post-fix: enqueue bytes on N streams synchronously, then drain ONCE
 * — the writer should pack many streams' data into each datagram. With
 * 64 streams × 50 bytes = 3200 bytes of payload, plus ~50 bytes/stream
 * STREAM-frame framing, total wire load ≈ 6.4 KB. At a 1452-byte UDP
 * cap, that's ~5 datagrams. The pre-fix shape would emit 64.
 */
class MultiplexingCoalescingTest {
    @Test
    fun `64 streams enqueued before drain coalesce into a small fixed number of packets`() =
        runBlocking {
            val client = handshakedClient()

            // Phase 1: serial enqueue. NO drainOutbound between streams.
            // This is exactly the shape InteropClient.runTransferTest's
            // chunked-multiplex path uses: prepareRequest is synchronous
            // for every URL in the chunk, then a single wakeup, then
            // parallel awaits.
            val n = 64
            val payloadPerStream = 50
            val streams =
                (0 until n).map { i ->
                    val s = client.openBidiStream()
                    s.send.enqueue(ByteArray(payloadPerStream) { (i and 0xff).toByte() })
                    s.send.finish()
                    s
                }
            assertEquals(n, streams.size)

            // Phase 2: drain everything at once. Count packets emitted.
            val packets = mutableListOf<ByteArray>()
            while (true) {
                val pkt = drainOutbound(client, nowMillis = 1L) ?: break
                packets += pkt
            }

            val totalBytes = packets.sumOf { it.size }
            val payloadTotal = n * payloadPerStream
            val avgBytesPerPacket = totalBytes / packets.size.coerceAtLeast(1)
            val streamsPerPacket = n.toDouble() / packets.size.coerceAtLeast(1)

            // Threshold derivation: each stream's wire load is ~50 bytes
            // payload + ~10 bytes STREAM-frame framing + per-packet AEAD
            // overhead. 64 streams ≈ 4 KB of frame data; at 1452 bytes
            // per UDP packet that's ≤ 4 packets of frames + 1 for any
            // pending ACK / control frames. Pre-fix produced 64+ packets.
            assertTrue(
                packets.size <= 6,
                "64 streams should coalesce into ≤6 packets, got ${packets.size}; " +
                    "totalBytes=$totalBytes avgBytesPerPacket=$avgBytesPerPacket " +
                    "streamsPerPacket=$streamsPerPacket payloadTotal=$payloadTotal",
            )
            // Sanity: average packet should carry at least ~10 streams' frames.
            // If it's ~1, we regressed back to one-stream-per-packet.
            assertTrue(
                streamsPerPacket >= 10.0,
                "expected ≥10 streams/packet, got $streamsPerPacket — coalescing broke",
            )
        }

    @Test
    fun `1000 streams enqueued in batches of 64 produce a tractable packet count`() =
        runBlocking {
            // Stress version. 1000 streams in chunks of 64 = ~16 chunks.
            // Each chunk should produce ≤6 packets per the test above,
            // so total ≤ 100 packets. Pre-fix this test would have
            // produced ~1000 packets.
            val client = handshakedClient(maxStreamsBidi = 2000)
            val chunkSize = 64
            val totalStreams = 1000
            val payloadPerStream = 50

            val packets = mutableListOf<ByteArray>()
            for (chunk in (0 until totalStreams).chunked(chunkSize)) {
                for (i in chunk) {
                    val s = client.openBidiStream()
                    s.send.enqueue(ByteArray(payloadPerStream) { (i and 0xff).toByte() })
                    s.send.finish()
                }
                while (true) {
                    val pkt = drainOutbound(client, nowMillis = 1L) ?: break
                    packets += pkt
                }
            }

            val streamsPerPacket = totalStreams.toDouble() / packets.size.coerceAtLeast(1)
            assertTrue(
                packets.size <= 150,
                "1000 streams should produce ≤150 packets, got ${packets.size} (streamsPerPacket=$streamsPerPacket)",
            )
        }

    private fun handshakedClient(maxStreamsBidi: Long = 100L): QuicConnection =
        runBlocking {
            val client =
                QuicConnection(
                    serverName = "example.test",
                    config = QuicConnectionConfig(initialMaxStreamsBidi = maxStreamsBidi),
                    tlsCertificateValidator =
                        com.vitorpamplona.quic.tls
                            .PermissiveCertificateValidator(),
                )
            val serverScid = ConnectionId.random(8)
            val tlsServer =
                InProcessTlsServer(
                    transportParameters =
                        TransportParameters(
                            initialMaxData = 100_000_000,
                            initialMaxStreamDataBidiLocal = 1_000_000,
                            initialMaxStreamDataBidiRemote = 1_000_000,
                            initialMaxStreamDataUni = 1_000_000,
                            initialMaxStreamsBidi = maxStreamsBidi,
                            initialMaxStreamsUni = maxStreamsBidi,
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
