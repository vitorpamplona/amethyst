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

import com.vitorpamplona.quic.crypto.Aes128Gcm
import com.vitorpamplona.quic.crypto.AesEcbHeaderProtection
import com.vitorpamplona.quic.crypto.InitialProtection
import com.vitorpamplona.quic.crypto.InitialSecrets
import com.vitorpamplona.quic.crypto.PlatformAesOneBlock
import com.vitorpamplona.quic.frame.PingFrame
import com.vitorpamplona.quic.frame.encodeFrames
import com.vitorpamplona.quic.packet.LongHeaderPacket
import com.vitorpamplona.quic.packet.LongHeaderPlaintextPacket
import com.vitorpamplona.quic.packet.LongHeaderType
import com.vitorpamplona.quic.packet.QuicVersion
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Coalesced-packet skip behaviour, RFC 9000 §12.2 + RFC 9001 §5.5.
 *
 * Multiple QUIC packets can share a single UDP datagram. The receive loop in
 * [feedDatagram] walks across them by trusting the long-header `length`
 * field. Two cases the audit-3 review specifically called out:
 *
 *   1. Two valid packets coalesced — both must be observed (the loop must
 *      not stop at the first).
 *   2. A packet whose AEAD verification fails — the receiver must drop ONLY
 *      that packet, advance using `peekHeader`'s totalLength, and continue
 *      with the next one (RFC 9001 §5.5: "the receiver MUST attempt to
 *      process all coalesced packets").
 *
 * Pre-fix the loop broke on first decrypt failure, dropping any subsequent
 * packets in the datagram on the floor — a silent data-loss bug that would
 * have surfaced as flaky handshakes.
 */
class CoalescedPacketSkipTest {
    private val hp = AesEcbHeaderProtection(PlatformAesOneBlock)

    private fun buildInitial(
        client: QuicConnection,
        secrets: InitialProtection,
        packetNumber: Long,
        scid: ConnectionId,
        payload: ByteArray,
    ): ByteArray =
        LongHeaderPacket.build(
            LongHeaderPlaintextPacket(
                type = LongHeaderType.INITIAL,
                version = QuicVersion.V1,
                dcid = client.sourceConnectionId,
                scid = scid,
                packetNumber = packetNumber,
                payload = payload,
            ),
            Aes128Gcm,
            secrets.serverKey,
            secrets.serverIv,
            hp,
            secrets.serverHp,
            largestAckedInSpace = -1L,
        )

    @Test
    fun two_coalesced_initial_packets_are_both_observed() {
        // Fresh client: Initial keys auto-install in the constructor based on
        // its DCID. We don't drive the handshake — we just want to verify the
        // datagram-level loop walks across both packets.
        val client =
            QuicConnection(
                serverName = "example.test",
                config = QuicConnectionConfig(),
                tlsCertificateValidator = null,
            )
        val secrets = InitialSecrets.derive(client.destinationConnectionId.bytes)
        val serverScid = ConnectionId.random(8)

        // PING + PADDING. The padding is required because header protection
        // samples 16 bytes starting 4 bytes past the PN offset — a bare PING
        // (1 byte plaintext + 16 byte AEAD tag) doesn't leave enough.
        val ping = encodeFrames(listOf(PingFrame)) + ByteArray(24)
        val pkt0 = buildInitial(client, secrets, packetNumber = 0L, scid = serverScid, payload = ping)
        val pkt1 = buildInitial(client, secrets, packetNumber = 1L, scid = serverScid, payload = ping)

        // Concatenate into one datagram (RFC 9000 §12.2 coalesced shape).
        val coalesced = pkt0 + pkt1
        feedDatagram(client, coalesced, nowMillis = 0L)

        // If the loop stopped after the first packet we'd have largestReceived=0.
        // Both packets observed → largestReceived advances to 1.
        assertEquals(
            1L,
            client.initial.pnSpace.largestReceived,
            "second coalesced packet must also be processed; loop must walk past the first",
        )
    }

    @Test
    fun corrupted_first_packet_does_not_swallow_a_valid_second() {
        val client =
            QuicConnection(
                serverName = "example.test",
                config = QuicConnectionConfig(),
                tlsCertificateValidator = null,
            )
        val secrets = InitialSecrets.derive(client.destinationConnectionId.bytes)
        val serverScid = ConnectionId.random(8)

        val ping = encodeFrames(listOf(PingFrame)) + ByteArray(24)
        val pkt0Good = buildInitial(client, secrets, packetNumber = 0L, scid = serverScid, payload = ping)
        // Corrupt the AEAD tag (last byte) of the first packet — header still
        // parses cleanly under peekHeader, but parseAndDecrypt fails GCM
        // verification and returns null.
        val pkt0Corrupt = pkt0Good.copyOf()
        pkt0Corrupt[pkt0Corrupt.size - 1] = (pkt0Corrupt[pkt0Corrupt.size - 1].toInt() xor 0x01).toByte()

        val pkt1 = buildInitial(client, secrets, packetNumber = 1L, scid = serverScid, payload = ping)

        feedDatagram(client, pkt0Corrupt + pkt1, nowMillis = 0L)

        // Pre-fix: loop broke on the decrypt failure, largestReceived stays -1.
        // Post-fix: loop advances by peekHeader.totalLength and processes pkt1.
        assertEquals(
            1L,
            client.initial.pnSpace.largestReceived,
            "valid second packet must still be processed when the first fails decrypt",
        )
    }

    @Test
    fun feed_stops_cleanly_when_header_is_truncated_inside_a_coalesced_run() {
        // Defensive case: if a coalesced run ends with a truncated header
        // (peekHeader returns null), the loop must `break` rather than spin
        // or read past the buffer. The first packet's effects MUST still be
        // observable.
        val client =
            QuicConnection(
                serverName = "example.test",
                config = QuicConnectionConfig(),
                tlsCertificateValidator = null,
            )
        val secrets = InitialSecrets.derive(client.destinationConnectionId.bytes)
        val serverScid = ConnectionId.random(8)

        val ping = encodeFrames(listOf(PingFrame)) + ByteArray(24)
        val pkt0 = buildInitial(client, secrets, packetNumber = 0L, scid = serverScid, payload = ping)

        // Append two bytes that look like the start of a long-header packet
        // (high bit set) but aren't enough for peekHeader to succeed.
        val truncated = pkt0 + byteArrayOf(0xC0.toByte(), 0x00)

        feedDatagram(client, truncated, nowMillis = 0L)

        // First packet processed, loop exits cleanly without throwing.
        assertEquals(0L, client.initial.pnSpace.largestReceived)
    }
}
