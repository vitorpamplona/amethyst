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
package com.vitorpamplona.quartz.nip52Calendar

import com.vitorpamplona.quartz.nip01Core.core.firstTagValue
import com.vitorpamplona.quartz.nip52Calendar.appt.tags.DayIndexTag
import com.vitorpamplona.quartz.nip52Calendar.appt.time.CalendarTimeSlotEvent
import com.vitorpamplona.quartz.nip52Calendar.appt.time.dayIndexes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * NIP-52's uppercase `D` day index on kind:31923 — the tag that makes a calendar event
 * discoverable by date. Covers the exclusive-`end` boundary and the case split from the
 * lowercase `d` identifier, which shares the same tag map.
 */
class DayIndexTagTest {
    // 2026-02-14T00:00:00Z — day index 20498.
    private val feb14 = 1771027200L
    private val dayIndexFeb14 = 20498L

    @Test
    fun dayIndexMatchesTheSpecFormula() {
        // The worked example from NIP-52 itself.
        assertEquals(82549L, DayIndexTag.dayIndex(82549L * 86400L))
        assertEquals(dayIndexFeb14, DayIndexTag.dayIndex(feb14))
        // Any instant inside the day maps to the same index.
        assertEquals(dayIndexFeb14, DayIndexTag.dayIndex(feb14 + 86399))
    }

    @Test
    fun noEndMeansASingleDay() {
        assertEquals(listOf(dayIndexFeb14), DayIndexTag.dayIndexes(feb14 + 3600))
    }

    @Test
    fun endBeforeOrAtStartStillEmitsTheStartDay() {
        assertEquals(listOf(dayIndexFeb14), DayIndexTag.dayIndexes(feb14, feb14))
        assertEquals(listOf(dayIndexFeb14), DayIndexTag.dayIndexes(feb14 + 100, feb14))
    }

    @Test
    fun aMultiDayRangeCoversEveryDayItTouches() {
        // 14th 10:00 → 16th 15:00 spans three days.
        val days = DayIndexTag.dayIndexes(feb14 + 10 * 3600, feb14 + 2 * 86400 + 15 * 3600)
        assertEquals(listOf(dayIndexFeb14, dayIndexFeb14 + 1, dayIndexFeb14 + 2), days)
    }

    @Test
    fun endIsExclusiveSoMidnightDoesNotClaimTheNextDay() {
        // Ends exactly at the 15th 00:00:00 — that instant belongs to the 15th, but the event
        // does not, so only the 14th is tagged.
        assertEquals(listOf(dayIndexFeb14), DayIndexTag.dayIndexes(feb14, feb14 + 86400))
        // One second later it genuinely runs into the 15th.
        assertEquals(
            listOf(dayIndexFeb14, dayIndexFeb14 + 1),
            DayIndexTag.dayIndexes(feb14, feb14 + 86401),
        )
    }

    @Test
    fun anAbsurdRangeIsCappedRatherThanEmittingThousandsOfTags() {
        val days = DayIndexTag.dayIndexes(feb14, feb14 + 4000L * 86400L)
        assertEquals(DayIndexTag.MAX_DAYS, days.size)
        assertEquals(dayIndexFeb14, days.first())
    }

    @Test
    fun buildEmitsTheDayTagsAndKeepsTheIdentifierSeparate() {
        val template =
            CalendarTimeSlotEvent.build(
                title = "Nostrautica",
                start = feb14 + 10 * 3600,
                end = feb14 + 86400 + 2 * 3600,
                dTag = "my-event",
            )

        val dayTags = template.tags.filter { it[0] == DayIndexTag.TAG_NAME }.map { it[1] }
        assertEquals(listOf(dayIndexFeb14.toString(), (dayIndexFeb14 + 1).toString()), dayTags)

        // The uppercase D must not disturb the lowercase d identifier — they collide in the
        // builder's tag map if either side is ever case-folded.
        assertEquals("my-event", template.tags.firstTagValue("d"))
    }

    @Test
    fun parseReadsBackWhatBuildWrote() {
        val template = CalendarTimeSlotEvent.build(title = "T", start = feb14, end = feb14 + 86401)
        val event = CalendarTimeSlotEvent("id", "pub", 0L, template.tags, template.content, "sig")

        assertEquals(listOf(dayIndexFeb14, dayIndexFeb14 + 1), event.dayIndexes())
    }

    @Test
    fun rebuildingAfterAnEditDropsDaysTheEventNoLongerCovers() {
        // A three-day event shortened to one: the two dropped days must not linger, or they keep
        // advertising the event on dates it no longer runs.
        val shortened =
            CalendarTimeSlotEvent.build(title = "T", start = feb14, end = feb14 + 3 * 86400, dTag = "same") {
                dayIndexes(feb14, feb14 + 3600)
            }

        val dayTags = shortened.tags.filter { it[0] == DayIndexTag.TAG_NAME }.map { it[1] }
        assertEquals(listOf(dayIndexFeb14.toString()), dayTags)
    }

    @Test
    fun parseRejectsNonDayTagsAndGarbage() {
        assertEquals(null, DayIndexTag.parse(arrayOf("d", "1234")))
        assertEquals(null, DayIndexTag.parse(arrayOf("D")))
        assertEquals(null, DayIndexTag.parse(arrayOf("D", "not-a-number")))
        assertTrue(DayIndexTag.parse(arrayOf("D", "82549")) == 82549L)
    }
}
