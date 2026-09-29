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

import com.vitorpamplona.quartz.nip01Core.links.Link
import com.vitorpamplona.quartz.nip01Core.links.LinkTarget
import com.vitorpamplona.quartz.nip01Core.links.Relation
import com.vitorpamplona.quartz.nip52Calendar.appt.day.CalendarDateSlotEvent
import com.vitorpamplona.quartz.nip52Calendar.appt.time.CalendarTimeSlotEvent
import com.vitorpamplona.quartz.nip52Calendar.calendar.CalendarCollectionEvent
import com.vitorpamplona.quartz.nip52Calendar.rsvp.CalendarRSVPEvent
import kotlin.test.Test
import kotlin.test.assertEquals

class Nip52CalendarLinksTest {
    private val id = "0".repeat(64)
    private val author = "1".repeat(64)
    private val sig = "0".repeat(128)
    private val host = "a".repeat(64)
    private val guest = "b".repeat(64)
    private val slot = "31922:$host:meetup"

    @Test
    fun slotsLinkParticipantsWithRolesAndTheCalendarsTheyAskToJoin() {
        val calendar = "31924:$guest:events"
        val tags =
            arrayOf(
                arrayOf("d", "meetup"),
                arrayOf("p", host, "", "host"),
                arrayOf("p", guest),
                arrayOf("a", calendar),
                arrayOf("t", "Meetup"),
                arrayOf("g", "u4pruy"),
                arrayOf("r", "https://example.com"),
            )
        val expected =
            listOf(
                Link(Relation.PARTICIPANT, LinkTarget.User(host), "p", mapOf("role" to "host")),
                Link(Relation.PARTICIPANT, LinkTarget.User(guest), "p"),
                Link(Relation.CALENDAR, LinkTarget.Address(calendar), "a"),
                Link(Relation.HASHTAG, LinkTarget.Tag("t", "meetup"), "t"),
                Link(Relation.TAG, LinkTarget.Tag("g", "u4pruy"), "g"),
                Link(Relation.TAG, LinkTarget.Tag("r", "https://example.com"), "r"),
            )
        assertEquals(expected, CalendarDateSlotEvent(id, host, 1, tags, "", sig).links())
        assertEquals(expected, CalendarTimeSlotEvent(id, host, 1, tags, "", sig).links())
    }

    @Test
    fun calendarHoldsItsSlots() {
        val other = "31923:$host:call"
        val event = CalendarCollectionEvent(id, host, 1, arrayOf(arrayOf("d", "events"), arrayOf("a", slot), arrayOf("a", other)), "", sig)
        assertEquals(
            listOf(
                Link(Relation.MEMBER, LinkTarget.Address(slot), "a"),
                Link(Relation.MEMBER, LinkTarget.Address(other), "a"),
            ),
            event.links(),
        )
    }

    @Test
    fun rsvpCarriesItsStatusOnTheCalendarEvent() {
        val version = "2".repeat(64)
        val event =
            CalendarRSVPEvent(
                id,
                guest,
                1,
                arrayOf(
                    arrayOf("d", "rsvp"),
                    arrayOf("a", slot),
                    arrayOf("e", version),
                    arrayOf("p", host),
                    arrayOf("status", "accepted"),
                    arrayOf("fb", "busy"),
                ),
                "",
                sig,
            )
        val props = mapOf("status" to "accepted", "fb" to "busy")
        assertEquals(
            listOf(
                Link(Relation.CALENDAR_EVENT, LinkTarget.Address(slot), "a", props),
                Link(Relation.CALENDAR_EVENT, LinkTarget.Event(version), "e", props),
                Link(Relation.CALENDAR_EVENT_AUTHOR, LinkTarget.User(host), "p"),
            ),
            event.links(),
        )
    }
}
