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

/**
 * 256-bit unsigned integer arithmetic for secp256k1 field and scalar operations.
 *
 * Numbers are represented as IntArray(8) in little-endian order where each element
 * holds 32 bits (treated as unsigned). Element [0] is the least significant limb.
 */
internal object U256 {
    val ZERO = IntArray(8)

    fun isZero(a: IntArray): Boolean {
        for (i in 0 until 8) if (a[i] != 0) return false
        return true
    }

    /** Compare: returns negative if a < b, 0 if equal, positive if a > b */
    fun cmp(
        a: IntArray,
        b: IntArray,
    ): Int {
        for (i in 7 downTo 0) {
            val ai = a[i].toLong() and 0xFFFFFFFFL
            val bi = b[i].toLong() and 0xFFFFFFFFL
            if (ai < bi) return -1
            if (ai > bi) return 1
        }
        return 0
    }

    /** a + b, returns (result, carry) where carry is 0 or 1 */
    fun addCarry(
        a: IntArray,
        b: IntArray,
    ): Pair<IntArray, Int> {
        val r = IntArray(8)
        var carry = 0L
        for (i in 0 until 8) {
            carry += (a[i].toLong() and 0xFFFFFFFFL) + (b[i].toLong() and 0xFFFFFFFFL)
            r[i] = carry.toInt()
            carry = carry ushr 32
        }
        return Pair(r, carry.toInt())
    }

    /** a - b, returns (result, borrow) where borrow is 0 or 1 */
    fun subBorrow(
        a: IntArray,
        b: IntArray,
    ): Pair<IntArray, Int> {
        val r = IntArray(8)
        var borrow = 0L
        for (i in 0 until 8) {
            val diff = (a[i].toLong() and 0xFFFFFFFFL) - (b[i].toLong() and 0xFFFFFFFFL) - borrow
            r[i] = diff.toInt()
            borrow = if (diff < 0) 1L else 0L
        }
        return Pair(r, borrow.toInt())
    }

    /** Full 256x256 -> 512 bit multiplication. Result is IntArray(16). */
    fun mulWide(
        a: IntArray,
        b: IntArray,
    ): IntArray {
        val r = IntArray(16)
        for (i in 0 until 8) {
            var carry = 0L
            val ai = a[i].toLong() and 0xFFFFFFFFL
            for (j in 0 until 8) {
                val prod = ai * (b[j].toLong() and 0xFFFFFFFFL) + (r[i + j].toLong() and 0xFFFFFFFFL) + carry
                r[i + j] = prod.toInt()
                carry = prod ushr 32
            }
            r[i + 8] = carry.toInt()
        }
        return r
    }

    /** Multiply 256-bit number by a small (fits in Long) constant. Result is IntArray(9). */
    fun mulSmall(
        a: IntArray,
        b: Long,
    ): IntArray {
        val r = IntArray(9)
        var carry = 0L
        for (i in 0 until 8) {
            carry += (a[i].toLong() and 0xFFFFFFFFL) * b
            r[i] = carry.toInt()
            carry = carry ushr 32
        }
        r[8] = carry.toInt()
        return r
    }

    /** Convert big-endian 32-byte array to IntArray(8) little-endian limbs */
    fun fromBytes(bytes: ByteArray): IntArray {
        require(bytes.size == 32) { "Expected 32 bytes, got ${bytes.size}" }
        val r = IntArray(8)
        for (i in 0 until 8) {
            val offset = 28 - i * 4
            r[i] = ((bytes[offset].toInt() and 0xFF) shl 24) or
                ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
                ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
                (bytes[offset + 3].toInt() and 0xFF)
        }
        return r
    }

    /** Convert IntArray(8) little-endian limbs to big-endian 32-byte array */
    fun toBytes(a: IntArray): ByteArray {
        val r = ByteArray(32)
        for (i in 0 until 8) {
            val offset = 28 - i * 4
            r[offset] = (a[i] ushr 24).toByte()
            r[offset + 1] = (a[i] ushr 16).toByte()
            r[offset + 2] = (a[i] ushr 8).toByte()
            r[offset + 3] = a[i].toByte()
        }
        return r
    }

    /** Check if bit at position pos is set (pos 0 = LSB) */
    fun testBit(
        a: IntArray,
        pos: Int,
    ): Boolean {
        val limb = pos / 32
        val bit = pos % 32
        return (a[limb] ushr bit) and 1 == 1
    }

    /** XOR two 256-bit values */
    fun xor(
        a: IntArray,
        b: IntArray,
    ): IntArray {
        val r = IntArray(8)
        for (i in 0 until 8) r[i] = a[i] xor b[i]
        return r
    }

    fun clone(a: IntArray): IntArray = a.copyOf()
}

