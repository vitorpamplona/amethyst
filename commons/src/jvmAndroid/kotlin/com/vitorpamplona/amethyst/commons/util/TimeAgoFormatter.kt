/**
 * Copyright (c) 2024 Vitor Pamplona
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

import com.vitorpamplona.quartz.utils.TimeUtils
import java.text.SimpleDateFormat
import java.util.Locale

private const val YEAR_DATE_FORMAT = "MMM dd, yyyy"
private const val MONTH_DATE_FORMAT = "MMM dd"

private var locale = Locale.getDefault()
private var yearFormatter = SimpleDateFormat(YEAR_DATE_FORMAT, locale)
private var monthFormatter = SimpleDateFormat(MONTH_DATE_FORMAT, locale)

private fun updateFormattersIfNeeded() {
    if (locale != Locale.getDefault()) {
        locale = Locale.getDefault()
        yearFormatter = SimpleDateFormat(YEAR_DATE_FORMAT, locale)
        monthFormatter = SimpleDateFormat(MONTH_DATE_FORMAT, locale)
    }
}

/**
 * Formats a Unix timestamp (seconds) as a human-readable time ago string.
 * Returns strings like " • 5m", " • 2h", " • Dec 12"
 *
 * @param time Unix timestamp in seconds, or null
 * @param withDot Whether to prefix with " • " (default true)
 * @return Formatted time ago string
 */
fun timeAgo(
    time: Long?,
    withDot: Boolean = true,
): String {
    if (time == null) return " "
    if (time == 0L) return if (withDot) " • never" else "never"

    val timeDifference = TimeUtils.now() - time
    val prefix = if (withDot) " • " else ""

    return when {
        timeDifference > TimeUtils.ONE_YEAR -> {
            updateFormattersIfNeeded()
            prefix + yearFormatter.format(time * 1000)
        }
        timeDifference > TimeUtils.ONE_MONTH -> {
            updateFormattersIfNeeded()
            prefix + monthFormatter.format(time * 1000)
        }
        timeDifference > TimeUtils.ONE_DAY -> {
            prefix + (timeDifference / TimeUtils.ONE_DAY).toString() + "d"
        }
        timeDifference > TimeUtils.ONE_HOUR -> {
            prefix + (timeDifference / TimeUtils.ONE_HOUR).toString() + "h"
        }
        timeDifference > TimeUtils.ONE_MINUTE -> {
            prefix + (timeDifference / TimeUtils.ONE_MINUTE).toString() + "m"
        }
        else -> {
            prefix + "now"
        }
    }
}

/**
 * Formats a Unix timestamp as a date string.
 * For recent dates (< 1 day), returns the provided today string.
 *
 * @param time Unix timestamp in seconds
 * @param never String to show for time == 0
 * @param today String to show for today's date
 */
fun dateFormatter(
    time: Long?,
    never: String = "never",
    today: String = "today",
): String {
    if (time == null) return " "
    if (time == 0L) return " $never"

    val timeDifference = TimeUtils.now() - time

    return when {
        timeDifference > TimeUtils.ONE_YEAR -> {
            updateFormattersIfNeeded()
            yearFormatter.format(time * 1000)
        }
        timeDifference > TimeUtils.ONE_DAY -> {
            updateFormattersIfNeeded()
            monthFormatter.format(time * 1000)
        }
        else -> today
    }
}

/**
 * Extension function for Long timestamps.
 */
fun Long.toTimeAgo(withDot: Boolean = true): String = timeAgo(this, withDot)
