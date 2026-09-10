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
 * Field arithmetic over GF(2^255-19) for Curve25519 and Ed25519.
 *
 * ## Representation: 10 limbs, radix 2^25.5
 *
 * A field element is a `LongArray(10)`. Limb `i` carries weight `2^OFFSET[i]`
 * where the offsets step alternately by 26 and 25 bits — even limbs hold 26
 * bits, odd limbs 25 — so ten limbs span the 255 bits of the field. This is
 * the layout ref10, curve25519-donna and SunEC all use.
 *
 * It replaces TweetNaCl's 16 limbs of radix 2^16, and the reason is arithmetic
 * rather than taste. A schoolbook multiply costs one product per pair of
 * limbs: 16 limbs means 256 products, 10 limbs means 100. Measured on the
 * benchmark machine, that is the difference between a 541us scalar
 * multiplication and SunEC's 160us — and SunEC is itself pure Java on the same
 * JIT, which is what rules out "the JVM is slow" as the explanation.
 *
 * Limbs are SIGNED. Subtraction does not borrow and multiplication does not
 * normalise beyond a carry chain, so intermediate limbs are allowed to go
 * negative and to exceed their nominal width; only [pack25519] produces a
 * canonical value. The bound that matters is that a limb entering [mulInto]
 * stays under about 2^26 in absolute value, which leaves the widest
 * accumulator column at 2^60 — three bits clear of overflowing a signed 64-bit
 * Long. Every operation here preserves that.
 *
 * ## Allocating vs in-place
 *
 * Each `add`/`sub`/`mul`/`sqr` has an `*Into` twin that writes into a
 * caller-owned output, because the allocating forms turned a scalar
 * multiplication into about a megabyte of garbage. The hot paths (the X25519
 * ladder and Ed25519 point addition) use the in-place forms exclusively and
 * allocate nothing; the allocating forms remain for off-hot-path clarity and
 * as a differential-testing partner for the in-place ones.
 *
 * Unlike the 16-limb version these need no scratch accumulator: [mulInto] and
 * [sqrInto] are straight-line over local Longs, so there is no array to pass
 * in and none to zero.
 *
 * Every `*Into` is safe when the output aliases an input — the ladder relies
 * on that, and it holds because each reads every input into locals before
 * writing any output.
 */
internal object Curve25519Field {
    /** Bit offset of each limb: even limbs are 26 bits wide, odd limbs 25. */
    private val OFFSET = intArrayOf(0, 26, 51, 77, 102, 128, 153, 179, 204, 230)

    /** Number of limbs in a field element. */
    const val LIMBS = 10

    /**
     * Build a field element from its limbs, zero-filling the rest.
     *
     * Values are limbs in THIS representation, not bytes — see [unpack25519]
     * to go from a 32-byte encoding.
     */
    fun gf(vararg values: Long): LongArray {
        val o = LongArray(LIMBS)
        for (i in values.indices) o[i] = values[i]
        return o
    }

    val GF0 = LongArray(LIMBS)
    val GF1 = gf(1)

    /** a24 = 121665, the Montgomery ladder constant. */
    val A24 = gf(121665)

    /** d = -121665/121666, the Edwards curve constant. */
    val D = gf(56195235, 13857412, 51736253, 6949390, 114729, 24766616, 60832955, 30306712, 48412415, 21499315)

    /** d2 = 2*d, for extended-coordinate point addition. */
    val D2 = gf(45281625, 27714825, 36363642, 13898781, 229458, 15978800, 54557047, 27058993, 29715967, 9444199)

    /** Ed25519 base point X coordinate. */
    val BX = gf(52811034, 25909283, 16144682, 17082669, 27570973, 30858332, 40966398, 8378388, 20764389, 8758491)

    /** Ed25519 base point Y coordinate, which is 4/5. */
    val BY = gf(40265304, 26843545, 13421772, 20132659, 26843545, 6710886, 53687091, 13421772, 40265318, 26843545)

