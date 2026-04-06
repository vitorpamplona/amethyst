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

/** Tests for scalar arithmetic modulo n. */
class ScalarNTest {
    private fun hex(s: String) =
        U256.fromBytes(
            s.chunked(2).map { it.toInt(16).toByte() }.toByteArray(),
        )

    private fun toHex(a: IntArray) = U256.toBytes(a).joinToString("") { "%02x".format(it) }

    // ==================== isValid ====================

    @Test
    fun isValidNormal() {
        assertTrue(ScalarN.isValid(hex("0000000000000000000000000000000000000000000000000000000000000001")))
    }

    @Test
    fun isValidZero() {
        assertFalse(ScalarN.isValid(LongArray(4)))
    }

    @Test
    fun isValidN() {
        // n itself is not valid (must be < n)
        assertFalse(ScalarN.isValid(ScalarN.N))
    }

    @Test
    fun isValidNMinus1() {
        val nMinus1 = hex("fffffffffffffffffffffffffffffffebaaedce6af48a03bbfd25e8cd0364140")
        assertTrue(ScalarN.isValid(nMinus1))
    }

    // ==================== Arithmetic identities ====================

    @Test
    fun addZeroIdentity() {
        val a = hex("67e56582298859ddae725f972992a07c6c4fb9f62a8fff58ce3ca926a1063530")
        assertEquals(toHex(a), toHex(ScalarN.add(a, LongArray(4))))
    }

    @Test
    fun subSelfIsZero() {
        val a = hex("67e56582298859ddae725f972992a07c6c4fb9f62a8fff58ce3ca926a1063530")
        assertTrue(U256.isZero(ScalarN.sub(a, a)))
    }

    @Test
    fun addThenSubRoundTrips() {
        val a = hex("67e56582298859ddae725f972992a07c6c4fb9f62a8fff58ce3ca926a1063530")
        val b = hex("3982f19bef1615bccfbb05e321c10e1d4cba3df0e841c2e41eeb6016347653c3")
        assertEquals(toHex(a), toHex(ScalarN.sub(ScalarN.add(a, b), b)))
    }

    @Test
    fun negTwiceIsIdentity() {
        val a = hex("67e56582298859ddae725f972992a07c6c4fb9f62a8fff58ce3ca926a1063530")
        assertEquals(toHex(a), toHex(ScalarN.neg(ScalarN.neg(a))))
    }

    @Test
    fun addNegIsZero() {
        val a = hex("67e56582298859ddae725f972992a07c6c4fb9f62a8fff58ce3ca926a1063530")
        assertTrue(U256.isZero(ScalarN.add(a, ScalarN.neg(a))))
    }

    // ==================== Multiplication ====================

    @Test
    fun mulOneIdentity() {
        val a = hex("67e56582298859ddae725f972992a07c6c4fb9f62a8fff58ce3ca926a1063530")
        val one = longArrayOf(1, 0, 0, 0, 0, 0, 0, 0)
        assertEquals(toHex(a), toHex(ScalarN.mul(a, one)))
    }

    @Test
    fun mulCommutative() {
        val a = hex("67e56582298859ddae725f972992a07c6c4fb9f62a8fff58ce3ca926a1063530")
        val b = hex("3982f19bef1615bccfbb05e321c10e1d4cba3df0e841c2e41eeb6016347653c3")
        assertEquals(toHex(ScalarN.mul(a, b)), toHex(ScalarN.mul(b, a)))
    }

    @Test
    fun mulDistributive() {
        val a = hex("67e56582298859ddae725f972992a07c6c4fb9f62a8fff58ce3ca926a1063530")
        val b = hex("3982f19bef1615bccfbb05e321c10e1d4cba3df0e841c2e41eeb6016347653c3")
        val c = hex("0000000000000000000000000000000000000000000000000000000000000007")
        val lhs = ScalarN.mul(a, ScalarN.add(b, c))
        val rhs = ScalarN.add(ScalarN.mul(a, b), ScalarN.mul(a, c))
        assertEquals(toHex(lhs), toHex(rhs))
    }

    // ==================== Inversion ====================

    @Test
    fun invMulIsOne() {
        val a = hex("67e56582298859ddae725f972992a07c6c4fb9f62a8fff58ce3ca926a1063530")
        val aInv = ScalarN.inv(a)
        val product = ScalarN.mul(a, aInv)
        val one = longArrayOf(1, 0, 0, 0, 0, 0, 0, 0)
        assertEquals(toHex(one), toHex(product))
    }

    // ==================== Reduction near n ====================

    @Test
    fun addNearN() {
        // (n-1) + 1 should wrap to 0
        val nMinus1 = hex("fffffffffffffffffffffffffffffffebaaedce6af48a03bbfd25e8cd0364140")
        val one = longArrayOf(1, 0, 0, 0, 0, 0, 0, 0)
        assertTrue(U256.isZero(ScalarN.add(nMinus1, one)))
    }

    @Test
    fun addNearNWrap() {
        // (n-1) + 2 should give 1
        val nMinus1 = hex("fffffffffffffffffffffffffffffffebaaedce6af48a03bbfd25e8cd0364140")
        val two = longArrayOf(2, 0, 0, 0, 0, 0, 0, 0)
        val result = ScalarN.add(nMinus1, two)
        val one = longArrayOf(1, 0, 0, 0, 0, 0, 0, 0)
        assertEquals(toHex(one), toHex(result))
    }

    @Test
    fun mulLargeScalars() {
        // (n-1) * (n-1) ≡ 1 mod n (since (n-1) ≡ -1 and (-1)² = 1)
        val nMinus1 = hex("fffffffffffffffffffffffffffffffebaaedce6af48a03bbfd25e8cd0364140")
        val result = ScalarN.mul(nMinus1, nMinus1)
        val one = longArrayOf(1, 0, 0, 0, 0, 0, 0, 0)
        assertEquals(toHex(one), toHex(result))
    }

    @Test
    fun negOfZeroIsZero() {
        assertTrue(U256.isZero(ScalarN.neg(LongArray(4))))
    }

    @Test
    fun reduceOfN() {
        val result = ScalarN.reduce(ScalarN.N.copyOf())
        assertTrue(U256.isZero(result))
    }

    @Test
    fun reduceOfSmall() {
        val a = hex("0000000000000000000000000000000000000000000000000000000000000005")
        val result = ScalarN.reduce(a)
        assertEquals(toHex(a), toHex(result)) // No change — already < n
    }
}
