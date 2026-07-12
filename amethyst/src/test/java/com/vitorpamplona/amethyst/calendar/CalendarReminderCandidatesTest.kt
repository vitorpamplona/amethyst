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
package com.vitorpamplona.amethyst.calendar

import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.service.calendar.CalendarReminderWorker
import com.vitorpamplona.quartz.nip52Calendar.appt.time.CalendarTimeSlotEvent
import com.vitorpamplona.quartz.nip52Calendar.rsvp.CalendarRSVPEvent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The predicate that decides whether the CalendarReminderWorker's periodic
 * chain may cancel itself. Getting it wrong in either direction is a bug:
 * a false "could fire" keeps waking the process forever; a false "can't fire"
 * silently kills a reminder the user RSVP'd to.
 */
class CalendarReminderCandidatesTest {
    private val now = 2_000_000_000L

    // Unique per-test-class identities so the shared LocalCache singleton
    // doesn't collide with other suites running in the same JVM.
    private val organizer = "b0b0000000000000000000000000000000000000000000000000000000000001"
    private val attendee = "a11ce00000000000000000000000000000000000000000000000000000000002"

    private var eventSeq = 0

    private fun timeSlot(
        dTag: String,
        startSeconds: Long?,
    ) = CalendarTimeSlotEvent(
        id = "c0ffee%058d".format(++eventSeq),
        pubKey = organizer,
        createdAt = now - 1000,
        tags =
            if (startSeconds != null) {
                arrayOf(arrayOf("d", dTag), arrayOf("start", startSeconds.toString()))
            } else {
                arrayOf(arrayOf("d", dTag))
            },
        content = "",
        sig = "sig",
    )

    private fun rsvp(
        dTag: String,
        targetDTag: String?,
        status: String = "accepted",
    ) = CalendarRSVPEvent(
        id = "faced%059d".format(++eventSeq),
        pubKey = attendee,
        createdAt = now - 900,
        tags =
            buildList {
                add(arrayOf("d", dTag))
                add(arrayOf("status", status))
                if (targetDTag != null) {
                    add(arrayOf("a", "${CalendarTimeSlotEvent.KIND}:$organizer:$targetDTag"))
                }
            }.toTypedArray(),
        content = "",
        sig = "sig",
    )

    @Test
    fun acceptedRsvpsInCache_returnsAcceptedAndSkipsDeclined() {
        val accepted = rsvp("cand-accepted", "cand-target-1")
        val declined = rsvp("cand-declined", "cand-target-2", status = "declined")
        LocalCache.justConsume(accepted, null, true)
        LocalCache.justConsume(declined, null, true)

        val found = CalendarReminderWorker.acceptedRsvpsInCache()
        assertTrue("accepted RSVP must be found", found.any { it.dTag() == "cand-accepted" })
        assertFalse("declined RSVP must be skipped", found.any { it.dTag() == "cand-declined" })
    }

    @Test
    fun futureTarget_couldStillFire() {
        val slot = timeSlot("cand-future", startSeconds = now + 3600)
        val r = rsvp("cand-rsvp-future", "cand-future")
        LocalCache.justConsume(slot, null, true)
        LocalCache.justConsume(r, null, true)

        assertTrue(CalendarReminderWorker.couldStillFire(listOf(r), now))
    }

    @Test
    fun pastTarget_cannotFireAnymore() {
        val slot = timeSlot("cand-past", startSeconds = now - 3600)
        val r = rsvp("cand-rsvp-past", "cand-past")
        LocalCache.justConsume(slot, null, true)
        LocalCache.justConsume(r, null, true)

        assertFalse(CalendarReminderWorker.couldStillFire(listOf(r), now))
    }

    @Test
    fun unresolvedTarget_keepsTheChainAlive() {
        // The RSVP points at an event the cache hasn't fetched yet: the start
        // is unknown, so the worker must NOT cancel its chain.
        val r = rsvp("cand-rsvp-unresolved", "cand-never-fetched")
        LocalCache.justConsume(r, null, true)

        assertTrue(CalendarReminderWorker.couldStillFire(listOf(r), now))
    }

    @Test
    fun targetWithoutStart_keepsTheChainAlive() {
        // Target resolved but carries no start tag: still unknown, keep alive.
        val slot = timeSlot("cand-no-start", startSeconds = null)
        val r = rsvp("cand-rsvp-no-start", "cand-no-start")
        LocalCache.justConsume(slot, null, true)
        LocalCache.justConsume(r, null, true)

        assertTrue(CalendarReminderWorker.couldStillFire(listOf(r), now))
    }

    @Test
    fun rsvpWithoutTargetAddress_doesNotKeepTheChainAlive() {
        val r = rsvp("cand-rsvp-no-a-tag", targetDTag = null)
        LocalCache.justConsume(r, null, true)

        assertFalse(CalendarReminderWorker.couldStillFire(listOf(r), now))
    }

    @Test
    fun emptyCache_doesNotKeepTheChainAlive() {
        assertFalse(CalendarReminderWorker.couldStillFire(emptyList(), now))
    }
}