    /** sqrt(-1) mod p, used for Ed25519 point decompression. */
    val I = gf(34513072, 25610706, 9377949, 3500415, 12389472, 33281959, 41962654, 31548777, 326685, 11406482)

    /** Field addition: o = a + b. */
    fun add(
        a: LongArray,
        b: LongArray,
    ): LongArray {
        val o = LongArray(LIMBS)
        addInto(o, a, b)
        return o
    }

    /** Field addition into [o]. Safe when [o] aliases [a] or [b]. */
    fun addInto(
        o: LongArray,
        a: LongArray,
        b: LongArray,
    ) {
        for (i in 0 until LIMBS) o[i] = a[i] + b[i]
    }

    /** Field subtraction: o = a - b. */
    fun sub(
        a: LongArray,
        b: LongArray,
    ): LongArray {
        val o = LongArray(LIMBS)
        subInto(o, a, b)
        return o
    }

    /** Field subtraction into [o]. Safe when [o] aliases [a] or [b]. */
    fun subInto(
        o: LongArray,
        a: LongArray,
        b: LongArray,
    ) {
        for (i in 0 until LIMBS) o[i] = a[i] - b[i]
    }

    /** Field multiplication: o = a * b (mod p). */
    fun mul(
        a: LongArray,
        b: LongArray,
    ): LongArray {
        val o = LongArray(LIMBS)
        mulInto(o, a, b)
        return o
    }

