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

class GlvTest {
    private fun toHex(a: IntArray) = U256.toBytes(a).joinToString("") { "%02x".format(it) }

    @Suppress("ktlint:standard:property-naming")
    private val LAMBDA =
        intArrayOf(
            0x1B23BD72.toInt(),
            0xDF02967C.toInt(),
            0x20816678.toInt(),
            0x122E22EA.toInt(),
            0x8812645A.toInt(),
            0xA5261C02.toInt(),
            0xC05C30E0.toInt(),
            0x5363AD4C.toInt(),
        )

    @Test
    fun scalarSplitReconstruction() {
        val k =
            U256.fromBytes(
                "67E56582298859DDAE725F972992A07C6C4FB9F62A8FFF58CE3CA926A1063530"
                    .chunked(2)
                    .map { it.toInt(16).toByte() }
                    .toByteArray(),
            )
        val split = Glv.splitScalar(k)
        val k1 = if (split.negK1) ScalarN.neg(split.k1) else split.k1
        val k2 = if (split.negK2) ScalarN.neg(split.k2) else split.k2
        val reconstructed = ScalarN.add(k1, ScalarN.mul(k2, LAMBDA))
        assertEquals(toHex(k), toHex(reconstructed), "k = k1 + k2*lambda mod n")
    }

    @Test
    fun endomorphismCorrectness() {
        val beta =
            intArrayOf(
                0x719501EE.toInt(),
                0xC1396C28.toInt(),
                0x12F58995.toInt(),
                0x9CF04975.toInt(),
                0xAC3434E9.toInt(),
                0x6E64479E.toInt(),
                0x657C0710.toInt(),
                0x7AE96A2B.toInt(),
            )
        val betaGx = FieldP.mul(ECPoint.GX, beta)
        val result = MutablePoint()
        ECPoint.mulG(result, LAMBDA)
        val rx = IntArray(8)
        val ry = IntArray(8)
        ECPoint.toAffine(result, rx, ry)
        assertEquals(toHex(betaGx), toHex(rx), "x should be beta*Gx")
        assertEquals(toHex(ECPoint.GY), toHex(ry), "y should be Gy")
    }

    @Test
    fun wnafK1GDirectly() {
        // Compute |k1|*G via wNAF, then negate if needed. Compare with k1_signed*G via mulG.
        val k =
            U256.fromBytes(
                "67E56582298859DDAE725F972992A07C6C4FB9F62A8FFF58CE3CA926A1063530"
                    .chunked(2)
                    .map { it.toInt(16).toByte() }
                    .toByteArray(),
            )
        val split = Glv.splitScalar(k)
        val gOdd = Array(8) { ECPoint.gTable[it * 2] }
        val digits = Glv.wnaf(split.k1, 5, 129)

        var bits = digits.size
        while (bits > 0 && digits[bits - 1] == 0) bits--
        val result = MutablePoint()
        result.setInfinity()
        val tmp = MutablePoint()
        val negY = IntArray(8)
        for (i in bits - 1 downTo 0) {
            ECPoint.doublePoint(result, result)
            val d = digits[i]
            if (d != 0) {
                val idx = (if (d > 0) d else -d) / 2
                val neg = (d < 0) xor split.negK1
                if (!neg) {
                    ECPoint.addMixed(tmp, result, gOdd[idx].x, gOdd[idx].y)
                } else {
                    FieldP.neg(negY, gOdd[idx].y)
                    ECPoint.addMixed(tmp, result, gOdd[idx].x, negY)
                }
                result.copyFrom(tmp)
            }
        }
        val rx = IntArray(8)
        val ry = IntArray(8)
        ECPoint.toAffine(result, rx, ry)

        // Expected: k1_signed * G
        val k1Signed = if (split.negK1) ScalarN.neg(split.k1) else split.k1
        val direct = MutablePoint()
        ECPoint.mulG(direct, k1Signed)
        val dx = IntArray(8)
        val dy = IntArray(8)
        ECPoint.toAffine(direct, dx, dy)

        println("k1=${toHex(split.k1)} neg=${split.negK1} bits=$bits wnaf_x=${toHex(rx)} direct_x=${toHex(dx)}")
        assertEquals(toHex(dx), toHex(rx), "k1 via wNAF+GLV sign should match direct")
    }

    @Test
    fun mulDoubleGWithZeroE() {
        val s =
            U256.fromBytes(
                "67E56582298859DDAE725F972992A07C6C4FB9F62A8FFF58CE3CA926A1063530"
                    .chunked(2)
                    .map { it.toInt(16).toByte() }
                    .toByteArray(),
            )
        val p = MutablePoint()
        ECPoint.mulG(p, intArrayOf(2, 0, 0, 0, 0, 0, 0, 0))
        val combined = MutablePoint()
        ECPoint.mulDoubleG(combined, s, p, IntArray(8))
        val cx = IntArray(8)
        val cy = IntArray(8)
        ECPoint.toAffine(combined, cx, cy)
        val direct = MutablePoint()
        ECPoint.mulG(direct, s)
        val dx = IntArray(8)
        val dy = IntArray(8)
        ECPoint.toAffine(direct, dx, dy)
        assertEquals(toHex(dx), toHex(cx), "s*G+0*P should equal s*G")
    }
}
