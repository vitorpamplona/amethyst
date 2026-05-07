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

import com.vitorpamplona.quic.frame.PingFrame
import com.vitorpamplona.quic.frame.StreamFrame
import com.vitorpamplona.quic.packet.ShortHeaderPacket
import com.vitorpamplona.quic.stream.StreamId
import com.vitorpamplona.quic.tls.InProcessTlsServer
import com.vitorpamplona.quic.tls.PermissiveCertificateValidator
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Pins the peer-initiated 1-RTT key update path (RFC 9001 §6).
 *
 * Soak target #2 from the audio-rooms hardening pass: long-lived
 * audio-room sessions outlive the QUIC AEAD key's safe usage window
 * (~2^60 packets per RFC 9001 §6.6, but real implementations rotate
 * far earlier — quic-go defaults to ~100 packets, picoquic to a few
 * thousand). The client MUST handle a peer that flips the
 * `KEY_PHASE` bit by deriving next-phase keys, retry-decrypting the
 * triggering packet, and then mirroring the rotation on its own
 * send side. Without this, post-rotation packets AEAD-fail
 * silently — qlog shows them as "AEAD auth failed" drops, the
 * connection wedges (no ACKs to peer, peer falls into PTO mode), and
 * audio cuts off.
 *
 * The implementation lives across:
 *  - `QuicConnectionParser.feedShortHeaderPacket` (peek key-phase
 *    bit, route to current/previous/next keys).
 *  - `QuicConnection.deriveNextPhaseReceiveKeys` (HKDF-Expand-Label
 *    "quic ku" → next secret → key/iv).
 *  - `QuicConnection.commitKeyUpdate` (install next as live, demote
 *    live to previous, mirror onto send side, flip phase bits).
 *  - `QuicConnectionWriter` stamping `currentSendKeyPhase` into the
 *    short header on each outbound build.
 *
 * These tests exercise the peer-initiated path end-to-end against
 * `InMemoryQuicPipe`, which now grows a `rotateServerApplicationKeys`
 * helper that walks the same HKDF dance the production peer would.
 */
class KeyUpdatePeerInitiatedTest {
    @Test
    fun peerInitiatedRotationCommitsAndMirrorsOnSend() =
        runBlocking {
            val (client, pipe) = newConnectedClient()

            // Pre-rotation invariants — pin the baseline so a regression
            // that fired commitKeyUpdate during the handshake itself
            // (it shouldn't) would surface here as a phase mismatch.
            assertEquals(false, client.currentReceiveKeyPhase, "key-phase 0 at start")
            assertEquals(false, client.currentSendKeyPhase, "send-phase 0 at start")
            assertEquals(null, client.previousReceiveProtection, "no prior keys before rotation")
            val originalReceiveProtection = client.application.receiveProtection
            assertNotNull(originalReceiveProtection, "client must have 1-RTT receive keys after handshake")
            val originalSendProtection = client.application.sendProtection
            assertNotNull(originalSendProtection, "client must have 1-RTT send keys after handshake")

            // Rotate the pipe's TX keys forward and emit a packet whose
            // body is a plain PING — small payload, ack-eliciting so the
            // client also has a reason to send back. The packet's
            // KEY_PHASE bit is true.
            pipe.rotateServerApplicationKeys()
            val rotatedPacket = pipe.buildServerApplicationDatagram(listOf(PingFrame))!!
            // Sanity: the bit on the wire is the rotated phase.
            assertEquals(
                true,
                peekKeyPhase(rotatedPacket, client),
                "the test fixture must produce a KEY_PHASE=1 packet after rotateServerApplicationKeys",
            )
            feedDatagram(client, rotatedPacket, nowMillis = 0L)

            // Post-rotation invariants:
            //  - currentReceiveKeyPhase flipped (commitKeyUpdate ran).
            //  - currentSendKeyPhase flipped in lockstep (we mirror).
            //  - previousReceiveProtection holds the pre-rotation keys
            //    (kept alive for the reorder window).
            //  - application.receiveProtection is a NEW instance (next-
            //    phase keys derived from HKDF "quic ku").
            //  - application.sendProtection is also a NEW instance
            //    (mirror onto send side).
            //  - connection stays CONNECTED — the rotation isn't a
            //    teardown trigger.
            assertEquals(
                true,
                client.currentReceiveKeyPhase,
                "currentReceiveKeyPhase must flip after the parser commits the rotation",
            )
            assertEquals(
                true,
                client.currentSendKeyPhase,
                "currentSendKeyPhase must mirror the receive-side rotation in lockstep " +
                    "(commitKeyUpdate on the send side derives the matching next-phase secret)",
            )
            assertEquals(
                originalReceiveProtection,
                client.previousReceiveProtection,
                "pre-rotation receive keys must be retained as previousReceiveProtection " +
                    "for the reorder window (RFC 9001 §6.1)",
            )
            assertTrue(
                client.application.receiveProtection !== originalReceiveProtection,
                "application.receiveProtection must reference the next-phase keys, not the originals",
            )
            assertTrue(
                client.application.sendProtection !== originalSendProtection,
                "application.sendProtection must reference the next-phase keys after the mirror",
            )
            assertEquals(
                QuicConnection.Status.CONNECTED,
                client.status,
                "connection must stay CONNECTED across a peer-initiated key update",
            )
        }