    /**
     * Field multiplication into [o]: o = f * g (mod p).
     *
     * Straight-line over locals, so it allocates nothing and [o] may alias
     * either input. Generated from the weight bookkeeping of the
     * representation and cross-checked against an independent reference: a
     * product of limbs i and j lands in limb i+j, doubled when i and j are
     * both odd (their offsets sum to one more than the target's), and folded
     * back into limb i+j-10 scaled by 19 when it overflows the top, since
     * 2^255 == 19 (mod p).
     *
     * 100 products, against 256 for the 16-limb representation this replaced.
     */
    fun mulInto(
        o: LongArray,
        f: LongArray,
        g: LongArray,
    ) {
        val f0 = f[0]
        val f1 = f[1]
        val f2 = f[2]
        val f3 = f[3]
        val f4 = f[4]
        val f5 = f[5]
        val f6 = f[6]
        val f7 = f[7]
        val f8 = f[8]
        val f9 = f[9]
        val g0 = g[0]
        val g1 = g[1]
        val g2 = g[2]
        val g3 = g[3]
        val g4 = g[4]
        val g5 = g[5]
        val g6 = g[6]
        val g7 = g[7]
        val g8 = g[8]
        val g9 = g[9]
        val f1x2 = f1 + f1
        val f3x2 = f3 + f3
        val f5x2 = f5 + f5
        val f7x2 = f7 + f7
        val f9x2 = f9 + f9
        val g1x19 = 19 * g1
        val g2x19 = 19 * g2
        val g3x19 = 19 * g3
        val g4x19 = 19 * g4
        val g5x19 = 19 * g5
        val g6x19 = 19 * g6
        val g7x19 = 19 * g7
        val g8x19 = 19 * g8
        val g9x19 = 19 * g9
        var h0 = f0 * g0 + f1x2 * g9x19 + f2 * g8x19 + f3x2 * g7x19 + f4 * g6x19 + f5x2 * g5x19 + f6 * g4x19 + f7x2 * g3x19 + f8 * g2x19 + f9x2 * g1x19
        var h1 = f0 * g1 + f1 * g0 + f2 * g9x19 + f3 * g8x19 + f4 * g7x19 + f5 * g6x19 + f6 * g5x19 + f7 * g4x19 + f8 * g3x19 + f9 * g2x19
        var h2 = f0 * g2 + f1x2 * g1 + f2 * g0 + f3x2 * g9x19 + f4 * g8x19 + f5x2 * g7x19 + f6 * g6x19 + f7x2 * g5x19 + f8 * g4x19 + f9x2 * g3x19
        var h3 = f0 * g3 + f1 * g2 + f2 * g1 + f3 * g0 + f4 * g9x19 + f5 * g8x19 + f6 * g7x19 + f7 * g6x19 + f8 * g5x19 + f9 * g4x19
        var h4 = f0 * g4 + f1x2 * g3 + f2 * g2 + f3x2 * g1 + f4 * g0 + f5x2 * g9x19 + f6 * g8x19 + f7x2 * g7x19 + f8 * g6x19 + f9x2 * g5x19
        var h5 = f0 * g5 + f1 * g4 + f2 * g3 + f3 * g2 + f4 * g1 + f5 * g0 + f6 * g9x19 + f7 * g8x19 + f8 * g7x19 + f9 * g6x19
        var h6 = f0 * g6 + f1x2 * g5 + f2 * g4 + f3x2 * g3 + f4 * g2 + f5x2 * g1 + f6 * g0 + f7x2 * g9x19 + f8 * g8x19 + f9x2 * g7x19
        var h7 = f0 * g7 + f1 * g6 + f2 * g5 + f3 * g4 + f4 * g3 + f5 * g2 + f6 * g1 + f7 * g0 + f8 * g9x19 + f9 * g8x19
        var h8 = f0 * g8 + f1x2 * g7 + f2 * g6 + f3x2 * g5 + f4 * g4 + f5x2 * g3 + f6 * g2 + f7x2 * g1 + f8 * g0 + f9x2 * g9x19
        var h9 = f0 * g9 + f1 * g8 + f2 * g7 + f3 * g6 + f4 * g5 + f5 * g4 + f6 * g3 + f7 * g2 + f8 * g1 + f9 * g0
        // ref10's carry ordering: independent carries are interleaved so the
        // limb-to-limb dependency chain does not stall the pipeline.
        var c = (h0 + (1L shl 25)) shr 26
        h1 += c
        h0 -= c shl 26
        c = (h4 + (1L shl 25)) shr 26
        h5 += c
        h4 -= c shl 26
        c = (h1 + (1L shl 24)) shr 25
        h2 += c
        h1 -= c shl 25
        c = (h5 + (1L shl 24)) shr 25
        h6 += c
        h5 -= c shl 25
        c = (h2 + (1L shl 25)) shr 26
        h3 += c
        h2 -= c shl 26
        c = (h6 + (1L shl 25)) shr 26
        h7 += c
        h6 -= c shl 26
        c = (h3 + (1L shl 24)) shr 25
        h4 += c
        h3 -= c shl 25
        c = (h7 + (1L shl 24)) shr 25
        h8 += c
        h7 -= c shl 25
        c = (h4 + (1L shl 25)) shr 26
        h5 += c
        h4 -= c shl 26
        c = (h8 + (1L shl 25)) shr 26
        h9 += c
        h8 -= c shl 26
        c = (h9 + (1L shl 24)) shr 25
        h0 += 19 * c
        h9 -= c shl 25
        c = (h0 + (1L shl 25)) shr 26
        h1 += c
        h0 -= c shl 26
        o[0] = h0
        o[1] = h1
        o[2] = h2
        o[3] = h3
        o[4] = h4
        o[5] = h5
        o[6] = h6
        o[7] = h7
        o[8] = h8
        o[9] = h9
    }

    /** Field squaring: o = a^2 (mod p). */
    fun sqr(a: LongArray): LongArray {
        val o = LongArray(LIMBS)
        sqrInto(o, a)
        return o
    }

