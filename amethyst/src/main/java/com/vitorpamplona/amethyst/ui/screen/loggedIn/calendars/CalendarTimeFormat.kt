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

import com.vitorpamplona.amethyst.commons.model.nip52Calendar.appointmentView
import com.vitorpamplona.amethyst.model.Note
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val DayMonthFormat = SimpleDateFormat("EEE, MMM d", Locale.getDefault())
private val FullDateFormat = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault())
private val MonthYearFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
private val TimeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
private val WeekdayShortFormat = SimpleDateFormat("EEE", Locale.getDefault())

fun formatCalendarRange(note: Note): String? {
    val view = note.appointmentView() ?: return null
    val start = view.startSeconds ?: return null
    return if (view.isAllDay) {
        formatDateRange(start, view.endSeconds)
    } else {
        formatTimeRange(start, view.endSeconds)
    }
}

private fun formatTimeRange(
    start: Long,
    end: Long?,
): String {
    val startMs = start * 1000
    val startStr = "${DayMonthFormat.format(Date(startMs))} · ${TimeFormat.format(Date(startMs))}"
    if (end == null || end == start) return startStr
    val endMs = end * 1000
    return if (isSameDay(startMs, endMs)) {
        "$startStr – ${TimeFormat.format(Date(endMs))}"
    } else {
        "$startStr – ${DayMonthFormat.format(Date(endMs))} · ${TimeFormat.format(Date(endMs))}"
    }
}

private fun formatDateRange(
    start: Long,
    end: Long?,
): String {
    val startStr = DayMonthFormat.format(Date(start * 1000))
    if (end == null || end == start) return startStr
    return "$startStr – ${DayMonthFormat.format(Date(end * 1000))}"
}

fun formatLongDate(unixSeconds: Long): String = FullDateFormat.format(Date(unixSeconds * 1000))

fun formatMonthYear(
    year: Int,
    monthZeroBased: Int,
): String {
    val cal = Calendar.getInstance()
    cal.clear()
    cal.set(year, monthZeroBased, 1)
    return MonthYearFormat.format(cal.time)
}

fun formatTimeOfDay(unixSeconds: Long): String = TimeFormat.format(Date(unixSeconds * 1000))

fun formatShortWeekday(weekdayZeroBased: Int): String {
    val cal = Calendar.getInstance()
    cal.clear()
    cal.firstDayOfWeek = Calendar.SUNDAY
    cal.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
    cal.add(Calendar.DAY_OF_YEAR, weekdayZeroBased)
    return WeekdayShortFormat.format(cal.time)
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
