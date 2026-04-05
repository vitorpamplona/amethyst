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

/** Tests for elliptic curve point operations. */
class PointTest {
    private fun hex(s: String) =
        U256.fromBytes(
            s.chunked(2).map { it.toInt(16).toByte() }.toByteArray(),
        )

    private fun toHex(a: IntArray) = U256.toBytes(a).joinToString("") { "%02x".format(it) }

    // ==================== Generator point ====================

    @Test
    fun generatorIsOnCurve() {
        // y² = x³ + 7
        val x3 = FieldP.mul(FieldP.sqr(ECPoint.GX), ECPoint.GX)
        val y2expected = FieldP.add(x3, intArrayOf(7, 0, 0, 0, 0, 0, 0, 0))
        val y2actual = FieldP.sqr(ECPoint.GY)
        assertEquals(toHex(y2expected), toHex(y2actual))
    }

    // ==================== Point doubling ====================

    @Test
    fun doubleGMatchesTwoG() {
        // 2·G via doubling
        val p = MutablePoint()
        p.setAffine(ECPoint.GX, ECPoint.GY)
        val doubled = MutablePoint()
        ECPoint.doublePoint(doubled, p)
        val dx = IntArray(8)
        val dy = IntArray(8)
        ECPoint.toAffine(doubled, dx, dy)

        // 2·G via scalar multiplication
        val two = intArrayOf(2, 0, 0, 0, 0, 0, 0, 0)
        val mulResult = MutablePoint()
        ECPoint.mulG(mulResult, two)
        val mx = IntArray(8)
        val my = IntArray(8)
        ECPoint.toAffine(mulResult, mx, my)

        assertEquals(toHex(mx), toHex(dx))
        assertEquals(toHex(my), toHex(dy))
    }

    @Test
    fun doubleInPlace() {
        // doublePoint(out, out) should work correctly
        val p = MutablePoint()
        p.setAffine(ECPoint.GX, ECPoint.GY)
        ECPoint.doublePoint(p, p)
        val x = IntArray(8)
        val y = IntArray(8)
        ECPoint.toAffine(p, x, y)

        val two = intArrayOf(2, 0, 0, 0, 0, 0, 0, 0)
        val expected = MutablePoint()
        ECPoint.mulG(expected, two)
        val ex = IntArray(8)
        val ey = IntArray(8)
        ECPoint.toAffine(expected, ex, ey)

        assertEquals(toHex(ex), toHex(x))
    }

    @Test
    fun doubleInfinityIsInfinity() {
        val inf = MutablePoint()
        inf.setInfinity()
        val result = MutablePoint()
        ECPoint.doublePoint(result, inf)
        assertTrue(result.isInfinity())
    }

    // ==================== Point addition ====================

    @Test
    fun addGPlusGEqualsDoubleG() {
        val g = MutablePoint()
        g.setAffine(ECPoint.GX, ECPoint.GY)
        val sum = MutablePoint()
        ECPoint.addPoints(sum, g, g)
        val sx = IntArray(8)
        val sy = IntArray(8)
        ECPoint.toAffine(sum, sx, sy)

        val doubled = MutablePoint()
        ECPoint.doublePoint(doubled, g)
        val dx = IntArray(8)
        val dy = IntArray(8)
        ECPoint.toAffine(doubled, dx, dy)

        assertEquals(toHex(dx), toHex(sx))
        assertEquals(toHex(dy), toHex(sy))
    }

    @Test
    fun addInfinityIdentity() {
        val g = MutablePoint()
        g.setAffine(ECPoint.GX, ECPoint.GY)
        val inf = MutablePoint()
        inf.setInfinity()

        val result = MutablePoint()
        ECPoint.addPoints(result, g, inf)
        val rx = IntArray(8)
        val ry = IntArray(8)
        ECPoint.toAffine(result, rx, ry)
        assertEquals(toHex(ECPoint.GX), toHex(rx))

        val result2 = MutablePoint()
        ECPoint.addPoints(result2, inf, g)
        val r2x = IntArray(8)
        val r2y = IntArray(8)
        ECPoint.toAffine(result2, r2x, r2y)
        assertEquals(toHex(ECPoint.GX), toHex(r2x))
    }

    @Test
    fun addInverseIsInfinity() {
        // G + (-G) = infinity
        val g = MutablePoint()
        g.setAffine(ECPoint.GX, ECPoint.GY)
        val negG = MutablePoint()
        negG.setAffine(ECPoint.GX, FieldP.neg(ECPoint.GY))

        val result = MutablePoint()
        ECPoint.addPoints(result, g, negG)
        assertTrue(result.isInfinity())
    }

    // ==================== Mixed addition ====================

    @Test
    fun addMixedMatchesFull() {
        // addMixed should produce the same result as addPoints when q is affine
        val three = intArrayOf(3, 0, 0, 0, 0, 0, 0, 0)
        val p = MutablePoint()
        ECPoint.mulG(p, three) // 3G in Jacobian (z ≠ 1)

        // Add G as affine
        val mixed = MutablePoint()
        ECPoint.addMixed(mixed, p, ECPoint.GX, ECPoint.GY)
        val mx = IntArray(8)
        val my = IntArray(8)
        ECPoint.toAffine(mixed, mx, my)

        // Add G as Jacobian
        val gJac = MutablePoint()
        gJac.setAffine(ECPoint.GX, ECPoint.GY)
        val full = MutablePoint()
        ECPoint.addPoints(full, p, gJac)
        val fx = IntArray(8)
        val fy = IntArray(8)
        ECPoint.toAffine(full, fx, fy)

        assertEquals(toHex(fx), toHex(mx))
        assertEquals(toHex(fy), toHex(my))
    }

