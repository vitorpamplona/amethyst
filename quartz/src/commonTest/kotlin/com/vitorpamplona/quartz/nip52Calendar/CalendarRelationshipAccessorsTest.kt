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

import com.vitorpamplona.quartz.nip52Calendar.appt.day.CalendarDateSlotEvent
import com.vitorpamplona.quartz.nip52Calendar.appt.time.CalendarTimeSlotEvent
import com.vitorpamplona.quartz.nip52Calendar.calendar.CalendarCollectionEvent
import com.vitorpamplona.quartz.nip52Calendar.rsvp.CalendarRSVPEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CalendarRelationshipAccessorsTest {
    private val host = "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c"
    private val invitee = "99bb5591c9116600f845107d31f9b59e2f7c7e09a1ff802e84f1d43da557ca64"
    private val apptId = "43575072239da152afe3d7b5c70ed2beb48db2b10e60c60da45229c09c877d2a"
    private val relay = "wss://relay.damus.io/"
    private val id = "00".repeat(32)
    private val sig = "00".repeat(64)

    private val participantTags =
        arrayOf(
            arrayOf("d", "party"),
            arrayOf("p", host, relay, "host"),
            arrayOf("p", "short"),
            arrayOf("p", invitee),
        )

    @Test
    fun slotsExposeParticipantKeys() {
        val time = CalendarTimeSlotEvent(id, host, 1700000000, participantTags, "", sig)
        val date = CalendarDateSlotEvent(id, host, 1700000000, participantTags, "", sig)

        assertEquals(listOf(host, invitee), time.participantKeys())
        assertEquals(time.participants().map { it.pubKey }, time.participantKeys())
        assertEquals(listOf(host, invitee), date.participantKeys())
        assertEquals(time.participantKeys(), time.linkedPubKeys())
        assertEquals(date.participantKeys(), date.linkedPubKeys())
        assertTrue(CalendarTimeSlotEvent(id, host, 1700000000, emptyArray(), "", sig).participantKeys().isEmpty())
    }

    @Test
    fun calendarExposesItsAppointmentAddressIds() {
        val time = "31923:$host:party"
        val day = "31922:$invitee:standup"
        val event =
            CalendarCollectionEvent(
                id,
                host,
                1700000000,
                arrayOf(arrayOf("d", "cal"), arrayOf("a", time, relay), arrayOf("a", "garbage"), arrayOf("a", day)),
                "",
                sig,
            )

        assertEquals(listOf(time, day), event.calendarEventAddressIds())
        assertEquals(event.calendarEventAddresses().map { it.toValue() }, event.calendarEventAddressIds())
        assertEquals(event.calendarEventAddressIds(), event.linkedAddressIds())
    }

    @Test
    fun rsvpLinksItsSingleAppointmentAndRevision() {
        val appt = "31923:$host:party"
        val event =
            CalendarRSVPEvent(
                id,
                invitee,
                1700000000,
                arrayOf(arrayOf("a", appt, relay), arrayOf("e", apptId, relay), arrayOf("status", "accepted")),
                "",
                sig,
            )

        assertEquals(listOf(appt), event.linkedAddressIds())
        assertEquals(listOf(apptId), event.linkedEventIds())
    }
}
