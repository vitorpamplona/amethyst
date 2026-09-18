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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.ui.note.CalendarRsvpCard
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.event.EventFinderFilterAssemblerSubscription
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.note.LoadAddressableNote
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.quartz.nip52Calendar.rsvp.CalendarRSVPEvent

/**
 * Entry for a NIP-52 calendar RSVP: decodes the [Note], makes sure the appointment it answers is
 * in the cache, and renders the shared commons [CalendarRsvpCard].
 */
@Composable
fun RenderCalendarRSVPEvent(
    note: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = note.event as? CalendarRSVPEvent ?: return

    LoadAppointmentBehind(event, accountViewModel)

    CalendarRsvpCard(event)
}

/**
 * Resolves the appointment this RSVP answers, from the `a` tag that is the only thing tying the
 * two together.
 *
 * [LoadAddressableNote] creates the [com.vitorpamplona.amethyst.commons.model.AddressableNote] in
 * `LocalCache` — a shell with a null event if we have never seen the appointment — and
 * [EventFinderFilterAssemblerSubscription] then asks relays for it: `filterMissingAddressables`
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
private fun LoadAppointmentBehind(
    event: CalendarRSVPEvent,
    accountViewModel: AccountViewModel,
) {
    val address = remember(event) { event.calendarEventAddress() } ?: return

    LoadAddressableNote(address, accountViewModel) { appointment ->
        if (appointment != null) {
            EventFinderFilterAssemblerSubscription(appointment, accountViewModel)
        }
    }
}