    /**
     * Field squaring into [o]: o = f^2 (mod p).
     *
     * Same bookkeeping as [mulInto], with each off-diagonal pair taken once
     * and doubled instead of computed twice: 55 products against the multiply's
     * 100. Squarings are not a rare case — the ladder squares four times per
     * bit, and [inv25519Into] is 254 squarings.
     */
    fun sqrInto(
        o: LongArray,
        f: LongArray,
    ) {
        val f0 = f[0]
        val f1 = f[1]
        val f2 = f[2]
        val f3 = f[3]
        val f4 = f[4]
        val f5 = f[5]
        val f6 = f[6]
        val f7 = f[7]
        val f8 = f[8]
        val f9 = f[9]
        val f0x2 = f0 + f0
        val f1x2 = f1 + f1
        val f2x2 = f2 + f2
        val f3x2 = f3 + f3
        val f4x2 = f4 + f4
        val f5x2 = f5 + f5
        val f6x2 = f6 + f6
        val f7x2 = f7 + f7
        val f8x2 = f8 + f8
        val f9x2 = f9 + f9
        val f1x4 = 4 * f1
        val f3x4 = 4 * f3
        val f5x4 = 4 * f5
        val f7x4 = 4 * f7
        val f1x19 = 19 * f1
        val f2x19 = 19 * f2
        val f3x19 = 19 * f3
        val f4x19 = 19 * f4
        val f5x19 = 19 * f5
        val f6x19 = 19 * f6
        val f7x19 = 19 * f7
        val f8x19 = 19 * f8
        val f9x19 = 19 * f9
        var h0 = f0 * f0 + f1x4 * f9x19 + f2x2 * f8x19 + f3x4 * f7x19 + f4x2 * f6x19 + f5x2 * f5x19
        var h1 = f0x2 * f1 + f2x2 * f9x19 + f3x2 * f8x19 + f4x2 * f7x19 + f5x2 * f6x19
        var h2 = f0x2 * f2 + f1x2 * f1 + f3x4 * f9x19 + f4x2 * f8x19 + f5x4 * f7x19 + f6 * f6x19
        var h3 = f0x2 * f3 + f1x2 * f2 + f4x2 * f9x19 + f5x2 * f8x19 + f6x2 * f7x19
        var h4 = f0x2 * f4 + f1x4 * f3 + f2 * f2 + f5x4 * f9x19 + f6x2 * f8x19 + f7x2 * f7x19
        var h5 = f0x2 * f5 + f1x2 * f4 + f2x2 * f3 + f6x2 * f9x19 + f7x2 * f8x19
        var h6 = f0x2 * f6 + f1x4 * f5 + f2x2 * f4 + f3x2 * f3 + f7x4 * f9x19 + f8 * f8x19
        var h7 = f0x2 * f7 + f1x2 * f6 + f2x2 * f5 + f3x2 * f4 + f8x2 * f9x19
        var h8 = f0x2 * f8 + f1x4 * f7 + f2x2 * f6 + f3x4 * f5 + f4 * f4 + f9x2 * f9x19
        var h9 = f0x2 * f9 + f1x2 * f8 + f2x2 * f7 + f3x2 * f6 + f4x2 * f5
        // ref10's carry ordering: independent carries are interleaved so the
        // limb-to-limb dependency chain does not stall the pipeline.
        var c = (h0 + (1L shl 25)) shr 26
        h1 += c
        h0 -= c shl 26
        c = (h4 + (1L shl 25)) shr 26
        h5 += c
        h4 -= c shl 26
        c = (h1 + (1L shl 24)) shr 25
        h2 += c
        h1 -= c shl 25
        c = (h5 + (1L shl 24)) shr 25
        h6 += c
        h5 -= c shl 25
        c = (h2 + (1L shl 25)) shr 26
        h3 += c
        h2 -= c shl 26
        c = (h6 + (1L shl 25)) shr 26
        h7 += c
        h6 -= c shl 26
        c = (h3 + (1L shl 24)) shr 25
        h4 += c
        h3 -= c shl 25
        c = (h7 + (1L shl 24)) shr 25
        h8 += c
        h7 -= c shl 25
        c = (h4 + (1L shl 25)) shr 26
        h5 += c
        h4 -= c shl 26
        c = (h8 + (1L shl 25)) shr 26
        h9 += c
        h8 -= c shl 26
        c = (h9 + (1L shl 24)) shr 25
        h0 += 19 * c
        h9 -= c shl 25
        c = (h0 + (1L shl 25)) shr 26
        h1 += c
        h0 -= c shl 26
        o[0] = h0
        o[1] = h1
        o[2] = h2
        o[3] = h3
        o[4] = h4
        o[5] = h5
        o[6] = h6
        o[7] = h7
        o[8] = h8
        o[9] = h9
    }

