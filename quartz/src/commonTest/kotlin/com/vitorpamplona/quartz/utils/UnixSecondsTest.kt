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
package com.vitorpamplona.quartz.utils

import com.vitorpamplona.quartz.nip23LongContent.tags.PublishedAtTag
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.tags.EndsTag
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.tags.StartsTag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import com.vitorpamplona.quartz.experimental.zapPolls.tags.ClosedAtTag as PollClosedAtTag
import com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.tags.StartsTag as RoomStartsTag
import com.vitorpamplona.quartz.nip75ZapGoals.tags.ClosedAtTag as GoalClosedAtTag

/**
 * Timestamp tags written in milliseconds by some clients. Read raw, `closed_at` of
 * 1_780_000_000_000 is the year 58,000: "Ends in 54,000 years".
 */
class UnixSecondsTest {
    private val seconds = 1_780_000_000L
    private val millis = seconds * 1000

    @Test
    fun secondsPassThrough() {
        assertEquals(seconds, TimeUtils.toUnixSeconds(seconds))
        assertEquals(0L, TimeUtils.toUnixSeconds(0L))
    }

    @Test
    fun millisecondsAreScaledDown() {
        assertEquals(seconds, TimeUtils.toUnixSeconds(millis))
        assertEquals(seconds, TimeUtils.toUnixSeconds(millis + 999))
    }

    @Test
    fun goalClosedAtAcceptsMilliseconds() {
        assertEquals(seconds, GoalClosedAtTag.parse(arrayOf("closed_at", millis.toString())))
        assertEquals(seconds, GoalClosedAtTag.parse(arrayOf("closed_at", seconds.toString())))
        assertNull(GoalClosedAtTag.parse(arrayOf("closed_at", "soon")))
    }

    @Test
    fun pollClosedAtAcceptsMilliseconds() {
        assertEquals(seconds, PollClosedAtTag.parse(arrayOf("closed_at", millis.toString())))
    }

    @Test
    fun liveActivityStartsAndEndsAcceptMilliseconds() {
        assertEquals(seconds, StartsTag.parse(arrayOf("starts", millis.toString())))
        assertEquals(seconds, EndsTag.parse(arrayOf("ends", millis.toString())))
        assertEquals(seconds, StartsTag.parse(arrayOf("starts", seconds.toString())))
    }

    @Test
    fun roomStartsAcceptsMilliseconds() {
        assertEquals(seconds, RoomStartsTag.parse(arrayOf("starts", millis.toString())))
    }

    @Test
    fun publishedAtStillAcceptsMilliseconds() {
        assertEquals(seconds, PublishedAtTag.parse(arrayOf("published_at", millis.toString())))
        assertEquals(seconds, PublishedAtTag.parse(arrayOf("published_at", seconds.toString())))
    }
}
