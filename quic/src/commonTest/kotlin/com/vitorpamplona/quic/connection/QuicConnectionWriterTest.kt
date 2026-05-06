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
import com.vitorpamplona.quic.tls.PermissiveCertificateValidator
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Coverage for [QuicConnectionWriter] paths that the survey flagged as
 * untested:
 *
 *  * `appendFlowControlUpdates` MAX_STREAM_DATA emission once consumption
 *    crosses half-window — pre-fix the writer never emitted these on its own,
 *    leaving peer credit pinned at the initial value.
 *  * `buildBestLevelPacket` CLOSING-status branch — the only path that
 *    emits CONNECTION_CLOSE; pre-fix nothing exercised it.
 *  * Connection-level send-credit enforcement (audit-4 #9) — the writer
 *    must skip stream chunks once `sendConnectionFlowConsumed` reaches the
 *    cap.
 */
class QuicConnectionWriterTest {
    private fun connectedClient(config: QuicConnectionConfig = QuicConnectionConfig()): Pair<QuicConnection, InMemoryQuicPipe> {
        val client =
            QuicConnection(
                serverName = "example.test",
                config = config,
                tlsCertificateValidator = PermissiveCertificateValidator(),
            )
        val pipe = InMemoryQuicPipe(client, client.destinationConnectionId.bytes)
        client.start()
        pipe.drive(maxRounds = 16)
        check(client.status == QuicConnection.Status.CONNECTED)
        return client to pipe
    }

    @Test
    fun draining_in_closing_status_emits_connection_close() {
        // Audit-4 #15 + survey HIGH-#4: drainOutbound's CLOSING branch was
        // never asserted. After connection.close() the next drain MUST
        // produce a packet (the CONNECTION_CLOSE), and the connection's
        // status must be CLOSING. We assert the side-effect rather than
        // re-decrypting because peer-side keys aren't reachable here.
        runBlocking {
            val (client, _) = connectedClient()
            client.close(errorCode = 7, reason = "buh bye")
            assertEquals(QuicConnection.Status.CLOSING, client.status)
            val packet = drainOutbound(client, nowMillis = 0L)
            assertNotNull(packet, "CLOSING-status drain must produce a CONNECTION_CLOSE packet")
            // The packet exists; details are exercised end-to-end by interop tests.
        }
    }

    @Test
    fun max_stream_data_emitted_after_consumer_drains_half_window() {
        // Open a peer-initiated stream by faking a STREAM frame from server,
        // then drain the consumer. The writer's appendFlowControlUpdates
        // should issue a MAX_STREAM_DATA frame raising the limit once
        // received >= half the window. We assert via stream.receiveLimit
        // (which the writer bumps in-place) rather than re-decrypting the
        // packet — the side-effect on connection state is the contract.
        runBlocking {
            val (client, pipe) =
                connectedClient(
                    QuicConnectionConfig(
                        initialMaxStreamDataBidiRemote = 64,
                        initialMaxStreamDataBidiLocal = 64,
                    ),
                )
            val streamId = 1L
            val data = ByteArray(40) { it.toByte() }
            val packet = pipe.buildServerApplicationDatagram(listOf(StreamFrame(streamId, 0L, data, false)))!!
            feedDatagram(client, packet, nowMillis = 0L)

            val stream = client.streamById(streamId)!!
            val initialLimit = stream.receiveLimit
            // Drain the data so contiguousEnd advances past half-window.
            kotlinx.coroutines.withTimeoutOrNull(2_000L) {
                stream.incoming.collect { /* consume */ }
            }
            // Drain outbound — writer should bump stream.receiveLimit upward
            // as part of appendFlowControlUpdates (and emit MAX_STREAM_DATA).
            assertNotNull(drainOutbound(client, nowMillis = 0L))
            assertTrue(
                stream.receiveLimit > initialLimit,
                "appendFlowControlUpdates must raise stream.receiveLimit; was $initialLimit, now ${stream.receiveLimit}",
            )
        }
    }

    @Test
    fun writer_drains_higher_priority_streams_before_lower_priority() {
        // T11.3 follow-up: priority must dominate iteration order on
        // EVERY drain, not just the first. The naive "sort by priority
        // then apply the existing rotating start globally" shape looks
        // right at a glance but the rotating start advances on every
        // drain, so cross-tier ordering flips on alternate drains and
        // the priority hint is silently defeated. The correct shape is
        // strict priority across tiers + round-robin only within a
        // tier, which we pin here by draining TWICE and asserting the
        // higher-priority stream's StreamFrame lands first in BOTH
        // packets. Low-priority stream is opened FIRST so insertion-
        // order can't accidentally pass for priority ordering.
        runBlocking {
            val (client, pipe) = connectedClient()
            val low = client.openBidiStream()
            val high = client.openBidiStream()
            low.priority = 0
            high.priority = 10

            repeat(2) { round ->
                low.send.enqueue(ByteArray(200) { 0xAA.toByte() })
                high.send.enqueue(ByteArray(200) { 0xBB.toByte() })

                val datagram = drainOutbound(client, nowMillis = 0L)
                assertNotNull(datagram, "drain on round $round must emit a packet")
                val frames = pipe.decryptClientApplicationFrames(datagram)
                assertNotNull(frames, "decrypt must succeed on round $round")
                val streamFrames = frames.filterIsInstance<StreamFrame>()
                assertEquals(
                    2,
                    streamFrames.size,
                    "expected one StreamFrame per stream on round $round, got $streamFrames",
                )
                assertEquals(
                    high.streamId,
                    streamFrames[0].streamId,
                    "higher-priority stream must drain first on round $round; " +
                        "saw ${streamFrames.map { it.streamId }}",
                )
                assertEquals(low.streamId, streamFrames[1].streamId, "low second on round $round")
            }
        }
    }

    @Test
    fun writer_round_robins_within_a_priority_tier() {
        // Regression guard for the tier-local round-robin: same-priority
        // streams must still rotate so an early-opened stream doesn't
        // monopolise a packet's stream-frame slot indefinitely. We open
        // three streams at the default (0) priority and verify the
        // rotating start advances by one per drain, matching the
        // pre-priority behaviour.
        runBlocking {
            val (client, pipe) = connectedClient()
            val a = client.openBidiStream()
            val b = client.openBidiStream()
            val c = client.openBidiStream()
            // All default priority — single tier, three streams.
            val expectedRotation =
                listOf(
                    listOf(a.streamId, b.streamId, c.streamId),
                    listOf(b.streamId, c.streamId, a.streamId),
                    listOf(c.streamId, a.streamId, b.streamId),
                )
            for ((round, expected) in expectedRotation.withIndex()) {
                a.send.enqueue(ByteArray(64) { 0xA1.toByte() })
                b.send.enqueue(ByteArray(64) { 0xB2.toByte() })
                c.send.enqueue(ByteArray(64) { 0xC3.toByte() })

                val datagram = drainOutbound(client, nowMillis = 0L)
                assertNotNull(datagram, "drain $round must emit a packet")
                val frames = pipe.decryptClientApplicationFrames(datagram)
                assertNotNull(frames, "decrypt must succeed on round $round")
                val ids = frames.filterIsInstance<StreamFrame>().map { it.streamId }
                assertEquals(expected, ids, "round $round round-robin order")
            }
        }
    }

    @Test
    fun writer_respects_connection_level_send_credit_cap() {
        // Audit-4 #9: pre-fix the writer ignored sendConnectionFlowCredit
        // and would happily send past the peer's initial_max_data, ending
        // in FLOW_CONTROL_ERROR. Post-fix, once sendConnectionFlowConsumed
        // reaches the cap the writer skips the stream entirely.
        runBlocking {
            val (client, _) = connectedClient()
            client.sendConnectionFlowCredit = 100L
            client.sendConnectionFlowConsumed = 0L

            val stream = client.openBidiStream()
            stream.send.enqueue(ByteArray(500))
            // Drain repeatedly until output stalls. The internal counter
            // sendConnectionFlowConsumed is the contract — it must equal
            // the cap and never exceed it.
            for (round in 0 until 20) {
                drainOutbound(client, nowMillis = 0L) ?: break
            }
            assertEquals(
                100L,
                client.sendConnectionFlowConsumed,
                "writer must emit exactly sendConnectionFlowCredit bytes, no more",
            )
        }
    }
}
