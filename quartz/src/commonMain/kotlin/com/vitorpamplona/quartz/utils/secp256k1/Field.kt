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

// =====================================================================================
// 256-BIT ARITHMETIC AND MODULAR FIELD OPERATIONS FOR secp256k1
// =====================================================================================
//
// This file implements the foundational math needed for elliptic curve cryptography on
// the secp256k1 curve (used by Bitcoin and Nostr). It provides:
//
//   - U256:    Raw 256-bit unsigned integer arithmetic (add, subtract, multiply, compare)
//   - FieldP:  Arithmetic modulo p (the field prime), used for point coordinates
//   - ScalarN: Arithmetic modulo n (the group order), used for private keys and signatures
//
// REPRESENTATION
// ==============
// A 256-bit number is stored as IntArray(8) in little-endian order. Each Int holds 32 bits,
// treated as unsigned. Element [0] is the least significant. For example, the number 1 is
// stored as [1, 0, 0, 0, 0, 0, 0, 0].
//
// We chose 8×32-bit limbs over alternatives like 5×52-bit because Kotlin's Long (64-bit)
// can hold the product of two 32-bit values without overflow (32+32=64 ≤ 63 signed bits
// for most cases). The C reference implementation uses 5×52-bit with compiler-specific
// __int128 which is unavailable on JVM. A future optimization could use 5×52-bit with
// split-product techniques to reduce the inner product count from 64 to ~40.
//
// FIELD REDUCTION
// ===============
// secp256k1's field prime p = 2^256 - 2^32 - 977 has a special sparse form that makes
// modular reduction efficient. After a 512-bit multiplication result, we split into
// lo (256-bit) + hi (256-bit) and use the identity:
//
//   hi × 2^256 ≡ hi × (2^32 + 977)  (mod p)
//
// This replaces a generic 512-bit mod with a 256×33-bit multiply and add. A second
// round handles any remaining overflow. This is much cheaper than generic Barrett or
// Montgomery reduction because secp256k1's prime was specifically chosen for this property.
//
// MODULAR INVERSION
// =================
// We use Fermat's little theorem: a^(-1) = a^(p-2) mod p, computed via repeated
// squaring (~255 squarings + ~255 multiplications). This is simple but expensive.
//
// The C reference library uses a faster algorithm called "safegcd" (Bernstein-Yang 2019)
// that computes the modular inverse using ~590 cheap division steps (shifts and additions)
// instead of ~510 field multiplications. Implementing safegcd would make inversion ~10×
// faster, but since inversion only happens once per signature verification (in the final
// Jacobian-to-affine conversion), the total impact on verify throughput is modest (~10%).
//
// PERFORMANCE APPROACH
// ====================
// Hot-path functions (mul, sqr, add, sub) take an output IntArray parameter to avoid
// allocating a new array on every call. During a single signature verification, the field
// multiplication is called thousands of times — allocating a new IntArray(8) each time
// would create significant GC pressure on Android. Convenience wrappers that allocate
// are provided for non-hot-path code.
//
// A thread-local IntArray(16) scratch buffer is reused across field multiplications to
// avoid allocating a 512-bit intermediate on every mul/sqr call.
// =====================================================================================

/**
 * Raw 256-bit unsigned integer arithmetic.
 *
 * All operations treat IntArray(8) as a 256-bit unsigned integer in little-endian
 * limb order. No modular reduction is performed — callers (FieldP, ScalarN) handle that.
 */
internal object U256 {
    val ZERO = IntArray(8)

    /** Branchless zero check — OR all limbs, avoiding per-limb branching. */
    fun isZero(a: IntArray): Boolean = (a[0] or a[1] or a[2] or a[3] or a[4] or a[5] or a[6] or a[7]) == 0

    /** Unsigned comparison. Returns -1 if a < b, 0 if equal, 1 if a > b. */
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

    /** out = a + b. Returns the carry bit (0 or 1). Safe for out aliasing a or b. */
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

    /** out = a - b. Returns the borrow bit (0 or 1). Safe for out aliasing a or b. */
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

    /**
     * Schoolbook multiplication: out = a × b (512-bit result in IntArray(16)).
     *
     * Uses the standard O(n²) algorithm with 8×8 = 64 inner Long multiplications.
     * Each partial product is at most 32×32 = 64 bits, which fits in a signed Long
     * with room for carry accumulation.
     */
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

