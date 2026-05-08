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

import com.vitorpamplona.quic.frame.AckFrame
import com.vitorpamplona.quic.frame.StreamFrame
import com.vitorpamplona.quic.stream.StreamId
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins the stream-retirement contract that keeps `streamsList` /
 * `streams` bounded across long-lived audio-room sessions.
 *
 * Production motivation: the moq-lite listener path in
 * `nestsClient/src/commonMain/kotlin/.../moq/lite/` receives one
 * peer-uni stream per Opus frame. At ~50 frames/sec a 3-hour broadcast
 * mints ~540 000 streams. Pre-retirement, every stream stayed pinned in
 * `streamsList` (insertion-only) and `streams` (no remove) for the
 * lifetime of the connection — which made `QuicConnection`'s heap grow
 * monotonically until the audio room session was torn down. The
 * acceptance criterion in the soak prompt is "no monotonic growth past
 * handshake-stable"; this file is the unit-test surface that pins the
 * mechanics behind that property.
 *
 * Three angles + a soak-shape:
 *  1. [retiredLocalUniStreamsAreRemovedAfterFinAndAck] — local-uni send
 *     path: client opens uni streams, writes + FIN, peer ACKs. Once FIN
 *     is `markAcked`'d, the next writer drain retires them.
 *  2. [retiredPeerInitiatedStreamsDoNotPinReceiveBuffers] — moq-lite
 *     listener path: peer opens server-uni streams, sends payload + FIN,
 *     parser drains contiguous bytes and closes incoming. Next writer
 *     drain retires them.
 *  3. [retirementPreservesConnectionLevelMaxDataAccounting] — the subtle
 *     correctness check. Removing N retired streams must NOT regress the
 *     writer's connection-level MAX_DATA accounting; the
 *     `retiredStreamsRecvBytes` accumulator on the connection folds
 *     per-stream `receive.contiguousEnd()` sums forward. After retire,
 *     `advertisedMaxData` continues to grow on subsequent receive bytes.
 *  4. [streamChurnSoakKeepsTrackerBounded] — soak-shape: cycle through
 *     200 generations of 50 streams (10 000 stream lifecycles, well past
 *     steady-state) and assert the working set stays bounded. Without
 *     retirement the working set would equal totalStreams.
 */
class StreamRetirementSoakTest {
    @Test
    fun retiredLocalUniStreamsAreRemovedAfterFinAndAck() =
        runBlocking {
            val (client, pipe) = newConnectedClient()
            val n = 50

            val streams =
                client.openUniStreamsBatch(items = (0 until n).toList()) { stream, i ->
                    stream.send.enqueue(
                        "frame-${i.toString().padStart(2, '0')}".encodeToByteArray(),
                    )
                    stream.send.finish()
                    stream
                }
            assertEquals(n, client.streamsListLocked().size)

            // Drain every outbound packet the writer has to send.
            val anyOutput = drainAll(client, pipe)
            assertTrue(anyOutput, "writer must have emitted at least one application packet")

            // Peer ACKs every PN we ever sent on the application space.
            // largestAcked = nextPacketNumber - 1; firstAckRange covers
            // PN 0 through largestAcked inclusive.
            val largestSent = client.application.pnSpace.nextPacketNumber - 1L
            assertTrue(
                largestSent >= 0L,
                "drainAll must have allocated at least one application PN (got=$largestSent)",
            )
            val ackPacket =
                pipe.buildServerApplicationDatagram(
                    listOf(
                        AckFrame(
                            largestAcknowledged = largestSent,
                            ackDelay = 0L,
                            firstAckRange = largestSent,
                        ),
                    ),
                )!!
            feedDatagram(client, ackPacket, nowMillis = 0L)

            for ((idx, s) in streams.withIndex()) {
                assertTrue(
                    s.send.finAcked,
                    "stream[$idx] (id=${s.streamId}) finAcked must latch after peer ACK",
                )
                assertTrue(
                    s.isFullyRetired,
                    "stream[$idx] uni-out direction is fully retired after finAcked",
                )
            }

            // One more drain triggers retireFullyDoneStreamsLocked at the
            // top of buildApplicationPacket — the production seam.
            drainAll(client, pipe)

            assertEquals(
                0,
                client.streamsListLocked().size,
                "streamsList must be empty after retirement of all FIN-acked uni streams",
            )
            assertEquals(
                n.toLong(),
                client.retiredStreamsCount,
                "retiredStreamsCount must equal the number of retired streams",
            )
            assertEquals(
                0L,
                client.retiredStreamsRecvBytes,
                "uni-out streams contribute no receive-side bytes to the accumulator",
            )
        }

