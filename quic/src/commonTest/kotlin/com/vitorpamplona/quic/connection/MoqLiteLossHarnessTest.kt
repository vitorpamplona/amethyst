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
import com.vitorpamplona.quic.stream.StreamId
import com.vitorpamplona.quic.tls.InProcessTlsServer
import com.vitorpamplona.quic.tls.PermissiveCertificateValidator
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * First pass at soak target #5 (transient packet loss + jitter) for
 * the moq-lite group-stream shape.
 *
 * **Scope of this draft.** moq-lite's group streams are best-effort
 * (the [SendBuffer.bestEffort] flag drops lost ranges instead of
 * retransmitting). On the LISTENER side that means: a peer-uni
 * stream that the server originated, whose carrying datagram never
 * arrives, is gone — the relay does not retransmit it. The listener
 * application is expected to observe a small fraction of frames
 * "missing" but the connection itself stays healthy and audio for
 * the surviving frames continues.
 *
 * This test pins exactly that contract:
 *  1. Server publishes 50 group streams (one StreamFrame + FIN per
 *     stream, one stream per datagram — the moq-lite group shape
 *     1:1).
 *  2. A configurable loss model drops 5% (uniformly random) of
 *     server datagrams before [feedDatagram].
 *  3. We assert the listener surfaces ≥ 90% of the published
 *     streams (≥ 45 of 50 at p_loss=0.05) and the connection stays
 *     CONNECTED.
 *
 * What's intentionally OUT of scope here:
 *  - **Reordering / jitter.** moq-lite tolerates reordering by
 *    construction (each group is its own stream, contiguous within
 *    itself). A reorder-injecting wrapper is the obvious next
 *    layer; this draft pins the loss-only contract first.
 *  - **Reliable bidi STREAM frame retransmit on loss.** Covered by
 *    the existing CryptoRetransmitTest / MultiplexingRoundTripTest;
 *    the transferloss / handshakeloss interop testcases drive that
 *    end-to-end.
 *  - **Latency under loss.** Worth measuring once we wire a real
 *    moq-lite publisher; for now `runBlocking` + in-memory pipe
 *    means "real time" is meaningless.
 *
 * Production link to chase next:
 *  - `nestsClient/.../moq/lite/Subscriber.kt` — the listener path.
 *    Verify it doesn't tear down its own subscription on a missing
 *    group sequence; surfacing the gap to the audio decoder
 *    (silence frames) is the right behaviour.
 */