    /**
     * Dedicated squaring: out = a² (512-bit result in IntArray(16)).
     *
     * Exploits the identity a²[i,j] = a²[j,i] to compute each cross-product once
     * and double it, reducing from 64 to 36 multiplications:
     *   - 28 cross-products (i < j), doubled
     *   - 8 diagonal products (i == i)
     *
     * This gives ~40% fewer multiplications than generic mulWide for squaring.
     */
    fun sqrWide(
        out: IntArray,
        a: IntArray,
    ) {
        for (i in 0 until 16) out[i] = 0

        // Pass 1: accumulate cross-products a[i]*a[j] for i < j (single, not doubled yet)
        for (i in 0 until 8) {
            var carry = 0L
            val ai = a[i].toLong() and 0xFFFFFFFFL
            for (j in i + 1 until 8) {
                val prod = ai * (a[j].toLong() and 0xFFFFFFFFL) + (out[i + j].toLong() and 0xFFFFFFFFL) + carry
                out[i + j] = prod.toInt()
                carry = prod ushr 32
            }
            out[i + 8] = carry.toInt()
        }

        // Pass 2: double all cross-products (shift entire 512-bit result left by 1 bit)
        var shiftCarry = 0
        for (i in 1 until 16) {
            val v = out[i]
            out[i] = (v shl 1) or shiftCarry
            shiftCarry = v ushr 31
        }

        // Pass 3: add diagonal products a[i]² at positions 2i and 2i+1
        var dCarry = 0L
        for (i in 0 until 8) {
            val ai = a[i].toLong() and 0xFFFFFFFFL
            val diag = ai * ai
            val pos = 2 * i
            dCarry += (out[pos].toLong() and 0xFFFFFFFFL) + (diag and 0xFFFFFFFFL)
            out[pos] = dCarry.toInt()
            dCarry = dCarry ushr 32
            dCarry += (out[pos + 1].toLong() and 0xFFFFFFFFL) + (diag ushr 32)
            out[pos + 1] = dCarry.toInt()
            dCarry = dCarry ushr 32
        }
    }

    // ==================== Serialization ====================

    /** Decode a big-endian 32-byte array into little-endian IntArray(8). */
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

    /** Encode little-endian IntArray(8) to a big-endian 32-byte array. */
    fun toBytes(a: IntArray): ByteArray {
        val r = ByteArray(32)
        toBytesInto(a, r, 0)
        return r
    }

    /** Encode into an existing byte array at the given offset. Avoids allocation. */
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

    // ==================== Bit manipulation ====================

    /** Extract 4-bit nibble at position pos (0 = lowest nibble). Used by windowed scalar mul. */
    fun getNibble(
        a: IntArray,
        pos: Int,
    ): Int {
        val limb = pos / 8
        val shift = (pos % 8) * 4
        return (a[limb] ushr shift) and 0xF
    }

    /** Test if bit at position pos is set (0 = LSB). */
    fun testBit(
        a: IntArray,
        pos: Int,
    ): Boolean = (a[pos / 32] ushr (pos % 32)) and 1 == 1

    /** out = a XOR b. Used by BIP-340 signing for nonce derivation. */
    fun xorTo(
        out: IntArray,
        a: IntArray,
        b: IntArray,
    ) {
        for (i in 0 until 8) out[i] = a[i] xor b[i]
    }

    /** Copy the contents of a into out. */
    fun copyInto(
        out: IntArray,
        a: IntArray,
    ) {
        a.copyInto(out)
    }
}

/**
 * Arithmetic modulo the secp256k1 field prime: p = 2^256 - 2^32 - 977.
 *
 * This is the "base field" — the coordinates (x, y) of every point on the secp256k1
 * curve are elements of this field. All coordinate math during point addition and
 * doubling uses these operations.
 *
 * Hot-path functions accept an output IntArray parameter to avoid per-call allocation.
 * Convenience wrappers that return a new IntArray are provided for non-performance-critical code.
 */
internal object FieldP {
    /** The field prime: p = 0xFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEFFFFFC2F */
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

