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
@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.vitorpamplona.amethyst.commons.search.calendar

import com.vitorpamplona.amethyst.commons.util.KmpLock
import com.vitorpamplona.amethyst.commons.util.withLock
import com.vitorpamplona.quartz.utils.currentTimeSeconds
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSLocale
import platform.Foundation.NSTimeZone
import platform.Foundation.currentLocale
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.localTimeZone

// NSDateFormatter is not thread-safe for concurrent stringFromDate calls (Apple docs), and the
// search field re-reads a month grid from whichever dispatcher the state flow ran on. One lock
// over the shared formatters is cheaper than allocating one per grid cell.
private val labelLock = KmpLock()

private val monthFormatter: NSDateFormatter = formatter("LLLL yyyy")
private val dayFormatter: NSDateFormatter = formatter("d MMM yyyy")

private fun formatter(pattern: String): NSDateFormatter =
    NSDateFormatter().apply {
        dateFormat = pattern
        timeZone = NSTimeZone.localTimeZone
        locale = NSLocale.currentLocale
    }

private fun dateAt(epochSeconds: Long): NSDate = NSDate.dateWithTimeIntervalSince1970(epochSeconds.toDouble())

/** The seconds this zone is ahead of UTC at [epochSeconds], which a clock change moves. */
private fun offsetAt(epochSeconds: Long): Long = NSTimeZone.localTimeZone.secondsFromGMTForDate(dateAt(epochSeconds)).toLong()

/**
 * The unix second at local 00:00 on a civil date, in two passes.
 *
 * The date arithmetic is exact and lives in [SearchDate]; the only thing this needs from the
 * platform is the zone's offset. But the offset itself depends on the instant, so the first pass
 * probes with UTC midnight and the second re-probes at the instant that produced — which is what
 * lands correctly on a day whose clocks changed, where a single pass is off by the change.
 */
private fun localMidnight(date: SearchDate): Long {
    val utcMidnight = date.daysFromEpoch() * 86400L
    val firstPass = utcMidnight - offsetAt(utcMidnight)
    return utcMidnight - offsetAt(firstPass)
}

actual object LocalClock {
    actual fun startOfDay(date: SearchDate): Long = localMidnight(date)

    // The second before the next midnight, so a day that gained or lost an hour still ends where
    // it ends — never midnight plus 86,399.
    actual fun endOfDay(date: SearchDate): Long = localMidnight(date.plusDays(1)) - 1

    actual fun today(): SearchDate {
        val now = currentTimeSeconds()
        return SearchDate.civilFromDays((now + offsetAt(now)).floorDiv(86400L))
    }

    /**
     * Which weekday a week starts on here. Foundation only exposes this through NSCalendar, so
     * this takes the ISO default rather than reaching for it — a week that starts on the wrong
     * day shifts a grid's columns, which is a cosmetic fault, and one worth taking over a
     * platform call this target cannot yet be built to verify.
     */
    actual fun firstDayOfWeek(): Int = 1

    actual fun monthLabel(date: SearchDate): String = labelLock.withLock { monthFormatter.stringFromDate(dateAt(localMidnight(date.firstOfMonth()))) }

    actual fun dayLabel(date: SearchDate): String = labelLock.withLock { dayFormatter.stringFromDate(dateAt(localMidnight(date))) }

    actual fun narrowWeekdayNames(): List<String> =
        labelLock.withLock {
            // veryShortWeekdaySymbols is already indexed 0 = Sunday, which is this API's order.
            val symbols = dayFormatter.veryShortWeekdaySymbols
            // A locale with no symbols is not one this can name days in; the ISO initials are
            // wrong-but-legible, where a short list would crash the grid's heading row.
            if (symbols.size == 7) symbols.map { it.toString() } else listOf("S", "M", "T", "W", "T", "F", "S")
        }
}
