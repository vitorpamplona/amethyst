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
package com.vitorpamplona.amethyst.commons.model.nip52Calendar

import androidx.compose.runtime.Immutable
import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip52Calendar.appt.day.CalendarDateSlotEvent
import com.vitorpamplona.quartz.nip52Calendar.appt.tags.RSVPStatusTag
import com.vitorpamplona.quartz.nip52Calendar.appt.time.CalendarTimeSlotEvent
import com.vitorpamplona.quartz.nip52Calendar.rsvp.CalendarRSVPEvent

/**
 * One appointment and the people (already narrowed to the ones the viewer cares about) whose
 * latest RSVP to it says "going". [attendees] is newest-RSVP first, so the faces a row shows are
 * the people who decided most recently.
 */
@Immutable
class AppointmentAttendance(
    val appointment: AddressableNote,
    val attendees: List<HexKey>,
)

/**
 * [upcoming] is what a "where are my follows going" list draws, soonest first. [unresolved] are
 * the appointments someone RSVP'd to that the cache has no event for yet: whether they are
 * upcoming at all is unknown until they load, so they are handed back for the caller to fetch
 * instead of being drawn as blank rows.
 */
@Immutable
class FollowsGoing(
    val upcoming: List<AppointmentAttendance>,
    val unresolved: List<AddressableNote>,
)

/**
 * Folds a pile of kind-31925 RSVPs into the appointments the accepted authors are going to.
 *
 * - Only an author's **latest** RSVP to a given appointment counts. RSVPs are addressable with a
 *   free-form `d` tag, so a person who said "going" and later "can't go" may have two live events
 *   (two d-tags) or an old version still in the cache; the newest one is their answer.
 * - [isAttendee] runs on that latest RSVP only: the follow-list / mute check belongs to the caller.
 * - An appointment counts as upcoming while it has not ended (an ongoing multi-day conference is
 *   still somewhere your friends are), matching the feed lens's upcoming/past split.
 *
 * [nowSeconds] is a parameter so the split is deterministic under test.
 */
fun computeFollowsGoing(
    rsvps: Collection<CalendarRSVPEvent>,
    isAttendee: (CalendarRSVPEvent) -> Boolean,
    appointmentFor: (Address) -> AddressableNote,
    nowSeconds: Long,
): FollowsGoing {
    // (appointment, author) -> that author's newest answer to it
    val latest = HashMap<Pair<Address, HexKey>, CalendarRSVPEvent>()
    rsvps.forEach { rsvp ->
        val target = rsvp.calendarEventAddress() ?: return@forEach
        if (target.kind != CalendarTimeSlotEvent.KIND && target.kind != CalendarDateSlotEvent.KIND) return@forEach
        val key = target to rsvp.pubKey
        val current = latest[key]
        if (current == null || rsvp.createdAt > current.createdAt) {
            latest[key] = rsvp
        }
    }

    val goingByAppointment = HashMap<Address, MutableList<CalendarRSVPEvent>>()
    latest.forEach { (key, rsvp) ->
        if (rsvp.status() == RSVPStatusTag.STATUS.ACCEPTED && isAttendee(rsvp)) {
            goingByAppointment.getOrPut(key.first) { mutableListOf() }.add(rsvp)
        }
    }

    val upcoming = ArrayList<Pair<Long, AppointmentAttendance>>()
    val unresolved = ArrayList<AddressableNote>()

    goingByAppointment.forEach { (address, going) ->
        val appointment = appointmentFor(address)
        if (appointment.event == null) {
            unresolved.add(appointment)
            return@forEach
        }
        val start = appointment.calendarStartSeconds() ?: return@forEach
        val end = appointment.calendarEndSeconds() ?: start
        if (end < nowSeconds) return@forEach

        val attendees = going.sortedByDescending { it.createdAt }.map { it.pubKey }
        upcoming.add(start to AppointmentAttendance(appointment, attendees))
    }

    upcoming.sortWith(
        compareBy<Pair<Long, AppointmentAttendance>> { it.first }
            .thenByDescending { it.second.attendees.size }
            .thenBy { it.second.appointment.idHex },
    )

    return FollowsGoing(upcoming.map { it.second }, unresolved)
}
