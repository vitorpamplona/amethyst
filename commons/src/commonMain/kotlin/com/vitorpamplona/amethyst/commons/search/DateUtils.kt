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
package com.vitorpamplona.amethyst.commons.search

object DateUtils {
    fun isLeapYear(year: Int): Boolean = (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)

    fun dateToUnix(
        year: Int,
        month: Int,
        day: Int,
    ): Long {
        var totalDays = 0L

        for (y in 1970 until year) {
            totalDays += if (isLeapYear(y)) 366 else 365
        }

        val daysInMonth = intArrayOf(0, 31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        if (isLeapYear(year)) daysInMonth[2] = 29
        for (m in 1 until month) {
            totalDays += daysInMonth[m]
        }

        totalDays += (day - 1)

        return totalDays * 86400L
    }

    fun timestampToDate(timestamp: Long): String {
        var remaining = timestamp
        var year = 1970
        while (true) {
            val daysInYear = if (isLeapYear(year)) 366L else 365L
            val secondsInYear = daysInYear * 86400L
            if (remaining < secondsInYear) break
            remaining -= secondsInYear
            year++
        }

        val daysInMonth = intArrayOf(0, 31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        if (isLeapYear(year)) daysInMonth[2] = 29

        var dayOfYear = (remaining / 86400).toInt() + 1
        var month = 1
        while (month <= 12 && dayOfYear > daysInMonth[month]) {
            dayOfYear -= daysInMonth[month]
            month++
        }

        return "$year-${month.toString().padStart(2, '0')}-${dayOfYear.toString().padStart(2, '0')}"
    }
}
