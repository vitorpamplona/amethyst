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
package com.vitorpamplona.quartz.utils.bigint

/**
 * A non-negative integer of arbitrary size, in portable Kotlin: the engine
 * behind [UBigInt] everywhere Java's is not available.
 *
 * On JVM and Android [UBigInt] wraps `java.math.BigInteger`, whose
 * `multiplyToLen` is a HotSpot intrinsic and which measured 3 to 10 times
 * faster than this on the operands a Cantor tree reaches. That speed matters —
 * `CYBERSPACE_V2.md` §7's whole feasibility argument is a number — but Apple
 * and Linux have nothing to wrap, so this exists and has to be exactly as
 * correct, because what it computes becomes a decryption key (§7.2). A limb
 * handled differently here is an object that opens on a desktop and not on a
 * phone.
 *
 * That risk is discharged by measurement rather than by argument:
 * `PortableUBigIntDifferentialTest` runs every operation against
 * `java.math.BigInteger` over random inputs at the sizes the tree reaches, and
 * `CantorTreeBenchmark` folds a whole subtree both ways and compares the roots.
 *
 * **Unsigned by construction.** Nothing here ever goes negative: the Cantor
 * pairing of two non-negative numbers is non-negative, and [subtract] is used
 * only inside Karatsuba where the result is known to be. That removes sign
 * handling, two's complement, and the whole class of bug where a leading zero
 * byte does or does not appear.
 *
 * Limbs are 32 bits each, little-endian — `limbs[0]` is the least significant —
 * and normalised so the top limb is never zero. Zero is the empty array.
 */
