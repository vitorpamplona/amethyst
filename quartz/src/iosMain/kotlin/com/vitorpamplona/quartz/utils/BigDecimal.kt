/**
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

import platform.Foundation.NSDecimalNumber

// class from https://github.com/apollographql/apollo-kotlin-adapters
actual class BigDecimal internal constructor(
    private val raw: NSDecimalNumber,
) : Number() {
    actual constructor(strVal: String) : this(NSDecimalNumber(strVal))

    actual constructor(doubleVal: Double) : this(NSDecimalNumber(doubleVal))

    actual constructor(intVal: Int) : this(NSDecimalNumber(int = intVal))

    actual constructor(longVal: Long) : this(NSDecimalNumber(longLong = longVal))

    actual fun add(augend: BigDecimal): BigDecimal = BigDecimal(raw.decimalNumberByAdding(augend.raw))

    actual fun subtract(subtrahend: BigDecimal): BigDecimal = BigDecimal(raw.decimalNumberBySubtracting(subtrahend.raw))

    actual fun multiply(multiplicand: BigDecimal): BigDecimal = BigDecimal(raw.decimalNumberByMultiplyingBy(multiplicand.raw))

    actual fun divide(divisor: BigDecimal): BigDecimal = BigDecimal(raw.decimalNumberByDividingBy(divisor.raw))

    actual fun negate(): BigDecimal = BigDecimal(NSDecimalNumber(int = 0).decimalNumberBySubtracting(raw))

    actual fun signum(): Int {
        val result = raw.compare(NSDecimalNumber(int = 0)).toInt()
        return when {
            result < 0 -> -1
            result > 0 -> 1
            else -> 0
        }
    }

    override fun toInt(): Int = raw.intValue

    override fun toLong(): Long = raw.longLongValue

    override fun toShort(): Short = raw.shortValue

    override fun toByte(): Byte = raw.charValue

    override fun toDouble(): Double = raw.doubleValue

    override fun toFloat(): Float = raw.floatValue

    override fun equals(other: Any?): Boolean = (this === other) || raw == (other as? BigDecimal)?.raw

    override fun hashCode(): Int = raw.hashCode()

    override fun toString(): String = raw.stringValue
}
