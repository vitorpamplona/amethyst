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
import kotlin.test.assertTrue

/** Comprehensive tests for GLV endomorphism and wNAF encoding. */
class GlvTest {
    private fun toHex(a: LongArray) = U256.toBytes(a).joinToString("") { "%02x".format(it) }

    private fun hex(s: String) =
        U256.fromBytes(
            s.chunked(2).map { it.toInt(16).toByte() }.toByteArray(),
        )

    @Suppress("ktlint:standard:property-naming")
    private val LAMBDA = hex("5363ad4cc05c30e0a5261c028812645a122e22ea20816678df02967c1b23bd72")

    // ==================== GLV Scalar Decomposition ====================

    @Test
    fun splitScalarReconstruction() {
        // k₁ + k₂·λ ≡ k (mod n) for a typical scalar
        val k = hex("67e56582298859ddae725f972992a07c6c4fb9f62a8fff58ce3ca926a1063530")
        val split = Glv.splitScalar(k)
        val k1 = if (split.negK1) ScalarN.neg(split.k1) else split.k1
        val k2 = if (split.negK2) ScalarN.neg(split.k2) else split.k2
        assertEquals(toHex(k), toHex(ScalarN.add(k1, ScalarN.mul(k2, LAMBDA))))
    }

    @Test
    fun splitScalarZero() {
        val split = Glv.splitScalar(LongArray(4))
        assertTrue(U256.isZero(split.k1) && U256.isZero(split.k2))
    }

    @Test
    fun splitScalarNMinus1() {
        // n-1 is the maximum valid scalar
        val nMinus1 = hex("fffffffffffffffffffffffffffffffebaaedce6af48a03bbfd25e8cd0364140")
        val split = Glv.splitScalar(nMinus1)
        val k1 = if (split.negK1) ScalarN.neg(split.k1) else split.k1
        val k2 = if (split.negK2) ScalarN.neg(split.k2) else split.k2
        assertEquals(toHex(nMinus1), toHex(ScalarN.add(k1, ScalarN.mul(k2, LAMBDA))))
    }

    @Test
    fun splitScalarHalvesAreSmall() {
        // Both k₁ and k₂ should be ~128 bits (fit in 4 limbs)
        val k = hex("67e56582298859ddae725f972992a07c6c4fb9f62a8fff58ce3ca926a1063530")
        val split = Glv.splitScalar(k)
        // Upper 4 limbs should be zero for a proper 128-bit half-scalar
        for (i in 2 until 4) {
            assertEquals(0, split.k1[i], "k1 limb $i should be 0")
            assertEquals(0, split.k2[i], "k2 limb $i should be 0")
        }
    }

    @Test
    fun splitMultipleScalars() {
        // Verify reconstruction for several different scalars
        val scalars =
            listOf(
                hex("0000000000000000000000000000000000000000000000000000000000000001"),
                hex("0000000000000000000000000000000000000000000000000000000000000003"),
                hex("b7e151628aed2a6abf7158809cf4f3c762e7160f38b4da56a784d9045190cfef"),
                hex("c90fdaa22168c234c4c6628b80dc1cd129024e088a67cc74020bbea63b14e5c9"),
                hex("944946c56e0d133f326db0b0645544a04bfcc5a0f447ad6d0227958414d8ba73"),
            )
        for (k in scalars) {
            val split = Glv.splitScalar(k)
            val k1 = if (split.negK1) ScalarN.neg(split.k1) else split.k1
            val k2 = if (split.negK2) ScalarN.neg(split.k2) else split.k2
            assertEquals(
                toHex(k),
                toHex(ScalarN.add(k1, ScalarN.mul(k2, LAMBDA))),
                "Reconstruction failed for ${toHex(k)}",
            )
        }
    }

    // ==================== Endomorphism ====================

    @Test
    fun betaCubedIsOne() {
        // β³ ≡ 1 (mod p) — the defining property of the cube root of unity
        val b2 = FieldP.sqr(Glv.BETA)
        val b3 = FieldP.mul(b2, Glv.BETA)
        val one = longArrayOf(1L, 0L, 0L, 0L, 0, 0, 0, 0)
        assertEquals(toHex(one), toHex(b3))
    }

    @Test
    fun endomorphismCorrectness() {
        // λ·G should equal (β·Gx, Gy)
        val result = MutablePoint()
        ECPoint.mulG(result, LAMBDA)
        val rx = LongArray(4)
        val ry = LongArray(4)
        ECPoint.toAffine(result, rx, ry)
        assertEquals(toHex(FieldP.mul(ECPoint.GX, Glv.BETA)), toHex(rx))
        assertEquals(toHex(ECPoint.GY), toHex(ry))
    }

    // ==================== wNAF Encoding ====================

    @Test
    fun wnafReconstructionSmall() {
        // wNAF digits should reconstruct to the original scalar
        val k = longArrayOf(17L, 0L, 0L, 0L) // 17 = 10001 in binary
        val digits = Glv.wnaf(k, 5, 256)
        assertEquals(k[0], reconstructWnaf(digits)[0])
    }

