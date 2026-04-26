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

class InitialPacketRoundTripTest {
    /**
     * Round-trip an Initial packet through:
     *   client-side encrypt + HP-apply → wire bytes → server-side HP-strip + decrypt.
     *
     * Uses RFC 9001 Appendix A.1's canonical client DCID `0x8394c8f03e515708`
     * so the protection material matches the canonical vectors.
     */
    @Test
    fun client_initial_round_trip() {
        val dcid = ConnectionId("8394c8f03e515708".hexToByteArray())
        val scid = ConnectionId("00".hexToByteArray())
        val proto = InitialSecrets.derive(dcid.bytes)
        val hp = AesEcbHeaderProtection(PlatformAesOneBlock)

        val payload = "deadbeefcafebabe1234567890abcdef".hexToByteArray()
        val plain =
            LongHeaderPlaintextPacket(
                type = LongHeaderType.INITIAL,
                dcid = dcid,
                scid = scid,
                packetNumber = 0L,
                payload = payload,
            )
        val wire =
            LongHeaderPacket.build(
                plain = plain,
                aead = Aes128Gcm,
                key = proto.clientKey,
                iv = proto.clientIv,
                hp = hp,
                hpKey = proto.clientHp,
                largestAckedInSpace = -1L,
            )
        // Server side reverses
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
        assertEquals(LongHeaderType.INITIAL, parsed.packet.type)
        assertEquals(dcid, parsed.packet.dcid)
        assertEquals(scid, parsed.packet.scid)
        assertEquals(0L, parsed.packet.packetNumber)
        assertContentEquals(payload, parsed.packet.payload)
        assertEquals(wire.size, parsed.consumed)
    }

    /**
     * Auth tag failure with a wrong key must surface as null (drop silently).
     */
    @Test
    fun decrypt_with_wrong_key_returns_null() {
        val dcid = ConnectionId("8394c8f03e515708".hexToByteArray())
        val scid = ConnectionId(byteArrayOf(0x42))
        val proto = InitialSecrets.derive(dcid.bytes)
        val wrongProto = InitialSecrets.derive("0000000000000000".hexToByteArray())
        val hp = AesEcbHeaderProtection(PlatformAesOneBlock)

        val payload = "00112233445566778899aabbccddeeff".hexToByteArray()
        val wire =
            LongHeaderPacket.build(
                plain =
                    LongHeaderPlaintextPacket(
                        type = LongHeaderType.INITIAL,
                        dcid = dcid,
                        scid = scid,
                        packetNumber = 0L,
                        payload = payload,
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
                key = wrongProto.clientKey,
                iv = wrongProto.clientIv,
                hp = hp,
                hpKey = wrongProto.clientHp,
                largestReceivedInSpace = -1L,
            )
        // With a wrong HP key the first byte/PN are mis-unmasked and AEAD will
        // certainly fail. We expect a clean null.
        assertEquals(null, parsed)
    }
}
