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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.calendars.dal

import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.quartz.nip52Calendar.appt.day.CalendarDateSlotEvent
import com.vitorpamplona.quartz.nip52Calendar.appt.time.CalendarTimeSlotEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

private val IsoDateParser =
    SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
        isLenient = false
    }

/**
 * Calendar 31922 carries an ISO date string. Parsed in UTC so day-only events compare
 * predictably across viewers in different time zones.
 */
fun parseIsoDateToUnixSeconds(date: String?): Long? {
    if (date.isNullOrBlank()) return null
    return try {
        IsoDateParser.parse(date)?.time?.div(1000)
    } catch (_: Throwable) {
        null
    }
}

/**
 * Unified start time as unix-seconds for any NIP-52 calendar appointment.
 * Returns null when neither slot kind is present or the start cannot be parsed.
 */
fun Note.calendarStartSeconds(): Long? =
    when (val e = event) {
        is CalendarTimeSlotEvent -> e.start()
        is CalendarDateSlotEvent -> parseIsoDateToUnixSeconds(e.start())
        else -> null
    }

fun Note.calendarEndSeconds(): Long? =
    when (val e = event) {
        is CalendarTimeSlotEvent -> e.end() ?: e.start()
        is CalendarDateSlotEvent -> parseIsoDateToUnixSeconds(e.end()) ?: parseIsoDateToUnixSeconds(e.start())
        else -> null
    }

/**
 * Sort by: upcoming events ascending (closest first), then past events descending (most-recent first).
 * Falls back to createdAt + id when start is missing so the order remains stable.
 */
val UpcomingFirstCalendarOrder: Comparator<Note> =
    Comparator { a, b ->
        val now = TimeUtils.now()
        val sa = a.calendarStartSeconds()
        val sb = b.calendarStartSeconds()

        when {
            sa == null && sb == null -> compareCreatedAt(a, b)
            sa == null -> 1
            sb == null -> -1
            else -> {
                val aUpcoming = sa >= now
                val bUpcoming = sb >= now
                when {
                    aUpcoming && !bUpcoming -> -1
                    !aUpcoming && bUpcoming -> 1
                    aUpcoming -> sa.compareTo(sb) // both future: nearest first
                    else -> sb.compareTo(sa) // both past: most recent first
                }
            }
        }.let { primary ->
            if (primary != 0) primary else a.idHex.compareTo(b.idHex)
        }
    }

private fun compareCreatedAt(
    a: Note,
    b: Note,
): Int {
    val ca = a.createdAt() ?: 0L
    val cb = b.createdAt() ?: 0L
    return cb.compareTo(ca)
}
