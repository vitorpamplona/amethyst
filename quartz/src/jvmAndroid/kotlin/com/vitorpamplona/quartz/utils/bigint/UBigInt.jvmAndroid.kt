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

import java.math.BigInteger

/**
 * [UBigInt] over `java.math.BigInteger`, which is what makes a region key
 * affordable on the two platforms Amethyst actually ships to.
 *
 * A wrapper rather than a `typealias` for one reason: [toMinimalBytes]. The
 * reference hashes `int_to_bytes_be_min`, and `BigInteger.toByteArray()` is
 * two's complement, so it prepends a `0x00` sign byte whenever the top bit is
 * set. Aliasing would put that byte into a SHA-256 for one number in two and
 * produce a key nobody else derives. The allocation per operation is nothing
 * against multiplications of megabyte operands.
 */
actual class UBigInt internal constructor(
    internal val raw: BigInteger,
) : Comparable<UBigInt> {
    actual val bitLength: Int get() = raw.bitLength()

    actual val isZero: Boolean get() = raw.signum() == 0

    actual operator fun plus(other: UBigInt): UBigInt = UBigInt(raw.add(other.raw))

    actual operator fun times(other: UBigInt): UBigInt = UBigInt(raw.multiply(other.raw))

    actual fun shiftRight(bits: Int): UBigInt {
        require(bits >= 0) { "shift must not be negative" }
        return UBigInt(raw.shiftRight(bits))
    }

    actual fun toMinimalBytes(): ByteArray {
        if (raw.signum() == 0) return byteArrayOf(0)
        val bytes = raw.toByteArray()
        // Two's complement grows a leading zero exactly when the magnitude's
        // top bit is set; the reference never writes one.
        return if (bytes[0] == 0.toByte()) bytes.copyOfRange(1, bytes.size) else bytes
    }

    actual override fun compareTo(other: UBigInt): Int = raw.compareTo(other.raw)

    actual override fun equals(other: Any?): Boolean = other is UBigInt && raw == other.raw

    actual override fun hashCode(): Int = raw.hashCode()

    override fun toString(): String = "UBigInt($bitLength bits)"

    actual companion object {
        actual val ZERO: UBigInt = UBigInt(BigInteger.ZERO)
        actual val ONE: UBigInt = UBigInt(BigInteger.ONE)

        actual fun of(value: Long): UBigInt {
            require(value >= 0) { "negative values have no place on this lattice" }
            return UBigInt(BigInteger.valueOf(value))
        }

        actual fun ofBytes(bytes: ByteArray): UBigInt = if (bytes.isEmpty()) ZERO else UBigInt(BigInteger(1, bytes))
    }
}
