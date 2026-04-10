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

import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Comprehensive tests for GLV endomorphism and wNAF encoding. */
class GlvTest {
    private fun toHex(a: Fe4) = U256.toBytes(a).toHexKey()

    private fun hex(s: String) = U256.fromBytes(s.hexToByteArray())

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
        val split = Glv.splitScalar(Fe4())
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
        // Upper 2 limbs should be zero for a proper 128-bit half-scalar
        assertEquals(0L, split.k1.l2, "k1 limb 2 should be 0")
        assertEquals(0L, split.k1.l3, "k1 limb 3 should be 0")
        assertEquals(0L, split.k2.l2, "k2 limb 2 should be 0")
        assertEquals(0L, split.k2.l3, "k2 limb 3 should be 0")
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
        val one = Fe4(1L, 0L, 0L, 0L)
        assertEquals(toHex(one), toHex(b3))
    }

    @Test
    fun endomorphismCorrectness() {
        // λ·G should equal (β·Gx, Gy)
        val result = MutablePoint()
        ECPoint.mulG(result, LAMBDA)
        val rx = Fe4()
        val ry = Fe4()
        ECPoint.toAffine(result, rx, ry)
        assertEquals(toHex(FieldP.mul(ECPoint.GX, Glv.BETA)), toHex(rx))
        assertEquals(toHex(ECPoint.GY), toHex(ry))
    }

    // ==================== wNAF Encoding ====================

    @Test
    fun wnafReconstructionSmall() {
        // wNAF digits should reconstruct to the original scalar
        val k = Fe4(17L, 0L, 0L, 0L) // 17 = 10001 in binary
        val digits = Glv.wnaf(k, 5, 256)
        val reconstructed = reconstructWnaf(digits)
        assertEquals(0, U256.cmp(k, reconstructed))
    }

    @Test
    fun wnafReconstructionLarge() {
        val k = hex("e907831f80848d1069a5371b402410364bdf1c5f8307b0084c55f1ce2dca8215")
        val digits = Glv.wnaf(k, 5, 256)
        val reconstructed = reconstructWnaf(digits)
        assertEquals(0, U256.cmp(k, reconstructed), "wNAF reconstruction mismatch")
    }

    @Test
    fun wnafCarryOverflowBit255() {
        // Regression: scalars with high bits set caused carries past bit 255 to be dropped.
        // This scalar (negE from BIP-340 vector 0) triggers the carry at bit 256.
        val k = hex("944946c56e0d133f326db0b0645544a04bfcc5a0f447ad6d0227958414d8ba73")
        val digits = Glv.wnaf(k, 5, 256)
        val reconstructed = reconstructWnaf(digits)
        assertEquals(0, U256.cmp(k, reconstructed), "wNAF carry overflow reconstruction mismatch")
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
        val k = Fe4(-7296712173568108936L, 2459565876494606609L, 0L, 0L)
        val digits = Glv.wnaf(k, 5, 129)
        val reconstructed = reconstructWnaf(digits)
        assertEquals(0, U256.cmp(k, reconstructed), "129-bit wNAF reconstruction mismatch")
    }

    // ==================== Integration: GLV mulDoubleG ====================

    @Test
    fun mulDoubleGWithZeroE() {
        // s·G + 0·P = s·G
        val s = hex("67e56582298859ddae725f972992a07c6c4fb9f62a8fff58ce3ca926a1063530")
        val p = MutablePoint()
        ECPoint.mulG(p, Fe4(2L, 0L, 0L, 0L))
        val combined = MutablePoint()
        ECPoint.mulDoubleG(combined, s, p, Fe4())
        val cx = Fe4()
        val cy = Fe4()
        ECPoint.toAffine(combined, cx, cy)
        val direct = MutablePoint()
        ECPoint.mulG(direct, s)
        val dx = Fe4()
        val dy = Fe4()
        ECPoint.toAffine(direct, dx, dy)
        assertEquals(toHex(dx), toHex(cx))
    }

    // ==================== Helpers ====================

    /** Reconstruct a scalar from wNAF digits using Horner's method. */
    private fun reconstructWnaf(digits: IntArray): Fe4 {
        var acc = Fe4()
        for (bit in digits.size - 1 downTo 0) {
            // Double: acc = acc * 2 (unsigned shift left by 1)
            val doubled = Fe4()
            doubled.l0 = (acc.l0 shl 1)
            doubled.l1 = (acc.l1 shl 1) or (acc.l0 ushr 63)
            doubled.l2 = (acc.l2 shl 1) or (acc.l1 ushr 63)
            doubled.l3 = (acc.l3 shl 1) or (acc.l2 ushr 63)
            acc = doubled
            // Add digit
            val d = digits[bit]
            if (d > 0) {
                val s = acc.l0 + d.toLong()
                val c = if (s.toULong() < acc.l0.toULong()) 1L else 0L
                acc.l0 = s
                if (c != 0L) {
                    acc.l1++
                    if (acc.l1 == 0L) {
                        acc.l2++
                        if (acc.l2 == 0L) acc.l3++
                    }
                }
            } else if (d < 0) {
                val s = acc.l0 - (-d).toLong()
                val b = if (acc.l0.toULong() < (-d).toULong()) 1L else 0L
                acc.l0 = s
                if (b != 0L) {
                    acc.l1--
                    if (acc.l1 == -1L) {
                        acc.l2--
                        if (acc.l2 == -1L) acc.l3--
                    }
                }
            }
        }
        return acc
    }
}