    @Test
    fun retiredPeerInitiatedStreamsDoNotPinReceiveBuffers() =
        runBlocking {
            val (client, pipe) = newConnectedClient()
            val n = 30

            val firstServerUniId = StreamId.build(StreamId.Kind.SERVER_UNI, 0L)
            val serverUniIds = (0 until n).map { firstServerUniId + 4L * it }
            val payloads = serverUniIds.associateWith { id -> "g-$id".encodeToByteArray() }

            for (id in serverUniIds) {
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

            // Every per-stream incoming Flow must complete (parser closed
            // it via the isFullyRead branch).
            for (id in serverUniIds) {
                val stream = client.streamById(id)!!
                val chunks = withTimeoutOrNull(2_000L) { stream.incoming.toList() }
                assertTrue(chunks != null, "stream $id incoming Flow must terminate after FIN")
                val joined = ByteArray(chunks.sumOf { it.size })
                var p = 0
                for (c in chunks) {
                    c.copyInto(joined, p)
                    p += c.size
                }
                assertEquals(
                    payloads[id]!!.decodeToString(),
                    joined.decodeToString(),
                    "stream $id payload must surface intact",
                )
            }

            // Drain → retireFullyDoneStreamsLocked at the top of
            // buildApplicationPacket drops every server-uni stream:
            //   - direction = UNIDIRECTIONAL_REMOTE_TO_LOCAL → send
            //     side is trivially settled
            //   - receive.finReceived AND receive.isFullyRead() because
            //     the parser drained every chunk into the incoming
            //     channel before closing it
            drainAll(client, pipe)

            assertEquals(
                0,
                client.streamsListLocked().size,
                "every server-uni stream must be retired after parsing FIN + writer drain",
            )
            assertEquals(
                n.toLong(),
                client.retiredStreamsCount,
                "retiredStreamsCount must equal the number of peer-uni streams retired",
            )
            val expectedRecvBytes = payloads.values.sumOf { it.size }.toLong()
            assertEquals(
                expectedRecvBytes,
                client.retiredStreamsRecvBytes,
                "retiredStreamsRecvBytes must aggregate every retired stream's receive high-water",
            )
        }

    @Test
    fun phantomGuardDropsRetransmitOnRetiredPeerStream() =
        runBlocking {
            // Phantom-stream guard regression: after we've retired a
            // peer-uni stream, a duplicate STREAM frame the peer
            // retransmits (because its loss-detector fired before our
            // ACK reached it) MUST NOT mint a fresh QuicStream object.
            // Pre-guard, the duplicate frame would:
            //   - re-populate `streams[id]` and `streamsList`
            //   - bump `peerInitiatedUniCount` a second time
            //   - re-fire `newPeerStreams.addLast(...)`, signalling
            //     a phantom stream-arrival to application code
            //   - re-deliver the same bytes on a fresh incoming Flow
            // The guard at the parser drops the frame (still
            // ack-eliciting, so our ACK still goes out and the peer's
            // loss-detector quiets down).
            val (client, pipe) = newConnectedClient()
            val streamId = StreamId.build(StreamId.Kind.SERVER_UNI, 0L)
            val payload = "phantom-test".encodeToByteArray()
            val frame = StreamFrame(streamId = streamId, offset = 0L, data = payload, fin = true)

            val firstPacket = pipe.buildServerApplicationDatagram(listOf(frame))!!
            feedDatagram(client, firstPacket, nowMillis = 0L)
            // Drain the original delivery to retire the stream.
            client.streamById(streamId)?.incoming?.toList()
            drainAll(client, pipe)
            assertEquals(
                1L,
                client.retiredStreamsCount,
                "first delivery + drain must retire exactly one stream",
            )
            assertTrue(
                client.isStreamIdRetiredLocked(streamId),
                "retired-id ring must hold the just-retired stream id",
            )
            val countAfterFirstDelivery = client.peerInitiatedUniCount

            // Replay the same STREAM+FIN frame — i.e. peer retransmit.
            val replayPacket = pipe.buildServerApplicationDatagram(listOf(frame))!!
            feedDatagram(client, replayPacket, nowMillis = 0L)
            drainAll(client, pipe)

            // No phantom stream — counters must not move.
            assertEquals(
                1L,
                client.retiredStreamsCount,
                "duplicate STREAM frame must not create a phantom stream that re-retires",
            )
            assertEquals(
                countAfterFirstDelivery,
                client.peerInitiatedUniCount,
                "peerInitiatedUniCount must not double-count the retransmitted FIN",
            )
            assertEquals(
                0,
                client.streamsListLocked().size,
                "streamsList must stay empty after the phantom-guard drop",
            )
            assertEquals(
                QuicConnection.Status.CONNECTED,
                client.status,
                "connection must stay CONNECTED across a duplicate STREAM frame",
            )
        }

    @Test
    fun phantomGuardDropsRetransmitOnRetiredClientBidiStream() =
        runBlocking {
            // Same shape as [phantomGuardDropsRetransmitOnRetiredPeerStream]
            // but for a CLIENT_BIDI id we opened ourselves. The audit-4 #5
            // guard rejects STREAM frames on client-initiated ids without a
            // live local stream (server squatting). Pre-fix that guard fired
            // BEFORE the retired-id check, so msquic-style aggressive
            // retransmits on a stream we'd already retired (peer's
            // loss-detector refire racing our FIN-ACK) closed the
            // connection with STREAM_STATE_ERROR. Observed in the parallel
            // `transfer` interop test where retransmits on retired
            // streams 0/4 truncated whichever stream was still mid-receive.
            val (client, pipe) = newConnectedClient()
            val stream = client.openBidiStream()
            val streamId = stream.streamId
            stream.send.enqueue("hi".encodeToByteArray())
            stream.send.finish()
            val anyOutput = drainAll(client, pipe)
            assertTrue(anyOutput, "writer must emit our request + FIN")

            // Peer ACKs everything sent so far → finAcked latches.
            val largestSent = client.application.pnSpace.nextPacketNumber - 1L
            val ackPacket =
                pipe.buildServerApplicationDatagram(
                    listOf(
                        AckFrame(
                            largestAcknowledged = largestSent,
                            ackDelay = 0L,
                            firstAckRange = largestSent,
                        ),
                    ),
                )!!
            feedDatagram(client, ackPacket, nowMillis = 0L)
            assertTrue(stream.send.finAcked, "send-side FIN must be ACKed")

            // Peer responds and FINs the bidi stream.
            val responseFrame =
                StreamFrame(
                    streamId = streamId,
                    offset = 0L,
                    data = "hello".encodeToByteArray(),
                    fin = true,
                )
            feedDatagram(
                client,
                pipe.buildServerApplicationDatagram(listOf(responseFrame))!!,
                nowMillis = 0L,
            )
            // Drain the application-side flow → isFullyRead transitions true.
            client.streamById(streamId)?.incoming?.toList()
            // Trigger retirement at the top of the next buildApplicationPacket.
            drainAll(client, pipe)
            assertTrue(
                client.isStreamIdRetiredLocked(streamId),
                "fully-settled CLIENT_BIDI stream must enter retired-id ring",
            )

            // The regression: replay the same STREAM+FIN frame as a peer
            // retransmit. Pre-fix the audit-4 #5 guard fired with reason
            // "peer opened stream X on client-initiated id space
            // (STREAM_STATE_ERROR)" because it ran BEFORE the retired-id
            // check. Post-fix the guard exempts retired ids and the
            // existing phantom-stream branch silently drops the frame.
            feedDatagram(
                client,
                pipe.buildServerApplicationDatagram(listOf(responseFrame))!!,
                nowMillis = 0L,
            )

            assertEquals(
                QuicConnection.Status.CONNECTED,
                client.status,
                "retransmit on retired CLIENT_BIDI id must NOT close the connection",
            )
            assertEquals(
                0,
                client.streamsListLocked().size,
                "retransmit must not mint a phantom stream entry",
            )
        }

    @Test
    fun retirementPreservesConnectionLevelMaxDataAccounting() =
        runBlocking {
            // Without the retiredStreamsRecvBytes seed, the writer's
            // `totalRecvAdvanced` would reset to 0 after retirement and
            // its half-window MAX_DATA threshold check would never fire
            // again — the peer's send credit would silently freeze at
            // initialMaxData. Pin the seed by:
            //  1. Pushing enough peer bytes to exceed initialMaxData / 2,
            //     forcing a fresh MAX_DATA frame on the wire.
            //  2. Triggering retirement.
            //  3. Pushing a second wave that ALSO crosses the half-window
            //     threshold from the *post-retire* baseline.
            // If the writer's seed is wrong, step 3 fails to advance
            // advertisedMaxData and MaxDataFrame is not emitted.
            val (client, pipe) = newConnectedClient()
            val cfg = client.config
            val perStream = (cfg.initialMaxStreamDataUni / 4).coerceAtMost(8L * 1024)
            val streamCountForFirstWave =
                (cfg.initialMaxData / 2 / perStream + 1).toInt().coerceAtLeast(2)

            val firstStart = StreamId.build(StreamId.Kind.SERVER_UNI, 0L)
            for (i in 0 until streamCountForFirstWave) {
                val id = firstStart + 4L * i
                val payload = ByteArray(perStream.toInt()) { (it and 0xFF).toByte() }
                val packet =
                    pipe.buildServerApplicationDatagram(
                        listOf(StreamFrame(streamId = id, offset = 0L, data = payload, fin = true)),
                    )!!
                feedDatagram(client, packet, nowMillis = 0L)
                client.streamById(id)?.incoming?.toList()
            }

            // Drain so MAX_DATA goes out + retirement runs.
            drainAll(client, pipe)
            val advertisedAfterFirstWave = client.advertisedMaxData
            assertTrue(
                advertisedAfterFirstWave > cfg.initialMaxData,
                "first wave must have bumped advertisedMaxData (was=$advertisedAfterFirstWave " +
                    "initialMaxData=${cfg.initialMaxData}) — fixture pre-condition for the " +
                    "retirement-preserves-credit test",
            )
            assertEquals(
                0,
                client.streamsListLocked().size,
                "first wave must be fully retired before the second wave begins",
            )

            // Second wave — push enough additional bytes that the running
            // total (retired + open) crosses the next half-window
            // threshold. Without the seed in totalRecvAdvanced, the
            // second wave would not raise advertisedMaxData.
            val secondStart = firstStart + 4L * streamCountForFirstWave
            for (i in 0 until streamCountForFirstWave) {
                val id = secondStart + 4L * i
                val payload = ByteArray(perStream.toInt()) { ((it + 1) and 0xFF).toByte() }
                val packet =
                    pipe.buildServerApplicationDatagram(
                        listOf(StreamFrame(streamId = id, offset = 0L, data = payload, fin = true)),
                    )!!
                feedDatagram(client, packet, nowMillis = 0L)
                client.streamById(id)?.incoming?.toList()
            }
            drainAll(client, pipe)

            val advertisedAfterSecondWave = client.advertisedMaxData
            assertTrue(
                advertisedAfterSecondWave > advertisedAfterFirstWave,
                "second wave must continue to advance advertisedMaxData past " +
                    "$advertisedAfterFirstWave (was $advertisedAfterSecondWave) — if it didn't, " +
                    "retirement regressed the writer's connection-level MAX_DATA accounting",
            )
            // The cumulative receive total — folded into the writer's
            // totalRecvAdvanced via `retiredStreamsRecvBytes` — must
            // include every stream from both waves.
            val expectedCumulativeRecv = 2L * streamCountForFirstWave * perStream
            assertTrue(
                client.retiredStreamsRecvBytes >= expectedCumulativeRecv,
                "retiredStreamsRecvBytes must include both waves; got=${client.retiredStreamsRecvBytes} " +
                    "expected≥$expectedCumulativeRecv",
            )
        }

    @Test
    fun streamChurnSoakKeepsTrackerBounded() =
        runBlocking {
            // Soak-shape: simulate the moq-lite listener over many group
            // generations. 200 generations × 50 streams = 10 000 stream
            // lifecycles. Without retirement, streamsList would hold all
            // 10 000 entries. With retirement, the working set stays
            // bounded at the per-generation batch.
            val (client, pipe) = newConnectedClient()
            val perGen = 50
            val gens = 200
            val totalStreams = perGen * gens
            var maxLiveStreams = 0
            var nextStreamId = StreamId.build(StreamId.Kind.SERVER_UNI, 0L)

            for (g in 0 until gens) {
                for (i in 0 until perGen) {
                    val payload = ByteArray(8) { ((g * perGen + i) and 0xFF).toByte() }
                    val packet =
                        pipe.buildServerApplicationDatagram(
                            listOf(StreamFrame(streamId = nextStreamId, offset = 0L, data = payload, fin = true)),
                        )!!
                    feedDatagram(client, packet, nowMillis = 0L)
                    client.streamById(nextStreamId)?.incoming?.toList()
                    nextStreamId += 4L
                }
                val live = client.streamsListLocked().size
                if (live > maxLiveStreams) maxLiveStreams = live
                drainAll(client, pipe)
            }

            assertEquals(
                0,
                client.streamsListLocked().size,
                "after the final retirement pass streamsList must drain fully",
            )
            assertEquals(
                totalStreams.toLong(),
                client.retiredStreamsCount,
                "retiredStreamsCount must equal every stream the soak ever opened",
            )
            // Bound: working set should stay near per-generation batch.
            // If retirement regressed (e.g. only fired at the end), we
            // would observe the full totalStreams here.
            assertTrue(
                maxLiveStreams <= 2 * perGen,
                "tracker working set must stay bounded at ~$perGen but observed $maxLiveStreams " +
                    "across $gens generations — retirement is leaking",
            )
        }

    /**
     * Drain every outbound application packet the writer has to send.
     * Returns true if at least one was emitted. The pipe consumes them
     * (decrypting each one updates its inbound PN tracking) but does
     * not itself reply — these tests inject ACKs explicitly.
     */
    private fun drainAll(
        client: QuicConnection,
        pipe: InMemoryQuicPipe,
    ): Boolean {
        var any = false
        while (true) {
            val out = drainOutbound(client, nowMillis = 0L) ?: break
            any = true
            // Decrypting bumps the pipe's applicationPnSpace so future
            // server-built packets carry an up-to-date `largestAckedInSpace`
            // value (avoids the truncated-PN encoder underflow that
            // otherwise breaks long sequences).
            pipe.decryptClientApplicationFrames(out)
        }
        return any
    }

    // moq-lite-shaped fixture: 4 K bidi caps + 64 K peer-uni caps so
    // the soak harness can churn 10 K + streams without hitting
    // either the peer cap or our advertised cap before the writer's
    // periodic MAX_STREAMS_UNI extension fires.
    private fun newConnectedClient(): Pair<QuicConnection, InMemoryQuicPipe> =
        com.vitorpamplona.quic.connection.newConnectedClient(
            maxStreamsBidi = 4096,
            maxStreamsUni = 65_536,
            maxData = 16L * 1024 * 1024,
        )
}