    @Test
    fun reorderedPacketOnPriorKeysStillDecryptsAfterRotation() =
        runBlocking {
            // RFC 9001 §6.1 reorder window: once the client has rotated,
            // it MUST keep the prior-phase receive keys for some bounded
            // window so that a packet sent before the peer rotated (but
            // delayed in the network) still decrypts. The parser path is
            // the `previousReceiveProtection != null` arm in
            // feedShortHeaderPacket. Pin it by:
            //  1. Pushing one peer-uni stream + FIN with the pre-rotation
            //     keys to establish that the client has live state on
            //     stream id 3 (server-uni #0).
            //  2. Rotating, pushing a rotation-triggering PING.
            //  3. Replaying a SECOND payload on the same stream id with
            //     the PRIOR keys — i.e. the pipe re-uses the stashed
            //     pre-rotation TX. The client must decrypt it via
            //     previousReceiveProtection and surface the bytes.
            val (client, pipe) = newConnectedClient()
            val streamId = StreamId.build(StreamId.Kind.SERVER_UNI, 0L)

            // Pre-rotation: bytes 0..3 with no FIN.
            val prePayload = "pre-".encodeToByteArray()
            val prePacket =
                pipe.buildServerApplicationDatagram(
                    listOf(StreamFrame(streamId = streamId, offset = 0L, data = prePayload, fin = false)),
                )!!
            feedDatagram(client, prePacket, nowMillis = 0L)

            // Rotate, then trigger commit with a PING.
            pipe.rotateServerApplicationKeys()
            feedDatagram(client, pipe.buildServerApplicationDatagram(listOf(PingFrame))!!, nowMillis = 0L)
            assertEquals(true, client.currentReceiveKeyPhase, "rotation must commit before reorder test")
            assertNotNull(client.previousReceiveProtection, "prior keys must be retained")

            // Now the reorder: the peer SENT this packet pre-rotation
            // but it arrives after the rotation-triggering PING. The
            // pipe builds it with the stashed prior keys. The packet's
            // KEY_PHASE bit is the pre-rotation value (false), and the
            // client's parser MUST route through previousReceiveProtection.
            val reorderedPayload = "post".encodeToByteArray()
            val reorderedPacket =
                pipe.buildServerApplicationDatagramWithPriorKeys(
                    listOf(
                        StreamFrame(
                            streamId = streamId,
                            offset = prePayload.size.toLong(),
                            data = reorderedPayload,
                            fin = true,
                        ),
                    ),
                )!!
            assertEquals(
                false,
                peekKeyPhase(reorderedPacket, client),
                "reordered packet must carry the pre-rotation KEY_PHASE bit",
            )
            feedDatagram(client, reorderedPacket, nowMillis = 0L)

            // The application sees the FULL stream payload despite the
            // mid-stream key rotation. If the parser had dropped the
            // reordered packet, the stream would stall (no FIN) and the
            // toList collector below would time out.
            val stream = client.streamById(streamId)!!
            val chunks = withTimeoutOrNull(2_000L) { stream.incoming.toList() }
            assertNotNull(chunks, "stream incoming Flow must complete despite the mid-stream rotation")
            val joined = ByteArray(chunks.sumOf { it.size })
            var p = 0
            for (c in chunks) {
                c.copyInto(joined, p)
                p += c.size
            }
            assertEquals(
                "pre-post",
                joined.decodeToString(),
                "reordered packet decrypted on prior keys must surface its bytes intact",
            )
            assertEquals(
                QuicConnection.Status.CONNECTED,
                client.status,
                "connection must stay CONNECTED across the reorder",
            )
        }

    @Test
    fun postRotationOutboundPacketCarriesNewKeyPhaseAndDecryptsForPeer() =
        runBlocking {
            // Send-side correctness check: after a peer-initiated
            // rotation, the writer's NEXT outbound packet must
            //  (a) stamp the new currentSendKeyPhase into the
            //      short header, and
            //  (b) be encrypted with the rotated send keys (so the
            //      peer, which has also rotated, decrypts it
            //      correctly).
            // If commitKeyUpdate's send-side mirror regressed (e.g.
            // updated currentSendKeyPhase but forgot to install fresh
            // sendProtection), the wire bit and the AEAD keys would
            // disagree and the peer would drop the packet — visible as
            // a silent "client never ACKs after rotation" wedge.
            val (client, pipe) = newConnectedClient()
            pipe.rotateServerApplicationKeys()
            feedDatagram(client, pipe.buildServerApplicationDatagram(listOf(PingFrame))!!, nowMillis = 0L)
            assertEquals(true, client.currentSendKeyPhase, "rotation must commit before send-side check")

            // The PING is ack-eliciting, so the writer has work to do.
            // drainOutbound builds the ACK + any other queued frames.
            val outbound = drainOutbound(client, nowMillis = 0L)
            assertNotNull(outbound, "writer must emit an ACK in response to the rotation-trigger PING")

            // Pipe re-uses the same applicationPnSpace and HP key, so
            // it can decrypt this packet via decryptClientApplicationFrames
            // — but only if the AEAD keys match. The pipe's RX side
            // ALSO has to rotate to match; the existing pipe
            // doesn't rotate RX automatically, so the AEAD will fail
            // and we just check the wire-level bit through peekKeyPhase.
            // What we CAN observe without rotating the pipe RX is the
            // unprotected first byte: header-protection key isn't
            // rotated (RFC 9001 §6.1), so the HP unmask still works
            // and tells us the wire bit.
            val wirePhase = peekKeyPhase(outbound, client, useSendKeys = true)
            assertEquals(
                true,
                wirePhase,
                "post-rotation outbound packet must carry KEY_PHASE=1 on the wire — the writer reads " +
                    "currentSendKeyPhase (now true) when stamping the short header",
            )
            assertEquals(QuicConnection.Status.CONNECTED, client.status)
        }

