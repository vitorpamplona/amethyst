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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Tests for field arithmetic modulo p. */
class FieldPTest {
    private fun hex(s: String) = U256.fromBytes(s.hexToByteArray())

    private fun toHex(a: Fe4) = U256.toBytes(a).toHexKey()

    // ==================== Basic identities ====================

    @Test
    fun addZeroIdentity() {
        val a = hex("67e56582298859ddae725f972992a07c6c4fb9f62a8fff58ce3ca926a1063530")
        val zero = Fe4()
        assertEquals(toHex(a), toHex(FieldP.add(a, zero)))
    }

    @Test
    fun subSelfIsZero() {
        val a = hex("67e56582298859ddae725f972992a07c6c4fb9f62a8fff58ce3ca926a1063530")
        val result = FieldP.sub(a, a)
        assertTrue(result.isZero())
    }

    @Test
    fun addThenSubRoundTrips() {
        val a = hex("67e56582298859ddae725f972992a07c6c4fb9f62a8fff58ce3ca926a1063530")
        val b = hex("3982f19bef1615bccfbb05e321c10e1d4cba3df0e841c2e41eeb6016347653c3")
        val sum = FieldP.add(a, b)
        val back = FieldP.sub(sum, b)
        assertEquals(toHex(a), toHex(back))
    }

    @Test
    fun mulOneIdentity() {
        val a = hex("67e56582298859ddae725f972992a07c6c4fb9f62a8fff58ce3ca926a1063530")
        val one = Fe4(1L, 0L, 0L, 0L)
        assertEquals(toHex(a), toHex(FieldP.mul(a, one)))
    }

    // ==================== Reduction near p ====================

    @Test
    fun addNearP() {
        // (p - 1) + 1 = p ≡ 0 (mod p)
        val pMinus1 = hex("fffffffffffffffffffffffffffffffffffffffffffffffffffffffefffffc2e")
        val one = Fe4(1L, 0L, 0L, 0L)
        val result = FieldP.add(pMinus1, one)
        assertTrue(result.isZero())
    }

    @Test
    fun addNearPOverflow() {
        // (p - 1) + (p - 1) = 2p - 2 ≡ p - 2 (mod p)
        val pMinus1 = hex("fffffffffffffffffffffffffffffffffffffffffffffffffffffffefffffc2e")
        val result = FieldP.add(pMinus1, pMinus1)
        val expected = hex("fffffffffffffffffffffffffffffffffffffffffffffffffffffffefffffc2d") // p-2
        assertEquals(toHex(expected), toHex(result))
    }

    @Test
    fun subUnderflow() {
        // 0 - 1 ≡ p - 1 (mod p)
        val zero = Fe4()
        val one = Fe4(1L, 0L, 0L, 0L)
        val result = FieldP.sub(zero, one)
        val expected = hex("fffffffffffffffffffffffffffffffffffffffffffffffffffffffefffffc2e") // p-1
        assertEquals(toHex(expected), toHex(result))
    }

    // ==================== Negation ====================

    @Test
    fun negTwiceIsIdentity() {
        val a = hex("67e56582298859ddae725f972992a07c6c4fb9f62a8fff58ce3ca926a1063530")
        assertEquals(toHex(a), toHex(FieldP.neg(FieldP.neg(a))))
    }

    @Test
    fun negZeroIsZero() {
        assertTrue(U256.isZero(FieldP.neg(Fe4())))
    }

    @Test
    fun addNegIsZero() {
        val a = hex("67e56582298859ddae725f972992a07c6c4fb9f62a8fff58ce3ca926a1063530")
        val result = FieldP.add(a, FieldP.neg(a))
        assertTrue(result.isZero())
    }

    // ==================== Multiplication ====================

    @Test
    fun mulCommutative() {
        val a = hex("67e56582298859ddae725f972992a07c6c4fb9f62a8fff58ce3ca926a1063530")
        val b = hex("3982f19bef1615bccfbb05e321c10e1d4cba3df0e841c2e41eeb6016347653c3")
        assertEquals(toHex(FieldP.mul(a, b)), toHex(FieldP.mul(b, a)))
    }

    @Test
    fun sqrMatchesMul() {
        val a = hex("67e56582298859ddae725f972992a07c6c4fb9f62a8fff58ce3ca926a1063530")
        assertEquals(toHex(FieldP.mul(a, a)), toHex(FieldP.sqr(a)))
    }

    @Test
    fun mulDistributive() {
        // a * (b + c) = a*b + a*c
        val a = hex("67e56582298859ddae725f972992a07c6c4fb9f62a8fff58ce3ca926a1063530")
        val b = hex("3982f19bef1615bccfbb05e321c10e1d4cba3df0e841c2e41eeb6016347653c3")
        val c = hex("0000000000000000000000000000000000000000000000000000000000000007")
        val lhs = FieldP.mul(a, FieldP.add(b, c))
        val rhs = FieldP.add(FieldP.mul(a, b), FieldP.mul(a, c))
        assertEquals(toHex(lhs), toHex(rhs))
    }

    // ==================== Inversion ====================

    @Test
    fun invMulIsOne() {
        val a = hex("67e56582298859ddae725f972992a07c6c4fb9f62a8fff58ce3ca926a1063530")
        val aInv = FieldP.inv(a)
        val product = FieldP.mul(a, aInv)
        val one = Fe4(1L, 0L, 0L, 0L)
        assertEquals(toHex(one), toHex(product))
    }

    @Test
    fun invOfOne() {
        val one = Fe4(1L, 0L, 0L, 0L)
        assertEquals(toHex(one), toHex(FieldP.inv(one)))
    }

