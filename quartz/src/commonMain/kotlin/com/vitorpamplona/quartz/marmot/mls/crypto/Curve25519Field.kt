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
package com.vitorpamplona.quartz.marmot.mls.crypto

/**
 * Field arithmetic over GF(2^255-19) for Curve25519 operations.
 *
 * Field elements are represented as LongArray(16) in radix-2^16.
 * Based on the TweetNaCl algorithm by Bernstein et al.
 */
internal object Curve25519Field {
    /** The constant a24 = 121665, used in the Montgomery ladder. */
    val A24 = gf(0xDB41L, 1)

    /** d2 = 2*d where d is the Edwards curve constant, for point addition. */
    val D2 =
        gf(
            0xF159,
            0x26B2,
            0x9B94,
            0xEBD6,
            0xB156,
            0x8283,
            0x149A,
            0x00E0,
            0xD130,
            0xEEF3,
            0x80F2,
            0x198E,
            0xFCE7,
            0x56DF,
            0xD9DC,
            0x2406,
        )

    /** Ed25519 base point X coordinate. */
    val BX =
        gf(
            0xD51A,
            0x8F25,
            0x2D60,
            0xC956,
            0xA7B2,
            0x9525,
            0xC760,
            0x692C,
            0xDC5C,
            0xFDD6,
            0xE231,
            0xC0A4,
            0x53FE,
            0xCD6E,
            0x36D3,
            0x2169,
        )

    /** Ed25519 base point Y coordinate. */
    val BY =
        gf(
            0x6658,
            0x6666,
            0x6666,
            0x6666,
            0x6666,
            0x6666,
            0x6666,
            0x6666,
            0x6666,
            0x6666,
            0x6666,
            0x6666,
            0x6666,
            0x6666,
            0x6666,
            0x6666,
        )

    /** sqrt(-1) mod p, used for Ed25519 point decompression. */
    val I =
        gf(
            0xA0B0,
            0x4A0E,
            0x1B27,
            0xC4EE,
            0xE478,
            0xAD2F,
            0x1806,
            0x2F43,
            0xD7A7,
            0x3DFB,
            0x0099,
            0x2B4D,
            0xDF0B,
            0x4FC1,
            0x2480,
            0x2B83,
        )

    fun gf(vararg values: Long): LongArray {
        val o = LongArray(16)
        for (i in values.indices) {
            o[i] = values[i]
        }
        return o
    }

    fun gf(
        a: Long,
        b: Long,
    ): LongArray {
        val o = LongArray(16)
        o[0] = a
        o[1] = b
        return o
    }

    val GF0 = LongArray(16)
    val GF1 = gf(1)

    /**
     * Carry and reduce a field element.
     *
     * TweetNaCl writes the loop over all 16 limbs and folds the wrap-around
     * into the body as `o[(i + 1) % 16]` plus an `if (i == 15)`, so a modulo
     * and a branch ride along on all 16 iterations to serve the one that needs
     * them. Peeling the last limb out takes both off the loop: limbs 0..14
     * carry into their neighbour, and limb 15 wraps into limb 0 scaled by 38,
     * which is the `c - 1` plus the `37 * (c - 1)` of the original folded into
     * one term.
     *
     * Measured honestly, this bought **nothing** on HotSpot — C2 was already
     * strength-reducing the modulo and hoisting the branch. It is kept because
     * it is strictly less work for a weaker JIT to undo, and ART on a phone is
     * the target that matters, but no speedup is claimed for it here: the
     * benchmark on this machine could not tell the two apart.
     *
     * That measurement is also the reason not to trust a CPU profile of this
     * file. JFR's execution sampler is safepoint-biased, and the counted loops
     * in this object carry no safepoint polls, so samples pile onto whichever
     * method follows the poll. It attributed 75% of all `create_group` samples
     * to this function; rewriting it changed nothing, which is the profiler
     * telling on itself. Time the primitives end to end instead — see
     * `marmotBench`'s `x25519_dh` and friends.
     *
     * The `+ (1 shl 16)` / `- 1` dance is TweetNaCl's, and stays: it biases the
     * limb so an arithmetic shift floors correctly for negative limbs, which is
     * what makes the carry branch-free for the sign as well.
     */
    fun car25519(o: LongArray) {
        for (i in 0 until 15) {
            o[i] += (1L shl 16)
            val c = o[i] shr 16
            o[i + 1] += c - 1
            o[i] -= c shl 16
        }
        o[15] += (1L shl 16)
        val c = o[15] shr 16
        o[0] += 38 * (c - 1)
        o[15] -= c shl 16
    }