    /**
     * Multiply by the ladder constant a24 = 121665.
     *
     * One limb-wise scalar multiply plus a carry, instead of the 100 products
     * a general [mulInto] would spend against a constant that is zero in nine
     * of its ten limbs. The ladder does this once per bit.
     *
     * 121665 < 2^17 and limbs stay under 2^26, so each product stays under
     * 2^43 — nowhere near overflowing.
     */
    fun mulA24Into(
        o: LongArray,
        f: LongArray,
    ) {
        var h0 = f[0] * 121665
        var h1 = f[1] * 121665
        var h2 = f[2] * 121665
        var h3 = f[3] * 121665
        var h4 = f[4] * 121665
        var h5 = f[5] * 121665
        var h6 = f[6] * 121665
        var h7 = f[7] * 121665
        var h8 = f[8] * 121665
        var h9 = f[9] * 121665
        // ref10's carry ordering: independent carries are interleaved so the
        // limb-to-limb dependency chain does not stall the pipeline.
        var c = (h0 + (1L shl 25)) shr 26
        h1 += c
        h0 -= c shl 26
        c = (h4 + (1L shl 25)) shr 26
        h5 += c
        h4 -= c shl 26
        c = (h1 + (1L shl 24)) shr 25
        h2 += c
        h1 -= c shl 25
        c = (h5 + (1L shl 24)) shr 25
        h6 += c
        h5 -= c shl 25
        c = (h2 + (1L shl 25)) shr 26
        h3 += c
        h2 -= c shl 26
        c = (h6 + (1L shl 25)) shr 26
        h7 += c
        h6 -= c shl 26
        c = (h3 + (1L shl 24)) shr 25
        h4 += c
        h3 -= c shl 25
        c = (h7 + (1L shl 24)) shr 25
        h8 += c
        h7 -= c shl 25
        c = (h4 + (1L shl 25)) shr 26
        h5 += c
        h4 -= c shl 26
        c = (h8 + (1L shl 25)) shr 26
        h9 += c
        h8 -= c shl 26
        c = (h9 + (1L shl 24)) shr 25
        h0 += 19 * c
        h9 -= c shl 25
        c = (h0 + (1L shl 25)) shr 26
        h1 += c
        h0 -= c shl 26
        o[0] = h0
        o[1] = h1
        o[2] = h2
        o[3] = h3
        o[4] = h4
        o[5] = h5
        o[6] = h6
        o[7] = h7
        o[8] = h8
        o[9] = h9
    }

    /**
     * Carry and partially reduce a field element in place.
     *
     * Brings limbs back inside their nominal widths so a value that has been
     * added or subtracted repeatedly is safe to feed to [mulInto] again. It
     * does NOT produce a canonical representative — [pack25519] does that.
     */
    fun car25519(o: LongArray) {
        var c: Long
        for (i in 0 until LIMBS) {
            val width = if (i and 1 == 0) 26 else 25
            c = (o[i] + (1L shl (width - 1))) shr width
            if (i == 9) o[0] += 19 * c else o[i + 1] += c
            o[i] -= c shl width
        }
        c = (o[0] + (1L shl 25)) shr 26
        o[1] += c
        o[0] -= c shl 26
    }

