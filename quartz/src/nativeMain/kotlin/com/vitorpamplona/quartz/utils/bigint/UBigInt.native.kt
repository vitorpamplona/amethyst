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
 * [UBigInt] over [PortableUBigInt], for Apple and Linux, which have no big
 * integer to borrow.
 *
 * Slower than the JVM's by 3 to 10 times on the operands a Cantor tree reaches,
 * and identical in what it produces — which is the part that matters, because
 * §7.2 turns it into a key. `PortableUBigIntDifferentialTest` is what holds the
 * two together.
 */
actual class UBigInt internal constructor(
    internal val raw: PortableUBigInt,
) : Comparable<UBigInt> {
    actual val bitLength: Int get() = raw.bitLength

    actual val isZero: Boolean get() = raw.isZero

    actual operator fun plus(other: UBigInt): UBigInt = UBigInt(raw + other.raw)

    actual operator fun times(other: UBigInt): UBigInt = UBigInt(raw * other.raw)

    actual fun shiftRight(bits: Int): UBigInt = UBigInt(raw.shiftRight(bits))

    actual fun toMinimalBytes(): ByteArray = raw.toMinimalBytes()

    actual override fun compareTo(other: UBigInt): Int = raw.compareTo(other.raw)

    actual override fun equals(other: Any?): Boolean = other is UBigInt && raw == other.raw

    actual override fun hashCode(): Int = raw.hashCode()

    override fun toString(): String = "UBigInt($bitLength bits)"

    actual companion object {
        actual val ZERO: UBigInt = UBigInt(PortableUBigInt.ZERO)
        actual val ONE: UBigInt = UBigInt(PortableUBigInt.ONE)

        actual fun of(value: Long): UBigInt = UBigInt(PortableUBigInt.of(value))

        actual fun ofBytes(bytes: ByteArray): UBigInt = UBigInt(PortableUBigInt.ofBytes(bytes))
    }
}