    /** Conditional swap: if b=1, swap p and q element-wise. */
    fun sel25519(
        p: LongArray,
        q: LongArray,
        b: Long,
    ) {
        val c = b.inv() + 1 // 0 -> 0, 1 -> -1 (all ones)
        for (i in 0 until 16) {
            val t = c and (p[i] xor q[i])
            p[i] = p[i] xor t
            q[i] = q[i] xor t
        }
    }

    /** Pack a field element to 32-byte little-endian representation. */
    fun pack25519(n: LongArray): ByteArray {
        val o = ByteArray(32)
        val m = LongArray(16)
        val t = n.copyOf()
        car25519(t)
        car25519(t)
        car25519(t)
        for (j in 0 until 2) {
            m[0] = t[0] - 0xFFED
            for (i in 1 until 15) {
                m[i] = t[i] - 0xFFFF - ((m[i - 1] shr 16) and 1)
                m[i - 1] = m[i - 1] and 0xFFFF
            }
            m[15] = t[15] - 0x7FFF - ((m[14] shr 16) and 1)
            val b = (m[15] shr 16) and 1
            m[14] = m[14] and 0xFFFF
            sel25519(t, m, 1 - b)
        }
        for (i in 0 until 16) {
            o[2 * i] = (t[i] and 0xFF).toByte()
            o[2 * i + 1] = (t[i] shr 8).toByte()
        }
        return o
    }

    /** Unpack 32-byte little-endian to field element. */
    fun unpack25519(n: ByteArray): LongArray {
        val o = LongArray(16)
        for (i in 0 until 16) {
            o[i] = (n[2 * i].toLong() and 0xFF) + ((n[2 * i + 1].toLong() and 0xFF) shl 8)
        }
        o[15] = o[15] and 0x7FFF
        return o
    }

    // Allocating vs in-place.
    //
    // Each `add`/`sub`/`mul`/`sqr` below returns a NEW field element, which
    // reads well and is what the TweetNaCl reference does. Inside a scalar
    // multiplication it is also ~1.3 MB of garbage per call: a Montgomery
    // ladder runs 255 iterations of ten muls and eight add/subs, and every one
    // of them allocated. An allocation profile of the Marmot benchmarks put
    // 93% of ALL sampled allocation in these three functions.
    //
    // So each one has an `*Into` twin that writes into a caller-owned output.
    // The hot paths (X25519 and Ed25519 scalar multiplication) allocate
    // their working set once and then run allocation-free.
    //
    // Both forms stay: the allocating ones are used off the hot path, where
    // the clarity is worth more than the bytes, and keeping them means the
    // in-place versions can be differentially tested against them.
    //
    // Every `*Into` is safe when the output aliases an input — the ladder
    // relies on that.

    /** Field addition: o = a + b. */
    fun add(
        a: LongArray,
        b: LongArray,
    ): LongArray {
        val o = LongArray(16)
        addInto(o, a, b)
        return o
    }

    /** Field addition into [o]. Safe when [o] aliases [a] or [b]. */
    fun addInto(
        o: LongArray,
        a: LongArray,
        b: LongArray,
    ) {
        for (i in 0 until 16) o[i] = a[i] + b[i]
    }

    /** Field subtraction: o = a - b. */
    fun sub(
        a: LongArray,
        b: LongArray,
    ): LongArray {
        val o = LongArray(16)
        subInto(o, a, b)
        return o
    }

    /** Field subtraction into [o]. Safe when [o] aliases [a] or [b]. */
    fun subInto(
        o: LongArray,
        a: LongArray,
        b: LongArray,
    ) {
        for (i in 0 until 16) o[i] = a[i] - b[i]
    }

