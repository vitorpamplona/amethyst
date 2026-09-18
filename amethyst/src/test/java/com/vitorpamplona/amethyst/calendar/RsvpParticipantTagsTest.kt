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

import com.vitorpamplona.amethyst.ui.note.types.rsvpParticipantTags
import com.vitorpamplona.quartz.nip52Calendar.appt.day.CalendarDateSlotEvent
import com.vitorpamplona.quartz.nip52Calendar.appt.time.CalendarTimeSlotEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The RSVP's `p` tags are what the broadcaster turns into inbox relays, so this list decides who
 * actually receives the RSVP beyond the host. See EventBroadcaster.computeRelayListToBroadcast.
 */
class RsvpParticipantTagsTest {
    private val host = "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c"
    private val invitee = "99bb5591c9116600f845107d31f9b59e2f7c7e09a1ff802e84f1d43da557ca64"
    private val speaker = "3bf0c63fcb93463407af97a5e5ee64fa883d107ef9e558472c4eb9aaaefa459d"
    private val me = "82341f882b6eabcd2ba7f1ef90aad961cf074af15b9ef44a09f9d2a8fbfbe6a2"

    private fun timeSlot(vararg tags: Array<String>) =
        CalendarTimeSlotEvent(
            id = "11".repeat(32),
            pubKey = host,
            createdAt = 1700000000,
            tags = arrayOf(*tags),
            content = "",
            sig = "00".repeat(64),
        )

    private fun dateSlot(vararg tags: Array<String>) =
        CalendarDateSlotEvent(
            id = "22".repeat(32),
            pubKey = host,
            createdAt = 1700000000,
            tags = arrayOf(*tags),
            content = "",
            sig = "00".repeat(64),
        )

    @Test
    fun collectsInviteesFromATimeSlot() {
        val appt = timeSlot(arrayOf("d", "party"), arrayOf("p", invitee), arrayOf("p", speaker))

        assertEquals(
            listOf(invitee, speaker),
            rsvpParticipantTags(appt, host, me).map { it.pubKey },
        )
    }

    @Test
    fun collectsInviteesFromADateSlot() {
        val appt = dateSlot(arrayOf("d", "party"), arrayOf("p", invitee))

        assertEquals(listOf(invitee), rsvpParticipantTags(appt, host, me).map { it.pubKey })
    }

    @Test
    fun keepsTheRelayHintOnEachParticipant() {
        val appt = timeSlot(arrayOf("d", "party"), arrayOf("p", invitee, "wss://relay.damus.io/", "speaker"))

        val tag = rsvpParticipantTags(appt, host, me).single()
        assertEquals(invitee, tag.pubKey)
        assertEquals("wss://relay.damus.io/", tag.relayHint?.url)
    }

    @Test
    fun dropsTheHostSinceBuildAlreadyTagsThem() {
        // CalendarRSVPEvent.build() writes the host as calendarEventAuthor; re-adding it here
        // would put two `p` tags for the same key on the event and break calendarEventAuthor()
        // for readers that expect the first one to be the host.
        val appt = timeSlot(arrayOf("d", "party"), arrayOf("p", host), arrayOf("p", invitee))

        assertEquals(listOf(invitee), rsvpParticipantTags(appt, host, me).map { it.pubKey })
    }

    @Test
    fun dropsOurselves() {
        val appt = timeSlot(arrayOf("d", "party"), arrayOf("p", me), arrayOf("p", invitee))

        assertEquals(listOf(invitee), rsvpParticipantTags(appt, host, me).map { it.pubKey })
    }

    @Test
    fun dedupesRepeatedParticipants() {
        val appt =
            timeSlot(
                arrayOf("d", "party"),
                arrayOf("p", invitee, "wss://relay.damus.io/"),
                arrayOf("p", invitee, "wss://nos.lol/"),
            )

        assertEquals(listOf(invitee), rsvpParticipantTags(appt, host, me).map { it.pubKey })
    }

    @Test
    fun emptyWhenTheAppointmentIsNotCachedYet() {
        assertTrue(rsvpParticipantTags(null, host, me).isEmpty())
    }
}