class MoqLiteLossHarnessTest {
    @Test
    fun listenerToleratesFivePercentLossOnGroupStreams() =
        runBlocking {
            // Deterministic RNG so a CI flake is reproducible: the
            // exact dropped indices are seeded from a fixed value.
            // Bumping the seed to find pathological loss patterns is
            // a good follow-up, but for now we just need any seed
            // that exercises the dropped-and-recovered path.
            val rng = Random(0xC01DBEEFL)
            val groupCount = 50
            val lossRate = 0.05
            val (client, pipe) = newConnectedClient()

            val firstId = StreamId.build(StreamId.Kind.SERVER_UNI, 0L)
            val streamIds = (0 until groupCount).map { firstId + 4L * it }
            val payloads = streamIds.associateWith { id -> "frame-$id".encodeToByteArray() }
            val droppedIds = mutableSetOf<Long>()

            for (id in streamIds) {
                val drop = rng.nextDouble() < lossRate
                if (drop) {
                    droppedIds += id
                    continue // Don't even build the packet — same shape as kernel
                    // dropping the datagram before it reaches the QUIC parser.
                }
                val frame =
                    StreamFrame(
                        streamId = id,
                        offset = 0L,
                        data = payloads[id]!!,
                        fin = true,
                    )
                val packet = pipe.buildServerApplicationDatagram(listOf(frame))!!
                feedDatagram(client, packet, nowMillis = 0L)
            }

            // Drain the listener's incoming Flow for every stream we
            // EXPECTED to receive (= all server-uni IDs minus the dropped
            // ones). Streams that were dropped never had a QuicStream
            // created on our side, so streamById(id) returns null — we
            // don't even try to drain those.
            val received = mutableMapOf<Long, ByteArray>()
            for (id in streamIds) {
                if (id in droppedIds) continue
                val stream = client.streamById(id) ?: continue
                val chunks = withTimeoutOrNull(2_000L) { stream.incoming.toList() } ?: continue
                val joined = ByteArray(chunks.sumOf { it.size })
                var p = 0
                for (c in chunks) {
                    c.copyInto(joined, p)
                    p += c.size
                }
                received[id] = joined
            }

            // Surviving streams must surface their full payload.
            for ((id, bytes) in received) {
                assertEquals(
                    payloads[id]!!.decodeToString(),
                    bytes.decodeToString(),
                    "stream $id payload corrupted under loss — best-effort path " +
                        "must NOT split or drop bytes WITHIN a delivered group",
                )
            }
            // ≥ 90% delivered (lossRate=5% — band leaves room for RNG
            // tail-end where the seed happens to drop more than the
            // mean).
            val expectedFloor = (groupCount * 0.9).toInt()
            assertTrue(
                received.size >= expectedFloor,
                "listener received only ${received.size} / $groupCount groups under " +
                    "${(lossRate * 100).toInt()}% loss — floor was $expectedFloor. " +
                    "dropped=${droppedIds.size} ids=${droppedIds.sorted()}",
            )
            assertEquals(
                QuicConnection.Status.CONNECTED,
                client.status,
                "connection must stay CONNECTED across packet loss on best-effort streams",
            )
        }

    @Test
    fun listenerSurfacesEveryFrameWhenLossRateIsZero() =
        runBlocking {
            // Sanity for the harness itself: with lossRate=0 we must
            // see all 50 frames intact. If this fails the loss-shape
            // assertions in the lossy test are unreliable.
            val (client, pipe) = newConnectedClient()
            val groupCount = 50
            val firstId = StreamId.build(StreamId.Kind.SERVER_UNI, 0L)

            for (i in 0 until groupCount) {
                val id = firstId + 4L * i
                val payload = "frame-$id".encodeToByteArray()
                val frame = StreamFrame(streamId = id, offset = 0L, data = payload, fin = true)
                val packet = pipe.buildServerApplicationDatagram(listOf(frame))!!
                feedDatagram(client, packet, nowMillis = 0L)
            }

            var delivered = 0
            for (i in 0 until groupCount) {
                val id = firstId + 4L * i
                val stream = client.streamById(id)!!
                val chunks = withTimeoutOrNull(2_000L) { stream.incoming.toList() }
                if (chunks != null) delivered += 1
            }
            assertEquals(groupCount, delivered, "lossRate=0 baseline must deliver every frame")
        }

