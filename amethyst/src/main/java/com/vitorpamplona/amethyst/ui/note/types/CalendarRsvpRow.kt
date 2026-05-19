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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip52Calendar.appt.tags.RSVPStatusTag
import com.vitorpamplona.quartz.nip52Calendar.rsvp.CalendarRSVPEvent

/**
 * Renders a 3-button RSVP row (Going / Maybe / Can't go) below a NIP-52 calendar event.
 * Tapping a button publishes a new kind 31925 with a random `d` tag — multiple taps create
 * multiple RSVPs, which is consistent with how the NIP describes "responses".
 */
@Composable
fun CalendarRsvpRow(
    eventKind: Int,
    eventPubKey: String,
    eventDTag: String,
    eventId: String,
    accountViewModel: AccountViewModel,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 10.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilledTonalButton(
            onClick = { sendRsvp(accountViewModel, eventKind, eventPubKey, eventDTag, eventId, RSVPStatusTag.STATUS.ACCEPTED) },
            modifier = Modifier.weight(1f),
            colors =
                ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
        ) {
            Text(text = stringRes(R.string.calendar_rsvp_going))
        }
        OutlinedButton(
            onClick = { sendRsvp(accountViewModel, eventKind, eventPubKey, eventDTag, eventId, RSVPStatusTag.STATUS.TENTATIVE) },
            modifier = Modifier.weight(1f),
        ) {
            Text(text = stringRes(R.string.calendar_rsvp_maybe))
        }
        OutlinedButton(
            onClick = { sendRsvp(accountViewModel, eventKind, eventPubKey, eventDTag, eventId, RSVPStatusTag.STATUS.DECLINED) },
            modifier = Modifier.weight(1f),
        ) {
            Text(text = stringRes(R.string.calendar_rsvp_not_going))
        }
    }
}

private fun sendRsvp(
    accountViewModel: AccountViewModel,
    eventKind: Int,
    eventPubKey: String,
    eventDTag: String,
    eventId: String,
    status: RSVPStatusTag.STATUS,
) {
    val noteRelays = LocalCache.getNoteIfExists(eventId)?.relays?.firstOrNull()
    val aTag = ATag(eventKind, eventPubKey, eventDTag, noteRelays)
    val pTag = PTag(eventPubKey)

    accountViewModel.launchSigner {
        accountViewModel.account.signAndComputeBroadcast(
            CalendarRSVPEvent.build(
                calendarEventAddress = aTag,
                status = status,
                calendarEventAuthor = pTag,
            ),
        )
    }
}