/**
 * Field arithmetic modulo p = 2^256 - 2^32 - 977 (= 2^256 - 4294968273).
 * This is the base field of the secp256k1 curve.
 */
internal object FieldP {
    // p = FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEFFFFFC2F
    val P =
        intArrayOf(
            0xFFFFFC2F.toInt(),
            0xFFFFFFFE.toInt(),
            0xFFFFFFFF.toInt(),
            0xFFFFFFFF.toInt(),
            0xFFFFFFFF.toInt(),
            0xFFFFFFFF.toInt(),
            0xFFFFFFFF.toInt(),
            0xFFFFFFFF.toInt(),
        )

    // 2^256 mod p = 2^32 + 977 = 0x1000003D1 (fits in 33 bits)
    // As limbs: [977, 1, 0, 0, 0, 0, 0, 0]
    private val P_COMPLEMENT_LIMBS = intArrayOf(977, 1, 0, 0, 0, 0, 0, 0)

    /** Reduce a value that might be >= p (but < 2p) */
    fun reduce(a: IntArray): IntArray =
        if (U256.cmp(a, P) >= 0) {
            U256.subBorrow(a, P).first
        } else {
            a
        }

    /** Reduce a wide 512-bit product mod p */
    fun reduceWide(w: IntArray): IntArray {
        // w = hi * 2^256 + lo
        // ≡ lo + hi * (2^32 + 977) (mod p)
        //
        // Split: hi * (2^32 + 977) = (hi << 32) + hi * 977
        // hi * 977: each limb product fits in 42 bits, no overflow
        // hi << 32: shift limbs by one position

        val lo = IntArray(8)
        val hi = IntArray(8)
        for (i in 0 until 8) {
            lo[i] = w[i]
            hi[i] = w[i + 8]
        }

        // Compute hi * 977 (result fits in 266 bits = 9 limbs)
        val hiTimes977 = U256.mulSmall(hi, 977L)

        // Compute lo + hi * 977 + (hi << 32)
        // hi << 32 means: [0, hi[0], hi[1], ..., hi[7]] (9 limbs)
        val result = IntArray(8)
        var carry = 0L
        for (i in 0 until 8) {
            carry += (lo[i].toLong() and 0xFFFFFFFFL) +
                (hiTimes977[i].toLong() and 0xFFFFFFFFL) +
                if (i > 0) (hi[i - 1].toLong() and 0xFFFFFFFFL) else 0L
            result[i] = carry.toInt()
            carry = carry ushr 32
        }
        // Remaining overflow: carry + hiTimes977[8] + hi[7]
        var overflow =
            carry +
                (hiTimes977[8].toLong() and 0xFFFFFFFFL) +
                (hi[7].toLong() and 0xFFFFFFFFL)

        // Second round: overflow * (2^32 + 977)
        // overflow is at most ~35 bits, so overflow * 977 fits in Long easily
        if (overflow > 0) {
            val ov977 = overflow * 977L
            var c2 = 0L
            // Add ov977 to result[0..1], and overflow to result[1..2] (the <<32 part)
            for (i in 0 until 8) {
                c2 += (result[i].toLong() and 0xFFFFFFFFL)
                if (i == 0) c2 += (ov977 and 0xFFFFFFFFL)
                if (i == 1) c2 += (ov977 ushr 32) + (overflow and 0xFFFFFFFFL)
                if (i == 2) c2 += (overflow ushr 32)
                result[i] = c2.toInt()
                c2 = c2 ushr 32
            }
            // c2 should be 0 or very small; if > 0, do a third tiny round
            if (c2 > 0) {
                val tiny = c2 * 977L
                var c3 = 0L
                for (i in 0 until 3) {
                    c3 += (result[i].toLong() and 0xFFFFFFFFL)
                    if (i == 0) c3 += (tiny and 0xFFFFFFFFL)
                    if (i == 1) c3 += (tiny ushr 32) + (c2 and 0xFFFFFFFFL)
                    if (i == 2) c3 += (c2 ushr 32)
                    result[i] = c3.toInt()
                    c3 = c3 ushr 32
                }
            }
        }

        // Final reduction: result might still be >= p
        return reduce(result)
    }

    fun add(
        a: IntArray,
        b: IntArray,
    ): IntArray {
        val (sum, carry) = U256.addCarry(a, b)
        return if (carry != 0) {
            // sum + 2^256 ≡ sum + (2^32 + 977) (mod p)
            // carry is always 1 here, so just add the constant
            val (r2, c2) = U256.addCarry(sum, P_COMPLEMENT_LIMBS)
            if (c2 != 0) reduce(U256.addCarry(r2, P_COMPLEMENT_LIMBS).first) else reduce(r2)
        } else {
            reduce(sum)
        }
    }