    @Test
    fun listenerToleratesPacketReorderingOnGroupStreams() =
        runBlocking {
            // Reorder injection — the network can deliver datagrams
            // out of order even when none are dropped. moq-lite group
            // streams MUST tolerate this: each stream is a self-
            // contained Opus frame (offset 0 + FIN), so reorder at
            // the datagram level just means the listener sees
            // streams arrive in a different order than the relay
            // sent them. Audio frames carry sequence numbers, so the
            // application-level player handles late-arrivers via its
            // own jitter buffer.
            //
            // Pin the contract: 100% delivery under arbitrary
            // datagram-order permutation. This catches a class of
            // regression where a parser invariant ("PN must
            // increase") gets accidentally tightened to "STREAM IDs
            // must arrive in order", which would silently break
            // moq-lite under any real-world jitter.
            val rng = Random(0xBAD5EEDL)
            val groupCount = 50
            val (client, pipe) = newConnectedClient()
            val firstId = StreamId.build(StreamId.Kind.SERVER_UNI, 0L)
            val streamIds = (0 until groupCount).map { firstId + 4L * it }
            val payloads = streamIds.associateWith { id -> "frame-$id".encodeToByteArray() }

            // Build all packets up-front; then shuffle the delivery
            // order. Permutation drives reorder of:
            //   - Stream IDs (peer-uni IDs are globally ordered;
            //     the listener sees later IDs before earlier ones).
            //   - QUIC packet numbers (each datagram carries a fresh
            //     PN; reorder means the parser's PN-space tracking
            //     observes non-monotonic largestReceived advances).
            val packets =
                streamIds.map { id ->
                    pipe.buildServerApplicationDatagram(
                        listOf(StreamFrame(streamId = id, offset = 0L, data = payloads[id]!!, fin = true)),
                    )!!
                }
            val deliveryOrder = packets.indices.shuffled(rng)
            for (idx in deliveryOrder) {
                feedDatagram(client, packets[idx], nowMillis = 0L)
            }

            // Every stream must have surfaced its full payload
            // despite arriving out of order.
            for (id in streamIds) {
                val s = client.streamById(id)!!
                val chunks = withTimeoutOrNull(2_000L) { s.incoming.toList() }
                assertNotNull(chunks, "stream $id must surface despite reorder")
                val joined = ByteArray(chunks.sumOf { it.size })
                var p = 0
                for (c in chunks) {
                    c.copyInto(joined, p)
                    p += c.size
                }
                assertEquals(payloads[id]!!.decodeToString(), joined.decodeToString())
            }
            assertEquals(QuicConnection.Status.CONNECTED, client.status)
        }

    @Test
    fun listenerSurvivesExtremeTwentyPercentLoss() =
        runBlocking {
            // Extreme-loss canary: 20% drop is far past the typical
            // 1-5% range a healthy mobile network sees, but
            // approaches what a degraded subway / elevator path
            // delivers. moq-lite's contract here is "best-effort —
            // gaps surface to audio decoder as silence." The QUIC
            // contract is "the connection itself stays healthy."
            //
            // The audible-quality bar is the application's problem
            // (jitter buffer, FEC). What this test pins is that the
            // QUIC LAYER doesn't tear itself down under aggressive
            // loss — flow-control accounting, ACK-tracker windows,
            // and the retired-stream-id ring all stay consistent.
            // A regression in any of those would either fire a
            // protocol violation (CONNECTION_CLOSE) or wedge the
            // peer into PTO mode.
            val rng = Random(0xDEADBEEFL)
            val groupCount = 200
            val lossRate = 0.20
            val (client, pipe) = newConnectedClient()
            val firstId = StreamId.build(StreamId.Kind.SERVER_UNI, 0L)
            val streamIds = (0 until groupCount).map { firstId + 4L * it }
            val payloads = streamIds.associateWith { id -> "frame-$id".encodeToByteArray() }
            val droppedIds = mutableSetOf<Long>()

            for (id in streamIds) {
                if (rng.nextDouble() < lossRate) {
                    droppedIds += id
                    continue
                }
                val packet =
                    pipe.buildServerApplicationDatagram(
                        listOf(StreamFrame(streamId = id, offset = 0L, data = payloads[id]!!, fin = true)),
                    )!!
                feedDatagram(client, packet, nowMillis = 0L)
            }

            val received = mutableListOf<Long>()
            for (id in streamIds) {
                if (id in droppedIds) continue
                val s = client.streamById(id) ?: continue
                if (withTimeoutOrNull(2_000L) { s.incoming.toList() } != null) {
                    received += id
                }
            }
            // Expected delivery is approximately (1 - lossRate) of the
            // total. RNG variance gives ~5% tolerance band; we assert
            // ≥ 60% to leave plenty of margin while still catching a
            // catastrophic collapse (e.g. parser silently drops every
            // packet after the first loss).
            val floor = (groupCount * 0.6).toInt()
            assertTrue(
                received.size >= floor,
                "listener received ${received.size} / $groupCount under 20% loss — floor $floor. " +
                    "dropped=${droppedIds.size}",
            )
            assertEquals(
                QuicConnection.Status.CONNECTED,
                client.status,
                "connection must stay CONNECTED through extreme loss",
            )
        }