    @Test
    fun wnafReconstructionLarge() {
        val k = hex("e907831f80848d1069a5371b402410364bdf1c5f8307b0084c55f1ce2dca8215")
        val digits = Glv.wnaf(k, 5, 256)
        val reconstructed = reconstructWnaf(digits)
        for (i in 0 until 4) assertEquals(k[i], reconstructed[i], "Limb $i mismatch")
    }

    @Test
    fun wnafCarryOverflowBit255() {
        // Regression: scalars with high bits set caused carries past bit 255 to be dropped.
        // This scalar (negE from BIP-340 vector 0) triggers the carry at bit 256.
        val k = hex("944946c56e0d133f326db0b0645544a04bfcc5a0f447ad6d0227958414d8ba73")
        val digits = Glv.wnaf(k, 5, 256)
        val reconstructed = reconstructWnaf(digits)
        for (i in 0 until 4) assertEquals(k[i], reconstructed[i], "Limb $i mismatch for carry test")
    }

    @Test
    fun wnafDigitsAreOddAndBounded() {
        val k = hex("67e56582298859ddae725f972992a07c6c4fb9f62a8fff58ce3ca926a1063530")
        val digits = Glv.wnaf(k, 5, 256)
        for (i in digits.indices) {
            val d = digits[i]
            if (d != 0) {
                assertTrue(d % 2 != 0, "wNAF digit at $i should be odd, got $d")
                assertTrue(d in -15..15, "wNAF digit at $i should be in [-15,15], got $d")
            }
        }
    }

    @Test
    fun wnafZeroRunsBetweenDigits() {
        // wNAF-5 guarantees at least 4 zeros between non-zero digits
        val k = hex("b7e151628aed2a6abf7158809cf4f3c762e7160f38b4da56a784d9045190cfef")
        val digits = Glv.wnaf(k, 5, 256)
        var lastNonZero = -5
        for (i in digits.indices) {
            if (digits[i] != 0) {
                assertTrue(i - lastNonZero >= 5, "Gap between digits at $lastNonZero and $i is ${i - lastNonZero}, expected ≥5")
                lastNonZero = i
            }
        }
    }

    @Test
    fun wnafSmallMaxBits() {
        // wNAF with maxBits=129 (used for GLV half-scalars)
        val k = longArrayOf(-7296712173568108936L, 2459565876494606609L, 0L, 0L)
        val digits = Glv.wnaf(k, 5, 129)
        val reconstructed = reconstructWnaf(digits)
        for (i in 0 until 4) assertEquals(k[i], reconstructed[i], "Limb $i mismatch for 129-bit wNAF")
    }

    // ==================== Integration: GLV mulDoubleG ====================

    @Test
    fun mulDoubleGWithZeroE() {
        // s·G + 0·P = s·G
        val s = hex("67e56582298859ddae725f972992a07c6c4fb9f62a8fff58ce3ca926a1063530")
        val p = MutablePoint()
        ECPoint.mulG(p, longArrayOf(2L, 0L, 0L, 0L, 0, 0, 0, 0))
        val combined = MutablePoint()
        ECPoint.mulDoubleG(combined, s, p, LongArray(4))
        val cx = LongArray(4)
        val cy = LongArray(4)
        ECPoint.toAffine(combined, cx, cy)
        val direct = MutablePoint()
        ECPoint.mulG(direct, s)
        val dx = LongArray(4)
        val dy = LongArray(4)
        ECPoint.toAffine(direct, dx, dy)
        assertEquals(toHex(dx), toHex(cx))
    }

    // ==================== Helpers ====================

    /** Reconstruct a scalar from wNAF digits using Horner's method. */
    private fun reconstructWnaf(digits: IntArray): LongArray {
        var acc = LongArray(4)
        for (bit in digits.size - 1 downTo 0) {
            // Double: acc = acc * 2 (unsigned shift left by 1)
            val doubled = LongArray(4)
            var shiftCarry = 0L
            for (j in 0 until 4) {
                doubled[j] = (acc[j] shl 1) or shiftCarry
                shiftCarry = acc[j] ushr 63
            }
            acc = doubled
            // Add digit
            val d = digits[bit]
            if (d > 0) {
                val s = acc[0] + d.toLong()
                val c = if (s.toULong() < acc[0].toULong()) 1L else 0L
                acc[0] = s
                if (c != 0L) {
                    for (j in 1 until 4) {
                        acc[j]++
                        if (acc[j] != 0L) break
                    }
                }
            } else if (d < 0) {
                val s = acc[0] - (-d).toLong()
                val b = if (acc[0].toULong() < (-d).toULong()) 1L else 0L
                acc[0] = s
                if (b != 0L) {
                    for (j in 1 until 4) {
                        acc[j]--
                        if (acc[j] != -1L) break
                    }
                }
            }
        }
        return acc
    }
}
