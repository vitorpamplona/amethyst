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
 *
 * Performance: All hot-path operations write into caller-provided output arrays
 * to avoid allocation. Only conversion methods (fromBytes/toBytes) allocate.
 */
internal object U256 {
    val ZERO = IntArray(8)

    fun isZero(a: IntArray): Boolean {
        // Merge all limbs to avoid branches
        return (a[0] or a[1] or a[2] or a[3] or a[4] or a[5] or a[6] or a[7]) == 0
    }

    /** Compare: returns negative if a < b, 0 if equal, positive if a > b */
    fun cmp(
        a: IntArray,
        b: IntArray,
    ): Int {
        for (i in 7 downTo 0) {
            val ai = a[i].toLong() and 0xFFFFFFFFL
            val bi = b[i].toLong() and 0xFFFFFFFFL
            if (ai != bi) return if (ai < bi) -1 else 1
        }
        return 0
    }

    /** a + b -> out, returns carry (0 or 1) */
    fun addTo(
        out: IntArray,
        a: IntArray,
        b: IntArray,
    ): Int {
        var carry = 0L
        for (i in 0 until 8) {
            carry += (a[i].toLong() and 0xFFFFFFFFL) + (b[i].toLong() and 0xFFFFFFFFL)
            out[i] = carry.toInt()
            carry = carry ushr 32
        }
        return carry.toInt()
    }

    /** a - b -> out, returns borrow (0 or 1) */
    fun subTo(
        out: IntArray,
        a: IntArray,
        b: IntArray,
    ): Int {
        var borrow = 0L
        for (i in 0 until 8) {
            val diff = (a[i].toLong() and 0xFFFFFFFFL) - (b[i].toLong() and 0xFFFFFFFFL) - borrow
            out[i] = diff.toInt()
            borrow = if (diff < 0) 1L else 0L
        }
        return borrow.toInt()
    }

    /** Full 256x256 -> 512 bit multiplication. Result written to out (size 16). */
    fun mulWide(
        out: IntArray,
        a: IntArray,
        b: IntArray,
    ) {
        for (i in 0 until 16) out[i] = 0
        for (i in 0 until 8) {
            var carry = 0L
            val ai = a[i].toLong() and 0xFFFFFFFFL
            for (j in 0 until 8) {
                val prod = ai * (b[j].toLong() and 0xFFFFFFFFL) + (out[i + j].toLong() and 0xFFFFFFFFL) + carry
                out[i + j] = prod.toInt()
                carry = prod ushr 32
            }
            out[i + 8] = carry.toInt()
        }
    }

    /** Convert big-endian 32-byte array to IntArray(8) little-endian limbs */
    fun fromBytes(bytes: ByteArray): IntArray {
        require(bytes.size == 32)
        val r = IntArray(8)
        for (i in 0 until 8) {
            val o = 28 - i * 4
            r[i] = ((bytes[o].toInt() and 0xFF) shl 24) or
                ((bytes[o + 1].toInt() and 0xFF) shl 16) or
                ((bytes[o + 2].toInt() and 0xFF) shl 8) or
                (bytes[o + 3].toInt() and 0xFF)
        }
        return r
    }

    /** Convert IntArray(8) little-endian limbs to big-endian 32-byte array */
    fun toBytes(a: IntArray): ByteArray {
        val r = ByteArray(32)
        for (i in 0 until 8) {
            val o = 28 - i * 4
            r[o] = (a[i] ushr 24).toByte()
            r[o + 1] = (a[i] ushr 16).toByte()
            r[o + 2] = (a[i] ushr 8).toByte()
            r[o + 3] = a[i].toByte()
        }
        return r
    }

    /** Write big-endian bytes into existing array at offset */
    fun toBytesInto(
        a: IntArray,
        dest: ByteArray,
        offset: Int,
    ) {
        for (i in 0 until 8) {
            val o = offset + 28 - i * 4
            dest[o] = (a[i] ushr 24).toByte()
            dest[o + 1] = (a[i] ushr 16).toByte()
            dest[o + 2] = (a[i] ushr 8).toByte()
            dest[o + 3] = a[i].toByte()
        }
    }

