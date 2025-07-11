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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.discover.nip72Communities

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment.Companion.BottomStart
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.ParticipantListBuilder
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.model.topNavFeeds.allFollows.AllFollowsByOutboxTopNavFilter
import com.vitorpamplona.amethyst.model.topNavFeeds.noteBased.author.AuthorsByOutboxTopNavFilter
import com.vitorpamplona.amethyst.model.topNavFeeds.noteBased.community.SingleCommunityTopNavFilter
import com.vitorpamplona.amethyst.model.topNavFeeds.noteBased.muted.MutedAuthorsByOutboxTopNavFilter
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.event.observeNote
import com.vitorpamplona.amethyst.ui.layouts.LeftPictureLayout
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.note.DisplayAuthorBanner
import com.vitorpamplona.amethyst.ui.note.Gallery
import com.vitorpamplona.amethyst.ui.note.LikeReaction
import com.vitorpamplona.amethyst.ui.note.ZapReaction
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.notifications.equalImmutableLists
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.HalfTopPadding
import com.vitorpamplona.amethyst.ui.theme.QuoteBorder
import com.vitorpamplona.amethyst.ui.theme.RowColSpacing
import com.vitorpamplona.amethyst.ui.theme.StdHorzSpacer
import com.vitorpamplona.amethyst.ui.theme.grayText
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip72ModCommunities.definition.CommunityDefinitionEvent
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Immutable
data class CommunityCard(
    val name: String,
    val description: String?,
    val cover: String?,
    val moderators: ImmutableList<HexKey>,
)

@Composable
fun RenderCommunitiesThumb(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val noteState by observeNote(baseNote, accountViewModel)
    val noteEvent = noteState.note.event as? CommunityDefinitionEvent ?: return

    RenderCommunitiesThumb(
        CommunityCard(
            name = noteEvent.dTag(),
            description = noteEvent.description(),
            cover = noteEvent.image()?.imageUrl,
            moderators = noteEvent.moderatorKeys().toImmutableList(),
        ),
        baseNote,
        accountViewModel,
        nav,
    )
}

@Composable
fun RenderCommunitiesThumb(
    card: CommunityCard,
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    LeftPictureLayout(
        onImage = {
            card.cover?.let {
                Box(contentAlignment = BottomStart) {
                    AsyncImage(
                        model = it,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .clip(QuoteBorder),
                    )
                }
            } ?: run { DisplayAuthorBanner(baseNote, accountViewModel) }
        },
        onTitleRow = {
            Text(
                text = card.name,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )

            Spacer(modifier = StdHorzSpacer)
            Row(
                verticalAlignment = CenterVertically,
                horizontalArrangement = RowColSpacing,
            ) {
                LikeReaction(
                    baseNote = baseNote,
                    grayTint = MaterialTheme.colorScheme.onSurface,
                    accountViewModel = accountViewModel,
                    nav,
                )
            }
            Spacer(modifier = StdHorzSpacer)
            ZapReaction(
                baseNote = baseNote,
                grayTint = MaterialTheme.colorScheme.onSurface,
                accountViewModel = accountViewModel,
                nav = nav,
            )
        },
        onDescription = {
            Text(
                text = card.description ?: stringRes(R.string.community_about_topic, card.name),
                color = MaterialTheme.colorScheme.grayText,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                fontSize = 14.sp,
                lineHeight = 18.sp,
                modifier = HalfTopPadding,
            )
        },
        onBottomRow = {
            LoadModerators(card.moderators, baseNote, accountViewModel) { participantUsers ->
                if (participantUsers.isNotEmpty()) {
                    Gallery(participantUsers, HalfTopPadding, accountViewModel, nav)
                }
            }
        },
    )
}

@Composable
fun LoadModerators(
    moderators: ImmutableList<String>,
    baseNote: Note,
    accountViewModel: AccountViewModel,
    content: @Composable (ImmutableList<User>) -> Unit,
) {
    var participantUsers by remember {
        mutableStateOf<ImmutableList<User>>(
            persistentListOf(),
        )
    }

    LaunchedEffect(key1 = moderators) {
        launch(Dispatchers.IO) {
            val hosts =
                moderators.mapNotNull { part ->
                    if (part != baseNote.author?.pubkeyHex) {
                        LocalCache.checkGetOrCreateUser(part)
                    } else {
                        null
                    }
                }

            val topFilter = accountViewModel.account.liveDiscoveryFollowLists.value
            val discoveryTopFilterAuthors =
                when (topFilter) {
                    is AuthorsByOutboxTopNavFilter -> topFilter.authors
                    is MutedAuthorsByOutboxTopNavFilter -> topFilter.authors
                    is AllFollowsByOutboxTopNavFilter -> topFilter.authors
                    is SingleCommunityTopNavFilter -> topFilter.authors
                    else -> null
                }

            val followingKeySet = discoveryTopFilterAuthors
            val allParticipants =
                ParticipantListBuilder().followsThatParticipateOn(baseNote, followingKeySet).minus(hosts)

            val newParticipantUsers =
                if (followingKeySet == null) {
                    val allFollows = accountViewModel.account.kind3FollowList.flow.value.authors
                    val followingParticipants =
                        ParticipantListBuilder().followsThatParticipateOn(baseNote, allFollows).minus(hosts)

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

    content(participantUsers)
}