    /** Conditional swap: if b=1, swap p and q element-wise. */
    fun sel25519(
        p: LongArray,
        q: LongArray,
        b: Long,
    ) {
        val c = b.inv() + 1 // 0 -> 0, 1 -> -1 (all ones)
        for (i in 0 until LIMBS) {
            val t = c and (p[i] xor q[i])
            p[i] = p[i] xor t
            q[i] = q[i] xor t
        }
    }

    /**
     * Encode a field element as 32 little-endian bytes, fully reduced.
     *
     * This is the only place a canonical representative is produced. The
     * leading pass computes the carry that WOULD come out of the top limb if
     * the value were >= p, and folds 19 times it back into limb 0; that turns
     * any representative of the class — including a non-canonical input such
     * as p itself — into the unique one below p. The second pass then carries
     * without wrapping, so the top carry falls off and the remaining limbs are
     * exactly the base-2^25.5 digits of the answer.
     */
    fun pack25519(n: LongArray): ByteArray {
        val h = n.copyOf()
        var q = (19 * h[9] + (1L shl 24)) shr 25
        for (i in 0 until LIMBS) {
            q = (h[i] + q) shr (if (i and 1 == 0) 26 else 25)
        }
        h[0] += 19 * q
        var c = 0L
        for (i in 0 until LIMBS) {
            val width = if (i and 1 == 0) 26 else 25
            h[i] += c
            c = h[i] shr width
            h[i] -= c shl width
        }
        val o = ByteArray(32)
        for (i in 0 until LIMBS) {
            var v = h[i]
            var bit = OFFSET[i]
            while (v != 0L) {
                val idx = bit shr 3
                val shift = bit and 7
                val room = 8 - shift
                o[idx] = (o[idx].toLong() or ((v and ((1L shl room) - 1)) shl shift)).toByte()
                v = v ushr room
                bit += room
            }
        }
        return o
    }

    /**
     * Decode 32 little-endian bytes into a field element.
     *
     * Bit 255 is ignored, per RFC 7748: the top bit of the last byte is not
     * part of the value. Limb 9 is 25 bits wide and ends at bit 254, so the
     * mask drops it without a separate step.
     */
    fun unpack25519(n: ByteArray): LongArray {
        val o = LongArray(LIMBS)
        for (i in 0 until LIMBS) {
            val width = if (i and 1 == 0) 26 else 25
            val bit = OFFSET[i]
            val byteStart = bit shr 3
            val shift = bit and 7
            var chunk = 0L
            for (k in 0 until 5) {
                val idx = byteStart + k
                if (idx < 32) chunk = chunk or ((n[idx].toLong() and 0xFF) shl (8 * k))
            }
            o[i] = (chunk ushr shift) and ((1L shl width) - 1)
        }
        return o
    }

    /** Field inversion: o = a^(-1) (mod p) using Fermat's little theorem. */
    fun inv25519(a: LongArray): LongArray {
        val o = LongArray(LIMBS)
        inv25519Into(o, a, LongArray(LIMBS))
        return o
    }

    /**
     * Field inversion into [o], allocation-free.
     *
     * a^(p-2) by square-and-multiply over the fixed exponent: 254 squarings
     * and ~250 multiplications. [c] is a caller-owned scratch element; [o] may
     * alias [a].
     */
    fun inv25519Into(
        o: LongArray,
        a: LongArray,
        c: LongArray,
    ) {
        a.copyInto(c)
        for (i in 253 downTo 0) {
            sqrInto(c, c)
            if (i != 2 && i != 4) mulInto(c, c, a)
        }
        c.copyInto(o)
    }

    /** Parity of a field element (lowest bit after reduction). */
    fun par25519(a: LongArray): Int {
        val d = pack25519(a)
        return (d[0].toInt() and 1)
    }

    /** Raise a field element to the power (2^252 - 3), used in sqrt. */
    fun pow2523(a: LongArray): LongArray {
        val c = a.copyOf()
        for (i in 250 downTo 0) {
            sqrInto(c, c)
            if (i != 1) mulInto(c, c, a)
        }
        return c
    }
}