    fun sub(
        a: IntArray,
        b: IntArray,
    ): IntArray {
        val (diff, borrow) = U256.subBorrow(a, b)
        return if (borrow != 0) {
            // Add p back
            U256.addCarry(diff, P).first
        } else {
            diff
        }
    }

    fun mul(
        a: IntArray,
        b: IntArray,
    ): IntArray = reduceWide(U256.mulWide(a, b))

    fun sqr(a: IntArray): IntArray = mul(a, a)

    fun neg(a: IntArray): IntArray = if (U256.isZero(a)) IntArray(8) else U256.subBorrow(P, a).first

    /** Modular inverse using Fermat's little theorem: a^(p-2) mod p */
    fun inv(a: IntArray): IntArray {
        require(!U256.isZero(a)) { "Cannot invert zero" }
        // p - 2 = FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEFFFFFC2D
        // Use square-and-multiply with an optimized addition chain for secp256k1
        return powModP(a, P_MINUS_2)
    }

    // p - 2
    private val P_MINUS_2 =
        intArrayOf(
            0xFFFFFC2D.toInt(),
            0xFFFFFFFE.toInt(),
            0xFFFFFFFF.toInt(),
            0xFFFFFFFF.toInt(),
            0xFFFFFFFF.toInt(),
            0xFFFFFFFF.toInt(),
            0xFFFFFFFF.toInt(),
            0xFFFFFFFF.toInt(),
        )

    // (p + 1) / 4 = 3FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFBFFFFF0C
    // Used for computing square roots since p ≡ 3 (mod 4)
    private val P_PLUS_1_DIV_4 =
        intArrayOf(
            0xBFFFFF0C.toInt(),
            0xFFFFFFFF.toInt(),
            0xFFFFFFFF.toInt(),
            0xFFFFFFFF.toInt(),
            0xFFFFFFFF.toInt(),
            0xFFFFFFFF.toInt(),
            0xFFFFFFFF.toInt(),
            0x3FFFFFFF,
        )

    /** Square root mod p. Returns null if a is not a quadratic residue. */
    fun sqrt(a: IntArray): IntArray? {
        // Since p ≡ 3 (mod 4), sqrt(a) = a^((p+1)/4) mod p
        val r = powModP(a, P_PLUS_1_DIV_4)
        // Verify: r^2 == a (mod p)
        return if (U256.cmp(mul(r, r), reduce(a)) == 0) r else null
    }

    /** Generic modular exponentiation mod p using square-and-multiply */
    private fun powModP(
        base: IntArray,
        exp: IntArray,
    ): IntArray {
        var result = intArrayOf(1, 0, 0, 0, 0, 0, 0, 0) // 1
        var b = base.copyOf()
        // Find highest set bit
        var highBit = 255
        while (highBit >= 0 && !U256.testBit(exp, highBit)) highBit--
        for (i in 0..highBit) {
            if (U256.testBit(exp, i)) {
                result = mul(result, b)
            }
            if (i < highBit) {
                b = sqr(b)
            }
        }
        return result
    }
}

/**
 * Scalar arithmetic modulo n (the order of the secp256k1 group).
 * n = FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364141
 */
internal object ScalarN {
    val N =
        intArrayOf(
            0xD0364141.toInt(),
            0xBFD25E8C.toInt(),
            0xAF48A03B.toInt(),
            0xBAAEDCE6.toInt(),
            0xFFFFFFFE.toInt(),
            0xFFFFFFFF.toInt(),
            0xFFFFFFFF.toInt(),
            0xFFFFFFFF.toInt(),
        )

    /** Check if 0 < a < n */
    fun isValid(a: IntArray): Boolean {
        if (U256.isZero(a)) return false
        return U256.cmp(a, N) < 0
    }

    /** Reduce mod n: for values up to 2n */
    fun reduce(a: IntArray): IntArray =
        if (U256.cmp(a, N) >= 0) {
            U256.subBorrow(a, N).first
        } else {
            a
        }

