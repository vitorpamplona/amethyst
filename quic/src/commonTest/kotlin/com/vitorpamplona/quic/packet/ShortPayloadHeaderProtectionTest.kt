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
package com.vitorpamplona.quic.packet

import com.vitorpamplona.quic.connection.ConnectionId
import com.vitorpamplona.quic.crypto.Aes128Gcm
import com.vitorpamplona.quic.crypto.AesEcbHeaderProtection
import com.vitorpamplona.quic.crypto.InitialSecrets
import com.vitorpamplona.quic.crypto.PlatformAesOneBlock
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * RFC 9001 §5.4.2: header protection samples 16 bytes starting 4 bytes after
 * the packet number offset. The sender MUST pad short plaintext payloads so
 * that pnLen + payload >= 4 — otherwise the encrypted output is too short
 * for the sample window and the build path tripped a `require` with
 * `packet too short for HP sample`.
 *
 * The crash was reachable from the application path whenever
 * [com.vitorpamplona.quic.connection.QuicConnectionWriter.buildApplicationPacket]
 * produced a packet whose only frame was a single-byte PING (e.g. a PTO
 * probe with no ACKs queued and no streams to drain) while the packet
 * number space was still small enough to encode in 1 byte.
 */
class ShortPayloadHeaderProtectionTest {
    private val dcid = ConnectionId("8394c8f03e515708".hexToByteArray())
    private val scid = ConnectionId("01020304".hexToByteArray())
    private val proto = InitialSecrets.derive(dcid.bytes)
    private val hp = AesEcbHeaderProtection(PlatformAesOneBlock)

    @Test
    fun short_header_with_one_byte_ping_payload_round_trips() {
        // 0x01 is the on-wire encoding of a PING frame. With pnLen=1 this is
        // exactly the path that crashed in production (NestRx audio rooms).
        val pingPayload = byteArrayOf(0x01)
        val plain =
            ShortHeaderPlaintextPacket(
                dcid = dcid,
                packetNumber = 0L,
                payload = pingPayload,
            )
        val wire =
            ShortHeaderPacket.build(
                plain = plain,
                aead = Aes128Gcm,
                key = proto.clientKey,
                iv = proto.clientIv,
                hp = hp,
                hpKey = proto.clientHp,
                largestAckedInSpace = -1L,
            )

        val parsed =
            ShortHeaderPacket.parseAndDecrypt(
                bytes = wire,
                offset = 0,
                dcidLen = dcid.length,
                aead = Aes128Gcm,
                key = proto.clientKey,
                iv = proto.clientIv,
                hp = hp,
                hpKey = proto.clientHp,
                largestReceivedInSpace = -1L,
            )
        assertNotNull(parsed)
        assertEquals(0L, parsed.packet.packetNumber)
        // Plaintext is padded to 3 bytes (pnLen=1 → minPayload=3); the
        // original PING frame survives at offset 0 and trailing zeros decode
        // as PADDING frames per RFC 9000 §19.1.
        assertTrue(parsed.packet.payload.size >= 3)
        assertEquals(0x01.toByte(), parsed.packet.payload[0])
        for (i in 1 until parsed.packet.payload.size) {
            assertEquals(0x00.toByte(), parsed.packet.payload[i])
        }
    }

    @Test
    fun short_header_with_empty_payload_round_trips() {
        // An empty payload is a degenerate input but should not crash the
        // builder — it should pad to satisfy the HP sample window.
        val plain =
            ShortHeaderPlaintextPacket(
                dcid = dcid,
                packetNumber = 0L,
                payload = ByteArray(0),
            )
        val wire =
            ShortHeaderPacket.build(
                plain = plain,
                aead = Aes128Gcm,
                key = proto.clientKey,
                iv = proto.clientIv,
                hp = hp,
                hpKey = proto.clientHp,
                largestAckedInSpace = -1L,
            )
        val parsed =
            ShortHeaderPacket.parseAndDecrypt(
                bytes = wire,
                offset = 0,
                dcidLen = dcid.length,
                aead = Aes128Gcm,
                key = proto.clientKey,
                iv = proto.clientIv,
                hp = hp,
                hpKey = proto.clientHp,
                largestReceivedInSpace = -1L,
            )
        assertNotNull(parsed)
        assertEquals(0L, parsed.packet.packetNumber)
    }

    @Test
    fun long_header_with_short_payload_round_trips() {
        // Same hazard at the long-header level: a PING-only Initial or
        // Handshake packet would also trip the HP sample require.
        val pingPayload = byteArrayOf(0x01)
        val wire =
            LongHeaderPacket.build(
                plain =
                    LongHeaderPlaintextPacket(
                        type = LongHeaderType.HANDSHAKE,
                        dcid = dcid,
                        scid = scid,
                        packetNumber = 0L,
                        payload = pingPayload,
                    ),
                aead = Aes128Gcm,
                key = proto.clientKey,
                iv = proto.clientIv,
                hp = hp,
                hpKey = proto.clientHp,
                largestAckedInSpace = -1L,
            )
        val parsed =
            LongHeaderPacket.parseAndDecrypt(
                bytes = wire,
                offset = 0,
                aead = Aes128Gcm,
                key = proto.clientKey,
                iv = proto.clientIv,
                hp = hp,
                hpKey = proto.clientHp,
                largestReceivedInSpace = -1L,
            )
        assertNotNull(parsed)
        assertEquals(0L, parsed.packet.packetNumber)
        assertTrue(parsed.packet.payload.size >= 3)
        assertEquals(0x01.toByte(), parsed.packet.payload[0])
        for (i in 1 until parsed.packet.payload.size) {
            assertEquals(0x00.toByte(), parsed.packet.payload[i])
        }
    }

    @Test
    fun payload_at_or_above_threshold_is_unchanged() {
        // pnLen=1, payload=4: already satisfies pnLen+payload >= 4. The
        // builder MUST NOT add gratuitous padding — a regression there would
        // bloat every outbound 1-RTT packet.
        val payload = byteArrayOf(0x01, 0x02, 0x03, 0x04)
        val plain =
            ShortHeaderPlaintextPacket(
                dcid = dcid,
                packetNumber = 0L,
                payload = payload,
            )
        val wire =
            ShortHeaderPacket.build(
                plain = plain,
                aead = Aes128Gcm,
                key = proto.clientKey,
                iv = proto.clientIv,
                hp = hp,
                hpKey = proto.clientHp,
                largestAckedInSpace = -1L,
            )
        val parsed =
            ShortHeaderPacket.parseAndDecrypt(
                bytes = wire,
                offset = 0,
                dcidLen = dcid.length,
                aead = Aes128Gcm,
                key = proto.clientKey,
                iv = proto.clientIv,
                hp = hp,
                hpKey = proto.clientHp,
                largestReceivedInSpace = -1L,
            )
        assertNotNull(parsed)
        assertContentEquals(payload, parsed.packet.payload)
    }
}
