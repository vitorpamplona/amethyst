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
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * RFC 9000 §17.2 (long-header) and §17.3 (short-header): the fixed-bit
 * (0x40) of the first byte MUST be 1 in every v1 packet. Packets with
 * fixed-bit=0 MUST be discarded.
 *
 * Pre-fix the parsers checked only the form-bit (0x80) and accepted the
 * packet regardless of fixed-bit. A peer (or off-path attacker) sending a
 * packet with fixed-bit=0 would have its bytes routed through AEAD —
 * cheap denial-of-service amplifier and a wire-spec violation we accepted
 * silently.
 *
 * The fixed-bit is NOT header-protected (HP only XORs the low five bits
 * of the first byte), so we can validate it on the raw wire byte BEFORE
 * paying for HP unmask + AEAD. Both `parseAndDecrypt` and the
 * `peekKeyPhase` shortcut do the check.
 */
class FixedBitValidationTest {
    private val dcid = ConnectionId("8394c8f03e515708".hexToByteArray())
    private val scid = ConnectionId("01020304".hexToByteArray())
    private val proto = InitialSecrets.derive(dcid.bytes)
    private val hp = AesEcbHeaderProtection(PlatformAesOneBlock)

    @Test
    fun long_header_fixed_bit_zero_is_discarded() {
        val plain =
            LongHeaderPlaintextPacket(
                type = LongHeaderType.INITIAL,
                dcid = dcid,
                scid = scid,
                packetNumber = 0L,
                payload = "01".hexToByteArray(),
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

        // Sanity check: the unmodified wire round-trips.
        val good =
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
        assertNotNull(good)

        // Flip fixed-bit (0x40) on the wire's first byte. The bit is NOT
        // header-protected, so flipping it does not affect HP unmask of
        // the type / pnLen bits — the packet is structurally valid in
        // every other way; it just isn't a v1 packet anymore.
        val tampered = wire.copyOf()
        tampered[0] = (tampered[0].toInt() xor 0x40).toByte()
        val parsed =
            LongHeaderPacket.parseAndDecrypt(
                bytes = tampered,
                offset = 0,
                aead = Aes128Gcm,
                key = proto.clientKey,
                iv = proto.clientIv,
                hp = hp,
                hpKey = proto.clientHp,
                largestReceivedInSpace = -1L,
            )
        assertNull(parsed, "RFC 9000 §17.2: long-header fixed-bit=0 MUST be discarded")
    }

    @Test
    fun short_header_fixed_bit_zero_is_discarded_in_parseAndDecrypt() {
        val plain =
            ShortHeaderPlaintextPacket(
                dcid = dcid,
                packetNumber = 0L,
                payload = byteArrayOf(0x01),
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

        val good =
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
        assertNotNull(good)

        val tampered = wire.copyOf()
        tampered[0] = (tampered[0].toInt() xor 0x40).toByte()
        val parsed =
            ShortHeaderPacket.parseAndDecrypt(
                bytes = tampered,
                offset = 0,
                dcidLen = dcid.length,
                aead = Aes128Gcm,
                key = proto.clientKey,
                iv = proto.clientIv,
                hp = hp,
                hpKey = proto.clientHp,
                largestReceivedInSpace = -1L,
            )
        assertNull(parsed, "RFC 9000 §17.3: short-header fixed-bit=0 MUST be discarded")
    }

    @Test
    fun short_header_fixed_bit_zero_is_discarded_in_peekKeyPhase() {
        val plain =
            ShortHeaderPlaintextPacket(
                dcid = dcid,
                packetNumber = 0L,
                payload = byteArrayOf(0x01),
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
        // peekKeyPhase is the upstream gate the parser uses to pick keys
        // before AEAD; if it accepts a fixed-bit=0 packet we waste an
        // AEAD attempt before parseAndDecrypt drops it.
        val tampered = wire.copyOf()
        tampered[0] = (tampered[0].toInt() xor 0x40).toByte()
        val peek =
            ShortHeaderPacket.peekKeyPhase(
                bytes = tampered,
                offset = 0,
                dcidLen = dcid.length,
                hp = hp,
                hpKey = proto.clientHp,
            )
        assertNull(peek, "RFC 9000 §17.3: peekKeyPhase MUST reject fixed-bit=0")
    }
}
