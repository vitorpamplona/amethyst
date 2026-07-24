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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.calendar_rsvp_going
import com.vitorpamplona.amethyst.commons.resources.calendar_rsvp_maybe
import com.vitorpamplona.amethyst.commons.resources.calendar_rsvp_not_going
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip52Calendar.appt.tags.RSVPStatusTag
import com.vitorpamplona.quartz.nip52Calendar.rsvp.CalendarRSVPEvent
import org.jetbrains.compose.resources.stringResource

/**
 * Renders a 3-button RSVP row (Going / Maybe / Can't go) below a NIP-52 calendar appointment.
 *
 * Uses a deterministic d-tag derived from the target appointment's address so the user has
 * exactly one RSVP per event from this client — tapping again replaces it rather than appending
 * another addressable. The button matching the current status renders as filled-tonal; the
 * others render outlined.
 */
@Composable
fun CalendarRsvpRow(
    eventKind: Int,
    eventPubKey: String,
    eventDTag: String,
    eventId: String,
    accountViewModel: AccountViewModel,
) {
    val myPubKey = accountViewModel.userProfile().pubkeyHex
    val targetAddress = remember(eventKind, eventPubKey, eventDTag) { Address(eventKind, eventPubKey, eventDTag) }
    val myRsvpAddress = remember(targetAddress, myPubKey) { rsvpAddressFor(myPubKey, targetAddress) }

    val myRsvpNote = remember(myRsvpAddress) { LocalCache.getOrCreateAddressableNote(myRsvpAddress) }
    val myRsvpState by myRsvpNote
        .flow()
        .metadata.stateFlow
        .collectAsStateWithLifecycle()

    val currentStatus = (myRsvpState.note.event as? CalendarRSVPEvent)?.status()

    val onTap: (RSVPStatusTag.STATUS) -> Unit = { newStatus ->
        sendRsvp(
            accountViewModel = accountViewModel,
            targetAddress = targetAddress,
            eventId = eventId,
            myPubKey = myPubKey,
            status = newStatus,
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 10.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        RsvpButton(
            label = stringResource(Res.string.calendar_rsvp_going),
            status = RSVPStatusTag.STATUS.ACCEPTED,
            currentStatus = currentStatus,
            modifier = Modifier.weight(1f),
            onClick = onTap,
        )
        RsvpButton(
            label = stringResource(Res.string.calendar_rsvp_maybe),
            status = RSVPStatusTag.STATUS.TENTATIVE,
            currentStatus = currentStatus,
            modifier = Modifier.weight(1f),
            onClick = onTap,
        )
        RsvpButton(
            label = stringResource(Res.string.calendar_rsvp_not_going),
            status = RSVPStatusTag.STATUS.DECLINED,
            currentStatus = currentStatus,
            modifier = Modifier.weight(1f),
            onClick = onTap,
        )
    }
}

@Composable
private fun RsvpButton(
    label: String,
    status: RSVPStatusTag.STATUS,
    currentStatus: RSVPStatusTag.STATUS?,
    modifier: Modifier,
    onClick: (RSVPStatusTag.STATUS) -> Unit,
) {
    val selected = status == currentStatus
    if (selected) {
        FilledTonalButton(
            onClick = { onClick(status) },
            modifier = modifier,
            contentPadding = RsvpButtonPadding,
            colors =
                ButtonDefaults.filledTonalButtonColors(
                    containerColor = colorFor(status),
                    contentColor = Color.White,
                ),
        ) {
            RsvpButtonLabel(label)
        }
    } else {
        OutlinedButton(
            onClick = { onClick(status) },
            modifier = modifier,
            contentPadding = RsvpButtonPadding,
        ) {
            RsvpButtonLabel(label)
        }
    }
}

private val RsvpButtonPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)

@Composable
private fun RsvpButtonLabel(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun colorFor(status: RSVPStatusTag.STATUS) =
    when (status) {
        RSVPStatusTag.STATUS.ACCEPTED -> MaterialTheme.colorScheme.primary
        RSVPStatusTag.STATUS.TENTATIVE -> MaterialTheme.colorScheme.tertiary
        RSVPStatusTag.STATUS.DECLINED -> MaterialTheme.colorScheme.error
    }

/**
 * Deterministic per-target d-tag so each user's RSVP for a given event is a single addressable.
 * The format mirrors the a-tag coordinate so it's debuggable (`rsvp:31923:<pubkey>:<dtag>`).
 */
fun rsvpDTagFor(targetAddress: Address): String = "rsvp:${targetAddress.kind}:${targetAddress.pubKeyHex}:${targetAddress.dTag}"

fun rsvpAddressFor(
    myPubKey: String,
    targetAddress: Address,
): Address = Address(CalendarRSVPEvent.KIND, myPubKey, rsvpDTagFor(targetAddress))

private fun sendRsvp(
    accountViewModel: AccountViewModel,
    targetAddress: Address,
    eventId: String,
    myPubKey: String,
    status: RSVPStatusTag.STATUS,
) {
    val relayHint = LocalCache.getNoteIfExists(eventId)?.relays?.firstOrNull()
    val aTag = ATag(targetAddress, relayHint)
    val pTag = PTag(targetAddress.pubKeyHex)
    val dTag = rsvpDTagFor(targetAddress)

    accountViewModel.launchSigner {
        accountViewModel.account.signAndComputeBroadcast(
            CalendarRSVPEvent.build(
                calendarEventAddress = aTag,
                status = status,
                calendarEventAuthor = pTag,
                dTag = dTag,
            ),
        )
    }
}
