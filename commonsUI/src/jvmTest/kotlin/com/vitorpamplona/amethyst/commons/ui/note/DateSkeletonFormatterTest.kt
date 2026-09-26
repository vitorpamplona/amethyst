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

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale
import java.util.TimeZone

class DateSkeletonFormatterTest {
    private lateinit var savedLocale: Locale
    private lateinit var savedZone: TimeZone

    @Before
    fun pin() {
        savedLocale = Locale.getDefault()
        savedZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After
    fun restore() {
        Locale.setDefault(savedLocale)
        TimeZone.setDefault(savedZone)
    }

    private fun millis(
        year: Int,
        month: Int,
        day: Int,
        hour: Int = 12,
    ) = LocalDateTime
        .of(year, month, day, hour, 0)
        .atZone(ZoneId.of("UTC"))
        .toInstant()
        .toEpochMilli()

    @Test
    fun skeletonFollowsTheLocalesFieldOrder() {
        val formatter = DateSkeletonFormatter("yMMMd")
        Locale.setDefault(Locale.US)
        assertEquals("Jan 5, 2024", formatter.format(millis(2024, 1, 5)))
        // Same instance, new locale: the cache must not hand back the US pattern.
        Locale.setDefault(Locale.UK)
        assertEquals("5 Jan 2024", formatter.format(millis(2024, 1, 5)))
    }

    @Test
    fun monthDayAndYearMonthSkeletons() {
        Locale.setDefault(Locale.US)
        assertEquals("Jan 5", DateSkeletonFormatter("MMMd").format(millis(2024, 1, 5)))
        assertEquals("Jan 2024", DateSkeletonFormatter("yMMM").format(millis(2024, 1, 5)))
    }

    @Test
    fun calendarDayDistinguishesDaysAndYears() {
        assertEquals(calendarYearAndDay(millis(2024, 3, 1, hour = 1)), calendarYearAndDay(millis(2024, 3, 1, hour = 23)))
        assertTrue(calendarYearAndDay(millis(2024, 3, 1)) != calendarYearAndDay(millis(2024, 3, 2)))
        assertEquals(2024, calendarYearAndDay(millis(2024, 12, 31)) / 1000)
        assertEquals(366, calendarYearAndDay(millis(2024, 12, 31)) % 1000)
    }

    @Test
    fun absoluteTimeUsesTimeOfDayOnlyForToday() {
        Locale.setDefault(Locale.US)
        val nowSec = System.currentTimeMillis() / 1000
        val timeOfDay: (Long) -> String = { "T" }
        assertEquals(" • T", timeAbsoluteWith(nowSec, timeOfDay, "never"))
        assertEquals("never", timeAbsoluteWith(0L, timeOfDay, "never", prefix = ""))
        // Two years back is always a different year: the date, with no time of day.
        val old = nowSec - 2L * 366 * 24 * 3600
        assertTrue(!timeAbsoluteWith(old, timeOfDay, "never").contains("T"))
    }
}
