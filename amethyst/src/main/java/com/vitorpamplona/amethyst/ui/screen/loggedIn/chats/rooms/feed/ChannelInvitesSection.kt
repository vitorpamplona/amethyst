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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.rooms.feed

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.model.buzz.BuzzChannelInvite
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.feed.types.observeUserNameByHex
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.rooms.RelayGroupRow
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.DividerThickness
import com.vitorpamplona.quartz.nip29RelayGroups.GroupId

/**
 * "Somebody added you to a channel" prompts, pinned above Messages › New Requests.
 *
 * Each one is an ordinary group row — the same [RelayGroupRow] the Known tab draws for a group you
 * already joined — with the invitation itself as the row's newest line. New Requests is a list of
 * rooms awaiting a decision, and a pending invite is exactly that; rendering it as a full note (which
 * is what the Notifications tab does, where the unit of the feed genuinely is a note) put a card the
 * height of five rooms on top of a list of rooms.
 *
 * Deciding happens where it does for every other group: tap opens the channel, so it can be read
 * before answering, and its top bar offers "Add to Messages"; long-press brings the same three
 * choices to the row. Nothing is auto-accepted — on a Buzz relay another member can add you to a
 * channel server-side, so the relay writes you into the kind-39002 roster and you can read and post
 * without ever having agreed to see it. Amethyst used to silently subscribe to those channels while
 * showing no row for them anywhere; the relay's decision is now surfaced as a question instead.
 */
@Composable
fun ChannelInvitesSection(
    accountViewModel: AccountViewModel,
    nav: INav,
    modifier: Modifier = Modifier,
) {
    val invites by accountViewModel.account.channelInvites.flow
        .collectAsStateWithLifecycle()

    if (invites.isEmpty()) return

    Column(modifier) {
        invites.forEach { invite ->
            // Keyed by the kind-44100 it came from: the list is sorted newest-first, so an arriving
            // invite shifts every row below it. Without a key Compose matches children by position and
            // each shifted row would recompose against a different invite.
            key(invite.eventId) {
                ChannelInviteRow(invite, accountViewModel, nav)
                HorizontalDivider(thickness = DividerThickness)
            }
        }
    }
}

@Composable
private fun ChannelInviteRow(
    invite: BuzzChannelInvite,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val baseChannel =
        remember(invite.channelId, invite.relay) {
            LocalCache.getOrCreateRelayGroupChannel(GroupId(invite.channelId, invite.relay))
        }

    // The actor, not the signer: a kind-44100 is signed by the relay keypair reporting the membership
    // change it made, so naming its author here would put an npub on every invite.
    val actorName = observeUserNameByHex(invite.actor, accountViewModel)
    val lastContent =
        if (invite.actor != null) {
            stringRes(R.string.channel_invite_row_added_you_by, actorName)
        } else {
            stringRes(R.string.channel_invite_row_added_you)
        }

    RelayGroupRow(
        baseChannel = baseChannel,
        lastContent = lastContent,
        lastTime = invite.createdAt,
        accountViewModel = accountViewModel,
        nav = nav,
    ) { channel, dismiss ->
        DropdownMenuItem(
            text = { Text(stringRes(R.string.add_to_messages)) },
            onClick = {
                dismiss()
                accountViewModel.acceptChannelInvite(channel)
            },
        )
        // Ignore is a local display choice that leaves you in the roster; Leave is the kind-9022 that
        // actually removes you from the channel. Keeping both means "get this off my list" never has
        // to mean "announce to the relay that I left".
        DropdownMenuItem(
            text = { Text(stringRes(R.string.channel_invite_ignore)) },
            onClick = {
                dismiss()
                accountViewModel.dismissChannelInvite(channel.groupId.id)
            },
        )
        DropdownMenuItem(
            text = { Text(stringRes(R.string.channel_invite_leave), color = MaterialTheme.colorScheme.error) },
            onClick = {
                dismiss()
                accountViewModel.leaveChannelInvite(channel)
            },
        )
    }
}
