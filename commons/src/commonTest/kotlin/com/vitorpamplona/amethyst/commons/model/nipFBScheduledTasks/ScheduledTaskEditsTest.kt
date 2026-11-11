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
package com.vitorpamplona.amethyst.commons.model.nipFBScheduledTasks

import com.vitorpamplona.quartz.nipFBScheduledTasks.CronExpression
import com.vitorpamplona.quartz.nipFBScheduledTasks.CronZone
import com.vitorpamplona.quartz.nipFBScheduledTasks.ScheduledTask
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ScheduledTaskEditsTest {
    private val daily = CronExpression.parse("0 7 * * *")!!
    private val weekdays = CronExpression.parse("30  6 * *  1-5")!!

    @Test
    fun aNewTaskIsCreatedPrivateUnderItsD() {
        val task = ScheduledTaskEdits.withSchedule(null, "digest", weekdays, zone = null, localZone = "Europe/Lisbon", now = 100)
        assertEquals("digest", task.task)
        assertEquals("30 6 * * 1-5", task.cron)
        assertEquals("Europe/Lisbon", task.tz)
        assertEquals(100, task.cronSetAt)
        assertFalse(task.paused)
        assertEquals(1, task.publicTags.size)

        val named = ScheduledTaskEdits.withSchedule(null, "x1", daily, zone = "UTC", localZone = "Europe/Lisbon", now = 100, taskName = "observer", paused = true)
        assertEquals("observer", named.task)
        assertEquals("UTC", named.tz)
        assertTrue(named.paused)
    }

    @Test
    fun anExistingTaskKeepsItsZoneAndEveryOtherTag() {
        val current =
            ScheduledTask(
                arrayOf(arrayOf("d", "observer"), arrayOf("public", "1")),
                arrayOf(arrayOf("task", "observer"), arrayOf("cron", "0 7 * * *", "America/New_York", "50"), arrayOf("ran", "60"), arrayOf("length", "short")),
            )
        val next = ScheduledTaskEdits.withSchedule(current, "observer", weekdays, zone = null, localZone = "Europe/Lisbon", now = 100)
        assertEquals("30 6 * * 1-5", next.cron)
        assertEquals("America/New_York", next.tz)
        assertEquals(100, next.cronSetAt)
        assertEquals(60, next.ran)
        assertTrue(next.privateTags.any { it.contentEquals(arrayOf("length", "short")) })
        assertTrue(next.publicTags.any { it.contentEquals(arrayOf("public", "1")) })

        // The same schedule changes nothing, so nothing is written.
        assertSame(current, ScheduledTaskEdits.withSchedule(current, "observer", daily, zone = "America/New_York", localZone = "UTC", now = 100))
        // Another zone is a new schedule.
        assertEquals("UTC", ScheduledTaskEdits.withSchedule(current, "observer", daily, zone = "UTC", localZone = "UTC", now = 100).tz)
    }

    @Test
    fun theNextRunIsInTheTasksZoneAndNeverWhilePaused() {
        // 2026-11-11 00:00 UTC.
        val midnight = 1_794_355_200L
        val task = ScheduledTask.create("observer", "observer", "0 7 * * *", "UTC", setAt = 0)
        assertEquals(midnight + 7 * 3_600, ScheduledTaskEdits.nextRun(task, CronZone.UTC, midnight))
        assertEquals(midnight + 2 * 3_600, ScheduledTaskEdits.nextRun(task, CronZone.fixed(5 * 3_600), midnight))
        assertNull(ScheduledTaskEdits.nextRun(task.withEnabled(false, 0), CronZone.UTC, midnight))
        assertNull(ScheduledTaskEdits.nextRun(task, null, midnight))
        assertNull(ScheduledTaskEdits.nextRun(task.withCron("61 * * * *", "UTC", 0), CronZone.UTC, midnight))
    }
}
