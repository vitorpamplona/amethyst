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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * Verifies the receive-side flow-control enforcement landed by audit-2.
 *
 * RFC 9000 §4.1: a peer that sends bytes past the receiver's advertised
 * MAX_STREAM_DATA limit MUST be torn down with FLOW_CONTROL_ERROR. Without
 * this enforcement a hostile peer could pin arbitrary memory by streaming
 * beyond the limit — the per-stream `incomingChannel` capacity (64 chunks)
 * caps the immediate damage, but the connection-level kill is what stops a
 * sustained attack.
 *
 * The parser implementation (QuicConnectionParser.kt:184) calls
 * `markClosedExternally` when `frame.offset + frame.data.size >
 * stream.receiveLimit`. These tests drive that path end-to-end via
 * [InMemoryQuicPipe], using a TLS server that advertises tiny per-stream
 * receive limits so we can produce the violation without huge buffers.
 */
class ReceiveLimitEnforcementTest {
    @Test
    fun peer_exceeding_per_stream_receive_limit_closes_connection() {
        val client =
            QuicConnection(
                serverName = "example.test",
                config =
                    QuicConnectionConfig(
                        // We advertise a small per-peer-bidi receive limit so the
                        // server only needs to push 100 bytes to overflow. The
                        // parser's check is `frameEnd > receiveLimit`, where
                        // receiveLimit defaults to initialMaxStreamDataBidiRemote
                        // for peer-initiated bidi streams.
                        initialMaxStreamDataBidiRemote = 32,
                        initialMaxStreamDataBidiLocal = 32,
                    ),
                tlsCertificateValidator =
                    com.vitorpamplona.quic.tls
                        .PermissiveCertificateValidator(),
            )
        // Audit-4 #7: TPs MUST include initial_source_connection_id matching
        // the SCID the server uses on the wire — otherwise the post-handshake
        // CID-validation step closes the connection.
        val serverScid = ConnectionId.random(8)
        val tlsServer =
            InProcessTlsServer(
                transportParameters =
                    TransportParameters(
                        initialMaxData = 1_000_000,
                        initialMaxStreamDataBidiLocal = 100_000,
                        initialMaxStreamDataBidiRemote = 100_000,
                        initialMaxStreamDataUni = 100_000,
                        initialMaxStreamsBidi = 16,
                        initialMaxStreamsUni = 16,
                        initialSourceConnectionId = serverScid.bytes,
                        originalDestinationConnectionId = client.destinationConnectionId.bytes,
                    ).encode(),
            )
        val pipe = InMemoryQuicPipe(client, client.destinationConnectionId.bytes, serverScid, tlsServer)
        client.start()
        pipe.drive(maxRounds = 16)
        assertEquals(QuicConnection.Status.CONNECTED, client.status)

        // Send a STREAM frame from server on a server-initiated bidi (id 1)
        // with 64 bytes — twice the client's advertised 32-byte cap. The
        // parser's frame loop must call markClosedExternally because the
        // last byte (offset 0 + size 64 = 64) exceeds receiveLimit (32).
        val streamId = 1L // server-initiated bidi: id % 4 == 1
        val payload = ByteArray(64) { it.toByte() }
        val streamFrame = StreamFrame(streamId = streamId, offset = 0L, data = payload, fin = false)
        val packet = pipe.buildServerApplicationDatagram(listOf(streamFrame))
        assertNotEquals(null, packet, "server must have application keys after handshake")
        feedDatagram(client, packet!!, nowMillis = 0L)

        // The connection is now CLOSED with the receive-limit reason. Without
        // the audit-2 fix this would have stayed CONNECTED (silently buffering
        // the over-limit bytes).
        assertEquals(
            QuicConnection.Status.CLOSED,
            client.status,
            "client must transition to CLOSED on receive-limit violation",
        )
    }

    @Test
    fun peer_within_per_stream_receive_limit_keeps_connection_open() {
        // Mirror of the violation test: same setup, but the server stays
        // strictly within the cap. The connection MUST remain CONNECTED, the
        // bytes MUST land in the stream's incoming buffer. Catches a regression
        // where the audit fix accidentally fires on the boundary value
        // (`==` vs `>` confusion).
        val client =
            QuicConnection(
                serverName = "example.test",
                config =
                    QuicConnectionConfig(
                        initialMaxStreamDataBidiRemote = 64,
                        initialMaxStreamDataBidiLocal = 64,
                    ),
                tlsCertificateValidator =
                    com.vitorpamplona.quic.tls
                        .PermissiveCertificateValidator(),
            )
        // Audit-4 #7: TPs MUST include initial_source_connection_id matching
        // the SCID the server uses on the wire — otherwise the post-handshake
        // CID-validation step closes the connection.
        val serverScid = ConnectionId.random(8)
        val tlsServer =
            InProcessTlsServer(
                transportParameters =
                    TransportParameters(
                        initialMaxData = 1_000_000,
                        initialMaxStreamDataBidiLocal = 100_000,
                        initialMaxStreamDataBidiRemote = 100_000,
                        initialMaxStreamDataUni = 100_000,
                        initialMaxStreamsBidi = 16,
                        initialMaxStreamsUni = 16,
                        initialSourceConnectionId = serverScid.bytes,
                        originalDestinationConnectionId = client.destinationConnectionId.bytes,
                    ).encode(),
            )
        val pipe = InMemoryQuicPipe(client, client.destinationConnectionId.bytes, serverScid, tlsServer)
        client.start()
        pipe.drive(maxRounds = 16)
        assertEquals(QuicConnection.Status.CONNECTED, client.status)

        // Send exactly 64 bytes — the cap. frameEnd == receiveLimit, which
        // the parser permits (strict `>` check).
        val streamId = 1L
        val payload = ByteArray(64) { it.toByte() }
        val frame = StreamFrame(streamId = streamId, offset = 0L, data = payload, fin = false)
        val packet = pipe.buildServerApplicationDatagram(listOf(frame))!!
        feedDatagram(client, packet, nowMillis = 0L)

        assertEquals(
            QuicConnection.Status.CONNECTED,
            client.status,
            "client at exact receive-limit boundary must remain CONNECTED",
        )
    }
}