    /** Get 4-bit nibble from scalar at position pos (pos 0 = lowest nibble) */
    fun getNibble(
        a: IntArray,
        pos: Int,
    ): Int {
        val limb = pos / 8
        val shift = (pos % 8) * 4
        return (a[limb] ushr shift) and 0xF
    }

    /** Check if bit at position pos is set */
    fun testBit(
        a: IntArray,
        pos: Int,
    ): Boolean = (a[pos / 32] ushr (pos % 32)) and 1 == 1

    /** XOR: out = a xor b */
    fun xorTo(
        out: IntArray,
        a: IntArray,
        b: IntArray,
    ) {
        for (i in 0 until 8) out[i] = a[i] xor b[i]
    }

    /** Copy a into out */
    fun copyInto(
        out: IntArray,
        a: IntArray,
    ) {
        a.copyInto(out)
    }
}

/**
 * Field arithmetic modulo p = 2^256 - 2^32 - 977.
 * All hot-path operations write results into caller-provided output arrays.
 */
internal object FieldP {
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

    // Thread-local scratch space to avoid allocation in hot path
    // Since Kotlin/JVM coroutines are cooperative (not preemptive on same thread),
    // thread-locals are safe as long as we don't call suspend functions mid-computation.
    private val wide = ThreadLocal.withInitial { IntArray(16) }

    /** Reduce in-place: if a >= p, subtract p */
    fun reduceSelf(a: IntArray) {
        if (U256.cmp(a, P) >= 0) {
            U256.subTo(a, a, P)
        }
    }

    /** Reduce a wide 512-bit value in w[0..15] -> out[0..7] mod p */
    fun reduceWide(
        out: IntArray,
        w: IntArray,
    ) {
        // w ≡ lo + hi * (2^32 + 977) (mod p)
        // Split: hi * (2^32 + 977) = (hi << 32) + hi * 977

        // Compute: out = lo + hi*977 + (hi << 32)
        var carry = 0L
        for (i in 0 until 8) {
            // lo[i]
            carry += (w[i].toLong() and 0xFFFFFFFFL)
            // hi[i] * 977
            carry += (w[i + 8].toLong() and 0xFFFFFFFFL) * 977L
            // hi[i-1] (the <<32 shift)
            if (i > 0) carry += (w[i + 7].toLong() and 0xFFFFFFFFL)
            out[i] = carry.toInt()
            carry = carry ushr 32
        }
        // Remaining: carry + hi[7]
        var overflow = carry + (w[15].toLong() and 0xFFFFFFFFL)

        // Second round: overflow * (2^32 + 977)
        if (overflow > 0) {
            val ov977 = overflow * 977L
            var c2 = 0L
            for (i in 0 until 8) {
                c2 += (out[i].toLong() and 0xFFFFFFFFL)
                if (i == 0) c2 += (ov977 and 0xFFFFFFFFL)
                if (i == 1) c2 += (ov977 ushr 32) + (overflow and 0xFFFFFFFFL)
                if (i == 2) c2 += (overflow ushr 32)
                out[i] = c2.toInt()
                c2 = c2 ushr 32
            }
            if (c2 > 0) {
                val tiny = c2 * 977L
                var c3 = 0L
                for (i in 0 until 3) {
                    c3 += (out[i].toLong() and 0xFFFFFFFFL)
                    if (i == 0) c3 += (tiny and 0xFFFFFFFFL)
                    if (i == 1) c3 += (tiny ushr 32) + (c2 and 0xFFFFFFFFL)
                    if (i == 2) c3 += (c2 ushr 32)
                    out[i] = c3.toInt()
                    c3 = c3 ushr 32
                }
            }
        }
        reduceSelf(out)
    }

    /** out = a + b mod p */
    fun add(
        out: IntArray,
        a: IntArray,
        b: IntArray,
    ) {
        val carry = U256.addTo(out, a, b)
        if (carry != 0) {
            // out + 2^256 ≡ out + (2^32 + 977) mod p
            var c = 977L + (out[0].toLong() and 0xFFFFFFFFL)
            out[0] = c.toInt()
            c = c ushr 32
            c += 1L + (out[1].toLong() and 0xFFFFFFFFL)
            out[1] = c.toInt()
            c = c ushr 32
            for (i in 2 until 8) {
                c += (out[i].toLong() and 0xFFFFFFFFL)
                out[i] = c.toInt()
                c = c ushr 32
            }
        }
        reduceSelf(out)
    }

