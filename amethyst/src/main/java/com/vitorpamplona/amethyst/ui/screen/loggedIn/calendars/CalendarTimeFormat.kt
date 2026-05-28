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

/**
 * Per-thread cached [SimpleDateFormat] keyed off the current default [Locale].
 *
 * Cached because these run per cell in the calendar grid (≈42 cells per month,
 * 7 per weekday header) — allocating a fresh SimpleDateFormat each call
 * showed up as scroll-time GC churn. Per-thread because SimpleDateFormat
 * isn't thread-safe; calendars today are read on the UI thread but future
 * callers could differ. ThreadLocal gives both — no locks, no contention,
 * lazy rebuild on locale change.
 */
private class LocaleAwareFormatter(
    private val skeleton: String,
) {
    private val cache = ThreadLocal<Pair<Locale, SimpleDateFormat>>()

    fun get(): SimpleDateFormat {
        val current = Locale.getDefault()
        val cached = cache.get()
        if (cached != null && cached.first == current) return cached.second
        val fresh = SimpleDateFormat(DateFormat.getBestDateTimePattern(current, skeleton), current)
        cache.set(current to fresh)
        return fresh
    }
}

private val dayMonthFormat = LocaleAwareFormatter(DAY_MONTH_SKELETON)
private val fullDateFormat = LocaleAwareFormatter(FULL_DATE_SKELETON)
private val monthYearFormat = LocaleAwareFormatter(MONTH_YEAR_SKELETON)
private val weekdayShortFormat = LocaleAwareFormatter(WEEKDAY_SHORT_SKELETON)

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
    val dayMonth = dayMonthFormat.get()
    val time = timeFormat(context)
    val startMs = start * 1000
    val startStr = "${dayMonth.format(Date(startMs))} · ${time.format(Date(startMs))}"
    if (end == null || end == start) return startStr
    val endMs = end * 1000
    return if (isSameDay(startMs, endMs)) {
        "$startStr – ${time.format(Date(endMs))}"
    } else {
        "$startStr – ${dayMonth.format(Date(endMs))} · ${time.format(Date(endMs))}"
    }
}

private fun formatDateRange(
    start: Long,
    end: Long?,
): String {
    val dayMonth = dayMonthFormat.get()
    val startStr = dayMonth.format(Date(start * 1000))
    if (end == null || end == start) return startStr
    return "$startStr – ${dayMonth.format(Date(end * 1000))}"
}

fun formatLongDate(unixSeconds: Long): String = fullDateFormat.get().format(Date(unixSeconds * 1000))

fun formatMonthYear(
    year: Int,
    monthZeroBased: Int,
): String {
    val cal = Calendar.getInstance()
    cal.clear()
    cal.set(year, monthZeroBased, 1)
    return monthYearFormat.get().format(cal.time)
}

fun formatTimeOfDay(
    unixSeconds: Long,
    context: Context,
): String = timeFormat(context).format(Date(unixSeconds * 1000))

fun formatShortWeekday(weekdayZeroBased: Int): String {
    val cal = Calendar.getInstance()
    cal.clear()
    cal.firstDayOfWeek = Calendar.SUNDAY
    cal.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
    cal.add(Calendar.DAY_OF_YEAR, weekdayZeroBased)
    return weekdayShortFormat.get().format(cal.time)
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