    /** Reduce a wide 512-bit value mod n */
    fun reduceWide(w: IntArray): IntArray {
        // Barrett-like reduction using schoolbook division
        // For simplicity, we do repeated subtraction with shifts
        // Since n is close to 2^256, the quotient is at most ~2^256.
        // We use a simpler approach: reduce by subtracting n * (hi_estimate)
        //
        // Actually, we'll use the same approach as field reduction but
        // for n which doesn't have a nice sparse form.
        // n = 2^256 - nComplement where nComplement = 2^256 - n
        //   = 0x14551231950B75FC4402DA1732FC9BEBF

        val lo = IntArray(8)
        val hi = IntArray(8)
        for (i in 0 until 8) {
            lo[i] = w[i]
            hi[i] = w[i + 8]
        }

        if (U256.isZero(hi)) return reduce(lo)

        // 2^256 mod n = N_COMPLEMENT
        // hi * 2^256 ≡ hi * N_COMPLEMENT (mod n)
        val hiTimesNC = U256.mulWide(hi, N_COMPLEMENT)
        // This is at most 384 bits. Add lo.
        val sum = IntArray(16)
        var carry = 0L
        for (i in 0 until 16) {
            carry += (hiTimesNC[i].toLong() and 0xFFFFFFFFL) +
                if (i < 8) (lo[i].toLong() and 0xFFFFFFFFL) else 0L
            sum[i] = carry.toInt()
            carry = carry ushr 32
        }

        // Now sum might be up to ~384 bits. Repeat reduction.
        val lo2 = IntArray(8)
        val hi2 = IntArray(8)
        for (i in 0 until 8) {
            lo2[i] = sum[i]
            hi2[i] = sum[i + 8]
        }

        if (U256.isZero(hi2)) return reduce(lo2)

        // Another round
        val hi2TimesNC = U256.mulWide(hi2, N_COMPLEMENT)
        var carry2 = 0L
        val result = IntArray(8)
        for (i in 0 until 8) {
            carry2 += (lo2[i].toLong() and 0xFFFFFFFFL) + (hi2TimesNC[i].toLong() and 0xFFFFFFFFL)
            result[i] = carry2.toInt()
            carry2 = carry2 ushr 32
        }
        // Handle any remaining overflow
        var overflow = carry2
        for (i in 8 until 16) {
            overflow += (hi2TimesNC[i].toLong() and 0xFFFFFFFFL)
        }
        // overflow * 2^256 ≡ overflow * N_COMPLEMENT (mod n)
        if (overflow > 0) {
            val corr = U256.mulSmall(N_COMPLEMENT, overflow)
            var c3 = 0L
            for (i in 0 until 8) {
                c3 += (result[i].toLong() and 0xFFFFFFFFL) + (corr[i].toLong() and 0xFFFFFFFFL)
                result[i] = c3.toInt()
                c3 = c3 ushr 32
            }
        }

        // Final reductions
        var r = result
        while (U256.cmp(r, N) >= 0) {
            r = U256.subBorrow(r, N).first
        }
        return r
    }

    // 2^256 - n = 14551231950B75FC4402DA1732FC9BEBF
    // In little-endian limbs:
    private val N_COMPLEMENT =
        intArrayOf(
            0x2FC9BEBF.toInt(),
            0x402DA173.toInt(),
            0x50B75FC4.toInt(),
            0x45512319.toInt(),
            0x00000001,
            0,
            0,
            0,
        )

    fun add(
        a: IntArray,
        b: IntArray,
    ): IntArray {
        val (sum, carry) = U256.addCarry(a, b)
        return if (carry != 0) {
            // sum + 2^256 ≡ sum + N_COMPLEMENT (mod n)
            val (r2, _) = U256.addCarry(sum, N_COMPLEMENT)
            reduce(r2)
        } else {
            reduce(sum)
        }
    }

    fun sub(
        a: IntArray,
        b: IntArray,
    ): IntArray {
        val (diff, borrow) = U256.subBorrow(a, b)
        return if (borrow != 0) {
            U256.addCarry(diff, N).first
        } else {
            diff
        }
    }

    fun mul(
        a: IntArray,
        b: IntArray,
    ): IntArray = reduceWide(U256.mulWide(a, b))

    fun neg(a: IntArray): IntArray = if (U256.isZero(a)) IntArray(8) else U256.subBorrow(N, a).first

    /** Modular inverse using Fermat's little theorem: a^(n-2) mod n */
    fun inv(a: IntArray): IntArray {
        require(!U256.isZero(a)) { "Cannot invert zero" }
        return powModN(a, N_MINUS_2)
    }

    // n - 2
    private val N_MINUS_2 =
        intArrayOf(
            0xD036413F.toInt(),
            0xBFD25E8C.toInt(),
            0xAF48A03B.toInt(),
            0xBAAEDCE6.toInt(),
            0xFFFFFFFE.toInt(),
            0xFFFFFFFF.toInt(),
            0xFFFFFFFF.toInt(),
            0xFFFFFFFF.toInt(),
        )

    private fun powModN(
        base: IntArray,
        exp: IntArray,
    ): IntArray {
        var result = intArrayOf(1, 0, 0, 0, 0, 0, 0, 0)
        var b = base.copyOf()
        var highBit = 255
        while (highBit >= 0 && !U256.testBit(exp, highBit)) highBit--
        for (i in 0..highBit) {
            if (U256.testBit(exp, i)) {
                result = mul(result, b)
            }
            if (i < highBit) {
                b = mul(b, b)
            }
        }
        return result
    }
}
