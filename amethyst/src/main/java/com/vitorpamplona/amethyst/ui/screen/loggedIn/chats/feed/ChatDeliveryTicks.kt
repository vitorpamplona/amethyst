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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.Channel
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.service.relayClient.chatDelivery.ChatDelivery
import com.vitorpamplona.amethyst.service.relayClient.chatDelivery.RecipientDelivery
import com.vitorpamplona.amethyst.ui.components.ClickableBox
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.note.UserPicture
import com.vitorpamplona.amethyst.ui.note.UsernameDisplay
import com.vitorpamplona.amethyst.ui.note.timeAbsolute
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.rooms.LoadUser
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.Font12SP
import com.vitorpamplona.amethyst.ui.theme.Size20dp
import com.vitorpamplona.amethyst.ui.theme.StdHorzSpacer
import com.vitorpamplona.amethyst.ui.theme.allGoodColor
import com.vitorpamplona.amethyst.ui.theme.placeholderText
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.displayUrl

/**
 * The timestamp on a chat message, as a single tap target that opens the relay /
 * delivery detail — "where did this message come from" and, for our own messages, how
 * far it got. It's always tappable so the relay list is one tap away on every message.
 *
 * Own messages additionally render the relay-acceptance tick glyph next to the time when
 * we have delivery data:
 * - clock: published, no relay has accepted yet
 * - single check: accepted somewhere (at least one relay OK / seen-on relay)
 * - double check (green): every recipient's / target relay accepted
 *
 * Old messages we didn't track this session simply show no tick, but the time still
 * opens the dialog (which lists the relays it was seen on, if any).
 */
@Composable
fun ChatTimeWithDelivery(
    baseNote: Note,
    isLoggedInUser: Boolean,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val delivery by
        remember(baseNote) { accountViewModel.account.chatDeliveryTracker.deliveryFlow(baseNote.idHex) }
            .collectAsStateWithLifecycle()

    val seenOnState by
        remember(baseNote) { baseNote.flow().relays.stateFlow }
            .collectAsStateWithLifecycle()

    val seenOnRelays = seenOnState.note.relays
    val seenSomewhere = seenOnRelays.isNotEmpty()

    var showDetails by remember { mutableStateOf(false) }

    ClickableBox(onClick = { showDetails = true }) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(vertical = 2.dp),
        ) {
            ChatTimeAgo(baseNote)
            if (isLoggedInUser && (delivery != null || seenSomewhere)) {
                Spacer(StdHorzSpacer)
                RenderDeliveryTicks(delivery, seenSomewhere)
            }
        }
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
 * Opens the relay / delivery detail for [baseNote] from anywhere (e.g. the long-press
 * action sheet), independent of the timestamp affordance — pulls our own delivery
 * tracking when present and always shows the relays the message was seen on.
 */
