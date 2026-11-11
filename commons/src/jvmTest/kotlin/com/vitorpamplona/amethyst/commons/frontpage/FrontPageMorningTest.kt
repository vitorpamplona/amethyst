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
package com.vitorpamplona.amethyst.commons.frontpage

import com.vitorpamplona.amethyst.commons.model.nipFBScheduledTasks.ScheduleEdit
import com.vitorpamplona.amethyst.commons.model.nipFBScheduledTasks.SyncedSchedule
import com.vitorpamplona.amethyst.commons.model.nipFBScheduledTasks.planScheduleEdit
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip5aStaticWebsites.observer.ObserverSchedule
import com.vitorpamplona.quartz.nipFBScheduledTasks.ScheduledTask
import com.vitorpamplona.quartz.nipFBScheduledTasks.ScheduledTaskClaims
import com.vitorpamplona.quartz.nipFBScheduledTasks.ScheduledTaskEvent
import com.vitorpamplona.quartz.nipFBScheduledTasks.tags.TaskTag
import kotlinx.coroutines.test.runTest
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FrontPageMorningTest {
    private val now = 1_794_394_800L // 2026-11-11T11:00:00Z, 06:00 in New York

    private val task = ObserverSchedule.new("America/New_York").withEnabled(true, 0).withCron("0 6 * * *", "America/New_York", 0)

    private val on = FrontPageSchedule.daily(true, 7, 30)
    private val off = FrontPageSchedule()

    private suspend fun readable(task: ScheduledTask): SyncedSchedule.Readable {
        val signer = NostrSignerInternal(KeyPair())
        val event: ScheduledTaskEvent = signer.sign(ObserverSchedule.build(task, signer, previous = null, now = now))
        return SyncedSchedule.Readable(event, task)
    }

    @Test
    fun aDevicePrintsOnlyWhereTheReaderTurnedItOn() =
        runTest {
            val synced = readable(task)
            assertEquals(FrontPageSchedule(true, "0 6 * * *", "America/New_York"), scheduleForThisDevice(synced, on, canPrintHere = true))
            // Not turned on here, or can't print alone here: nothing scheduled here.
            assertFalse(scheduleForThisDevice(synced, off, canPrintHere = true)!!.enabled)
            assertFalse(scheduleForThisDevice(synced, on, canPrintHere = false)!!.enabled)
            // Paused for everyone, a task this app doesn't run, a cron it can't read: nobody here.
            assertFalse(scheduleForThisDevice(readable(task.withEnabled(false, 0)), on, true)!!.enabled)
            assertFalse(scheduleForThisDevice(readable(task.replacing(TaskTag::isTag, TaskTag.assemble("digest"))), on, true)!!.enabled)
            assertFalse(scheduleForThisDevice(readable(task.withCron("@daily", "UTC", 0)), on, true)!!.enabled)
        }

    @Test
    fun anOldLocalScheduleStandsInUntilTheSyncedOneIsKnown() =
        runTest {
            assertEquals(on, scheduleForThisDevice(SyncedSchedule.Unknown, on, canPrintHere = true))
            // Nothing known and nothing local: decide nothing, leave the job as it is.
            assertNull(scheduleForThisDevice(SyncedSchedule.Unknown, off, canPrintHere = true))
            // The synced one decides when, once it is here.
            assertEquals("0 6 * * *", scheduleForThisDevice(readable(task), on, true)!!.cron)
            // One that can't be read: nobody prints here.
            assertFalse(scheduleForThisDevice(SyncedSchedule.Unreadable(readable(task).event), on, true)!!.enabled)
        }

    @Test
    fun theViewSaysWhoPrintedLastAndWarns() =
        runTest {
            val printed = ScheduledTaskClaims.finished(ScheduledTaskClaims.claimed(task, now, "desk", "Desktop (Linux)", now), now + 60)
            val view = viewOf(readable(printed), on, canPrintHere = true, now = now + 3_600)
            assertTrue(view.enabled)
            assertTrue(view.printsHere)
            assertEquals("Desktop (Linux)", view.lastBy)
            assertEquals(now + 60, view.lastRan)
            assertNull(view.missedSince)
            // Two days later with no run since: missed, on every device.
            assertEquals(now + 60, viewOf(readable(printed), off, canPrintHere = false, now = now + 2 * 86_400).missedSince)
            // Never ran: no warning.
            assertNull(viewOf(readable(task), off, false, now + 9 * 86_400).missedSince)
            assertFalse(viewOf(SyncedSchedule.Unreadable(readable(task).event), on, true).readable)
        }

    @Test
    fun anEditNeverStartsFromNothingItCouldNotRead() {
        val change: (ScheduledTask?) -> ScheduledTask? = { (it ?: ObserverSchedule.new("UTC")).withEnabled(true, 0) }
        // Not more than half of the write relays answered: nothing, whatever the cache holds.
        assertEquals(null to ScheduleEdit.Unreachable, planScheduleEdit(hasEvent = false, current = null, quorum = false, transform = change))
        assertEquals(null to ScheduleEdit.Unreachable, planScheduleEdit(hasEvent = true, current = task, quorum = false, transform = change))
        // An event that couldn't be read.
        assertEquals(null to ScheduleEdit.Unreadable, planScheduleEdit(hasEvent = true, current = null, quorum = true, transform = change))
        // The relays answered, none has one: the first one is written.
        val (first, saved) = planScheduleEdit(hasEvent = false, current = null, quorum = true, transform = change)
        assertTrue(first!!.enabled)
        assertIs<ScheduleEdit.Saved>(saved)
        // An edit that changes nothing writes nothing.
        assertEquals(null to ScheduleEdit.Unchanged, planScheduleEdit(hasEvent = true, current = task, quorum = true) { it })
        assertEquals(null to ScheduleEdit.Unchanged, planScheduleEdit(hasEvent = true, current = task, quorum = true) { null })
    }

    @Test
    fun theCronIsReadInItsZone() {
        val ny = ZoneId.of("America/New_York")
        val schedule = FrontPageSchedule(true, "0 6 * * 1-5", "America/New_York")
        // Friday 07:00 in New York: the next is Monday 06:00 there; the last was Friday's.
        val friday7 = ZonedDateTime.of(LocalDateTime.parse("2026-11-13T07:00"), ny).toEpochSecond()
        assertEquals(ZonedDateTime.of(LocalDateTime.parse("2026-11-16T06:00"), ny).toEpochSecond(), schedule.nextRun(friday7))
        assertEquals(friday7 - 3_600, schedule.lastRun(friday7))
        // The old local `on|7|30`.
        assertEquals("30 7 * * *", FrontPageSchedule.daily(true, 7, 30).cron)
        assertNull(FrontPageSchedule(true, "@daily").nextRun(friday7))
        // The editor's time comes from the cron's own fields, never from a run a clock change moved.
        assertEquals(6 to 0, FrontPageSchedule(true, "0 6 * * 1-5", null).localTime(friday7))
    }
}
