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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.feed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.service.relayClient.chatDelivery.ChatDelivery
import com.vitorpamplona.amethyst.service.relayClient.chatDelivery.RecipientDelivery
import com.vitorpamplona.amethyst.ui.components.ClickableBox
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.note.UserPicture
import com.vitorpamplona.amethyst.ui.note.UsernameDisplay
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.rooms.LoadUser
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.Font12SP
import com.vitorpamplona.amethyst.ui.theme.Size20dp
import com.vitorpamplona.amethyst.ui.theme.allGoodColor
import com.vitorpamplona.amethyst.ui.theme.placeholderText
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.displayUrl

/**
 * Relay-acceptance ticks for the logged-in user's own chat messages, rendered next
 * to the timestamp:
 *
 * - clock: published, no relay has accepted yet
 * - single check: accepted somewhere (at least one relay OK / seen-on relay)
 * - double check (green): DMs — every participant's gift wrap was accepted by at
 *   least one of that participant's relays; rooms — every target room relay accepted.
 *
 * For DM group rooms a `k/n` participant count accompanies the ticks. Messages sent
 * before an app restart have no tracker entry and fall back to the note's seen-on
 * relays (single check when present).
 */
@Composable
fun ChatDeliveryTicks(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val tracker = accountViewModel.account.chatDeliveryTracker

    val delivery by
        remember(baseNote) { tracker.deliveryFlow(baseNote.idHex) }
            .collectAsStateWithLifecycle(tracker.currentFor(baseNote.idHex))

    val seenOnState by
        remember(baseNote) { baseNote.flow().relays.stateFlow }
            .collectAsStateWithLifecycle()

    val seenOnRelays = seenOnState.note.relays
    val seenSomewhere = seenOnRelays.isNotEmpty()

    var showDetails by remember { mutableStateOf(false) }

    ClickableBox(onClick = { showDetails = true }) {
        RenderDeliveryTicks(delivery, seenSomewhere)
    }

    if (showDetails) {
        ChatDeliveryDetailDialog(
            baseNote = baseNote,
            delivery = delivery,
            seenOnRelays = seenOnRelays,
            onDismiss = { showDetails = false },
            accountViewModel = accountViewModel,
            nav = nav,
        )
    }
}

/**
 * Per-recipient (DMs) or per-relay (rooms) acceptance detail behind the tick,
 * with a re-broadcast escape hatch for messages stuck on pending relays.
 */
@Composable
private fun ChatDeliveryDetailDialog(
    baseNote: Note,
    delivery: ChatDelivery?,
    seenOnRelays: List<NormalizedRelayUrl>,
    onDismiss: () -> Unit,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringRes(R.string.chat_delivery_details_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val recipients = delivery?.recipients
                when {
                    recipients != null ->
                        recipients.forEach { recipient ->
                            RecipientDeliveryRow(recipient, accountViewModel, nav)
                        }

                    delivery != null ->
                        delivery.targetRelays.sortedBy { it.url }.forEach { relay ->
                            RelayDeliveryRow(
                                relay = relay,
                                accepted = relay in delivery.acceptedRelays || relay in seenOnRelays,
                            )
                        }

                    else ->
                        // Untracked (sent before a restart): only the seen-on set is known.
                        seenOnRelays.sortedBy { it.url }.forEach { relay ->
                            RelayDeliveryRow(relay = relay, accepted = true)
                        }
                }
            }
        },
        confirmButton = {
            if (accountViewModel.canBroadcast(baseNote)) {
                TextButton(
                    onClick = {
                        accountViewModel.broadcast(baseNote)
                        onDismiss()
                    },
                ) {
                    Text(stringRes(R.string.broadcast))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringRes(R.string.close))
            }
        },
    )
}

