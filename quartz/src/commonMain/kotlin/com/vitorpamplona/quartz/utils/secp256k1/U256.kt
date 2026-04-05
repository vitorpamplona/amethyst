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
     *
     * Note: Karatsuba (splitting into 4-limb halves for 48 products) was attempted
     * but the overhead of extra additions, carry propagation, and 5 temporary array
     * allocations per call negates the product-count savings at only 8 limbs.
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
    fun fromBytes(bytes: ByteArray): IntArray = fromBytes(bytes, 0)

    /** Decode 32 big-endian bytes starting at [offset] into little-endian IntArray(8). */
    fun fromBytes(
        bytes: ByteArray,
        offset: Int,
    ): IntArray {
        val r = IntArray(8)
        for (i in 0 until 8) {
            val o = offset + 28 - i * 4
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
