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

/**
 * Clock and duration helpers for Nostr timestamps.
 *
 * **Everything here is in Unix _seconds_, not milliseconds** — that is the unit
 * Nostr uses for an event's `created_at` and for filter `since`/`until` bounds.
 * Use [now] to stamp an event and the `...Ago` / `...FromNow` helpers to build
 * relative filter windows; do **not** hand-roll `currentTimeMillis() / 1000`.
 * The `const val` durations ([ONE_MINUTE], [ONE_HOUR], [ONE_DAY], …) are also in
 * seconds and can be added/subtracted directly.
 *
 * ```kotlin
 * val createdAt = TimeUtils.now()                 // seconds, for created_at
 * val since = TimeUtils.oneDayAgo()               // filter: last 24h
 * val fresh = TimeUtils.withinTenMinutes(event.createdAt)
 * ```
 *
 * [nowMillis] is the one exception: it returns milliseconds, for the rare
 * non-protocol case (UI timers, latency measurements) that needs finer detail.
 */
object TimeUtils {
    const val TEN_SECONDS = 10
    const val ONE_MINUTE = 60
    const val FIVE_MINUTES = 5 * ONE_MINUTE
    const val TEN_MINUTES = 10 * ONE_MINUTE
    const val FIFTEEN_MINUTES = 15 * ONE_MINUTE
    const val ONE_HOUR = 60 * ONE_MINUTE
    const val EIGHT_HOURS = 8 * ONE_HOUR
    const val ONE_DAY = 24 * ONE_HOUR
    const val NINETY_DAYS = 90 * ONE_DAY
    const val ONE_WEEK = 7 * ONE_DAY
    const val ONE_MONTH = 30 * ONE_DAY
    const val ONE_YEAR = 365 * ONE_DAY

    /** Current time in Unix **seconds** — the value for an event's `created_at`. */
    fun now() = currentTimeSeconds()

    /** Current time in Unix **milliseconds**. For non-protocol use only; `created_at` wants [now]. */
    fun nowMillis() = currentTimeMillis()

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

    /** True when [time] (Unix seconds) is within ±10 minutes of [now] — e.g. a fresh NIP-98/NIP-42 stamp. */
    fun withinTenMinutes(time: Long): Boolean {
        val now = now()
        return time > now - TEN_MINUTES && time < now + TEN_MINUTES
    }
}