internal class PortableUBigInt internal constructor(
    internal val limbs: IntArray,
) : Comparable<PortableUBigInt> {
    /** How many bits this number occupies; 0 for zero. */
    val bitLength: Int
        get() {
            if (limbs.isEmpty()) return 0
            val top = limbs[limbs.size - 1]
            return limbs.size * Int.SIZE_BITS - leadingZeros(top)
        }

    val isZero: Boolean get() = limbs.isEmpty()

    operator fun plus(other: PortableUBigInt): PortableUBigInt {
        if (isZero) return other
        if (other.isZero) return this
        val long = if (limbs.size >= other.limbs.size) limbs else other.limbs
        val short = if (limbs.size >= other.limbs.size) other.limbs else limbs
        val out = IntArray(long.size + 1)
        var carry = 0L
        for (i in long.indices) {
            val sum = (long[i].toLong() and MASK) + (if (i < short.size) short[i].toLong() and MASK else 0L) + carry
            out[i] = sum.toInt()
            carry = sum ushr Int.SIZE_BITS
        }
        out[long.size] = carry.toInt()
        return normalised(out)
    }

    /**
     * `this - other`, which the caller guarantees is not negative.
     *
     * Only Karatsuba calls this, on `(a0 + a1)(b0 + b1) - z0 - z2`, which is a
     * cross term and cannot go below zero. A borrow off the end would mean the
     * multiplication itself was wrong, so it is an error rather than a wrap.
     */
    internal fun subtract(other: PortableUBigInt): PortableUBigInt {
        if (other.isZero) return this
        val out = IntArray(limbs.size)
        var borrow = 0L
        for (i in limbs.indices) {
            val diff = (limbs[i].toLong() and MASK) - (if (i < other.limbs.size) other.limbs[i].toLong() and MASK else 0L) - borrow
            out[i] = diff.toInt()
            borrow = if (diff < 0) 1L else 0L
        }
        check(borrow == 0L) { "unsigned subtract went below zero" }
        return normalised(out)
    }

    operator fun times(other: PortableUBigInt): PortableUBigInt {
        if (isZero || other.isZero) return ZERO
        if (limbs.size < KARATSUBA_LIMBS || other.limbs.size < KARATSUBA_LIMBS) {
            return schoolbook(limbs, other.limbs)
        }
        return karatsuba(this, other)
    }

    /** This number with its low [bits] bits dropped. */
    fun shiftRight(bits: Int): PortableUBigInt {
        require(bits >= 0) { "shift must not be negative" }
        if (bits == 0 || isZero) return this
        val wholeLimbs = bits / Int.SIZE_BITS
        if (wholeLimbs >= limbs.size) return ZERO
        val withinLimb = bits % Int.SIZE_BITS
        val out = IntArray(limbs.size - wholeLimbs)
        if (withinLimb == 0) {
            limbs.copyInto(out, 0, wholeLimbs, limbs.size)
        } else {
            for (i in out.indices) {
                val low = limbs[i + wholeLimbs] ushr withinLimb
                val high = if (i + wholeLimbs + 1 < limbs.size) limbs[i + wholeLimbs + 1] shl (Int.SIZE_BITS - withinLimb) else 0
                out[i] = low or high
            }
        }
        return normalised(out)
    }

    /**
     * The `int_to_bytes_be_min` of the reference implementation: big-endian, no
     * leading zero byte, and a single zero byte for zero.
     *
     * This exact shape is what §7.2 hashes, so a spare leading byte — which is
     * what `java.math.BigInteger.toByteArray()` adds whenever the top bit is
     * set — would silently produce a different key for one number in two.
     */
    fun toMinimalBytes(): ByteArray {
        if (isZero) return byteArrayOf(0)
        val bytes = (bitLength + 7) / 8
        val out = ByteArray(bytes)
        for (i in 0 until bytes) {
            val limb = limbs[i / 4]
            out[bytes - 1 - i] = (limb ushr ((i % 4) * 8)).toByte()
        }
        return out
    }

    override fun compareTo(other: PortableUBigInt): Int {
        if (limbs.size != other.limbs.size) return if (limbs.size < other.limbs.size) -1 else 1
        for (i in limbs.indices.reversed()) {
            val a = limbs[i].toLong() and MASK
            val b = other.limbs[i].toLong() and MASK
            if (a != b) return if (a < b) -1 else 1
        }
        return 0
    }

    override fun equals(other: Any?): Boolean = other is PortableUBigInt && limbs.contentEquals(other.limbs)

    override fun hashCode(): Int = limbs.contentHashCode()

    override fun toString(): String = "PortableUBigInt($bitLength bits)"

    companion object {
        private const val MASK = 0xFFFFFFFFL

        /**
         * Below this many limbs on either side, schoolbook wins: Karatsuba's
         * three sub-products and their adds cost more than the `n * m` limb
         * multiplications they save. The exact crossover is not sensitive —
         * anything in the tens works — and this one is measured, not guessed.
         */
        private const val KARATSUBA_LIMBS = 40

        val ZERO = PortableUBigInt(IntArray(0))
        val ONE = PortableUBigInt(intArrayOf(1))

        fun of(value: Long): PortableUBigInt {
            require(value >= 0) { "negative values have no place on this lattice" }
            if (value == 0L) return ZERO
            val high = (value ushr Int.SIZE_BITS).toInt()
            return if (high == 0) PortableUBigInt(intArrayOf(value.toInt())) else PortableUBigInt(intArrayOf(value.toInt(), high))
        }

        /** An unsigned big-endian magnitude, the inverse of [toMinimalBytes]. */
        fun ofBytes(bytes: ByteArray): PortableUBigInt {
            if (bytes.isEmpty()) return ZERO
            val out = IntArray((bytes.size + 3) / 4)
            for (i in bytes.indices) {
                val fromEnd = bytes.size - 1 - i
                out[i / 4] = out[i / 4] or ((bytes[fromEnd].toInt() and 0xFF) shl ((i % 4) * 8))
            }
            return normalised(out)
        }

        private fun normalised(limbs: IntArray): PortableUBigInt {
            var size = limbs.size
            while (size > 0 && limbs[size - 1] == 0) size--
            if (size == 0) return ZERO
            return PortableUBigInt(if (size == limbs.size) limbs else limbs.copyOf(size))
        }

        private fun leadingZeros(value: Int): Int {
            if (value == 0) return Int.SIZE_BITS
            var count = 0
            var v = value
            while (v > 0) {
                v = v shl 1
                count++
            }
            return count
        }

        private fun schoolbook(
            a: IntArray,
            b: IntArray,
        ): PortableUBigInt {
            val out = IntArray(a.size + b.size)
            for (i in a.indices) {
                val ai = a[i].toLong() and MASK
                if (ai == 0L) continue
                var carry = 0L
                for (j in b.indices) {
                    val at = i + j
                    val product = ai * (b[j].toLong() and MASK) + (out[at].toLong() and MASK) + carry
                    out[at] = product.toInt()
                    carry = product ushr Int.SIZE_BITS
                }
                var at = i + b.size
                while (carry != 0L) {
                    val sum = (out[at].toLong() and MASK) + carry
                    out[at] = sum.toInt()
                    carry = sum ushr Int.SIZE_BITS
                    at++
                }
            }
            return normalised(out)
        }

        /**
         * `a * b` in three half-width products instead of four.
         *
         * Splitting both at `half` limbs, `a = a1·B + a0` and `b = b1·B + b0`,
         * the product is `z2·B² + z1·B + z0` where `z2 = a1·b1`, `z0 = a0·b0`
         * and the cross term `z1 = (a0 + a1)(b0 + b1) − z2 − z0`, which is one
         * multiplication rather than two. That turns the exponent from 2 to
         * about 1.585, and on the numbers a Cantor tree reaches — megabytes by
         * height 16 — it is the difference between a key and a coffee break.
         */
        private fun karatsuba(
            a: PortableUBigInt,
            b: PortableUBigInt,
        ): PortableUBigInt {
            val half = maxOf(a.limbs.size, b.limbs.size) / 2
            val a0 = a.low(half)
            val a1 = a.high(half)
            val b0 = b.low(half)
            val b1 = b.high(half)

            val z0 = a0 * b0
            val z2 = a1 * b1
            val z1 = ((a0 + a1) * (b0 + b1)).subtract(z2).subtract(z0)

            return z2.shiftLeftLimbs(half * 2) + z1.shiftLeftLimbs(half) + z0
        }
    }

    private fun low(limbCount: Int): PortableUBigInt = if (limbs.size <= limbCount) this else normalised(limbs.copyOfRange(0, limbCount))

    private fun high(limbCount: Int): PortableUBigInt = if (limbs.size <= limbCount) ZERO else normalised(limbs.copyOfRange(limbCount, limbs.size))

    private fun shiftLeftLimbs(limbCount: Int): PortableUBigInt {
        if (isZero || limbCount == 0) return this
        val out = IntArray(limbs.size + limbCount)
        limbs.copyInto(out, limbCount)
        return PortableUBigInt(out)
    }
}