@Composable
fun ChatMessageDeliveryDialog(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
    onDismiss: () -> Unit,
) {
    val delivery by
        remember(baseNote) { accountViewModel.account.chatDeliveryTracker.deliveryFlow(baseNote.idHex) }
            .collectAsStateWithLifecycle()

    val seenOnState by
        remember(baseNote) { baseNote.flow().relays.stateFlow }
            .collectAsStateWithLifecycle()

    ChatDeliveryDetailDialog(
        baseNote = baseNote,
        delivery = delivery,
        seenOnRelays = seenOnState.note.relays,
        onDismiss = onDismiss,
        accountViewModel = accountViewModel,
        nav = nav,
    )
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
                // Absolute timestamp header (the chat time no longer toggles to absolute
                // on tap — it opens this dialog instead).
                val context = LocalContext.current
                Text(
                    text = timeAbsolute(baseNote.createdAt(), context, prefix = "").trim(),
                    color = MaterialTheme.colorScheme.placeholderText,
                    fontSize = Font12SP,
                )

                // Encrypted-channel messages (Concord/Armada) are decrypted locally with no
                // per-relay attribution, so seen-on is empty — fall back to the channel's own
                // relays as "where this message lives".
                val channelRelays =
                    remember(baseNote) {
                        baseNote.inGatherers?.firstNotNullOfOrNull { (it as? Channel)?.relays()?.takeIf { r -> r.isNotEmpty() } } ?: emptySet()
                    }

                val recipients = delivery?.recipients
                when {
                    !recipients.isNullOrEmpty() ->
                        recipients.forEach { recipient ->
                            RecipientDeliveryRow(recipient, accountViewModel, nav)
                        }

                    delivery != null && delivery.targetRelays.isNotEmpty() ->
                        delivery.targetRelays.sortedBy { it.url }.forEach { relay ->
                            RelayDeliveryRow(
                                relay = relay,
                                accepted = relay in delivery.acceptedRelays || relay in seenOnRelays,
                            )
                        }

                    seenOnRelays.isNotEmpty() ->
                        // Untracked (sent before a restart): only the seen-on set is known.
                        seenOnRelays.sortedBy { it.url }.forEach { relay ->
                            RelayDeliveryRow(relay = relay, accepted = true)
                        }

                    channelRelays.isNotEmpty() ->
                        channelRelays.sortedBy { it.url }.forEach { relay ->
                            RelayDeliveryRow(relay = relay, accepted = true)
                        }

                    else ->
                        // Nothing to list: our own just-sent message no relay has acknowledged
                        // yet (pending), or a received/old message with no recorded relays.
                        Text(
                            text =
                                stringRes(
                                    if (delivery != null) {
                                        R.string.chat_delivery_pending
                                    } else {
                                        R.string.chat_delivery_no_relay_info
                                    },
                                ),
                            color = MaterialTheme.colorScheme.placeholderText,
                        )
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

        DeliveryStatusTick(recipient.isDelivered)
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

        DeliveryStatusTick(accepted)
    }
}

@Composable
private fun DeliveryStatusTick(delivered: Boolean) {
    if (delivered) {
        TickIcon(MaterialSymbols.Done, R.string.chat_delivery_accepted, MaterialTheme.colorScheme.allGoodColor)
    } else {
        TickIcon(MaterialSymbols.Schedule, R.string.chat_delivery_pending, MaterialTheme.colorScheme.placeholderText)
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

    // "Delivered to everyone" and the k/n count describe the OTHER participants;
    // the sender's own self-copy wrap only shows in the detail dialog.
    val others = delivery.otherRecipients
    if (others != null && others.size > 1) {
        // Group DM: double check once everyone got it, plus a delivered count.
        val deliveredCount = others.count { it.isDelivered }
        Row(verticalAlignment = Alignment.CenterVertically) {
            DeliveryLadderTick(
                pending = deliveredCount == 0 && !seenSomewhere,
                fullyAccepted = delivery.isFullyAccepted,
            )
            Text(
                text = "$deliveredCount/${others.size}",
                fontSize = Font12SP,
                color = if (delivery.isFullyAccepted) deliveredColor else pendingColor,
                maxLines = 1,
            )
        }
        return
    }

    // 1:1 DMs and public rooms share the classic tick ladder.
    val acceptedSomewhere = seenSomewhere || delivery.acceptedRelays.isNotEmpty()
    DeliveryLadderTick(
        pending = !acceptedSomewhere,
        fullyAccepted = delivery.isFullyAccepted,
    )
}

/** The shared pending -> accepted-somewhere -> fully-accepted tick selection. */
@Composable
private fun DeliveryLadderTick(
    pending: Boolean,
    fullyAccepted: Boolean,
) {
    when {
        pending ->
            TickIcon(MaterialSymbols.Schedule, R.string.chat_delivery_pending, MaterialTheme.colorScheme.placeholderText)

        fullyAccepted ->
            TickIcon(MaterialSymbols.DoneAll, R.string.chat_delivery_delivered_all, MaterialTheme.colorScheme.allGoodColor)

        else ->
            TickIcon(MaterialSymbols.Done, R.string.chat_delivery_accepted, MaterialTheme.colorScheme.placeholderText)
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