    /** out = a - b mod p */
    fun sub(
        out: IntArray,
        a: IntArray,
        b: IntArray,
    ) {
        val borrow = U256.subTo(out, a, b)
        if (borrow != 0) {
            U256.addTo(out, out, P)
        }
    }

    /** out = a * b mod p. Uses thread-local scratch space. */
    fun mul(
        out: IntArray,
        a: IntArray,
        b: IntArray,
    ) {
        val w = wide.get()
        U256.mulWide(w, a, b)
        reduceWide(out, w)
    }

    /** out = a² mod p. Uses thread-local scratch space. */
    fun sqr(
        out: IntArray,
        a: IntArray,
    ) {
        mul(out, a, a)
    }

    /** out = -a mod p */
    fun neg(
        out: IntArray,
        a: IntArray,
    ) {
        if (U256.isZero(a)) {
            for (i in 0 until 8) out[i] = 0
        } else {
            U256.subTo(out, P, a)
        }
    }

    /** out = a^(-1) mod p via Fermat's little theorem */
    fun inv(
        out: IntArray,
        a: IntArray,
    ) {
        require(!U256.isZero(a))
        powModP(out, a, P_MINUS_2)
    }

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

    /** out = sqrt(a) mod p, returns false if not a QR */
    fun sqrt(
        out: IntArray,
        a: IntArray,
    ): Boolean {
        powModP(out, a, P_PLUS_1_DIV_4)
        // Verify: out² == a
        val check = IntArray(8)
        mul(check, out, out)
        // Need a reduced copy of a for comparison
        val ar = IntArray(8)
        U256.copyInto(ar, a)
        reduceSelf(ar)
        return U256.cmp(check, ar) == 0
    }

    /** out = base^exp mod p */
    private fun powModP(
        out: IntArray,
        base: IntArray,
        exp: IntArray,
    ) {
        // Left-to-right square-and-multiply
        val b = IntArray(8)
        U256.copyInto(b, base)

        var highBit = 255
        while (highBit >= 0 && !U256.testBit(exp, highBit)) highBit--
        if (highBit < 0) {
            out[0] = 1
            for (i in 1 until 8) out[i] = 0
            return
        }

        // Start with the base (first bit is always 1)
        U256.copyInto(out, b)
        for (i in highBit - 1 downTo 0) {
            sqr(out, out) // out = out²
            if (U256.testBit(exp, i)) {
                mul(out, out, b) // out = out * base
            }
        }
    }

    // === Allocating convenience wrappers (for non-hot paths) ===

    fun add(
        a: IntArray,
        b: IntArray,
    ): IntArray {
        val r = IntArray(8)
        add(r, a, b)
        return r
    }

    fun sub(
        a: IntArray,
        b: IntArray,
    ): IntArray {
        val r = IntArray(8)
        sub(r, a, b)
        return r
    }

    fun mul(
        a: IntArray,
        b: IntArray,
    ): IntArray {
        val r = IntArray(8)
        mul(r, a, b)
        return r
    }

    fun sqr(a: IntArray): IntArray {
        val r = IntArray(8)
        sqr(r, a)
        return r
    }

    fun neg(a: IntArray): IntArray {
        val r = IntArray(8)
        neg(r, a)
        return r
    }

    fun inv(a: IntArray): IntArray {
        val r = IntArray(8)
        inv(r, a)
        return r
    }

    fun sqrt(a: IntArray): IntArray? {
        val r = IntArray(8)
        return if (sqrt(r, a)) r else null
    }

    fun reduce(a: IntArray): IntArray {
        val r = a.copyOf()
        reduceSelf(r)
        return r
    }
}

