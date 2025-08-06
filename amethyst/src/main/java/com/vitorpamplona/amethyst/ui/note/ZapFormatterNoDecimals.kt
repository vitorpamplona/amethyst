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
package com.vitorpamplona.amethyst.ui.note

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat

private val dfG =
    object : ThreadLocal<DecimalFormat>() {
        override fun initialValue() = DecimalFormat("#G")
    }

private val dfM =
    object : ThreadLocal<DecimalFormat>() {
        override fun initialValue() = DecimalFormat("#M")
    }

private val dfK =
    object : ThreadLocal<DecimalFormat>() {
        override fun initialValue() = DecimalFormat("#k")
    }

private val dfN =
    object : ThreadLocal<DecimalFormat>() {
        override fun initialValue() = DecimalFormat("#")
    }

fun showAmountInteger(amount: BigDecimal?): String {
    if (amount == null) return ""
    if (amount.abs() < BigDecimal(0.01)) return ""

    return when {
        amount >= OneGiga -> dfG.get().format(amount.div(OneGiga).setScale(0, RoundingMode.HALF_UP))
        amount >= OneMega -> dfM.get().format(amount.div(OneMega).setScale(0, RoundingMode.HALF_UP))
        amount >= TenKilo -> dfK.get().format(amount.div(OneKilo).setScale(0, RoundingMode.HALF_UP))
        else -> dfN.get().format(amount)
    }
}

fun showAmountIntegerWithZero(amount: BigDecimal?): String {
    if (amount == null) return "0"
    if (amount.abs() < BigDecimal(0.01)) return "0"

    return showAmountInteger(amount)
}
