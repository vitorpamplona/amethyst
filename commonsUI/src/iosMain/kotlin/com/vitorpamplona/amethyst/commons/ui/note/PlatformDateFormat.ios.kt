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
package com.vitorpamplona.amethyst.commons.ui.note

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarUnitDay
import platform.Foundation.NSCalendarUnitYear
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSDateFormatterNoStyle
import platform.Foundation.NSDateFormatterShortStyle
import platform.Foundation.NSLocale
import platform.Foundation.NSTimeZone
import platform.Foundation.currentLocale
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.defaultTimeZone
import platform.Foundation.localeIdentifier
import kotlin.concurrent.Volatile

private fun dateOf(epochMillis: Long) = NSDate.dateWithTimeIntervalSince1970(epochMillis / 1000.0)

/**
 * NSDateFormatter is thread-safe for formatting on iOS 7+; the template picks the locale's order.
 * Rebuilt when the current locale or time zone changes, like the other actuals.
 */
actual class DateSkeletonFormatter actual constructor(
    private val skeleton: String,
) {
    private class Cached(
        val key: String,
        val formatter: NSDateFormatter,
    )

    @Volatile private var cached: Cached? = null

    actual fun format(epochMillis: Long): String {
        val locale = NSLocale.currentLocale
        val key = locale.localeIdentifier + "|" + NSTimeZone.defaultTimeZone.name
        val formatter =
            cached?.takeIf { it.key == key }?.formatter
                ?: NSDateFormatter()
                    .apply {
                        this.locale = locale
                        setLocalizedDateFormatFromTemplate(skeleton)
                    }.also { cached = Cached(key, it) }
        return formatter.stringFromDate(dateOf(epochMillis))
    }
}

actual fun calendarYearAndDay(epochMillis: Long): Int {
    val calendar = NSCalendar.currentCalendar
    val date = dateOf(epochMillis)
    val year = calendar.component(NSCalendarUnitYear, fromDate = date).toInt()
    val day = calendar.ordinalityOfUnit(NSCalendarUnitDay, inUnit = NSCalendarUnitYear, forDate = date).toInt()
    return year * 1000 + day
}

@Composable
actual fun rememberTimeOfDayFormatter(): (epochMillis: Long) -> String =
    remember {
        val formatter =
            NSDateFormatter().apply {
                dateStyle = NSDateFormatterNoStyle
                timeStyle = NSDateFormatterShortStyle
            }
        val format: (Long) -> String = { epochMillis -> formatter.stringFromDate(dateOf(epochMillis)) }
        format
    }

actual fun relativeTimeSpanShort(
    epochMillis: Long,
    nowMillis: Long,
    nowLabel: String,
    fallback: () -> String,
): String = fallback()