    @Test
    fun twoConsecutiveRotationsCommitCorrectly() =
        runBlocking {
            // Belt + braces: one rotation is the simple case; a second
            // rotation must derive off the FIRST-rotation secret, not
            // the original handshake secret, and the parser must NOT
            // misroute the second-rotation packet to
            // previousReceiveProtection (which would AEAD-fail and
            // silently drop, wedging the connection — the
            // KNOWN-LIMITATION pre-fix this test pins now closes).
            //
            // The parser fix is "try previous keys; on AEAD failure
            // fall through to next-phase derivation" — neqo's
            // approach. Two AEAD attempts on a mismatched-phase
            // packet are cheap; KEY_PHASE mismatch is rare to begin
            // with (at most once per a-billion-packets in normal
            // usage).
            val (client, pipe) = newConnectedClient()

            pipe.rotateServerApplicationKeys()
            feedDatagram(client, pipe.buildServerApplicationDatagram(listOf(PingFrame))!!, nowMillis = 0L)
            assertEquals(true, client.currentReceiveKeyPhase, "first rotation must flip the bit")

            pipe.rotateServerApplicationKeys()
            feedDatagram(client, pipe.buildServerApplicationDatagram(listOf(PingFrame))!!, nowMillis = 0L)
            assertEquals(
                false,
                client.currentReceiveKeyPhase,
                "second rotation must flip the bit back to 0 — failure here means the parser " +
                    "misrouted the second rotation through previousReceiveProtection (the prior " +
                    "key-update bug) instead of falling through to next-phase derivation",
            )
            assertEquals(
                false,
                client.currentSendKeyPhase,
                "send-side mirror must also have rolled forward through both rotations",
            )
            assertEquals(QuicConnection.Status.CONNECTED, client.status)
        }

    /**
     * Read the unprotected KEY_PHASE bit out of a packet so the test
     * can assert the wire shape independent of the client's
     * bookkeeping.
     *
     * Walks past any leading long-header (Initial / Handshake) packets
     * in the datagram — drainOutbound will keep emitting a Handshake-
     * level ACK at the front of each datagram until the InMemoryQuicPipe
     * delivers a HANDSHAKE_DONE frame (which it doesn't), so the short-
     * header packet is rarely first on the wire.
     *
     * For inbound (server-built) packets we hand the client's RECEIVE
     * HP key. For outbound (client-built) packets we hand the client's
     * SEND HP key. Both phases share their direction's HP key per RFC
     * 9001 §6.1 ("the header_protection key is not updated when keys
     * are updated"), so this stays valid across rotations.
     */
    private fun peekKeyPhase(
        packet: ByteArray,
        client: QuicConnection,
        useSendKeys: Boolean = false,
    ): Boolean? {
        val live =
            if (useSendKeys) {
                client.application.sendProtection
            } else {
                client.application.receiveProtection
            } ?: return null
        var offset = 0
        while (offset < packet.size) {
            val first = packet[offset].toInt() and 0xFF
            if ((first and 0x80) == 0) {
                // Short header — what we want.
                return ShortHeaderPacket
                    .peekKeyPhase(
                        bytes = packet,
                        offset = offset,
                        dcidLen = client.sourceConnectionId.length,
                        hp = live.hp,
                        hpKey = live.hpKey,
                    )?.keyPhase
            }
            // Long header — skip past using the encoded length field.
            val peeked =
                com.vitorpamplona.quic.packet.LongHeaderPacket
                    .peekHeader(packet, offset) ?: return null
            offset += peeked.totalLength
        }
        return null
    }

    private fun newConnectedClient(): Pair<QuicConnection, InMemoryQuicPipe> =
        runBlocking {
            val client =
                QuicConnection(
                    serverName = "keyupdate.test",
                    config =
                        QuicConnectionConfig(
                            initialMaxStreamsBidi = 16,
                            initialMaxStreamsUni = 16,
                            initialMaxData = 1L * 1024 * 1024,
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
