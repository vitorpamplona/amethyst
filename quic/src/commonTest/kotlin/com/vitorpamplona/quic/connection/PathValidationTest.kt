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

import com.vitorpamplona.quic.frame.PathChallengeFrame
import com.vitorpamplona.quic.frame.PathResponseFrame
import com.vitorpamplona.quic.frame.decodeFrames
import com.vitorpamplona.quic.frame.encodeFrames
import com.vitorpamplona.quic.tls.InProcessTlsServer
import com.vitorpamplona.quic.tls.PermissiveCertificateValidator
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * RFC 9000 §8.2 path validation — minimum-viable peer-initiated case.
 *
 * Soak target #4 from the audio-rooms hardening pass: support
 * the spec-required PATH_CHALLENGE / PATH_RESPONSE round-trip.
 *
 * Scope of this landing:
 *  - Frame codec for PATH_CHALLENGE (0x1A) and PATH_RESPONSE
 *    (0x1B) — was previously decoded-and-discarded.
 *  - Server-initiated path validation: peer sends
 *    PATH_CHALLENGE; client MUST echo the SAME 8-byte payload
 *    in a PATH_RESPONSE on the next outbound packet.
 *
 * Out of scope (explicit follow-on):
 *  - Client-initiated migration: requires UdpSocket replacement,
 *    new-CID acquisition tracking, and validating the new path
 *    BEFORE moving traffic to it.
 *  - Anti-amplification on unvalidated paths (RFC 9000 §8.1).
 *
 * Why this matters even without client-initiated migration: any
 * compliant peer (server or middlebox) MAY probe the path at any
 * time — most commonly after a NAT rebind or our connection-id
 * rotation. A peer that doesn't see a PATH_RESPONSE within a few
 * RTTs may declare the path dead and tear the connection down,
 * visible to users as a sudden audio cut on a phone that briefly
 * switched cells.
 */
class PathValidationTest {
    @Test
    fun pathChallengeFrameRoundTripsThroughCodec() {
        val payload = byteArrayOf(0x01, 0x23, 0x45, 0x67, -0x77, -0x55, -0x33, -0x11)
        val encoded = encodeFrames(listOf(PathChallengeFrame(payload)))
        val decoded = decodeFrames(encoded)
        assertEquals(1, decoded.size)
        val frame = decoded.first() as PathChallengeFrame
        assertContentEquals(
            payload,
            frame.data,
            "PATH_CHALLENGE codec must round-trip the 8-byte payload byte-for-byte",
        )
    }

    @Test
    fun pathResponseFrameRoundTripsThroughCodec() {
        val payload = byteArrayOf(-0x80, 0x7F, 0x00, -0x01, 0x55, -0x56, 0x42, -0x43)
        val encoded = encodeFrames(listOf(PathResponseFrame(payload)))
        val decoded = decodeFrames(encoded)
        assertEquals(1, decoded.size)
        val frame = decoded.first() as PathResponseFrame
        assertContentEquals(payload, frame.data)
    }

