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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.notifications

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.model.buzz.BuzzChannelInvite
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.user.observeUserName
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.displayUrl
import com.vitorpamplona.quartz.nip29RelayGroups.GroupId

/**
 * "Somebody added you to a channel" prompts, rendered above the Notifications feed (the same header slot
 * the missing-inbox-relay prompt uses) and inside Messages › New Requests.
 *
 * These are deliberately NOT auto-accepted. On a Buzz relay another member can add you to a channel
 * server-side: the relay writes you into the kind-39002 roster and you can immediately read and post,
 * without you ever agreeing to see it. Amethyst used to silently subscribe to those channels' messages
 * while showing no row for them anywhere, so a channel could be joined, streaming, and invisible at once.
 * Now the relay's decision is surfaced as a question instead of being acted on.
 */
@Composable
fun ChannelInvitesSection(
    accountViewModel: AccountViewModel,
    modifier: Modifier = Modifier,
) {
    val invites by accountViewModel.feedStates.channelInvites.flow
        .collectAsStateWithLifecycle()

    if (invites.isEmpty()) return

    Column(modifier) {
        invites.forEach { invite ->
            ChannelInviteCard(invite, accountViewModel)
        }
    }
}

@Composable
fun ChannelInviteCard(
    invite: BuzzChannelInvite,
    accountViewModel: AccountViewModel,
) {
    val channel = remember(invite.channelId, invite.relay) { LocalCache.getOrCreateRelayGroupChannel(GroupId(invite.channelId, invite.relay)) }

    val actorUser = remember(invite.actor) { invite.actor?.let { LocalCache.getOrCreateUser(it) } }
    val actorName = actorUser?.let { observeUserName(it, accountViewModel).value }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                text = stringRes(R.string.channel_invite_title, channel.toBestDisplayName()),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                // Who did it matters: the relay reports a self-join with the same event, so naming the
                // actor is what tells "I joined this" apart from "a stranger put me here".
                text =
                    stringRes(
                        R.string.channel_invite_body,
                        actorName ?: stringRes(R.string.channel_invite_unknown_actor),
                        invite.relay.displayUrl(),
                    ),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp),
            )

            Row(
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            ) {
                // Leave is separate from Ignore on purpose: Ignore is a local display choice that leaves
                // you in the roster, Leave is the kind-9022 that actually removes you from the channel.
                TextButton(onClick = { accountViewModel.leaveChannelInvite(channel) }) {
                    Text(stringRes(R.string.channel_invite_leave), color = MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = { accountViewModel.dismissChannelInvite(invite.channelId) }) {
                    Text(stringRes(R.string.channel_invite_ignore))
                }
                TextButton(onClick = { accountViewModel.acceptChannelInvite(channel) }) {
                    Text(stringRes(R.string.channel_invite_accept), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
