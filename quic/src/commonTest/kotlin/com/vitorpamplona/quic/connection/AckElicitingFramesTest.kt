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

import com.vitorpamplona.quic.frame.HandshakeDoneFrame
import com.vitorpamplona.quic.frame.MaxDataFrame
import com.vitorpamplona.quic.frame.MaxStreamDataFrame
import com.vitorpamplona.quic.frame.MaxStreamsFrame
import com.vitorpamplona.quic.frame.PaddingFrame
import com.vitorpamplona.quic.frame.ResetStreamFrame
import com.vitorpamplona.quic.tls.PermissiveCertificateValidator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Round-5 regression: every frame the parser dispatches MUST set
 * `ackEliciting = true` if RFC 9000 §13.2.1 lists the frame type as
 * ack-eliciting. Pre-fix the new ACK gating (round-4 perf #1) caused a
 * packet carrying only e.g. MAX_DATA or HANDSHAKE_DONE to never trigger an
 * ACK — the peer would PTO-retransmit forever.
 *
 * Tests drive each frame through a CONNECTED client and assert that a
 * subsequent drainOutbound produces a packet (which contains the ACK).
 */
class AckElicitingFramesTest {
    private fun connectedClient(): Pair<QuicConnection, InMemoryQuicPipe> {
        val client =
            QuicConnection(
                serverName = "example.test",
                config = QuicConnectionConfig(),
                tlsCertificateValidator = PermissiveCertificateValidator(),
            )
        val pipe = InMemoryQuicPipe(client, client.destinationConnectionId.bytes)
        client.start()
        pipe.drive(maxRounds = 16)
        check(client.status == QuicConnection.Status.CONNECTED)
        // Drain any handshake-induced ACKs out of the way so subsequent
        // tests see a clean state.
        kotlinx.coroutines.runBlocking {
            // Pull whatever the client has queued post-handshake; we don't
            // care about the contents, only that the slate is clean.
            while (drainOutbound(client, nowMillis = 0L) != null) { /* drain */ }
        }
        return client to pipe
    }

    @Test
    fun max_data_alone_triggers_an_ack() {
        // Pre-round-5: the parser handled MaxDataFrame without setting
        // ackEliciting, so the round-4 ACK gate refused to emit an ACK.
        val (client, pipe) = connectedClient()
        // Server sends a packet carrying ONLY MAX_DATA (well, plus padding to
        // hit the HP-sample minimum).
        val packet =
            pipe.buildServerApplicationDatagram(
                listOf(MaxDataFrame(2_000_000), PaddingFrame, PaddingFrame, PaddingFrame),
            )!!
        feedDatagram(client, packet, nowMillis = 0L)
        // The client should now want to send an ACK.
        val out = drainOutbound(client, nowMillis = 0L)
        assertNotNull(
            out,
            "MAX_DATA must be ack-eliciting; client must produce an ACK packet",
        )
    }

    @Test
    fun max_stream_data_alone_triggers_an_ack() {
        val (client, pipe) = connectedClient()
        // First open a stream so MaxStreamDataFrame has a target to update.
        val packet =
            pipe.buildServerApplicationDatagram(
                listOf(
                    MaxStreamDataFrame(streamId = 0L, maxStreamData = 50_000),
                    PaddingFrame,
                    PaddingFrame,
                    PaddingFrame,
                ),
            )!!
        feedDatagram(client, packet, nowMillis = 0L)
        assertNotNull(drainOutbound(client, nowMillis = 0L))
    }

    @Test
    fun max_streams_alone_triggers_an_ack() {
        val (client, pipe) = connectedClient()
        val packet =
            pipe.buildServerApplicationDatagram(
                listOf(
                    MaxStreamsFrame(bidi = true, maxStreams = 100),
                    PaddingFrame,
                    PaddingFrame,
                    PaddingFrame,
                ),
            )!!
        feedDatagram(client, packet, nowMillis = 0L)
        assertNotNull(drainOutbound(client, nowMillis = 0L))
    }

    @Test
    fun handshake_done_alone_triggers_an_ack() {
        val (client, pipe) = connectedClient()
        // Pad heavily so HP-sample minimum is met.
        val pings = List(40) { PaddingFrame }
        val packet = pipe.buildServerApplicationDatagram(listOf(HandshakeDoneFrame()) + pings)!!
        feedDatagram(client, packet, nowMillis = 0L)
        assertNotNull(drainOutbound(client, nowMillis = 0L))
    }

    @Test
    fun reset_stream_alone_triggers_an_ack() {
        val (client, pipe) = connectedClient()
        val frame = ResetStreamFrame(streamId = 1L, applicationErrorCode = 0L, finalSize = 0L)
        val pings = List(40) { PaddingFrame }
        val packet = pipe.buildServerApplicationDatagram(listOf(frame) + pings)!!
        feedDatagram(client, packet, nowMillis = 0L)
        assertNotNull(drainOutbound(client, nowMillis = 0L))
    }

    @Test
    fun reset_stream_on_client_uni_id_closes_connection() {
        // Round-5 #2: peer can't RESET_STREAM a stream we own the only side
        // of (CLIENT_UNI). It's STREAM_STATE_ERROR.
        val (client, pipe) = connectedClient()
        val clientUniId = 2L // id % 4 == 2 → CLIENT_UNI
        val frame =
            ResetStreamFrame(
                streamId = clientUniId,
                applicationErrorCode = 0L,
                finalSize = 0L,
            )
        val pings = List(40) { PaddingFrame }
        val packet = pipe.buildServerApplicationDatagram(listOf(frame) + pings)!!
        feedDatagram(client, packet, nowMillis = 0L)
        assertEquals(
            QuicConnection.Status.CLOSED,
            client.status,
            "RESET_STREAM on CLIENT_UNI is STREAM_STATE_ERROR; peer has no send side",
        )
    }
}
