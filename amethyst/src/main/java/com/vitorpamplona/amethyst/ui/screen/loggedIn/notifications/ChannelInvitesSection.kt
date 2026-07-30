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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.model.buzz.BuzzChannelInvite
import com.vitorpamplona.amethyst.commons.model.nip29RelayGroups.RelayGroupChannel
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.ui.layouts.NoteComposeLayout
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.note.DisplayBlankAuthor
import com.vitorpamplona.amethyst.ui.note.UserPicture
import com.vitorpamplona.amethyst.ui.note.UsernameDisplay
import com.vitorpamplona.amethyst.ui.note.elements.TimeAgo
import com.vitorpamplona.amethyst.ui.note.elements.TimeAgoStyle
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.ui.theme.Size10dp
import com.vitorpamplona.amethyst.ui.theme.Size55Modifier
import com.vitorpamplona.amethyst.ui.theme.Size55dp
import com.vitorpamplona.amethyst.ui.theme.Size5dp
import com.vitorpamplona.amethyst.ui.theme.UserNameRowHeight
import com.vitorpamplona.amethyst.ui.theme.newItemBackgroundColor
import com.vitorpamplona.amethyst.ui.theme.placeholderText
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
    nav: INav,
    modifier: Modifier = Modifier,
) {
    val invites by accountViewModel.feedStates.channelInvites.flow
        .collectAsStateWithLifecycle()

    if (invites.isEmpty()) return

    Column(modifier) {
        invites.forEach { invite ->
            // Keyed by channel: the list is sorted newest-first, so an arriving invite shifts every row
            // below it. Without a key Compose matches children by position and each shifted row would
            // recompose against a different invite — re-resolving the actor and reloading their avatar.
            key(invite.channelId) {
                ChannelInviteCard(invite, accountViewModel, nav)
                HorizontalDivider(thickness = DividerThickness)
            }
        }
    }
}

/**
 * One pending add, drawn as a feed row instead of a floating Material card: the actor is the row's
 * author — picture, name and time in the usual note header — and "added you to X" is the row's content,
 * so the prompt reads like the reply/mention notifications it sits next to. The three choices take the
 * reactions slot, which spans the full width and therefore fits "Add to Messages" without wrapping.
 */
@Composable
fun ChannelInviteCard(
    invite: BuzzChannelInvite,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val baseChannel =
        remember(invite.channelId, invite.relay) {
            LocalCache.getOrCreateRelayGroupChannel(GroupId(invite.channelId, invite.relay))
        }

    // The channel's own metadata flow, collected directly rather than through `observeChannel`. That
    // helper also registers a ChannelFinder query, and every assembler under it is gated on
    // `is PublicChatChannel` / `is LiveActivitiesChannel` — a RelayGroupChannel yields no filter at all,
    // so the registration buys nothing and only churns the app-wide key set on mount/unmount. The flow
    // still fills the name in when the group's kind-39000 lands from the directory subscription, and
    // nothing here opens the channel's *message* subscription — holding that back until the viewer
    // answers is the whole point of the prompt.
    val channelState by
        remember(baseChannel) { baseChannel.flow().metadata.stateFlow }
            .collectAsStateWithLifecycle()
    val channel = channelState.channel as? RelayGroupChannel ?: baseChannel

    val actorUser = remember(invite.actor) { invite.actor?.let { LocalCache.getOrCreateUser(it) } }

    // A pending invite is by definition unanswered, so it always carries the new-item wash rather than
    // fading with a last-read marker: it is a standing question, not a dated event.
    val backgroundColor =
        MaterialTheme.colorScheme.newItemBackgroundColor
            .compositeOver(MaterialTheme.colorScheme.background)

    NoteComposeLayout(
        modifier =
            remember(backgroundColor) {
                Modifier.drawBehind { drawRect(backgroundColor) }.fillMaxWidth()
            },
        authorPicture = {
            Box(Size55Modifier, contentAlignment = Alignment.BottomEnd) {
                if (actorUser != null) {
                    UserPicture(actorUser, Size55dp, accountViewModel = accountViewModel, nav = nav)
                } else {
                    DisplayBlankAuthor(Size55dp, accountViewModel = accountViewModel)
                }
            }
        },
        firstRow = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Size5dp),
                modifier = UserNameRowHeight,
            ) {
                // Who did it matters: the relay reports a self-join with the same event, so naming the
                // actor is what tells "I joined this" apart from "a stranger put me here".
                if (actorUser != null) {
                    UsernameDisplay(actorUser, Modifier.weight(1f), accountViewModel = accountViewModel)
                } else {
                    Text(
                        text = stringRes(R.string.channel_invite_unknown_actor),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }

                // DottedTight, not Dotted: the row's `spacedBy` already supplies the gap, so the
                // dotted variant's own leading space would double it. Same choice the note header makes.
                TimeAgo(invite.createdAt, style = TimeAgoStyle.DottedTight)
            }
        },
        secondRow = {},
        noteContent = {
            Text(text = stringRes(R.string.channel_invite_title, channel.toBestDisplayName()))

            Text(
                text = invite.relay.displayUrl(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.placeholderText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        reactionsRow = {
            Row(
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Size10dp),
            ) {
                // Leave is separate from Ignore on purpose: Ignore is a local display choice that leaves
                // you in the roster, Leave is the kind-9022 that actually removes you from the channel.
                TextButton(onClick = { accountViewModel.leaveChannelInvite(channel) }) {
                    Text(stringRes(R.string.channel_invite_leave), color = MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = { accountViewModel.dismissChannelInvite(invite.channelId) }) {
                    Text(stringRes(R.string.channel_invite_ignore))
                }
                // Accepting *is* `addRelayGroupToMessages`, the same call behind the channel top bar's
                // "Add to Messages", so it carries that label rather than a second word for one action.
                TextButton(onClick = { accountViewModel.acceptChannelInvite(channel) }) {
                    Text(stringRes(R.string.add_to_messages), fontWeight = FontWeight.Bold)
                }
            }
        },
    )
}
