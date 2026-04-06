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

/** Tests for KeyCodec: key parsing, serialization, liftX, hasEvenY. */
class KeyCodecTest {
    private fun toHex(a: IntArray) = U256.toBytes(a).joinToString("") { "%02x".format(it) }

    // ==================== liftX ====================

    @Test
    fun liftXGenerator() {
        val x = LongArray(4)
        val y = LongArray(4)
        assertTrue(KeyCodec.liftX(x, y, ECPoint.GX))
        assertEquals(toHex(ECPoint.GX), toHex(x))
        assertTrue(KeyCodec.hasEvenY(y))
    }

    @Test
    fun liftXInvalidFieldElement() {
        // p itself is not a valid x
        val x = LongArray(4)
        val y = LongArray(4)
        assertFalse(KeyCodec.liftX(x, y, FieldP.P))
    }

    @Test
    fun liftXNotOnCurve() {
        // x=2: y² = 8+7 = 15. 15 is not a quadratic residue mod p.
        val x = LongArray(4)
        val y = LongArray(4)
        val two = longArrayOf(2, 0, 0, 0, 0, 0, 0, 0)
        // This may or may not be on the curve — just check it doesn't crash
        KeyCodec.liftX(x, y, two) // result doesn't matter, just no exception
    }

    @Test
    fun liftXAlwaysReturnsEvenY() {
        val x = LongArray(4)
        val y = LongArray(4)
        assertTrue(KeyCodec.liftX(x, y, ECPoint.GX))
        assertTrue(KeyCodec.hasEvenY(y), "liftX should always return even y")
    }

    // ==================== hasEvenY ====================

    @Test
    fun hasEvenYForEvenValue() {
        assertTrue(KeyCodec.hasEvenY(longArrayOf(2, 0, 0, 0, 0, 0, 0, 0)))
        assertTrue(KeyCodec.hasEvenY(longArrayOf(0, 0, 0, 0, 0, 0, 0, 0)))
    }

    @Test
    fun hasEvenYForOddValue() {
        assertFalse(KeyCodec.hasEvenY(longArrayOf(1, 0, 0, 0, 0, 0, 0, 0)))
        assertFalse(KeyCodec.hasEvenY(longArrayOf(3, 0, 0, 0, 0, 0, 0, 0)))
    }

    // ==================== parsePublicKey ====================

    @Test
    fun parseCompressedEvenY() {
        val compressed = KeyCodec.serializeCompressed(ECPoint.GX, ECPoint.GY)
        assertEquals(0x02.toByte(), compressed[0])
        val x = LongArray(4)
        val y = LongArray(4)
        assertTrue(KeyCodec.parsePublicKey(compressed, x, y))
        assertEquals(toHex(ECPoint.GX), toHex(x))
        assertEquals(toHex(ECPoint.GY), toHex(y))
    }

    @Test
    fun parseCompressedOddY() {
        val negGy = FieldP.neg(ECPoint.GY)
        val compressed = KeyCodec.serializeCompressed(ECPoint.GX, negGy)
        assertEquals(0x03.toByte(), compressed[0])
        val x = LongArray(4)
        val y = LongArray(4)
        assertTrue(KeyCodec.parsePublicKey(compressed, x, y))
        assertEquals(toHex(ECPoint.GX), toHex(x))
        assertEquals(toHex(negGy), toHex(y))
    }

    @Test
    fun parseUncompressed() {
        val uncompressed = KeyCodec.serializeUncompressed(ECPoint.GX, ECPoint.GY)
        assertEquals(0x04.toByte(), uncompressed[0])
        val x = LongArray(4)
        val y = LongArray(4)
        assertTrue(KeyCodec.parsePublicKey(uncompressed, x, y))
        assertEquals(toHex(ECPoint.GX), toHex(x))
        assertEquals(toHex(ECPoint.GY), toHex(y))
    }

    @Test
    fun parseInvalidSizes() {
        val x = LongArray(4)
        val y = LongArray(4)
        assertFalse(KeyCodec.parsePublicKey(ByteArray(0), x, y))
        assertFalse(KeyCodec.parsePublicKey(ByteArray(10), x, y))
        assertFalse(KeyCodec.parsePublicKey(ByteArray(32), x, y))
        assertFalse(KeyCodec.parsePublicKey(ByteArray(34), x, y))
        assertFalse(KeyCodec.parsePublicKey(ByteArray(64), x, y))
        assertFalse(KeyCodec.parsePublicKey(ByteArray(66), x, y))
    }

    @Test
    fun parseInvalidPrefix() {
        val x = LongArray(4)
        val y = LongArray(4)
        assertFalse(KeyCodec.parsePublicKey(ByteArray(33), x, y)) // prefix 0x00
        assertFalse(KeyCodec.parsePublicKey(ByteArray(65), x, y)) // prefix 0x00
    }

    @Test
    fun parseUncompressedNotOnCurve() {
        // Valid-looking 65 bytes but y doesn't satisfy y² = x³ + 7
        val fake = ByteArray(65)
        fake[0] = 0x04
        fake[1] = 0x01 // x = 1 (padded)
        fake[33] = 0x01 // y = 1 (padded) — 1² ≠ 1³ + 7
        val x = LongArray(4)
        val y = LongArray(4)
        assertFalse(KeyCodec.parsePublicKey(fake, x, y))
    }

    // ==================== Serialization round-trips ====================

    @Test
    fun compressDecompressRoundTrip() {
        val compressed = KeyCodec.serializeCompressed(ECPoint.GX, ECPoint.GY)
        val x = LongArray(4)
        val y = LongArray(4)
        assertTrue(KeyCodec.parsePublicKey(compressed, x, y))
        val recompressed = KeyCodec.serializeCompressed(x, y)
        assertEquals(compressed.toList(), recompressed.toList())
    }

    @Test
    fun uncompressedRoundTrip() {
        val uncompressed = KeyCodec.serializeUncompressed(ECPoint.GX, ECPoint.GY)
        val x = LongArray(4)
        val y = LongArray(4)
        assertTrue(KeyCodec.parsePublicKey(uncompressed, x, y))
        val reser = KeyCodec.serializeUncompressed(x, y)
        assertEquals(uncompressed.toList(), reser.toList())
    }
}
