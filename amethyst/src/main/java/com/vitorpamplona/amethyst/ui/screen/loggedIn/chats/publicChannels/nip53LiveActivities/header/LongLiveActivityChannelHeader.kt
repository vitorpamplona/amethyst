/**
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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.nip53LiveActivities.header

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.model.nip53LiveActivities.LiveActivitiesChannel
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.channel.observeChannel
import com.vitorpamplona.amethyst.ui.components.TranslatableRichTextViewer
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.routeFor
import com.vitorpamplona.amethyst.ui.note.ClickableUserPicture
import com.vitorpamplona.amethyst.ui.note.LoadAddressableNote
import com.vitorpamplona.amethyst.ui.note.NoteAuthorPicture
import com.vitorpamplona.amethyst.ui.note.NoteUsernameDisplay
import com.vitorpamplona.amethyst.ui.note.UsernameDisplay
import com.vitorpamplona.amethyst.ui.note.elements.DisplayUncitedHashtags
import com.vitorpamplona.amethyst.ui.note.elements.MoreOptionsButton
import com.vitorpamplona.amethyst.ui.note.elements.NormalTimeAgo
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.notifications.equalImmutableLists
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.DoubleHorzSpacer
import com.vitorpamplona.amethyst.ui.theme.Size25dp
import com.vitorpamplona.quartz.nip01Core.core.toImmutableListOfLists
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.hasHashtags
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.LiveActivitiesEvent
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.tags.ParticipantTag
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun LongLiveActivityChannelHeader(
    baseChannel: LiveActivitiesChannel,
    lineModifier: Modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val channelState by observeChannel(baseChannel, accountViewModel)
    val channel = channelState?.channel as? LiveActivitiesChannel ?: return
    val activity = channel.info ?: return
    val callbackUri = remember(channel) { channel.toNostrUri() }

    Row(lineModifier) {
        RenderSummary(activity, callbackUri, accountViewModel, nav)
    }

    LoadAddressableNote(channel.address, accountViewModel) { loadingNote ->
        loadingNote?.let { note ->
            Row(
                lineModifier,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringRes(id = R.string.owner),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(75.dp),
                )
                Spacer(DoubleHorzSpacer)
                NoteAuthorPicture(note, Size25dp, accountViewModel = accountViewModel, nav = nav)
                Spacer(DoubleHorzSpacer)
                NoteUsernameDisplay(note, Modifier.weight(1f), accountViewModel = accountViewModel)
            }

            Row(
                lineModifier,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringRes(id = R.string.created_at),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(75.dp),
                )
                Spacer(DoubleHorzSpacer)
                NormalTimeAgo(note, remember { Modifier.weight(1f) })
                MoreOptionsButton(note, null, accountViewModel, nav)
            }
        }
    }

    var participantUsers by remember(baseChannel) {
        mutableStateOf<ImmutableList<Pair<ParticipantTag, User>>>(
            persistentListOf(),
        )
    }

    LaunchedEffect(key1 = channelState) {
        launch(Dispatchers.IO) {
            val newParticipantUsers =
                channel.info
                    ?.participants()
                    ?.mapNotNull { part ->
                        LocalCache.checkGetOrCreateUser(part.pubKey)?.let { Pair(part, it) }
                    }?.toImmutableList()

            if (
                newParticipantUsers != null && !equalImmutableLists(newParticipantUsers, participantUsers)
            ) {
                participantUsers = newParticipantUsers
            }
        }
    }

    participantUsers.forEach {
        Row(
            lineModifier.clickable { nav.nav(routeFor(it.second)) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            it.first.role?.let { it1 ->
                Text(
                    text =
                        it1.replaceFirstChar {
                            if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
                        },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(55.dp),
                )
            }
            Spacer(DoubleHorzSpacer)
            ClickableUserPicture(it.second, Size25dp, accountViewModel)
            Spacer(DoubleHorzSpacer)
            UsernameDisplay(it.second, Modifier.weight(1f), accountViewModel = accountViewModel)
        }
    }
}

@Composable
private fun RowScope.RenderSummary(
    activity: LiveActivitiesEvent,
    callbackUri: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val summary = activity.summary() ?: stringRes(id = R.string.groups_no_descriptor)

    Column(Modifier.weight(1f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val defaultBackground = MaterialTheme.colorScheme.background
            val background = remember { mutableStateOf(defaultBackground) }

            TranslatableRichTextViewer(
                content = summary,
                canPreview = false,
                quotesLeft = 1,
                tags = activity.tags.toImmutableListOfLists(),
                backgroundColor = background,
                id = activity.id,
                callbackUri = callbackUri,
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }

        if (activity.hasHashtags()) {
            DisplayUncitedHashtags(activity, summary, callbackUri, accountViewModel, nav)
        }
    }
}