    @Test
    fun reliableBidiStreamRecoversFromMidStreamPacketLoss() =
        runBlocking {
            // Reliable-stream loss harness — distinct from the moq-
            // lite group-stream best-effort path above. RFC 9000
            // STREAM frames carry full reliability semantics: a
            // dropped packet must trigger retransmit on PTO, and
            // the listener MUST eventually see the bytes contiguous
            // and in order. This test pins that contract end-to-end
            // by:
            //   1. Server sends 4 STREAM frames on the same bidi
            //      stream, each in its own datagram, covering a
            //      monotonic offset range.
            //   2. The middle two datagrams are dropped on first
            //      delivery — simulating a transient mid-stream
            //      loss.
            //   3. Server retransmits the dropped ranges on the
            //      same offsets (mimicking what RFC 9002
            //      retransmit logic does on PTO).
            //   4. Client must surface all 4 chunks contiguous,
            //      in order, no gaps.
            val (client, pipe) = newConnectedClient()
            // Open a client-bidi stream so the server can write back.
            val stream = client.openBidiStream()
            val streamId = stream.streamId

            val chunks =
                listOf(
                    "AAAA".encodeToByteArray(),
                    "BBBB".encodeToByteArray(),
                    "CCCC".encodeToByteArray(),
                    "DDDD".encodeToByteArray(),
                )
            val offsets = LongArray(chunks.size)
            var off = 0L
            for ((i, c) in chunks.withIndex()) {
                offsets[i] = off
                off += c.size
            }

            // First-pass delivery: drop chunks[1] and chunks[2].
            for (i in chunks.indices) {
                if (i == 1 || i == 2) continue
                val frame =
                    StreamFrame(
                        streamId = streamId,
                        offset = offsets[i],
                        data = chunks[i],
                        fin = i == chunks.size - 1,
                    )
                feedDatagram(client, pipe.buildServerApplicationDatagram(listOf(frame))!!, nowMillis = 0L)
            }
            // Retransmit the dropped chunks on their original offsets.
            for (i in listOf(1, 2)) {
                val frame =
                    StreamFrame(
                        streamId = streamId,
                        offset = offsets[i],
                        data = chunks[i],
                        fin = false,
                    )
                feedDatagram(client, pipe.buildServerApplicationDatagram(listOf(frame))!!, nowMillis = 0L)
            }

            val collected = withTimeoutOrNull(2_000L) { stream.incoming.toList() }
            assertNotNull(collected, "stream must complete after retransmits fill the gaps")
            val joined = ByteArray(collected.sumOf { it.size })
            var p = 0
            for (c in collected) {
                c.copyInto(joined, p)
                p += c.size
            }
            assertEquals(
                "AAAABBBBCCCCDDDD",
                joined.decodeToString(),
                "reliable bidi stream must surface every byte in offset order even after " +
                    "mid-stream packet loss + retransmit",
            )
            assertEquals(QuicConnection.Status.CONNECTED, client.status)
        }

    private fun newConnectedClient(): Pair<QuicConnection, InMemoryQuicPipe> =
        runBlocking {
            val client =
                QuicConnection(
                    serverName = "loss.test",
                    config =
                        QuicConnectionConfig(
                            initialMaxStreamsBidi = 1024,
                            initialMaxStreamsUni = 1024,
                            initialMaxData = 16L * 1024 * 1024,
                            initialMaxStreamDataBidiLocal = 64L * 1024,
                            initialMaxStreamDataBidiRemote = 64L * 1024,
                            initialMaxStreamDataUni = 64L * 1024,
                        ),
                    tlsCertificateValidator = PermissiveCertificateValidator(),
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
