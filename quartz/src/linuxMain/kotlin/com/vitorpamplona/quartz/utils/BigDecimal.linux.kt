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
package com.vitorpamplona.quartz.utils

actual class BigDecimal actual constructor(
    strVal: String,
) : Number() {
    private val value: String = strVal.trim()

    actual constructor(doubleVal: Double) : this(doubleVal.toBigDecimalString())

    actual constructor(intVal: Int) : this(intVal.toString())

    actual constructor(longVal: Long) : this(longVal.toString())

    actual fun add(augend: BigDecimal): BigDecimal = BigDecimal((toDouble() + augend.toDouble()).toBigDecimalString())

    actual fun subtract(subtrahend: BigDecimal): BigDecimal = BigDecimal((toDouble() - subtrahend.toDouble()).toBigDecimalString())

    actual fun multiply(multiplicand: BigDecimal): BigDecimal = BigDecimal((toDouble() * multiplicand.toDouble()).toBigDecimalString())

    actual fun divide(divisor: BigDecimal): BigDecimal = BigDecimal((toDouble() / divisor.toDouble()).toBigDecimalString())

    actual fun negate(): BigDecimal = BigDecimal((-toDouble()).toBigDecimalString())

    actual fun signum(): Int {
        val d = toDouble()
        return when {
            d < 0.0 -> -1
            d > 0.0 -> 1
            else -> 0
        }
    }

    override fun toInt(): Int = toDouble().toInt()

    override fun toLong(): Long = toDouble().toLong()

    override fun toShort(): Short = toDouble().toInt().toShort()

    override fun toByte(): Byte = toDouble().toInt().toByte()

    override fun toDouble(): Double = value.toDouble()

    override fun toFloat(): Float = value.toFloat()

    override fun equals(other: Any?): Boolean = (this === other) || (other is BigDecimal && value == other.value)

    override fun hashCode(): Int = value.hashCode()

    override fun toString(): String = value
}

private fun Double.toBigDecimalString(): String {
    if (this == this.toLong().toDouble()) {
        return this.toLong().toString()
    }
    return this.toString()
}