    /** Field multiplication: o = a * b (mod p). */
    fun mul(
        a: LongArray,
        b: LongArray,
    ): LongArray {
        val o = LongArray(16)
        mulInto(o, a, b, LongArray(31))
        return o
    }

    /**
     * Field multiplication into [o], using [t] as the 31-limb accumulator.
     *
     * [t] is caller-owned so a loop can reuse one across thousands of calls;
     * it is zeroed here, so callers never have to. Safe when [o] aliases [a]
     * or [b]: the product is fully accumulated in [t] before [o] is touched.
     */
    fun mulInto(
        o: LongArray,
        a: LongArray,
        b: LongArray,
        t: LongArray,
    ) {
        t.fill(0L)
        for (i in 0 until 16) {
            val ai = a[i]
            for (j in 0 until 16) {
                t[i + j] += ai * b[j]
            }
        }
        for (i in 0 until 15) {
            t[i] += 38 * t[i + 16]
        }
        for (i in 0 until 16) o[i] = t[i]
        car25519(o)
        car25519(o)
    }

    /** Field squaring: o = a^2 (mod p). */
    fun sqr(a: LongArray): LongArray {
        val o = LongArray(16)
        sqrInto(o, a, LongArray(31))
        return o
    }

    /**
     * Field squaring into [o]. See [mulInto] for the [t] contract.
     *
     * A square is not just `mulInto(o, a, a, t)`: in `a[i] * a[j]` every
     * off-diagonal pair is computed twice, once as (i,j) and once as (j,i).
     * Taking each pair once and doubling it turns the 256 multiplications of
     * the schoolbook into 136 — the 16 diagonal squares plus 120 cross terms.
     *
     * That is worth having because squarings are not a rare case: the
     * Montgomery ladder squares four times per bit out of ten field
     * multiplications, and [inv25519Into] is 254 squarings against ~250
     * multiplications.
     *
     * Doubling costs no headroom. Limbs reaching here are bounded well under
     * 2^18 even after an unreduced add or subtract, so a doubled cross term
     * stays under 2^37 and a full 16-term column under 2^41 — far from
     * overflowing the signed 64-bit accumulator.
     */
    fun sqrInto(
        o: LongArray,
        a: LongArray,
        t: LongArray,
    ) {
        t.fill(0L)
        for (i in 0 until 16) {
            val ai = a[i]
            t[i + i] += ai * ai
            val twice = ai + ai
            for (j in i + 1 until 16) {
                t[i + j] += twice * a[j]
            }
        }
        for (i in 0 until 15) {
            t[i] += 38 * t[i + 16]
        }
        for (i in 0 until 16) o[i] = t[i]
        car25519(o)
        car25519(o)
    }

    /** Field inversion: o = a^(-1) (mod p) using Fermat's little theorem. */
    fun inv25519(a: LongArray): LongArray {
        val o = LongArray(16)
        inv25519Into(o, a, LongArray(16), LongArray(31))
        return o
    }

    /**
     * Field inversion into [o], allocation-free.
     *
     * 254 squarings and ~250 multiplications, which is why this one matters:
     * on the allocating path it was the single largest contributor after the
     * ladder itself. [c] is a scratch field element and [t] the [mulInto]
     * accumulator; [o] may alias [a].
     */
    fun inv25519Into(
        o: LongArray,
        a: LongArray,
        c: LongArray,
        t: LongArray,
    ) {
        a.copyInto(c)
        for (i in 253 downTo 0) {
            sqrInto(c, c, t)
            if (i != 2 && i != 4) mulInto(c, c, a, t)
        }
        c.copyInto(o)
    }

    /** Parity of a field element (lowest bit after reduction). */
    fun par25519(a: LongArray): Int {
        val d = pack25519(a)
        return d[0].toInt() and 1
    }

    /** Raise a field element to the power (2^252 - 3), used in sqrt. */
    fun pow2523(a: LongArray): LongArray {
        val c = a.copyOf()
        val t = LongArray(31)
        for (i in 250 downTo 0) {
            sqrInto(c, c, t)
            if (i != 1) mulInto(c, c, a, t)
        }
        return c
    }
}
