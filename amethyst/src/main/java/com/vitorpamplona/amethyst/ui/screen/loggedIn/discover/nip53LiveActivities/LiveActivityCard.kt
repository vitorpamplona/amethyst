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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.discover.nip53LiveActivities

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment.Companion.BottomStart
import androidx.compose.ui.Alignment.Companion.TopEnd
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.ParticipantListBuilder
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.model.topNavFeeds.allFollows.AllFollowsByOutboxTopNavFilter
import com.vitorpamplona.amethyst.model.topNavFeeds.allFollows.AllFollowsByProxyTopNavFilter
import com.vitorpamplona.amethyst.model.topNavFeeds.noteBased.author.AuthorsByOutboxTopNavFilter
import com.vitorpamplona.amethyst.model.topNavFeeds.noteBased.author.AuthorsByProxyTopNavFilter
import com.vitorpamplona.amethyst.model.topNavFeeds.noteBased.community.SingleCommunityTopNavFilter
import com.vitorpamplona.amethyst.model.topNavFeeds.noteBased.muted.MutedAuthorsByOutboxTopNavFilter
import com.vitorpamplona.amethyst.model.topNavFeeds.noteBased.muted.MutedAuthorsByProxyTopNavFilter
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.event.observeNoteAndMap
import com.vitorpamplona.amethyst.ui.actions.CrossfadeIfEnabled
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.note.DisplayAuthorBanner
import com.vitorpamplona.amethyst.ui.note.Gallery
import com.vitorpamplona.amethyst.ui.note.LoadLiveActivityChannel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.nip53LiveActivities.EndedFlag
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.nip53LiveActivities.LiveFlag
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.nip53LiveActivities.OfflineFlag
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.nip53LiveActivities.ScheduledFlag
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.nip53LiveActivities.header.LiveActivitiesChannelHeader
import com.vitorpamplona.amethyst.ui.screen.loggedIn.home.CheckIfVideoIsOnline
import com.vitorpamplona.amethyst.ui.screen.loggedIn.notifications.equalImmutableLists
import com.vitorpamplona.amethyst.ui.theme.DoubleVertSpacer
import com.vitorpamplona.amethyst.ui.theme.QuoteBorder
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.LiveActivitiesEvent
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.tags.ParticipantTag
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.tags.StatusTag
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Immutable
data class LiveActivityCard(
    val id: Address?,
    val name: String,
    val cover: String?,
    val media: String?,
    val subject: String?,
    val content: String?,
    val participants: ImmutableList<ParticipantTag>,
    val status: StatusTag.STATUS?,
    val starts: Long?,
)

@Composable
fun RenderLiveActivityThumb(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val card by observeNoteAndMap(baseNote, accountViewModel) {
        val noteEvent = it.event as? LiveActivitiesEvent

        LiveActivityCard(
            id = noteEvent?.address(),
            name = noteEvent?.dTag() ?: "",
            cover = noteEvent?.image()?.ifBlank { null },
            media = noteEvent?.streaming(),
            subject = noteEvent?.title()?.ifBlank { null },
            content = noteEvent?.summary(),
            participants = noteEvent?.participants()?.toImmutableList() ?: persistentListOf(),
            status = noteEvent?.status(),
            starts = noteEvent?.starts(),
        )
    }

    RenderLiveActivityThumb(
        card,
        baseNote,
        accountViewModel,
        nav,
    )
}