    /**
     * Thread-local 512-bit scratch buffer, reused across mul/sqr calls.
     *
     * Each field multiplication produces a 512-bit intermediate result before reduction.
     * Rather than allocating a new IntArray(16) on every mul (thousands of times per
     * verify), we reuse this thread-local buffer. This is safe because:
     * - EC point operations are synchronous (no suspension points mid-computation)
     * - Each thread gets its own buffer via ThreadLocal
     */
    private val wide = ThreadLocal.withInitial { IntArray(16) }

    // ==================== Core arithmetic ====================

    /** out = a + b mod p */
    fun add(
        out: IntArray,
        a: IntArray,
        b: IntArray,
    ) {
        val carry = U256.addTo(out, a, b)
        if (carry != 0) {
            // Overflow past 2^256: add 2^256 mod p = 2^32 + 977
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
        if (borrow != 0) U256.addTo(out, out, P) // Underflow: add p
    }

    /** out = a × b mod p */
    fun mul(
        out: IntArray,
        a: IntArray,
        b: IntArray,
    ) {
        val w = wide.get()
        U256.mulWide(w, a, b)
        reduceWide(out, w)
    }

    /** out = a² mod p. Uses dedicated squaring for ~40% fewer inner products. */
    fun sqr(
        out: IntArray,
        a: IntArray,
    ) {
        val w = wide.get()
        U256.sqrWide(w, a)
        reduceWide(out, w)
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

    /**
     * out = a / 2 mod p (field halving).
     *
     * If a is odd, computes (a + p) / 2 (since p is odd, a+p is even).
     * Implemented branchlessly using a conditional mask to avoid timing leaks.
     * Used by the optimized point doubling formula to compute (3/2)x² cheaply.
     */
    fun half(
        out: IntArray,
        a: IntArray,
    ) {
        val mask = (-(a[0] and 1)).toLong() // all 1s if odd, all 0s if even
        var carry = 0L
        for (i in 0 until 8) {
            carry += (a[i].toLong() and 0xFFFFFFFFL) + ((P[i].toLong() and 0xFFFFFFFFL) and mask)
            out[i] = carry.toInt()
            carry = carry ushr 32
        }
        // Right-shift by 1 (carry becomes the top bit)
        for (i in 0 until 7) {
            out[i] = (out[i] ushr 1) or (out[i + 1] shl 31)
        }
        out[7] = (out[7] ushr 1) or (carry.toInt() shl 31)
    }

    // ==================== Inversion and square root ====================

    /**
     * out = a^(-1) mod p using Fermat's little theorem: a^(p-2) mod p.
     *
     * This computes the modular inverse via exponentiation by repeated squaring.
     * It requires ~255 squarings and ~255 multiplications (one per bit of p-2).
     *
     * Called once per signature verify (in Jacobian-to-affine conversion) and once
     * per public key decompression (in square root).
     */
    fun inv(
        out: IntArray,
        a: IntArray,
    ) {
        require(!U256.isZero(a))
        powModP(out, a, P_MINUS_2)
    }

    /**
     * out = √a mod p, returns false if a is not a quadratic residue.
     *
     * Since p ≡ 3 (mod 4), the square root is simply a^((p+1)/4) mod p.
     * We verify the result by checking that out² = a (mod p).
     * Used to decompress public keys: given x, compute y from y² = x³ + 7.
     */
    fun sqrt(
        out: IntArray,
        a: IntArray,
    ): Boolean {
        powModP(out, a, P_PLUS_1_DIV_4)
        val check = IntArray(8)
        mul(check, out, out)
        val ar = IntArray(8)
        U256.copyInto(ar, a)
        reduceSelf(ar)
        return U256.cmp(check, ar) == 0
    }

    // ==================== Reduction ====================

    /** Conditional subtraction: if a >= p, set a = a - p. */
    fun reduceSelf(a: IntArray) {
        if (U256.cmp(a, P) >= 0) U256.subTo(a, a, P)
    }

    /**
     * Reduce a 512-bit value (from multiplication) to 256 bits mod p.
     *
     * Uses the special form of p: since p = 2^256 - (2^32 + 977), any value
     * above 2^256 can be "folded back" by multiplying the high part by (2^32 + 977)
     * and adding to the low part. We split this into two cheaper operations:
     *   hi × (2^32 + 977) = (hi << 32) + hi × 977
     * to avoid overflow, since hi × (2^32 + 977) could exceed 64 bits per limb.
     */
    fun reduceWide(
        out: IntArray,
        w: IntArray,
    ) {
        // First round: out = lo + hi*977 + (hi << 32)
        var carry = 0L
        for (i in 0 until 8) {
            carry += (w[i].toLong() and 0xFFFFFFFFL) // lo[i]
            carry += (w[i + 8].toLong() and 0xFFFFFFFFL) * 977L // hi[i] * 977
            if (i > 0) carry += (w[i + 7].toLong() and 0xFFFFFFFFL) // hi[i-1] (the <<32)
            out[i] = carry.toInt()
            carry = carry ushr 32
        }
        var overflow = carry + (w[15].toLong() and 0xFFFFFFFFL) // hi[7] from the <<32

        // Second round: fold overflow × (2^32 + 977) back in
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
            // Extremely rare third round (overflow from second round)
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
        reduceSelf(out) // Final conditional subtraction
    }

    // ==================== Internal exponentiation ====================

    /** Compute base^exp mod p using left-to-right binary exponentiation (square-and-multiply). */
    private fun powModP(
        out: IntArray,
        base: IntArray,
        exp: IntArray,
    ) {
        val b = IntArray(8)
        U256.copyInto(b, base)
        var highBit = 255
        while (highBit >= 0 && !U256.testBit(exp, highBit)) highBit--
        if (highBit < 0) {
            out[0] = 1
            for (i in 1 until 8) out[i] = 0
            return
        }
        U256.copyInto(out, b) // Start with base (MSB is always 1)
        for (i in highBit - 1 downTo 0) {
            sqr(out, out)
            if (U256.testBit(exp, i)) mul(out, out, b)
        }
    }

    // ==================== Constants ====================

    /** p - 2: exponent for Fermat inversion */
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

    /** (p + 1) / 4: exponent for square root when p ≡ 3 (mod 4) */
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

    // ==================== Convenience wrappers (allocating — for non-hot paths) ====================

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
 * Arithmetic modulo the secp256k1 group order: n = 0xFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364141.
 *
 * This is the "scalar field" — private keys, nonces, and challenge hashes are elements
 * of this field. Schnorr signing computes s = k + e·d (mod n), and scalar multiplication
 * computes k·G (mod n) where G is the generator point.
 *
 * Unlike FieldP, the group order n doesn't have a nice sparse form, so reduction from
 * 512 bits uses a different strategy: we exploit n ≈ 2^256, so 2^256 mod n is a small
 * ~129-bit constant. We multiply the high part by this constant and fold it back,
 * repeating until the result fits in 256 bits.
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

    /** 2^256 - n: the small constant used for reduction (≈129 bits) */
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

    /** n - 2: exponent for Fermat inversion */
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

    /** Check if 0 < a < n (valid non-zero scalar). */
    fun isValid(a: IntArray): Boolean = !U256.isZero(a) && U256.cmp(a, N) < 0

    /** If a >= n, return a - n. Otherwise return a unchanged. */
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
        if (carry != 0) U256.addTo(r, r, N_COMPLEMENT)
        reduceSelf(r)
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
    ): IntArray {
        val w = IntArray(16)
        U256.mulWide(w, a, b)
        return reduceWide(w)
    }

    fun neg(a: IntArray): IntArray {
        if (U256.isZero(a)) return IntArray(8)
        val r = IntArray(8)
        U256.subTo(r, N, a)
        return r
    }

    /** a^(-1) mod n via Fermat's little theorem. */
    fun inv(a: IntArray): IntArray {
        require(!U256.isZero(a))
        return powModN(a, N_MINUS_2)
    }

    private fun reduceSelf(a: IntArray) {
        if (U256.cmp(a, N) >= 0) U256.subTo(a, a, N)
    }

    /**
     * Reduce a 512-bit product mod n.
     *
     * Strategy: split w = lo + hi × 2^256, then use hi × 2^256 ≡ hi × N_COMPLEMENT (mod n).
     * Since N_COMPLEMENT is ~129 bits, hi × N_COMPLEMENT is ~385 bits. We repeat the
     * reduction until the result fits in 256 bits, then do a final conditional subtraction.
     */
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

        // Round 1: lo + hi × N_COMPLEMENT
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

        // Round 2 if still > 256 bits
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
