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
package com.vitorpamplona.amethyst.ui.note

import android.content.Context
import android.text.format.DateFormat
import java.util.Date

// Date/time formatters that respect the user's Android system settings: the date order comes
// from the active locale and the clock follows DateFormat.is24HourFormat (the user's manual
// override on top of the locale default). The shared month/day/time formatter lives in
// commonsUI (rememberMonthDayTimeFormatter).

/** Locale-aware medium date (e.g. "May 28, 2026" / "28 May 2026" / "28.05.2026"). */
fun formatMediumDate(
    epochSeconds: Long,
    context: Context,
): String = DateFormat.getMediumDateFormat(context).format(Date(epochSeconds * 1000L))

/** Locale-aware medium date + short time, respecting the system 12/24-hour setting. */
fun formatMediumDateTime(
    epochSeconds: Long,
    context: Context,
): String {
    val date = DateFormat.getMediumDateFormat(context).format(Date(epochSeconds * 1000L))
    val time = DateFormat.getTimeFormat(context).format(Date(epochSeconds * 1000L))
    return "$date $time"
}
