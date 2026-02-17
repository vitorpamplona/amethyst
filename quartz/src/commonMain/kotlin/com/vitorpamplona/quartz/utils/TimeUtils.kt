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

object TimeUtils {
    const val TEN_SECONDS = 10
    const val ONE_MINUTE = 60
    const val FIVE_MINUTES = 5 * ONE_MINUTE
    const val FIFTEEN_MINUTES = 15 * ONE_MINUTE
    const val ONE_HOUR = 60 * ONE_MINUTE
    const val EIGHT_HOURS = 8 * ONE_HOUR
    const val ONE_DAY = 24 * ONE_HOUR
    const val NINETY_DAYS = 90 * ONE_DAY
    const val ONE_WEEK = 7 * ONE_DAY
    const val ONE_MONTH = 30 * ONE_DAY
    const val ONE_YEAR = 365 * ONE_DAY

    fun now() = currentTimeSeconds()

    fun tenSecondsFromNow() = now() + TEN_SECONDS

    fun tenSecondsAgo() = now() - TEN_SECONDS

    fun oneMinuteFromNow() = now() + ONE_MINUTE

    fun oneMinuteAgo() = now() - ONE_MINUTE

    fun fiveMinutesAgo() = now() - FIVE_MINUTES

    fun fiveMinutesAhead() = now() + FIVE_MINUTES

    fun fifteenMinutesAgo() = now() - FIFTEEN_MINUTES

    fun oneHourAgo() = now() - ONE_HOUR

    fun oneHourAhead() = now() + ONE_HOUR

    fun oneDayAgo() = now() - ONE_DAY

    fun oneDayAhead() = now() + ONE_DAY

    fun eightHoursAgo() = now() - EIGHT_HOURS

    fun twoDays() = ONE_DAY * 2

    fun oneWeekAgo() = now() - ONE_WEEK

    fun oneMonthAgo() = now() - ONE_MONTH

    fun randomWithTwoDays() = now() - RandomInstance.int(twoDays())

    fun ninetyDaysFromNow() = now() + NINETY_DAYS

    fun oneYearAgo() = now() - ONE_YEAR
}
