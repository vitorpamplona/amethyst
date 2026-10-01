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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.time.Instant
import java.time.ZoneId
import java.time.chrono.IsoChronology
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.FormatStyle
import java.util.Locale

/**
 * [DateTimeFormatter.ofLocalizedPattern] (JDK 19+) resolves the skeleton against the locale's
 * CLDR data, the JVM counterpart of Android's `getBestDateTimePattern`. The formatter is
 * immutable, so one cached instance serves every thread; it is rebuilt when the default locale
 * or time zone changes.
 */
actual class DateSkeletonFormatter actual constructor(
    private val skeleton: String,
) {
    private class Cached(
        val locale: Locale,
        val zone: ZoneId,
        val formatter: DateTimeFormatter,
    )

    @Volatile private var cached: Cached? = null

    actual fun format(epochMillis: Long): String {
        val locale = Locale.getDefault()
        val zone = ZoneId.systemDefault()
        val current = cached?.takeIf { it.locale == locale && it.zone == zone }
        val formatter =
            current?.formatter
                ?: DateTimeFormatter.ofLocalizedPattern(skeleton).withLocale(locale).withZone(zone).also {
                    cached = Cached(locale, zone, it)
                }
        return formatter.format(Instant.ofEpochMilli(epochMillis))
    }
}

private class CachedTimeOfDay(
    val locale: Locale,
    val zone: ZoneId,
    val formatter: DateTimeFormatter,
)

@Volatile private var cachedTimeOfDay: CachedTimeOfDay? = null

/** One formatter for every item, rebuilt only when the locale or zone changes. */
private val timeOfDay: (Long) -> String = { epochMillis ->
    val locale = Locale.getDefault()
    val zone = ZoneId.systemDefault()
    val formatter =
        cachedTimeOfDay?.takeIf { it.locale == locale && it.zone == zone }?.formatter
            ?: DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale).withZone(zone).also {
                cachedTimeOfDay = CachedTimeOfDay(locale, zone, it)
            }
    formatter.format(Instant.ofEpochMilli(epochMillis))
}

@Composable
actual fun rememberTimeOfDayFormatter(): (epochMillis: Long) -> String = timeOfDay

@Composable
actual fun rememberIs24HourClock(): Boolean =
    remember {
        DateTimeFormatterBuilder
            .getLocalizedDateTimePattern(null, FormatStyle.SHORT, IsoChronology.INSTANCE, Locale.getDefault())
            .contains('H')
    }

actual fun relativeTimeSpanShortOrNull(
    epochMillis: Long,
    nowMillis: Long,
    nowLabel: String,
): String? = null
