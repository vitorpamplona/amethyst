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

import android.text.format.DateFormat
import android.text.format.DateUtils
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Per-thread cached [SimpleDateFormat] keyed off the current default [Locale].
 *
 * `SimpleDateFormat` is mutable and not thread-safe, and these formatters are read from both
 * the UI thread (composition) and background coroutines. `ThreadLocal` gives each thread its
 * own instance: no locks, no allocation per call, and a lazy rebuild on locale change.
 */
actual class DateSkeletonFormatter actual constructor(
    private val skeleton: String,
) {
    private val cache = ThreadLocal<Pair<Locale, SimpleDateFormat>>()

    private fun get(): SimpleDateFormat {
        val current = Locale.getDefault()
        val cached = cache.get()
        if (cached != null && cached.first == current) return cached.second
        val fresh = SimpleDateFormat(DateFormat.getBestDateTimePattern(current, skeleton), current)
        cache.set(current to fresh)
        return fresh
    }

    actual fun format(epochMillis: Long): String = get().format(epochMillis)
}

/** Built per call, as before the move: the system 12/24-hour setting can change under a running app. */
@Composable
actual fun rememberTimeOfDayFormatter(): (epochMillis: Long) -> String {
    val context = LocalContext.current
    return remember(context) { { epochMillis -> DateFormat.getTimeFormat(context).format(Date(epochMillis)) } }
}

actual fun relativeTimeSpanShort(
    epochMillis: Long,
    nowMillis: Long,
    nowLabel: String,
    fallback: () -> String,
): String {
    val humanReadable =
        DateUtils
            .getRelativeTimeSpanString(
                epochMillis,
                nowMillis,
                DateUtils.MINUTE_IN_MILLIS,
                DateUtils.FORMAT_ABBREV_ALL,
            ).toString()
    return if (humanReadable.startsWith("In") || humanReadable.startsWith("0")) nowLabel else humanReadable
}