/**
 * Scalar arithmetic modulo n (the order of the secp256k1 group).
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

    fun isValid(a: IntArray): Boolean = !U256.isZero(a) && U256.cmp(a, N) < 0

    fun reduce(a: IntArray): IntArray =
        if (U256.cmp(a, N) >= 0) {
            val r = IntArray(8)
            U256.subTo(r, a, N)
            r
        } else {
            a
        }

    fun add(
        a: IntArray,
        b: IntArray,
    ): IntArray {
        val r = IntArray(8)
        val carry = U256.addTo(r, a, b)
        if (carry != 0) {
            U256.addTo(r, r, N_COMPLEMENT)
            reduceSelf(r)
        } else {
            reduceSelf(r)
        }
        return r
    }

    fun sub(
        a: IntArray,
        b: IntArray,
    ): IntArray {
        val r = IntArray(8)
        val borrow = U256.subTo(r, a, b)
        if (borrow != 0) U256.addTo(r, r, N)
        return r
    }

    fun mul(
        a: IntArray,
        b: IntArray,
    ): IntArray = reduceWide(mulWideAlloc(a, b))

    fun neg(a: IntArray): IntArray {
        if (U256.isZero(a)) return IntArray(8)
        val r = IntArray(8)
        U256.subTo(r, N, a)
        return r
    }

    fun inv(a: IntArray): IntArray {
        require(!U256.isZero(a))
        return powModN(a, N_MINUS_2)
    }

    private fun reduceSelf(a: IntArray) {
        if (U256.cmp(a, N) >= 0) U256.subTo(a, a, N)
    }

    private fun mulWideAlloc(
        a: IntArray,
        b: IntArray,
    ): IntArray {
        val w = IntArray(16)
        U256.mulWide(w, a, b)
        return w
    }

    private fun reduceWide(w: IntArray): IntArray {
        val lo = IntArray(8)
        val hi = IntArray(8)
        for (i in 0 until 8) {
            lo[i] = w[i]
            hi[i] = w[i + 8]
        }
        if (U256.isZero(hi)) {
            reduceSelf(lo)
            return lo
        }

        val hiTimesNC = IntArray(16)
        U256.mulWide(hiTimesNC, hi, N_COMPLEMENT)
        val sum = IntArray(16)
        var carry = 0L
        for (i in 0 until 16) {
            carry += (hiTimesNC[i].toLong() and 0xFFFFFFFFL) +
                if (i < 8) (lo[i].toLong() and 0xFFFFFFFFL) else 0L
            sum[i] = carry.toInt()
            carry = carry ushr 32
        }

        val lo2 = IntArray(8)
        val hi2 = IntArray(8)
        for (i in 0 until 8) {
            lo2[i] = sum[i]
            hi2[i] = sum[i + 8]
        }
        if (U256.isZero(hi2)) {
            reduceSelf(lo2)
            return lo2
        }

        val hi2NC = IntArray(16)
        U256.mulWide(hi2NC, hi2, N_COMPLEMENT)
        var c2 = 0L
        val result = IntArray(8)
        for (i in 0 until 8) {
            c2 += (lo2[i].toLong() and 0xFFFFFFFFL) + (hi2NC[i].toLong() and 0xFFFFFFFFL)
            result[i] = c2.toInt()
            c2 = c2 ushr 32
        }
        var overflow = c2
        for (i in 8 until 16) overflow += (hi2NC[i].toLong() and 0xFFFFFFFFL)
        if (overflow > 0) {
            val corr = IntArray(9)
            var cc = 0L
            for (i in 0 until 8) {
                cc += (N_COMPLEMENT[i].toLong() and 0xFFFFFFFFL) * overflow
                corr[i] = cc.toInt()
                cc = cc ushr 32
            }
            var c3 = 0L
            for (i in 0 until 8) {
                c3 += (result[i].toLong() and 0xFFFFFFFFL) + (corr[i].toLong() and 0xFFFFFFFFL)
                result[i] = c3.toInt()
                c3 = c3 ushr 32
            }
        }
        while (U256.cmp(result, N) >= 0) U256.subTo(result, result, N)
        return result
    }

    private fun powModN(
        base: IntArray,
        exp: IntArray,
    ): IntArray {
        val result = IntArray(8)
        val b = base.copyOf()
        var highBit = 255
        while (highBit >= 0 && !U256.testBit(exp, highBit)) highBit--
        if (highBit < 0) {
            result[0] = 1
            return result
        }
        U256.copyInto(result, b)
        for (i in highBit - 1 downTo 0) {
            val sq = mul(result, result)
            U256.copyInto(result, sq)
            if (U256.testBit(exp, i)) {
                val prod = mul(result, b)
                U256.copyInto(result, prod)
            }
        }
        return result
    }
}