@Composable
private fun RecipientDeliveryRow(
    recipient: RecipientDelivery,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        LoadUser(baseUserHex = recipient.recipient, accountViewModel = accountViewModel) { user ->
            if (user != null) {
                UserPicture(user, Size20dp, Modifier, accountViewModel, nav)
                Row(modifier = Modifier.weight(1f)) {
                    UsernameDisplay(baseUser = user, accountViewModel = accountViewModel)
                }
            } else {
                Text(text = recipient.recipient.take(8), modifier = Modifier.weight(1f), maxLines = 1)
            }
        }

        if (recipient.isDelivered) {
            TickIcon(MaterialSymbols.Done, R.string.chat_delivery_accepted, MaterialTheme.colorScheme.allGoodColor)
        } else {
            TickIcon(MaterialSymbols.Schedule, R.string.chat_delivery_pending, MaterialTheme.colorScheme.placeholderText)
        }
    }
}

@Composable
private fun RelayDeliveryRow(
    relay: NormalizedRelayUrl,
    accepted: Boolean,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = relay.displayUrl(),
            modifier = Modifier.weight(1f),
            maxLines = 1,
        )

        if (accepted) {
            TickIcon(MaterialSymbols.Done, R.string.chat_delivery_accepted, MaterialTheme.colorScheme.allGoodColor)
        } else {
            TickIcon(MaterialSymbols.Schedule, R.string.chat_delivery_pending, MaterialTheme.colorScheme.placeholderText)
        }
    }
}

@Composable
private fun RenderDeliveryTicks(
    delivery: ChatDelivery?,
    seenSomewhere: Boolean,
) {
    val pendingColor = MaterialTheme.colorScheme.placeholderText
    val deliveredColor = MaterialTheme.colorScheme.allGoodColor

    if (delivery == null) {
        // Untracked (sent before a restart): the seen-on relay set is the only signal.
        if (seenSomewhere) {
            TickIcon(MaterialSymbols.Done, R.string.chat_delivery_accepted, pendingColor)
        } else {
            TickIcon(MaterialSymbols.Schedule, R.string.chat_delivery_pending, pendingColor)
        }
        return
    }

    val recipients = delivery.recipients
    if (recipients != null && recipients.size > 2) {
        // Group DM: double check once everyone got it, plus a delivered count.
        val deliveredCount = recipients.count { it.isDelivered }
        Row(verticalAlignment = Alignment.CenterVertically) {
            when {
                deliveredCount == 0 && !seenSomewhere ->
                    TickIcon(MaterialSymbols.Schedule, R.string.chat_delivery_pending, pendingColor)

                delivery.isFullyAccepted ->
                    TickIcon(MaterialSymbols.DoneAll, R.string.chat_delivery_delivered_all, deliveredColor)

                else ->
                    TickIcon(MaterialSymbols.Done, R.string.chat_delivery_accepted, pendingColor)
            }
            Text(
                text = "$deliveredCount/${recipients.size}",
                fontSize = Font12SP,
                color = if (delivery.isFullyAccepted) deliveredColor else pendingColor,
                maxLines = 1,
            )
        }
        return
    }

    // 1:1 DMs and public rooms share the classic tick ladder.
    val acceptedSomewhere = seenSomewhere || delivery.acceptedRelays.isNotEmpty()
    when {
        !acceptedSomewhere ->
            TickIcon(MaterialSymbols.Schedule, R.string.chat_delivery_pending, pendingColor)

        delivery.isFullyAccepted ->
            TickIcon(MaterialSymbols.DoneAll, R.string.chat_delivery_delivered_all, deliveredColor)

        else ->
            TickIcon(MaterialSymbols.Done, R.string.chat_delivery_accepted, pendingColor)
    }
}

@Composable
private fun TickIcon(
    symbol: MaterialSymbol,
    contentDescription: Int,
    tint: Color,
) {
    Icon(
        symbol = symbol,
        contentDescription = stringRes(contentDescription),
        modifier = Modifier.size(14.dp),
        tint = tint,
    )
}
