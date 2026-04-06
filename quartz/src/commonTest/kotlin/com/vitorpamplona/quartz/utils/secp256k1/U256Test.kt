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
package com.vitorpamplona.quartz.utils.secp256k1

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Tests for 256-bit unsigned integer arithmetic. */
class U256Test {
    private fun hex(s: String) =
        U256.fromBytes(
            s.chunked(2).map { it.toInt(16).toByte() }.toByteArray(),
        )

    private fun toHex(a: LongArray) = U256.toBytes(a).joinToString("") { "%02x".format(it) }

    // ==================== isZero / cmp ====================

    @Test
    fun isZeroTrue() = assertTrue(U256.isZero(LongArray(4)))

    @Test
    fun isZeroFalse() = assertFalse(U256.isZero(longArrayOf(1, 0L, 0L, 0L)))

    @Test
    fun isZeroHighBit() = assertFalse(U256.isZero(longArrayOf(0L, 0L, 0L, 1L)))

    @Test
    fun cmpEqual() = assertEquals(0, U256.cmp(hex("0000000000000000000000000000000000000000000000000000000000000001"), hex("0000000000000000000000000000000000000000000000000000000000000001")))

    @Test
    fun cmpLessThan() = assertEquals(-1, U256.cmp(hex("0000000000000000000000000000000000000000000000000000000000000001"), hex("0000000000000000000000000000000000000000000000000000000000000002")))

    @Test
    fun cmpGreaterThan() = assertEquals(1, U256.cmp(hex("ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"), hex("0000000000000000000000000000000000000000000000000000000000000001")))

    // ==================== add / sub ====================

    @Test
    fun addSimple() {
        val out = LongArray(4)
        val carry = U256.addTo(out, hex("0000000000000000000000000000000000000000000000000000000000000001"), hex("0000000000000000000000000000000000000000000000000000000000000002"))
        assertEquals("0000000000000000000000000000000000000000000000000000000000000003", toHex(out))
        assertEquals(0, carry)
    }

    @Test
    fun addOverflow() {
        val out = LongArray(4)
        val carry = U256.addTo(out, hex("ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"), hex("0000000000000000000000000000000000000000000000000000000000000001"))
        assertEquals("0000000000000000000000000000000000000000000000000000000000000000", toHex(out))
        assertEquals(1, carry)
    }

    @Test
    fun addLimbCarry() {
        val out = LongArray(4)
        U256.addTo(out, hex("00000000000000000000000000000000000000000000000000000000ffffffff"), hex("0000000000000000000000000000000000000000000000000000000000000001"))
        assertEquals("0000000000000000000000000000000000000000000000000000000100000000", toHex(out))
    }

    @Test
    fun subSimple() {
        val out = LongArray(4)
        val borrow = U256.subTo(out, hex("0000000000000000000000000000000000000000000000000000000000000003"), hex("0000000000000000000000000000000000000000000000000000000000000001"))
        assertEquals("0000000000000000000000000000000000000000000000000000000000000002", toHex(out))
        assertEquals(0, borrow)
    }

    @Test
    fun subUnderflow() {
        val out = LongArray(4)
        val borrow = U256.subTo(out, hex("0000000000000000000000000000000000000000000000000000000000000000"), hex("0000000000000000000000000000000000000000000000000000000000000001"))
        assertEquals("ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff", toHex(out))
        assertEquals(1, borrow)
    }

    // ==================== mulWide / sqrWide ====================

    @Test
    fun mulWideSmall() {
        val out = LongArray(8)
        U256.mulWide(out, hex("0000000000000000000000000000000000000000000000000000000000000003"), hex("0000000000000000000000000000000000000000000000000000000000000007"))
        // 3 * 7 = 21 = 0x15
        assertEquals(0x15L, out[0])
        for (i in 1 until 8) assertEquals(0L, out[i])
    }

    @Test
    fun mulWideLarge() {
        // (2^128 - 1)² consistency: mulWide and sqrWide should match
        val out1 = LongArray(8)
        val out2 = LongArray(8)
        val maxHalf = hex("00000000000000000000000000000000ffffffffffffffffffffffffffffffff")
        U256.mulWide(out1, maxHalf, maxHalf)
        U256.sqrWide(out2, maxHalf)
        for (i in 0 until 8) assertEquals(out1[i], out2[i], "Limb $i mismatch")
        // Lowest limb is 1 (from +1 in (2^128-1)² = 2^256 - 2^129 + 1)
        assertEquals(1L, out1[0])
    }