    @Test
    fun addMixedInfinityInput() {
        val inf = MutablePoint()
        inf.setInfinity()
        val result = MutablePoint()
        ECPoint.addMixed(result, inf, ECPoint.GX, ECPoint.GY)
        val rx = IntArray(8)
        val ry = IntArray(8)
        ECPoint.toAffine(result, rx, ry)
        assertEquals(toHex(ECPoint.GX), toHex(rx))
    }

    // ==================== Scalar multiplication ====================

    @Test
    fun mulGByOne() {
        val one = intArrayOf(1, 0, 0, 0, 0, 0, 0, 0)
        val result = MutablePoint()
        ECPoint.mulG(result, one)
        val rx = IntArray(8)
        val ry = IntArray(8)
        ECPoint.toAffine(result, rx, ry)
        assertEquals(toHex(ECPoint.GX), toHex(rx))
        assertEquals(toHex(ECPoint.GY), toHex(ry))
    }

    @Test
    fun mulGByZeroIsInfinity() {
        val zero = IntArray(8)
        val result = MutablePoint()
        ECPoint.mulG(result, zero)
        assertTrue(result.isInfinity())
    }

    @Test
    fun mulGByNIsInfinity() {
        // n·G = infinity (the group order)
        val result = MutablePoint()
        ECPoint.mulG(result, ScalarN.N)
        assertTrue(result.isInfinity())
    }

    @Test
    fun mulGMatchesMul() {
        // mulG(k) should equal mul(G, k)
        val k = hex("67e56582298859ddae725f972992a07c6c4fb9f62a8fff58ce3ca926a1063530")
        val gResult = MutablePoint()
        ECPoint.mulG(gResult, k)
        val gx = IntArray(8)
        val gy = IntArray(8)
        ECPoint.toAffine(gResult, gx, gy)

        val g = MutablePoint()
        g.setAffine(ECPoint.GX, ECPoint.GY)
        val mResult = MutablePoint()
        ECPoint.mul(mResult, g, k)
        val mx = IntArray(8)
        val my = IntArray(8)
        ECPoint.toAffine(mResult, mx, my)

        assertEquals(toHex(mx), toHex(gx))
        assertEquals(toHex(my), toHex(gy))
    }

    @Test
    fun mulDoubleGSeparateVsCombined() {
        // mulDoubleG(s, P, e) should equal mulG(s) + mul(P, e)
        val s = hex("67e56582298859ddae725f972992a07c6c4fb9f62a8fff58ce3ca926a1063530")
        val e = hex("3982f19bef1615bccfbb05e321c10e1d4cba3df0e841c2e41eeb6016347653c3")

        val p = MutablePoint()
        val two = intArrayOf(2, 0, 0, 0, 0, 0, 0, 0)
        ECPoint.mulG(p, two) // P = 2·G

        // Combined
        val combined = MutablePoint()
        ECPoint.mulDoubleG(combined, s, p, e)
        val cx = IntArray(8)
        val cy = IntArray(8)
        ECPoint.toAffine(combined, cx, cy)

        // Separate
        val sG = MutablePoint()
        ECPoint.mulG(sG, s)
        val eP = MutablePoint()
        ECPoint.mul(eP, p, e)
        val sep = MutablePoint()
        ECPoint.addPoints(sep, sG, eP)
        val sx = IntArray(8)
        val sy = IntArray(8)
        ECPoint.toAffine(sep, sx, sy)

        assertEquals(toHex(sx), toHex(cx))
        assertEquals(toHex(sy), toHex(cy))
    }

    // ==================== liftX ====================

    @Test
    fun liftXGenerator() {
        val x = IntArray(8)
        val y = IntArray(8)
        assertTrue(ECPoint.liftX(x, y, ECPoint.GX))
        assertEquals(toHex(ECPoint.GX), toHex(x))
        // liftX returns even y
        assertTrue(ECPoint.hasEvenY(y))
    }

    @Test
    fun liftXInvalidX() {
        // p itself is not a valid x coordinate
        val x = IntArray(8)
        val y = IntArray(8)
        assertFalse(ECPoint.liftX(x, y, FieldP.P))
    }

    // ==================== Serialization round-trips ====================

    @Test
    fun compressDecompressRoundTrip() {
        val compressed = ECPoint.serializeCompressed(ECPoint.GX, ECPoint.GY)
        val x = IntArray(8)
        val y = IntArray(8)
        assertTrue(ECPoint.parsePublicKey(compressed, x, y))
        assertEquals(toHex(ECPoint.GX), toHex(x))
        assertEquals(toHex(ECPoint.GY), toHex(y))
    }

    @Test
    fun uncompressedRoundTrip() {
        val uncompressed = ECPoint.serializeUncompressed(ECPoint.GX, ECPoint.GY)
        val x = IntArray(8)
        val y = IntArray(8)
        assertTrue(ECPoint.parsePublicKey(uncompressed, x, y))
        assertEquals(toHex(ECPoint.GX), toHex(x))
        assertEquals(toHex(ECPoint.GY), toHex(y))
    }

    @Test
    fun parseInvalidKey() {
        val x = IntArray(8)
        val y = IntArray(8)
        assertFalse(ECPoint.parsePublicKey(ByteArray(10), x, y))
        assertFalse(ECPoint.parsePublicKey(ByteArray(33), x, y)) // wrong prefix (0x00)
    }
}