@Composable
fun RenderLiveActivityThumb(
    card: LiveActivityCard,
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            contentAlignment = TopEnd,
            modifier =
                Modifier
                    .aspectRatio(ratio = 16f / 9f)
                    .fillMaxWidth(),
        ) {
            card.cover?.let {
                AsyncImage(
                    model = it,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .clip(QuoteBorder),
                )
            } ?: run { DisplayAuthorBanner(baseNote, accountViewModel) }

            Box(Modifier.padding(10.dp)) {
                CrossfadeIfEnabled(targetState = card.status, label = "RenderLiveActivityThumb", accountViewModel = accountViewModel) {
                    when (it) {
                        StatusTag.STATUS.LIVE -> {
                            val url = card.media
                            if (url.isNullOrBlank()) {
                                EndedFlag()
                            } else {
                                CheckIfVideoIsOnline(url, accountViewModel) { isOnline ->
                                    if (isOnline) {
                                        LiveFlag()
                                    } else {
                                        OfflineFlag()
                                    }
                                }
                            }
                        }
                        StatusTag.STATUS.ENDED -> {
                            EndedFlag()
                        }
                        StatusTag.STATUS.PLANNED -> {
                            ScheduledFlag(card.starts)
                        }
                        else -> {
                            EndedFlag()
                        }
                    }
                }
            }

            LoadParticipants(card.participants, baseNote, accountViewModel) { participantUsers ->
                Box(
                    Modifier
                        .padding(10.dp)
                        .align(BottomStart),
                ) {
                    if (participantUsers.isNotEmpty()) {
                        Gallery(participantUsers, Modifier, accountViewModel, nav)
                    }
                }
            }
        }

        Spacer(modifier = DoubleVertSpacer)

        baseNote.address()?.let {
            LoadLiveActivityChannel(it, accountViewModel) {
                LiveActivitiesChannelHeader(
                    baseChannel = it,
                    showVideo = false,
                    showFlag = false,
                    sendToChannel = true,
                    modifier = Modifier,
                    accountViewModel = accountViewModel,
                    nav = nav,
                )
            }
        }
    }
}

@Composable
fun LoadParticipants(
    participants: ImmutableList<ParticipantTag>,
    baseNote: Note,
    accountViewModel: AccountViewModel,
    inner: @Composable (ImmutableList<User>) -> Unit,
) {
    var participantUsers by remember {
        mutableStateOf<ImmutableList<User>>(
            persistentListOf(),
        )
    }

    LaunchedEffect(key1 = participants) {
        launch(Dispatchers.IO) {
            val hosts =
                participants.mapNotNull { part ->
                    if (part.pubKey != baseNote.author?.pubkeyHex) {
                        LocalCache.checkGetOrCreateUser(part.pubKey)
                    } else {
                        null
                    }
                }

            val hostsAuthor = hosts + (baseNote.author?.let { listOf(it) } ?: emptyList<User>())

            val topFilter = accountViewModel.account.liveDiscoveryFollowLists.value

            val followingKeySet =
                when (topFilter) {
                    is AuthorsByOutboxTopNavFilter -> topFilter.authors
                    is MutedAuthorsByOutboxTopNavFilter -> topFilter.authors
                    is AllFollowsByOutboxTopNavFilter -> topFilter.authors
                    is SingleCommunityTopNavFilter -> topFilter.authors
                    is AuthorsByProxyTopNavFilter -> topFilter.authors
                    is MutedAuthorsByProxyTopNavFilter -> topFilter.authors
                    is AllFollowsByProxyTopNavFilter -> topFilter.authors
                    else -> emptySet()
                }

            val allParticipants =
                ParticipantListBuilder()
                    .followsThatParticipateOn(baseNote, followingKeySet)
                    .minus(hostsAuthor)

            val newParticipantUsers =
                if (followingKeySet == null) {
                    val allFollows = accountViewModel.account.kind3FollowList.flow.value.authors
                    val followingParticipants =
                        ParticipantListBuilder()
                            .followsThatParticipateOn(baseNote, allFollows)
                            .minus(hostsAuthor)

                    (hosts + followingParticipants + (allParticipants - followingParticipants))
                        .toImmutableList()
                } else {
                    (hosts + allParticipants).toImmutableList()
                }

            if (!equalImmutableLists(newParticipantUsers, participantUsers)) {
                participantUsers = newParticipantUsers
            }
        }
    }

    inner(participantUsers)
}