    @Test
    fun pathChallengeFrameRejectsNonEightByteData() {
        try {
            PathChallengeFrame(ByteArray(7))
            error("PATH_CHALLENGE constructor must reject < 8 bytes")
        } catch (_: IllegalArgumentException) {
            // expected
        }
        try {
            PathChallengeFrame(ByteArray(9))
            error("PATH_CHALLENGE constructor must reject > 8 bytes")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun inboundPathChallengeQueuesMatchingPathResponse() =
        runBlocking {
            val (client, pipe) = newConnectedClient()
            val challengeData = byteArrayOf(0xCA.toByte(), 0xFE.toByte(), 0xBA.toByte(), 0xBE.toByte(), 0xDE.toByte(), 0xAD.toByte(), 0xBE.toByte(), 0xEF.toByte())

            // Server sends a PATH_CHALLENGE in a 1-RTT packet. Pre-fix
            // the client would silently absorb it — the connection
            // would stay CONNECTED but the peer would never see a
            // PATH_RESPONSE, eventually declaring the path dead.
            val packet = pipe.buildServerApplicationDatagram(listOf(PathChallengeFrame(challengeData)))!!
            feedDatagram(client, packet, nowMillis = 0L)

            // Drain the next outbound packet and verify it carries a
            // PATH_RESPONSE with the EXACT same 8 bytes.
            val outbound = drainOutbound(client, nowMillis = 0L)
            assertTrue(outbound != null, "client must emit an outbound packet (ACK + PATH_RESPONSE)")
            val frames = pipe.decryptClientApplicationFrames(outbound)
            assertTrue(frames != null, "outbound packet must decrypt with the live application keys")
            val response = frames.firstOrNull { it is PathResponseFrame } as? PathResponseFrame
            assertTrue(response != null, "outbound packet must contain a PATH_RESPONSE — got ${frames.map { it::class.simpleName }}")
            assertContentEquals(
                challengeData,
                response.data,
                "PATH_RESPONSE MUST echo the PATH_CHALLENGE payload exactly (byte equality is " +
                    "the discriminator the peer uses to match a response to its outstanding challenge)",
            )
            assertEquals(QuicConnection.Status.CONNECTED, client.status)
        }

    @Test
    fun multipleQueuedPathChallengesDrainAsMultipleResponses() =
        runBlocking {
            // RFC 9000 §8.2: a peer MAY send several PATH_CHALLENGEs
            // (e.g. validation retries on packet loss). Each one
            // requires its own PATH_RESPONSE — a single response
            // doesn't subsume earlier ones because the payload bytes
            // are independent random values.
            val (client, pipe) = newConnectedClient()
            val challenges =
                listOf(
                    ByteArray(8) { 0x10.toByte() },
                    ByteArray(8) { 0x20.toByte() },
                    ByteArray(8) { 0x30.toByte() },
                )
            for (data in challenges) {
                val packet = pipe.buildServerApplicationDatagram(listOf(PathChallengeFrame(data)))!!
                feedDatagram(client, packet, nowMillis = 0L)
            }

            // Drain all outbound packets and collect every PATH_RESPONSE
            // we see. The writer can fold all three into one packet
            // (each is just 9 wire bytes) but might also split — either
            // shape is spec-correct.
            val responses = mutableListOf<PathResponseFrame>()
            while (true) {
                val out = drainOutbound(client, nowMillis = 0L) ?: break
                val frames = pipe.decryptClientApplicationFrames(out) ?: continue
                for (f in frames) {
                    if (f is PathResponseFrame) responses += f
                }
            }
            assertEquals(
                challenges.size,
                responses.size,
                "client must emit exactly one PATH_RESPONSE per inbound PATH_CHALLENGE",
            )
            // The responses can arrive in any order; match by content.
            val responseSet = responses.map { it.data.toList() }.toSet()
            val challengeSet = challenges.map { it.toList() }.toSet()
            assertEquals(
                challengeSet,
                responseSet,
                "every challenge payload must appear in some response",
            )
        }

    @Test
    fun pathResponseQueueIsBoundedAgainstChallengeFlood() =
        runBlocking {
            // Defence-in-depth: an attacker spamming PATH_CHALLENGE
            // shouldn't pin arbitrary memory in our pendingPathResponses
            // queue. Cap is MAX_PENDING_PATH_RESPONSES (64); excess
            // challenges are silently dropped — the protocol allows
            // it (peer would retransmit on PTO if a response actually
            // mattered).
            val (client, pipe) = newConnectedClient()
            val flood = QuicConnection.MAX_PENDING_PATH_RESPONSES * 4
            for (i in 0 until flood) {
                val data = ByteArray(8) { ((i shr 8) and 0xFF).toByte() }
                data[7] = (i and 0xFF).toByte()
                val packet = pipe.buildServerApplicationDatagram(listOf(PathChallengeFrame(data)))!!
                feedDatagram(client, packet, nowMillis = 0L)
            }

            val responses = mutableListOf<PathResponseFrame>()
            while (true) {
                val out = drainOutbound(client, nowMillis = 0L) ?: break
                val frames = pipe.decryptClientApplicationFrames(out) ?: continue
                for (f in frames) {
                    if (f is PathResponseFrame) responses += f
                }
            }
            assertTrue(
                responses.size <= QuicConnection.MAX_PENDING_PATH_RESPONSES,
                "response count ${responses.size} must not exceed cap " +
                    "${QuicConnection.MAX_PENDING_PATH_RESPONSES}",
            )
            // And: connection survives the flood.
            assertEquals(QuicConnection.Status.CONNECTED, client.status)
        }

    private fun newConnectedClient(): Pair<QuicConnection, InMemoryQuicPipe> =
        runBlocking {
            val client =
                QuicConnection(
                    serverName = "path.test",
                    config = QuicConnectionConfig(),
                    tlsCertificateValidator = PermissiveCertificateValidator(),
                )
            val serverScid = ConnectionId.random(8)
            val tlsServer =
                InProcessTlsServer(
                    transportParameters =
                        TransportParameters(
                            initialMaxData = 1L * 1024 * 1024,
                            initialMaxStreamDataBidiLocal = 64L * 1024,
                            initialMaxStreamDataBidiRemote = 64L * 1024,
                            initialMaxStreamDataUni = 64L * 1024,
                            initialMaxStreamsBidi = 16,
                            initialMaxStreamsUni = 16,
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
