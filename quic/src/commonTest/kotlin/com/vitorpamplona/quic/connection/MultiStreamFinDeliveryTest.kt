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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Regression for the quic-interop-runner `multiplexing` failure observed
 * 2026-05-06 against aioquic: 677 client-bidi streams opened, server FIN'd
 * every one, zero responses surfaced to the application — every per-stream
 * `incoming.collect` hung forever.
 *
 * The single-stream cases (`transfer`, `chacha20`, `http3`) all pass, so
 * STREAM-frame routing for low concurrency works. The bug only fires when
 * many streams' responses stream through together.
 *
 * Two regressions are pinned here:
 *
 *   1. Server replies to every client-opened bidi stream with `data + FIN`
 *      coalesced into a single datagram. Each stream's `incoming` Flow MUST
 *      complete (FIN delivery), and the bytes MUST surface (no truncation).
 *
 *   2. If the parser tears the connection down (e.g. STREAM channel
 *      saturated → INTERNAL_ERROR), every stream's `incoming` Flow MUST
 *      still terminate. Otherwise application-side `incoming.collect`
 *      callers leak as zombie coroutines stuck on a channel that nobody
 *      will ever close. This is the "FIN never arrives" symptom from the
 *      runner's perspective: the connection died mid-response and we
 *      forgot to release the per-stream channels.
 */
class MultiStreamFinDeliveryTest {
    @Test
    fun finIsDeliveredToAllParallelClientBidiStreamsUnderConcurrentLoad() =
        runBlocking {
            val (client, pipe) = newConnectedClient()

            // Open N streams. Match the multiplexing case shape — many streams
            // each with a small response — without paying for full 1999-file
            // overhead.
            val n = 50
            val streams = (0 until n).map { client.openBidiStream() }

            // Build one server datagram per stream that responds with
            // "resp-<i>" + FIN. We keep them in separate datagrams (rather
            // than coalescing all into one short-header packet which the
            // packet codec doesn't support) so the parser sees a stream of
            // back-to-back STREAM frames just as it would on the wire.
            for ((i, stream) in streams.withIndex()) {
                val payload = "resp-$i".encodeToByteArray()
                val frame =
                    StreamFrame(
                        streamId = stream.streamId,
                        offset = 0L,
                        data = payload,
                        fin = true,
                    )
                val packet = pipe.buildServerApplicationDatagram(listOf(frame))!!
                feedDatagram(client, packet, nowMillis = 0L)
            }

            // Each stream's collector must terminate (FIN closed the
            // channel) AND must have received the expected payload.
            val collected =
                coroutineScope {
                    streams
                        .mapIndexed { i, stream ->
                            async {
                                withTimeoutOrNull(5_000L) {
                                    stream.incoming.toList()
                                } to i
                            }
                        }.awaitAll()
                }
            val hung = collected.firstOrNull { it.first == null }
            assertTrue(
                hung == null,
                "stream index ${hung?.second} never received FIN — Flow.toList() timed out",
            )
            for ((result, i) in collected) {
                val chunks = result!!
                val joined = ByteArray(chunks.sumOf { it.size })
                var p = 0
                for (c in chunks) {
                    c.copyInto(joined, p)
                    p += c.size
                }
                assertEquals(
                    "resp-$i",
                    joined.decodeToString(),
                    "stream $i: bytes mismatch",
                )
            }
            assertEquals(
                QuicConnection.Status.CONNECTED,
                client.status,
                "connection must remain CONNECTED through the response burst",
            )
        }

    @Test
    fun finIsDeliveredEvenWhenChannelAlreadyHasBufferedChunks() =
        runBlocking {
            // Defence-in-depth: a stream that has bytes still queued in
            // its incomingChannel when the connection tears down must still
            // surface those bytes AND complete the Flow. consumeAsFlow drains
            // the buffer before honouring the close, so closeIncoming after
            // deliverIncoming is safe — we pin that contract here so a
            // future "close the channel and reset the buffer" refactor can't
            // silently regress the byte loss.
            val (client, pipe) = newConnectedClient()
            val stream = client.openBidiStream()
            val payload = "buffered-then-torn-down".encodeToByteArray()
            val packet =
                pipe.buildServerApplicationDatagram(
                    listOf(
                        StreamFrame(
                            streamId = stream.streamId,
                            offset = 0L,
                            data = payload,
                            fin = false, // intentionally no FIN
                        ),
                    ),
                )!!
            feedDatagram(client, packet, nowMillis = 0L)

            // Tear down WITHOUT delivering FIN — bytes are now buffered in the
            // incomingChannel and the connection-level close must still
            // terminate the per-stream Flow.
            client.markClosedExternally("test teardown after partial response")

            val chunks =
                withTimeoutOrNull(2_000L) { stream.incoming.toList() }
            assertTrue(chunks != null, "Flow leaked after teardown with buffered bytes")
            val joined = ByteArray(chunks.sumOf { it.size })
            var p = 0
            for (c in chunks) {
                c.copyInto(joined, p)
                p += c.size
            }
            assertEquals(
                "buffered-then-torn-down",
                joined.decodeToString(),
                "buffered bytes must surface before the Flow terminates",
            )
        }

    @Test
    fun connectionTeardownClosesEveryPerStreamIncomingChannel() =
        runBlocking {
            // Even when the parser kills the connection (e.g. channel
            // overflow surfaces as INTERNAL_ERROR via markClosedExternally),
            // every per-stream `incoming` Flow MUST terminate. Otherwise
            // application coroutines that called `stream.incoming.toList()`
            // leak forever — exactly the symptom seen in the multiplexing
            // runner where 677 collectors hung.
            val (client, _) = newConnectedClient()
            val n = 20
            val streams = (0 until n).map { client.openBidiStream() }

            // Tear the connection down externally without delivering any FIN.
            client.markClosedExternally("simulated parser-side teardown")

            // Every stream's collector must complete promptly. Pre-fix the
            // per-stream incomingChannel stays open (closeIncoming is never
            // called) and the collector hangs.
            val results =
                coroutineScope {
                    streams
                        .mapIndexed { i, stream ->
                            async {
                                withTimeoutOrNull(2_000L) {
                                    stream.incoming.toList()
                                } to i
                            }
                        }.awaitAll()
                }
            val hung = results.firstOrNull { it.first == null }
            assertTrue(
                hung == null,
                "stream index ${hung?.second} incoming Flow leaked after connection teardown",
            )
        }

    private fun newConnectedClient(): Pair<QuicConnection, InMemoryQuicPipe> =
        runBlocking {
            val client =
                QuicConnection(
                    serverName = "example.test",
                    config = QuicConnectionConfig(),
                    tlsCertificateValidator =
                        com.vitorpamplona.quic.tls
                            .PermissiveCertificateValidator(),
                )
            val serverScid = ConnectionId.random(8)
            val tlsServer =
                InProcessTlsServer(
                    transportParameters =
                        TransportParameters(
                            initialMaxData = 16L * 1024 * 1024,
                            initialMaxStreamDataBidiLocal = 1L * 1024 * 1024,
                            initialMaxStreamDataBidiRemote = 1L * 1024 * 1024,
                            initialMaxStreamDataUni = 1L * 1024 * 1024,
                            initialMaxStreamsBidi = 1000,
                            initialMaxStreamsUni = 1000,
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
            client to pipe
        }
}
