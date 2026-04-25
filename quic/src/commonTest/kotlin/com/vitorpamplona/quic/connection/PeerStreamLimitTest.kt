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

import com.vitorpamplona.quic.frame.MaxStreamsFrame
import com.vitorpamplona.quic.tls.InProcessTlsServer
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Verifies the audit-3 fix: peer-granted stream concurrency limits are now
 * tracked and enforced.
 *
 * Two paths feed the cap:
 *   1. Peer transport parameters at handshake (`initial_max_streams_bidi/uni`)
 *   2. Subsequent MAX_STREAMS frames (RFC 9000 §19.11)
 *
 * Without this enforcement, [QuicConnection.openBidiStream] silently allocated
 * stream IDs past the cap, and the peer eventually closed the connection with
 * STREAM_LIMIT_ERROR — a failure that surfaced as "connection randomly drops
 * after a burst of opens" rather than a clean error.
 */
class PeerStreamLimitTest {
    @Test
    fun open_bidi_throws_when_peer_advertises_zero_bidi_streams() {
        runBlocking {
            val client =
                QuicConnection(
                    serverName = "example.test",
                    config = QuicConnectionConfig(),
                    tlsCertificateValidator = null,
                )
            // Default InProcessTlsServer sends empty transport parameters — so
            // the client's peerMaxStreamsBidi resolves to 0 after handshake.
            val pipe = InMemoryQuicPipe(client, client.destinationConnectionId.bytes)
            client.start()
            pipe.drive(maxRounds = 16)
            assertEquals(QuicConnection.Status.CONNECTED, client.status)

            assertFailsWith<QuicStreamLimitException> {
                client.openBidiStream()
            }
        }
    }

    @Test
    fun open_bidi_succeeds_within_peer_advertised_cap_and_throws_at_boundary() {
        runBlocking {
            val client =
                QuicConnection(
                    serverName = "example.test",
                    config = QuicConnectionConfig(),
                    tlsCertificateValidator = null,
                )
            val serverTpBytes =
                TransportParameters(
                    initialMaxData = 1_000_000,
                    initialMaxStreamDataBidiLocal = 100_000,
                    initialMaxStreamDataBidiRemote = 100_000,
                    initialMaxStreamDataUni = 100_000,
                    initialMaxStreamsBidi = 3,
                    initialMaxStreamsUni = 0,
                ).encode()
            val tlsServer = InProcessTlsServer(transportParameters = serverTpBytes)
            val pipe =
                InMemoryQuicPipe(
                    client = client,
                    initialDcid = client.destinationConnectionId.bytes,
                    tlsServer = tlsServer,
                )
            client.start()
            pipe.drive(maxRounds = 16)
            assertEquals(QuicConnection.Status.CONNECTED, client.status)
            assertEquals(3L, client.peerMaxStreamsBidiSnapshot())

            // Three opens should succeed.
            client.openBidiStream()
            client.openBidiStream()
            client.openBidiStream()

            // Fourth must throw — we'd otherwise violate the peer's cap.
            assertFailsWith<QuicStreamLimitException> {
                client.openBidiStream()
            }
        }
    }

    @Test
    fun max_streams_frame_roundtrips_via_decode_frames() {
        // Bytes-level sanity: encode a MaxStreamsFrame and decode it back.
        // Catches a regression where the parser ignored MAX_STREAMS entirely
        // (the pre-fix `is MaxStreamsFrame -> { /* tracking left for later */ }`
        // branch was dead code as far as tests could observe).
        val encoded =
            com.vitorpamplona.quic.frame
                .encodeFrames(listOf(MaxStreamsFrame(bidi = true, maxStreams = 100)))
        val decoded =
            com.vitorpamplona.quic.frame
                .decodeFrames(encoded)
        assertEquals(1, decoded.size)
        val frame = decoded.first() as MaxStreamsFrame
        assertTrue(frame.bidi)
        assertEquals(100L, frame.maxStreams)
    }
}
