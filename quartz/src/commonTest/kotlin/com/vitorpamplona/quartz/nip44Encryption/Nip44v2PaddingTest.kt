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
package com.vitorpamplona.quartz.nip44Encryption

import com.vitorpamplona.quartz.utils.RandomInstance
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Padding and payload-size rules from NIP-44 v2 (extended length prefix, 64-bit padding math).
 */
class Nip44v2PaddingTest {
    private val nip44v2 = Nip44v2()

    @Test
    fun paddingUsesIntegerMathAboveFloatPrecision() {
        // Float math rounds these up to the next bucket (25165824 / 41943040).
        assertEquals(20_971_520L, nip44v2.calcPaddedLen(20_971_520))
        assertEquals(33_554_432L, nip44v2.calcPaddedLen(33_554_432))
        assertEquals(41_943_040L, nip44v2.calcPaddedLen(33_554_433))
        assertEquals(20_971_520L, nip44v2.calcPaddedLen(16_777_217))
    }

    @Test
    fun paddingDoesNotOverflowNearTheJvmArrayLimit() {
        assertEquals(1L shl 30, nip44v2.calcPaddedLen(1 shl 30))
        assertEquals(1_342_177_280L, nip44v2.calcPaddedLen((1 shl 30) + 1))
        assertEquals(1L shl 31, nip44v2.calcPaddedLen(Int.MAX_VALUE))
        // The spec's theoretical maximum: 2^32 - 1 pads to 2^32, which needs 64-bit math.
        assertEquals(1L shl 32, nip44v2.calcPaddedLen(0xffffffffL))
    }

    @Test
    fun paddingMatchesSpecFormulaAroundBoundaries() {
        val probes = listOf(33L, 256L, 257L, 65_535L, 65_536L, 65_537L, 1L shl 24, (1L shl 24) + 1, 20_000_000L, 100_000_000L)
        for (n in probes) {
            assertEquals(specPaddedLen(n), nip44v2.calcPaddedLen(n), "calcPaddedLen($n)")
        }
    }

    @Test
    fun padUsesExtendedPrefixFrom65536() {
        val small = nip44v2.pad("a".repeat(65_535))
        assertEquals(2 + 65_536, small.size)
        assertEquals(0xff.toByte(), small[0])
        assertEquals(0xff.toByte(), small[1])

        val big = nip44v2.pad("a".repeat(65_536))
        assertEquals(6 + 65_536, big.size)
        assertEquals(listOf<Byte>(0, 0, 0, 1, 0, 0), big.copyOfRange(0, 6).toList())
        assertEquals("a".repeat(65_536), nip44v2.unpad(big))
    }

    @Test
    fun unpadRejectsExtendedPrefixForShortLengths() {
        // [0,0][u32 = 5]["hello"][zeros] would be a second encoding of a 5-byte plaintext.
        val padded = ByteArray(6 + 32)
        padded[5] = 5
        "hello".encodeToByteArray().copyInto(padded, 6)
        assertFailsWith<IllegalStateException> { nip44v2.unpad(padded) }

        // The largest length that must not use the extended prefix.
        val padded2 = ByteArray(6 + 65_536)
        padded2[4] = 0xff.toByte()
        padded2[5] = 0xff.toByte()
        assertFailsWith<IllegalStateException> { nip44v2.unpad(padded2) }
    }

    @Test
    fun unpadRejectsZeroLength() {
        assertFailsWith<IllegalStateException> { nip44v2.unpad(ByteArray(2 + 32)) }
        assertFailsWith<IllegalStateException> { nip44v2.unpad(ByteArray(6 + 32)) }
    }

    @Test
    fun rejectsOversizedPayloadBeforeDecoding() {
        val key = RandomInstance.bytes(32)
        val payload = nip44v2.encrypt("a".repeat(1000), key).encodePayload()

        val limited = Nip44v2(maxPayloadLength = payload.length - 1)
        val error = assertFailsWith<IllegalStateException> { limited.decrypt(payload, key) }
        assertTrue(error.message!!.contains("exceeds"), error.message)

        // Not base64 at all: must still fail on the size check, without reaching the decoder.
        val garbage = "!".repeat(1000)
        val error2 = assertFailsWith<IllegalStateException> { Nip44v2.EncryptedInfo.decodePayload(garbage, maxPayloadLength = 500) }
        assertTrue(error2.message!!.contains("exceeds"), error2.message)

        assertEquals("a".repeat(1000), Nip44v2(maxPayloadLength = payload.length).decrypt(payload, key))
    }

    @Test
    fun defaultMaxPayloadAcceptsTheLongestSpecVector() {
        // 20,000,000-byte plaintext from encrypt_decrypt_long_msg.
        val rawLen = 1 + 32 + 6 + nip44v2.calcPaddedLen(20_000_000) + 32
        val base64Len = (rawLen + 2) / 3 * 4
        assertTrue(base64Len <= Nip44v2.DEFAULT_MAX_PAYLOAD_LENGTH)
    }

    @Test
    fun rejectsShortDecodedPayload() {
        // Long enough as text (132 chars) but only 98 bytes once decoded.
        val tooShort = Base64.encode(byteArrayOf(2) + ByteArray(97))
        assertEquals(132, tooShort.length)
        assertFailsWith<IllegalStateException> { Nip44v2.EncryptedInfo.decodePayload(tooShort) }
    }

    private fun specPaddedLen(n: Long): Long {
        if (n <= 32) return 32
        val bitLength = 64 - (n - 1).countLeadingZeroBits()
        val nextPower = 1L shl bitLength
        val chunk = if (nextPower <= 256) 32 else nextPower / 8
        return chunk * ((n - 1) / chunk + 1)
    }
}
