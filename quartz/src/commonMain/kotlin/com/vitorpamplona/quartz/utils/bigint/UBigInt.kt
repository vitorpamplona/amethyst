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
 * A non-negative integer of arbitrary size, with only the operations Cantor
 * pairing needs (`CYBERSPACE_V2.md` §4.6).
 *
 * Two implementations, for one reason: speed on the platforms people use, and
 * an implementation at all on the ones they might. JVM and Android wrap
 * `java.math.BigInteger`, whose `multiplyToLen` is a HotSpot intrinsic and
 * which measured 3 to 10 times faster than portable Kotlin on the operands a
 * Cantor tree reaches — and §7's feasibility is a number, so that factor is the
 * difference between a search a reader will wait for and one they will not.
 * Apple and Linux have nothing to wrap and get [PortableUBigInt].
 *
 * Two implementations of a **consensus value** would normally be a bad trade:
 * §7.2 turns this into a decryption key, so a carry handled differently on one
 * platform is an object that opens on a desktop and not on a phone. What makes
 * it safe is that the disagreement is testable, and tested — every operation
 * against `java.math.BigInteger` on random inputs at the sizes that matter, and
 * a whole subtree folded both ways with the roots compared.
 *
 * Unsigned throughout: nothing on this lattice is negative, so there is no sign
 * to carry and no two's complement to get wrong.
 */
expect class UBigInt : Comparable<UBigInt> {
    /** How many bits this number occupies; 0 for zero. */
    val bitLength: Int

    val isZero: Boolean

    operator fun plus(other: UBigInt): UBigInt

    operator fun times(other: UBigInt): UBigInt

    /** This number with its low [bits] bits dropped. */
    fun shiftRight(bits: Int): UBigInt

    /**
     * The reference's `int_to_bytes_be_min`: big-endian, no leading zero byte,
     * and a single zero byte for zero.
     *
     * This exact shape is what §7.2 hashes. `java.math.BigInteger.toByteArray()`
     * is two's complement and prepends `0x00` whenever the top bit is set —
     * half of all numbers — so the JVM actual strips it, and a test pins that
     * it did.
     */
    fun toMinimalBytes(): ByteArray

    override fun compareTo(other: UBigInt): Int

    override fun equals(other: Any?): Boolean

    override fun hashCode(): Int

    companion object {
        val ZERO: UBigInt
        val ONE: UBigInt

        fun of(value: Long): UBigInt

        /** An unsigned big-endian magnitude, the inverse of [toMinimalBytes]. */
        fun ofBytes(bytes: ByteArray): UBigInt
    }
}
