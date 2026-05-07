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
 * In-process mirror of the [quic-interop-runner](https://github.com/quic-interop/quic-interop-runner)
 * `multiplexing` testcase. The runner generates ~50–2000 small files and
 * has the client open one bidi stream per file in parallel, then asserts
 * every file is downloaded with correct content. This test runs the same
 * shape against the [InMemoryQuicPipe] harness — no UDP, no real server —
 * so the regression that drove this test (aioquic CONNECTION_CLOSE on
 * bare-PING PTO probes, plus the parallel-open / FIN-delivery bugs) can
 * be caught in a unit-test loop instead of a Docker matrix run.
 *
 * Why this shape and not just `MultiStreamFinDeliveryTest`:
 *
 *   - `MultiStreamFinDeliveryTest` only exercises server-pushed STREAM
 *     frames. It hand-builds N response datagrams and feeds them
 *     directly. The CLIENT never sends a STREAM frame, so any regression
 *     in the writer's multiplex-stream-coalesce path or in the parser's
 *     server-side request-decode logic is invisible to it.
 *
 *   - This test drives the full request → response loop:
 *
 *       1. Client opens N bidi streams in parallel (matches the matrix)
 *       2. Each stream enqueues a tiny request + FIN
 *       3. drainOutbound pulls a coalesced 1-RTT datagram off the writer
 *       4. The pipe decrypts, extracts STREAM frames, builds server
 *          responses (`response-<id>` + FIN) and feeds them back
 *       5. Per-stream `incoming.toList()` must yield the expected bytes
 *
 *     If any step in that loop regresses — the writer drops streams, the
 *     parser routes STREAM frames wrong under load, the server-side state
 *     forgets which stream sent which request — the assertion fails with a
 *     specific stream id, not a vague timeout.
 *
 * Stream-count notes: the matrix uses up to 1999 files; we use 64 to keep
 * the test under a second on CI. The *bug class* the test guards against
 * is invariant to the count — any "loses a stream's response" regression
 * will fire at 64 too because it's per-stream, not aggregate.
 */
class MultiplexingRoundTripTest {
    @Test
    fun parallel_streams_each_get_their_own_response_back() =
        runBlocking {
            val (client, pipe) = newConnectedClient()

            val n = 64
            val streams =
                (0 until n).map { i ->
                    val s = client.openBidiStream()
                    // Tiny request body. Real H3/HQ would be a `GET /file-i\r\n`;
                    // the matrix doesn't care WHAT the bytes are, only that the
                    // client emitted them on a distinct stream and the server's
                    // response surfaces back.
                    s.send.enqueue("req-$i".encodeToByteArray())
                    s.send.finish()
                    s
                }

            // Drain everything the client wants to send. drainOutbound returns
            // one datagram per call (up to ~1200 bytes); on each, decrypt the
            // 1-RTT short-header packet and collect STREAM frames. The writer
            // is supposed to coalesce many streams' frames into one datagram
            // (MultiplexingCoalescingTest pins that contract); here we just
            // walk every emitted datagram until the client has nothing left.
            val seenRequests = mutableMapOf<Long, ByteArray>()
            val seenFin = mutableSetOf<Long>()
            var totalDatagrams = 0
            while (true) {
                val out = drainOutbound(client, nowMillis = 0L) ?: break
                if (out.isEmpty()) break
                totalDatagrams += 1
                val frames = pipe.decryptClientApplicationFrames(out) ?: break
                for (frame in frames) {
                    if (frame is StreamFrame) {
                        // Concatenate by offset (matrix reqs are tiny; offset
                        // is always 0, but real flows might split — accept both).
                        val existing = seenRequests[frame.streamId] ?: ByteArray(0)
                        val merged = ByteArray(existing.size + frame.data.size)
                        existing.copyInto(merged, 0)
                        frame.data.copyInto(merged, existing.size)
                        seenRequests[frame.streamId] = merged
                        if (frame.fin) seenFin += frame.streamId
                    }
                }
                if (seenFin.size == n) break
            }
            assertEquals(
                n,
                seenRequests.size,
                "server saw STREAM frames from only ${seenRequests.size}/$n streams " +
                    "after $totalDatagrams datagrams — writer dropped streams under multiplex load",
            )
            assertEquals(
                n,
                seenFin.size,
                "server saw FINs from only ${seenFin.size}/$n streams — client failed " +
                    "to deliver request FIN on every stream",
            )
            // Sanity-check the request payloads match what each stream sent.
            for ((i, stream) in streams.withIndex()) {
                val expected = "req-$i"
                val actual = seenRequests[stream.streamId]?.decodeToString()
                assertEquals(
                    expected,
                    actual,
                    "stream ${stream.streamId} (i=$i): server received '$actual', expected '$expected'",
                )
            }

            // Server responds: one STREAM frame per stream with a known body
            // + FIN, packed into one datagram each (the in-memory pipe doesn't
            // coalesce frames into a single packet across stream-ids by
            // design, since the writer side of the pipe is intentionally
            // minimal). Client must surface every body via stream.incoming
            // and terminate each Flow on FIN.
            for (stream in streams) {
                val body = "response-${stream.streamId}".encodeToByteArray()
                val frame =
                    StreamFrame(
                        streamId = stream.streamId,
                        offset = 0L,
                        data = body,
                        fin = true,
                    )
                val datagram = pipe.buildServerApplicationDatagram(listOf(frame))!!
                feedDatagram(client, datagram, nowMillis = 0L)
            }

            val collected =
                coroutineScope {
                    streams
                        .map { stream ->
                            async {
                                val chunks =
                                    withTimeoutOrNull(5_000L) { stream.incoming.toList() }
                                stream.streamId to chunks
                            }
                        }.awaitAll()
                }
            val hung = collected.firstOrNull { it.second == null }
            assertTrue(
                hung == null,
                "stream ${hung?.first} never received FIN — Flow.toList() timed out " +
                    "(this is the matrix's 'incomplete transfer' symptom)",
            )
            for ((streamId, chunks) in collected) {
                val joined = ByteArray(chunks!!.sumOf { it.size })
                var p = 0
                for (c in chunks) {
                    c.copyInto(joined, p)
                    p += c.size
                }
                assertEquals(
                    "response-$streamId",
                    joined.decodeToString(),
                    "stream $streamId: response bytes mismatch — wrong content delivered",
                )
            }
            assertEquals(
                QuicConnection.Status.CONNECTED,
                client.status,
                "connection must remain CONNECTED through the full round-trip",
            )
        }

    private fun newConnectedClient(): Pair<QuicConnection, InMemoryQuicPipe> =
        runBlocking {
            val client =
                QuicConnection(
                    serverName = "example.test",
                    config =
                        QuicConnectionConfig(
                            initialMaxStreamsBidi = 1024,
                            initialMaxStreamsUni = 1024,
                            initialMaxData = 16L * 1024 * 1024,
                            initialMaxStreamDataBidiLocal = 64L * 1024,
                            initialMaxStreamDataBidiRemote = 64L * 1024,
                            initialMaxStreamDataUni = 64L * 1024,
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
                            initialMaxData = 16L * 1024 * 1024,
                            initialMaxStreamDataBidiLocal = 64L * 1024,
                            initialMaxStreamDataBidiRemote = 64L * 1024,
                            initialMaxStreamDataUni = 64L * 1024,
                            initialMaxStreamsBidi = 1024,
                            initialMaxStreamsUni = 1024,
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
