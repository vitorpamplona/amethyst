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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.calendars

import android.content.Context
import android.text.format.DateFormat
import com.vitorpamplona.amethyst.commons.model.nip52Calendar.appointmentView
import com.vitorpamplona.amethyst.model.Note
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// Skeletons follow Unicode LDML — getBestDateTimePattern picks locale ordering.
private const val DAY_MONTH_SKELETON = "EEEMMMd" // "Mon, May 28" / "Mon 28 May"
private const val FULL_DATE_SKELETON = "EEEEMMMMdy" // "Monday, May 28, 2026" / "Monday, 28 May 2026"
private const val MONTH_YEAR_SKELETON = "MMMMy" // "May 2026" / "Mai 2026"
private const val WEEKDAY_SHORT_SKELETON = "EEE" // "Mon" — order doesn't matter

// Cached at module scope: these are called per-cell in the calendar grid (≈42 cells
// per month render, 7 per weekday header) — allocating a fresh SimpleDateFormat on
// every call showed up as scroll-time GC churn before caching.
private var cachedLocale: Locale = Locale.getDefault()
private var dayMonthFormat = build(DAY_MONTH_SKELETON, cachedLocale)
private var fullDateFormat = build(FULL_DATE_SKELETON, cachedLocale)
private var monthYearFormat = build(MONTH_YEAR_SKELETON, cachedLocale)
private var weekdayShortFormat = build(WEEKDAY_SHORT_SKELETON, cachedLocale)

private fun build(
    skeleton: String,
    locale: Locale,
): SimpleDateFormat = SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, skeleton), locale)

private fun refreshFormattersIfLocaleChanged() {
    val current = Locale.getDefault()
    if (cachedLocale != current) {
        cachedLocale = current
        dayMonthFormat = build(DAY_MONTH_SKELETON, current)
        fullDateFormat = build(FULL_DATE_SKELETON, current)
        monthYearFormat = build(MONTH_YEAR_SKELETON, current)
        weekdayShortFormat = build(WEEKDAY_SHORT_SKELETON, current)
    }
}

// Time format respects the user's Android 12/24-hour system setting.
private fun timeFormat(context: Context): java.text.DateFormat = DateFormat.getTimeFormat(context)

fun formatCalendarRange(
    note: Note,
    context: Context,
): String? {
    val view = note.appointmentView() ?: return null
    val start = view.startSeconds ?: return null
    return if (view.isAllDay) {
        formatDateRange(start, view.endSeconds)
    } else {
        formatTimeRange(start, view.endSeconds, context)
    }
}

private fun formatTimeRange(
    start: Long,
    end: Long?,
    context: Context,
): String {
    refreshFormattersIfLocaleChanged()
    val time = timeFormat(context)
    val startMs = start * 1000
    val startStr = "${dayMonthFormat.format(Date(startMs))} · ${time.format(Date(startMs))}"
    if (end == null || end == start) return startStr
    val endMs = end * 1000
    return if (isSameDay(startMs, endMs)) {
        "$startStr – ${time.format(Date(endMs))}"
    } else {
        "$startStr – ${dayMonthFormat.format(Date(endMs))} · ${time.format(Date(endMs))}"
    }
}

private fun formatDateRange(
    start: Long,
    end: Long?,
): String {
    refreshFormattersIfLocaleChanged()
    val startStr = dayMonthFormat.format(Date(start * 1000))
    if (end == null || end == start) return startStr
    return "$startStr – ${dayMonthFormat.format(Date(end * 1000))}"
}

fun formatLongDate(unixSeconds: Long): String {
    refreshFormattersIfLocaleChanged()
    return fullDateFormat.format(Date(unixSeconds * 1000))
}

fun formatMonthYear(
    year: Int,
    monthZeroBased: Int,
): String {
    refreshFormattersIfLocaleChanged()
    val cal = Calendar.getInstance()
    cal.clear()
    cal.set(year, monthZeroBased, 1)
    return monthYearFormat.format(cal.time)
}

fun formatTimeOfDay(
    unixSeconds: Long,
    context: Context,
): String = timeFormat(context).format(Date(unixSeconds * 1000))

fun formatShortWeekday(weekdayZeroBased: Int): String {
    refreshFormattersIfLocaleChanged()
    val cal = Calendar.getInstance()
    cal.clear()
    cal.firstDayOfWeek = Calendar.SUNDAY
    cal.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
    cal.add(Calendar.DAY_OF_YEAR, weekdayZeroBased)
    return weekdayShortFormat.format(cal.time)
}

private fun isSameDay(
    aMs: Long,
    bMs: Long,
): Boolean {
    val ca = Calendar.getInstance().apply { timeInMillis = aMs }
    val cb = Calendar.getInstance().apply { timeInMillis = bMs }
    return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR) &&
        ca.get(Calendar.DAY_OF_YEAR) == cb.get(Calendar.DAY_OF_YEAR)
}
