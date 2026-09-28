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
package com.vitorpamplona.amethyst.ui.note.types

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.nip52Calendar.appointmentView
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.CalendarRsvpCard
import com.vitorpamplona.amethyst.ui.components.MyAsyncImage
import com.vitorpamplona.amethyst.ui.note.LoadAddressableNote
import com.vitorpamplona.amethyst.ui.note.WatchNoteEvent
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.calendars.CalendarAppointmentLines
import com.vitorpamplona.amethyst.ui.screen.loggedIn.calendars.CalendarDateBadge
import com.vitorpamplona.amethyst.ui.screen.loggedIn.calendars.detailRouteFor
import com.vitorpamplona.amethyst.ui.screen.loggedIn.calendars.formatCalendarRange
import com.vitorpamplona.amethyst.ui.screen.loggedIn.calendars.rememberRelativeTimeLabel
import com.vitorpamplona.quartz.nip52Calendar.rsvp.CalendarRSVPEvent

/**
 * Entry for a NIP-52 calendar RSVP: decodes the [Note], makes sure the appointment it answers is
 * in the cache, and renders the shared commons [CalendarRsvpCard] with that appointment merged
 * into its frame — "going" is only interesting next to *what*. Until the appointment loads the
 * card shows its status strip over a "loading" line.
 *
 * [LoadAddressableNote] creates the [com.vitorpamplona.amethyst.commons.model.AddressableNote] in
 * `LocalCache` — a shell with a null event if we have never seen the appointment — and
 * [WatchNoteEvent]'s event-finder subscription then asks relays for it: `filterMissingAddressables`
 * picks up exactly those addressables whose `event == null` and queries the address author's
 * outbox relays plus any stored hints.
 *
 * Both halves matter beyond drawing this card. `EventBroadcaster` routes an RSVP by following its
 * `a` tag into the appointment and reading the participants off it, and every step of that walk
 * is a `LocalCache` lookup. An RSVP seen in a feed for an appointment that was never cached would
 * otherwise leave the cache with no entry to walk, so answering it from here would reach the host
 * (whose pubkey the coordinate carries) but none of the other invitees.
 *
 * Composition-scoped like every other per-note subscription: the row unsubscribes ~30s after it
 * scrolls away or the app backgrounds.
 */
@Composable
fun RenderCalendarRSVPEvent(
    note: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = note.event as? CalendarRSVPEvent ?: return
    val address = remember(event) { event.calendarEventAddress() }

    if (address == null) {
        CalendarRsvpCard(event)
        return
    }

    LoadAddressableNote(address) { appointment ->
        if (appointment == null) {
            CalendarRsvpCard(event)
        } else {
            // WatchNoteEvent subscribes the event finder only while the appointment is missing.
            // Once it is here the card has everything it draws; a standing subscription would
            // keep asking relays for the appointment's reactions and zaps on every RSVP in the feed.
            WatchNoteEvent(
                baseNote = appointment,
                onNoteEventFound = { RsvpWithAppointment(event, appointment, accountViewModel, nav) },
                onBlank = { CalendarRsvpCard(event) },
                accountViewModel = accountViewModel,
            )
        }
    }
}

/**
 * The appointment drawn *inside* the RSVP's frame, under its status strip: cover image full
 * width, then the same date badge and text lines the calendar list uses. No author header — the
 * post already says who answered — and no card of its own, so there is one frame, not two.
 * "In 3 days" moves up into the strip, next to the answer it qualifies.
 */
@Composable
private fun RsvpWithAppointment(
    event: CalendarRSVPEvent,
    appointment: AddressableNote,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val view = appointment.appointmentView()
    if (view == null) {
        CalendarRsvpCard(event)
        return
    }

    val context = LocalContext.current
    val appointmentEvent = appointment.event
    val range = remember(appointmentEvent) { formatCalendarRange(appointment, context) }
    val relative = rememberRelativeTimeLabel(view, appointmentEvent?.id)
    val route = remember(appointment) { detailRouteFor(appointment) }

    CalendarRsvpCard(
        event = event,
        statusDetail = relative,
        onClick = { nav.nav(route) },
    ) {
        val image = view.image
        if (!image.isNullOrBlank()) {
            MyAsyncImage(
                imageUrl = image,
                contentDescription = view.title,
                contentScale = ContentScale.Crop,
                mainImageModifier = CoverImageModifier,
                loadedImageModifier = Modifier,
                accountViewModel = accountViewModel,
                onLoadingBackground = { Box(CoverImageModifier) },
                onError = null,
            )
        }

        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            CalendarDateBadge(view.startSeconds)
            Spacer(modifier = Modifier.size(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                CalendarAppointmentLines(view, range, relative = null)
            }
        }
    }
}

private val CoverImageModifier = Modifier.fillMaxWidth().aspectRatio(2f)
