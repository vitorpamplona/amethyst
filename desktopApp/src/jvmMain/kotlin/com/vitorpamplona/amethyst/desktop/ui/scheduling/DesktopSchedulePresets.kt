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

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

// Pure scheduling helpers adapted from the Android
// `ui.note.creators.scheduling.ScheduleAtPicker`. Kept UI-free so both the
// Desktop picker and any test can reuse them.

/** Now + one hour, in epoch seconds. */
fun presetInOneHour(): Long = (System.currentTimeMillis() / 1000) + 3600

/** Tomorrow at 09:00 local time, in epoch seconds. */
fun presetTomorrowMorning(): Long {
    val zone = ZoneId.systemDefault()
    val tomorrow9am = LocalDate.now(zone).plusDays(1).atTime(LocalTime.of(9, 0))
    return tomorrow9am.atZone(zone).toEpochSecond()
}

/** Next Monday at 09:00 local time (always at least one day ahead), in epoch seconds. */
fun presetNextMondayMorning(): Long {
    val zone = ZoneId.systemDefault()
    val target =
        LocalDate
            .now(zone)
            .plusDays(1)
            .with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY))
            .atTime(LocalTime.of(9, 0))
    return target.atZone(zone).toEpochSecond()
}

/**
 * Normalizes a chosen schedule time to whole-minute precision (drops seconds) and
 * guarantees it is strictly in the future. Unlike Android — which rounds to the next
 * quarter-hour because WorkManager fires no more often than every 15 min — the
 * Desktop publisher runs a 45s in-app tick (and a ~5-min OS tick when closed), so we
 * honor the exact minute the user picks (e.g. 15:58 stays 15:58).
 */
fun sanitizeScheduleTime(epochSec: Long): Long {
    val minute = 60L
    val floored = (epochSec / minute) * minute
    val nowSec = System.currentTimeMillis() / 1000
    return if (floored <= nowSec) ((nowSec / minute) * minute) + minute else floored
}
