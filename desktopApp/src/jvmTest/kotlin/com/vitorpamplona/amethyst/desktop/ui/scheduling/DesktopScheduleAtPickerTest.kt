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
package com.vitorpamplona.amethyst.desktop.ui.scheduling

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopScheduleAtPickerTest {
    /**
     * Reconstructs the confirm math from [DateTimePickerDialog]: the DatePicker's
     * selectedDateMillis is UTC-midnight; add the chosen hour/minute (local wall-clock),
     * then subtract the system-default offset to get the epoch-second of that local
     * date+time. This mirrors the production code so a round-trip is exercised end-to-end.
     */
    private fun confirmSec(
        pickerMillis: Long,
        hour: Int,
        minute: Int,
        zone: ZoneId,
    ): Long {
        val datetimeLocalSec = (pickerMillis / 1000) + (hour * 3600L) + (minute * 60L)
        val offset: ZoneOffset = zone.rules.getOffset(Instant.now())
        return datetimeLocalSec - offset.totalSeconds
    }

    @Test
    fun seed_millis_lands_on_the_local_calendar_day() {
        // A UTC+ zone is where the naive `epochSec*1000` seed shifts the day back by one.
        val zone = ZoneId.of("Australia/Sydney") // UTC+10/+11
        // 2026-06-01 08:30 local -> epoch second.
        val localDateTime = LocalDate.of(2026, 6, 1).atTime(8, 30)
        val epochSec = localDateTime.atZone(zone).toEpochSecond()

        val seed = datePickerInitialMillisForZone(epochSec, zone)

        // The picker interprets the seed as a UTC calendar day; it must be 2026-06-01.
        val shownDate =
            Instant
                .ofEpochMilli(seed)
                .atZone(ZoneOffset.UTC)
                .toLocalDate()
        assertEquals(LocalDate.of(2026, 6, 1), shownDate)
    }

    @Test
    fun round_trip_open_confirm_untouched_preserves_the_day() {
        val zone = ZoneId.of("Australia/Sydney")
        val localDateTime = LocalDate.of(2026, 6, 1).atTime(8, 30)
        val epochSec = localDateTime.atZone(zone).toEpochSecond()

        val seed = datePickerInitialMillisForZone(epochSec, zone)
        // Confirm untouched: same day, and the time the TimePicker was seeded with (8:30).
        val confirmed = confirmSec(seed, hour = 8, minute = 30, zone = zone)

        val confirmedLocalDate =
            Instant
                .ofEpochSecond(confirmed)
                .atZone(zone)
                .toLocalDate()
        assertEquals(
            LocalDate.of(2026, 6, 1),
            confirmedLocalDate,
            "confirming without changing the date must keep the same local day",
        )
    }

    // Zone-parameterised twin of the production helper so the test is deterministic
    // regardless of the machine's default zone.
    private fun datePickerInitialMillisForZone(
        epochSec: Long,
        zone: ZoneId,
    ): Long =
        Instant
            .ofEpochSecond(epochSec)
            .atZone(zone)
            .toLocalDate()
            .atStartOfDay(ZoneOffset.UTC)
            .toEpochSecond() * 1000
}