    @Test
    fun sqrWideMatchesMulWide() {
        // sqrWide(a) should produce the same result as mulWide(a, a)
        val a = hex("67E56582298859DDAE725F972992A07C6C4FB9F62A8FFF58CE3CA926A1063530")
        val mulResult = LongArray(8)
        U256.mulWide(mulResult, a, a)
        val sqrResult = LongArray(8)
        U256.sqrWide(sqrResult, a)
        for (i in 0 until 8) {
            assertEquals(mulResult[i], sqrResult[i], "Limb $i mismatch")
        }
    }

    @Test
    fun sqrWideMaxValue() {
        // (2^256 - 1)^2 should match mulWide
        val maxVal = hex("ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff")
        val mulResult = LongArray(8)
        U256.mulWide(mulResult, maxVal, maxVal)
        val sqrResult = LongArray(8)
        U256.sqrWide(sqrResult, maxVal)
        for (i in 0 until 8) {
            assertEquals(mulResult[i], sqrResult[i], "Limb $i mismatch for max value sqr")
        }
    }

    // ==================== fromBytes / toBytes ====================

    @Test
    fun bytesRoundTrip() {
        val hex = "67E56582298859DDAE725F972992A07C6C4FB9F62A8FFF58CE3CA926A1063530"
        val bytes = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val limbs = U256.fromBytes(bytes)
        val back = U256.toBytes(limbs)
        assertEquals(hex.lowercase(), back.joinToString("") { "%02x".format(it) })
    }

    @Test
    fun bytesZero() {
        val limbs = U256.fromBytes(ByteArray(32))
        assertTrue(U256.isZero(limbs))
    }

    // ==================== getNibble ====================

    @Test
    fun getNibbleValues() {
        // 0xAB at the lowest byte = limb[0] = 0x...AB
        val a = hex("00000000000000000000000000000000000000000000000000000000000000ab")
        assertEquals(0xB, U256.getNibble(a, 0)) // lowest nibble
        assertEquals(0xA, U256.getNibble(a, 1)) // second nibble
        assertEquals(0, U256.getNibble(a, 2)) // third nibble
    }

    @Test
    fun getNibbleHighBits() {
        val a = hex("f000000000000000000000000000000000000000000000000000000000000000")
        assertEquals(0xF, U256.getNibble(a, 63)) // highest nibble
        assertEquals(0, U256.getNibble(a, 62))
    }

    // ==================== testBit ====================

    @Test
    fun testBitLow() {
        val a = hex("0000000000000000000000000000000000000000000000000000000000000005") // bits 0 and 2
        assertTrue(U256.testBit(a, 0))
        assertFalse(U256.testBit(a, 1))
        assertTrue(U256.testBit(a, 2))
    }

    @Test
    fun testBitHigh() {
        val a = hex("8000000000000000000000000000000000000000000000000000000000000000") // bit 255
        assertTrue(U256.testBit(a, 255))
        assertFalse(U256.testBit(a, 254))
    }

    // ==================== xor ====================

    @Test
    fun xorBasic() {
        val out = LongArray(4)
        U256.xorTo(out, hex("ff00ff00ff00ff00ff00ff00ff00ff00ff00ff00ff00ff00ff00ff00ff00ff00"), hex("0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f"))
        assertEquals("f00ff00ff00ff00ff00ff00ff00ff00ff00ff00ff00ff00ff00ff00ff00ff00f", toHex(out))
    }

    @Test
    fun toBytesIntoAtOffset() {
        val a = hex("0102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f20")
        val dest = ByteArray(64)
        U256.toBytesInto(a, dest, 16) // write at offset 16
        // First 16 bytes should be zero
        for (i in 0 until 8) assertEquals(0, dest[i].toInt())
        // Bytes 16-47 should contain the value
        assertEquals(0x01, dest[16].toInt() and 0xFF)
        assertEquals(0x20, dest[47].toInt() and 0xFF)
    }

    @Test
    fun copyIntoTest() {
        val src = hex("67e56582298859ddae725f972992a07c6c4fb9f62a8fff58ce3ca926a1063530")
        val dst = LongArray(4)
        U256.copyInto(dst, src)
        for (i in 0 until 4) assertEquals(src[i], dst[i])
    }

    @Test
    fun fromBytesWithOffset() {
        val fullArray = ByteArray(64)
        // Put a known value at offset 32
        val expected = hex("67e56582298859ddae725f972992a07c6c4fb9f62a8fff58ce3ca926a1063530")
        U256.toBytesInto(expected, fullArray, 32)
        val decoded = U256.fromBytes(fullArray, 32)
        for (i in 0 until 4) assertEquals(expected[i], decoded[i])
    }
}
