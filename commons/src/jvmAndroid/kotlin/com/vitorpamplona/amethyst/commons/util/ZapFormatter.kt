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
package com.vitorpamplona.amethyst.commons.util

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat

private val TenGiga = BigDecimal(10_000_000_000)
private val OneGiga = BigDecimal(1_000_000_000)
private val TenMega = BigDecimal(10_000_000)
private val OneMega = BigDecimal(1_000_000)
private val TenKilo = BigDecimal(10_000)
private val OneKilo = BigDecimal(1_000)

private val dfGBig = ThreadLocal.withInitial { DecimalFormat("#.#G") }
private val dfGSmall = ThreadLocal.withInitial { DecimalFormat("#.0G") }
private val dfMBig = ThreadLocal.withInitial { DecimalFormat("#.#M") }
private val dfMSmall = ThreadLocal.withInitial { DecimalFormat("#.0M") }
private val dfK = ThreadLocal.withInitial { DecimalFormat("#.#k") }
private val dfN = ThreadLocal.withInitial { DecimalFormat("#") }

/**
 * Formats a BigDecimal amount to human-readable format with G/M/K suffixes.
 * Returns empty string for null or very small amounts.
 *
 * Examples:
 * - 1500 -> "1.5k"
 * - 2500000 -> "2.5M"
 * - 10000000000 -> "10G"
 */
fun showAmount(amount: BigDecimal?): String {
    if (amount == null) return ""
    if (amount.abs() < BigDecimal(0.01)) return ""

    return when {
        amount >= TenGiga -> dfGBig.get()!!.format(amount.div(OneGiga).setScale(0, RoundingMode.HALF_UP))
        amount >= OneGiga -> dfGSmall.get()!!.format(amount.div(OneGiga).setScale(0, RoundingMode.HALF_UP))
        amount >= TenMega -> dfMBig.get()!!.format(amount.div(OneMega).setScale(0, RoundingMode.HALF_UP))
        amount >= OneMega -> dfMSmall.get()!!.format(amount.div(OneMega).setScale(0, RoundingMode.HALF_UP))
        amount >= TenKilo -> dfK.get()!!.format(amount.div(OneKilo).setScale(0, RoundingMode.HALF_UP))
        else -> dfN.get()!!.format(amount)
    }
}

/**
 * Formats a BigDecimal amount to human-readable format.
 * Returns "0" for null or very small amounts instead of empty string.
 */
fun showAmountWithZero(amount: BigDecimal?): String {
    if (amount == null) return "0"
    if (amount.abs() < BigDecimal(0.01)) return "0"
    return showAmount(amount)
}

/**
 * Extension function to format Long as zap amount.
 */
fun Long.toZapAmount(): String = showAmount(BigDecimal(this))

/**
 * Extension function to format Int as zap amount.
 */
fun Int.toZapAmount(): String = showAmount(BigDecimal(this))