    @Test
    fun invOfPMinus1() {
        // (p-1)^(-1) = p-1 because (p-1)^2 = 1 mod p
        val pMinus1 = hex("fffffffffffffffffffffffffffffffffffffffffffffffffffffffefffffc2e")
        assertEquals(toHex(pMinus1), toHex(FieldP.inv(pMinus1)))
    }

    // ==================== Half ====================

    @Test
    fun halfOfEven() {
        val out = Fe4()
        val four = Fe4(4L, 0L, 0L, 0L)
        FieldP.half(out, four)
        assertEquals(2L, out.l0)
        assertEquals(0L, out.l1)
        assertEquals(0L, out.l2)
        assertEquals(0L, out.l3)
    }

    @Test
    fun halfOfOdd() {
        // half(1) = (1 + p) / 2 = (p + 1) / 2
        val out = Fe4()
        val one = Fe4(1L, 0L, 0L, 0L)
        FieldP.half(out, one)
        // Verify: 2 * half(1) = 1 mod p
        val doubled = FieldP.add(out, out)
        assertEquals(1L, doubled.l0)
        assertEquals(0L, doubled.l1)
        assertEquals(0L, doubled.l2)
        assertEquals(0L, doubled.l3)
    }

    @Test
    fun halfThenDoubleRoundTrips() {
        val a = hex("67e56582298859ddae725f972992a07c6c4fb9f62a8fff58ce3ca926a1063530")
        val out = Fe4()
        FieldP.half(out, a)
        val doubled = FieldP.add(out, out)
        assertEquals(toHex(a), toHex(doubled))
    }

    // ==================== Square root ====================

    @Test
    fun sqrtOfSquare() {
        // sqrt(a²) should return a or p-a
        val a = hex("67e56582298859ddae725f972992a07c6c4fb9f62a8fff58ce3ca926a1063530")
        val aSq = FieldP.sqr(a)
        val root = FieldP.sqrt(aSq)!!
        // root² should equal a²
        assertEquals(toHex(aSq), toHex(FieldP.sqr(root)))
    }

    @Test
    fun sqrtOfNonResidue() {
        // 3 is not a quadratic residue mod p (for secp256k1's p)
        val three = Fe4(3, 0L, 0L, 0L)
        assertNull(FieldP.sqrt(three))
    }

    @Test
    fun sqrtOfSecp256k1Generator() {
        // G.y² = G.x³ + 7. sqrt(G.x³ + 7) should give G.y or -G.y
        val gx = ECPoint.GX
        val gy = ECPoint.GY
        val x3 = FieldP.mul(FieldP.sqr(gx), gx)
        val y2 = FieldP.add(x3, Fe4(7, 0L, 0L, 0L))
        val root = FieldP.sqrt(y2)!!
        // root should be gy or -gy
        val isGy = U256.cmp(root, gy) == 0
        val isNegGy = U256.cmp(root, FieldP.neg(gy)) == 0
        assertTrue(isGy || isNegGy)
    }

    // ==================== Reduction edge cases ====================

    @Test
    fun reduceWideWithMaxValues() {
        // Multiply two values near p and verify result is < p
        val pMinus1 = hex("fffffffffffffffffffffffffffffffffffffffffffffffffffffffefffffc2e")
        val result = FieldP.mul(pMinus1, pMinus1)
        assertTrue(U256.cmp(result, FieldP.P) < 0, "Result should be < p")
        // (p-1)² ≡ 1 (mod p)
        val one = Fe4(1L, 0L, 0L, 0L)
        assertEquals(toHex(one), toHex(result))
    }

    // ==================== In-place operations ====================

    @Test
    fun inPlaceAdd() {
        val a = hex("0000000000000000000000000000000000000000000000000000000000000005")
        val b = hex("0000000000000000000000000000000000000000000000000000000000000003")
        val out = Fe4()
        FieldP.add(out, a, b)
        assertEquals(8L, out.l0)
    }

    @Test
    fun inPlaceSqr() {
        val a = hex("0000000000000000000000000000000000000000000000000000000000000005")
        val out = Fe4()
        FieldP.sqr(out, a)
        assertEquals(25L, out.l0) // 5² = 25
    }

    @Test
    fun halfOfPMinus1() {
        // half(p-1) should equal (p-1)/2
        val pMinus1 = hex("fffffffffffffffffffffffffffffffffffffffffffffffffffffffefffffc2e")
        val out = Fe4()
        FieldP.half(out, pMinus1)
        // Verify: 2 * half(p-1) = p-1
        val doubled = FieldP.add(out, out)
        assertEquals(toHex(pMinus1), toHex(doubled))
    }

    @Test
    fun invOfTwo() {
        val two = Fe4(2, 0L, 0L, 0L)
        val inv2 = FieldP.inv(two)
        val product = FieldP.mul(two, inv2)
        val one = Fe4(1L, 0L, 0L, 0L)
        assertEquals(toHex(one), toHex(product))
    }

    @Test
    fun sqrtOfZero() {
        val zero = Fe4()
        val root = FieldP.sqrt(zero)
        assertTrue(root != null && root.isZero())
    }

    @Test
    fun sqrtOfOne() {
        val one = Fe4(1L, 0L, 0L, 0L)
        val root = FieldP.sqrt(one)!!
        assertEquals(toHex(one), toHex(root))
    }

    @Test
    fun mulAliasingOutputEqualsInput() {
        // mul(out, a, b) where out == a should work
        val a = hex("67e56582298859ddae725f972992a07c6c4fb9f62a8fff58ce3ca926a1063530")
        val b = hex("3982f19bef1615bccfbb05e321c10e1d4cba3df0e841c2e41eeb6016347653c3")
        val expected = FieldP.mul(a, b)
        val aCopy = a.copyOf()
        FieldP.mul(aCopy, aCopy, b) // out == a
        assertEquals(toHex(expected), toHex(aCopy))
    }
}
