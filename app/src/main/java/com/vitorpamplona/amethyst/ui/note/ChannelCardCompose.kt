/**
 * Copyright (c) 2024 Vitor Pamplona
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
package com.vitorpamplona.amethyst.ui.note

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment.Companion.BottomStart
import androidx.compose.ui.Alignment.Companion.Center
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Alignment.Companion.TopEnd
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.distinctUntilChanged
import androidx.lifecycle.map
import coil.compose.AsyncImage
import com.vitorpamplona.amethyst.model.Channel
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.ParticipantListBuilder
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.ui.components.SensitivityWarning
import com.vitorpamplona.amethyst.ui.layouts.LeftPictureLayout
import com.vitorpamplona.amethyst.ui.note.elements.BannerImage
import com.vitorpamplona.amethyst.ui.screen.equalImmutableLists
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.ChannelHeader
import com.vitorpamplona.amethyst.ui.screen.loggedIn.CheckIfUrlIsOnline
import com.vitorpamplona.amethyst.ui.screen.loggedIn.EndedFlag
import com.vitorpamplona.amethyst.ui.screen.loggedIn.LiveFlag
import com.vitorpamplona.amethyst.ui.screen.loggedIn.OfflineFlag
import com.vitorpamplona.amethyst.ui.screen.loggedIn.ScheduledFlag
import com.vitorpamplona.amethyst.ui.screen.loggedIn.showAmountAxis
import com.vitorpamplona.amethyst.ui.theme.DoubleVertSpacer
import com.vitorpamplona.amethyst.ui.theme.HalfPadding
import com.vitorpamplona.amethyst.ui.theme.QuoteBorder
import com.vitorpamplona.amethyst.ui.theme.Size35dp
import com.vitorpamplona.amethyst.ui.theme.Size5dp
import com.vitorpamplona.amethyst.ui.theme.StdHorzSpacer
import com.vitorpamplona.amethyst.ui.theme.StdPadding
import com.vitorpamplona.amethyst.ui.theme.StdVertSpacer
import com.vitorpamplona.amethyst.ui.theme.placeholderText
import com.vitorpamplona.quartz.events.ChannelCreateEvent
import com.vitorpamplona.quartz.events.ClassifiedsEvent
import com.vitorpamplona.quartz.events.CommunityDefinitionEvent
import com.vitorpamplona.quartz.events.LiveActivitiesEvent
import com.vitorpamplona.quartz.events.LiveActivitiesEvent.Companion.STATUS_ENDED
import com.vitorpamplona.quartz.events.LiveActivitiesEvent.Companion.STATUS_LIVE
import com.vitorpamplona.quartz.events.LiveActivitiesEvent.Companion.STATUS_PLANNED
import com.vitorpamplona.quartz.events.Participant
import com.vitorpamplona.quartz.events.Price
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun ChannelCardCompose(
    baseNote: Note,
    routeForLastRead: String? = null,
    modifier: Modifier = Modifier,
    parentBackgroundColor: MutableState<Color>? = null,
    forceEventKind: Int?,
    isHiddenFeed: Boolean = false,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    WatchNoteEvent(baseNote = baseNote, accountViewModel = accountViewModel) {
        if (forceEventKind == null || baseNote.event?.kind() == forceEventKind) {
            CheckHiddenFeedWatchBlockAndReport(
                note = baseNote,
                modifier = modifier,
                showHidden = isHiddenFeed,
                showHiddenWarning = false,
                accountViewModel = accountViewModel,
                nav = nav,
            ) { canPreview ->
                NormalChannelCard(
                    baseNote = baseNote,
                    routeForLastRead = routeForLastRead,
                    modifier = modifier,
                    parentBackgroundColor = parentBackgroundColor,
                    accountViewModel = accountViewModel,
                    nav = nav,
                )
            }
        }
    }
}

@Composable
fun NormalChannelCard(
    baseNote: Note,
    routeForLastRead: String? = null,
    modifier: Modifier = Modifier,
    parentBackgroundColor: MutableState<Color>? = null,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    LongPressToQuickAction(baseNote = baseNote, accountViewModel = accountViewModel) { showPopup ->
        CheckNewAndRenderChannelCard(
            baseNote,
            routeForLastRead,
            modifier,
            parentBackgroundColor,
            accountViewModel,
            showPopup,
            nav,
        )
    }
}

@Composable
private fun CheckNewAndRenderChannelCard(
    baseNote: Note,
    routeForLastRead: String? = null,
    modifier: Modifier = Modifier,
    parentBackgroundColor: MutableState<Color>? = null,
    accountViewModel: AccountViewModel,
    showPopup: () -> Unit,
    nav: (String) -> Unit,
) {
    val backgroundColor =
        calculateBackgroundColor(
            createdAt = baseNote.createdAt(),
            routeForLastRead = routeForLastRead,
            parentBackgroundColor = parentBackgroundColor,
            accountViewModel = accountViewModel,
        )

    ClickableNote(
        baseNote = baseNote,
        backgroundColor = backgroundColor,
        modifier = modifier,
        accountViewModel = accountViewModel,
        showPopup = showPopup,
        nav = nav,
    ) {
        InnerChannelCardWithReactions(
            baseNote = baseNote,
            accountViewModel = accountViewModel,
            nav = nav,
        )
    }
}

@Composable
fun InnerChannelCardWithReactions(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    when (baseNote.event) {
        is LiveActivitiesEvent -> {
            InnerCardRow(baseNote, accountViewModel, nav)
        }
        is CommunityDefinitionEvent -> {
            InnerCardRow(baseNote, accountViewModel, nav)
        }
        is ChannelCreateEvent -> {
            InnerCardRow(baseNote, accountViewModel, nav)
        }
        is ClassifiedsEvent -> {
            InnerCardBox(baseNote, accountViewModel, nav)
        }
    }
}

@Composable
fun InnerCardRow(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    Column(StdPadding) {
        SensitivityWarning(
            note = baseNote,
            accountViewModel = accountViewModel,
        ) {
            RenderNoteRow(
                baseNote,
                accountViewModel,
                nav,
            )
        }
    }
}

@Composable
fun InnerCardBox(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    Column(HalfPadding) {
        SensitivityWarning(
            note = baseNote,
            accountViewModel = accountViewModel,
        ) {
            RenderClassifiedsThumb(baseNote, accountViewModel, nav)
        }
    }
}

@Composable
private fun RenderNoteRow(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    when (baseNote.event) {
        is LiveActivitiesEvent -> {
            RenderLiveActivityThumb(baseNote, accountViewModel, nav)
        }
        is CommunityDefinitionEvent -> {
            RenderCommunitiesThumb(baseNote, accountViewModel, nav)
        }
        is ChannelCreateEvent -> {
            RenderChannelThumb(baseNote, accountViewModel, nav)
        }
    }
}

@Immutable
data class ClassifiedsThumb(
    val image: String?,
    val title: String?,
    val price: Price?,
)

@Composable
fun RenderClassifiedsThumb(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    val noteEvent = baseNote.event as? ClassifiedsEvent ?: return

    val card by
        baseNote
            .live()
            .metadata
            .map {
                val noteEvent = it.note.event as? ClassifiedsEvent

                ClassifiedsThumb(
                    image = noteEvent?.image(),
                    title = noteEvent?.title(),
                    price = noteEvent?.price(),
                )
            }
            .distinctUntilChanged()
            .observeAsState(
                ClassifiedsThumb(
                    image = noteEvent.image(),
                    title = noteEvent.title(),
                    price = noteEvent.price(),
                ),
            )

    InnerRenderClassifiedsThumb(card, baseNote)
}

@Preview
@Composable
fun RenderClassifiedsThumbPreview() {
    Surface(Modifier.size(200.dp)) {
        InnerRenderClassifiedsThumb(
            card =
                ClassifiedsThumb(
                    image = null,
                    title = "Like New",
                    price = Price("800000", "SATS", null),
                ),
            note = Note("hex"),
        )
    }
}

@Composable
fun InnerRenderClassifiedsThumb(
    card: ClassifiedsThumb,
    note: Note,
) {
    Box(
        Modifier.fillMaxWidth().aspectRatio(1f),
        contentAlignment = BottomStart,
    ) {
        card.image?.let {
            AsyncImage(
                model = it,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } ?: run { DisplayAuthorBanner(note) }

        Row(
            Modifier.fillMaxWidth().background(Color.Black.copy(0.6f)).padding(Size5dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            card.title?.let {
                Text(
                    text = it,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = Color.White,
                    modifier = Modifier.weight(1f),
                )
            }

            card.price?.let {
                val priceTag =
                    remember(card) {
                        val newAmount = it.amount.toBigDecimalOrNull()?.let { showAmountAxis(it) } ?: it.amount

                        if (it.frequency != null && it.currency != null) {
                            "$newAmount ${it.currency}/${it.frequency}"
                        } else if (it.currency != null) {
                            "$newAmount ${it.currency}"
                        } else {
                            newAmount
                        }
                    }

                Text(
                    text = priceTag,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = Color.White,
                )
            }
        }
    }
}

@Immutable
data class LiveActivityCard(
    val name: String,
    val cover: String?,
    val media: String?,
    val subject: String?,
    val content: String?,
    val participants: ImmutableList<Participant>,
    val status: String?,
    val starts: Long?,
)

@Composable
fun RenderLiveActivityThumb(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    val noteEvent = baseNote.event as? LiveActivitiesEvent ?: return

    val card by
        baseNote
            .live()
            .metadata
            .map {
                val noteEvent = it.note.event as? LiveActivitiesEvent

                LiveActivityCard(
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
            .distinctUntilChanged()
            .observeAsState(
                LiveActivityCard(
                    name = noteEvent.dTag(),
                    cover = noteEvent.image()?.ifBlank { null },
                    media = noteEvent.streaming(),
                    subject = noteEvent.title()?.ifBlank { null },
                    content = noteEvent.summary(),
                    participants = noteEvent.participants().toImmutableList(),
                    status = noteEvent.status(),
                    starts = noteEvent.starts(),
                ),
            )

    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            contentAlignment = TopEnd,
            modifier = Modifier.aspectRatio(ratio = 16f / 9f).fillMaxWidth(),
        ) {
            card.cover?.let {
                AsyncImage(
                    model = it,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(QuoteBorder),
                )
            } ?: run { DisplayAuthorBanner(baseNote) }

            Box(Modifier.padding(10.dp)) {
                Crossfade(targetState = card.status, label = "RenderLiveActivityThumb") {
                    when (it) {
                        STATUS_LIVE -> {
                            val url = card.media
                            if (url.isNullOrBlank()) {
                                LiveFlag()
                            } else {
                                CheckIfUrlIsOnline(url, accountViewModel) { isOnline ->
                                    if (isOnline) {
                                        LiveFlag()
                                    } else {
                                        OfflineFlag()
                                    }
                                }
                            }
                        }
                        STATUS_ENDED -> {
                            EndedFlag()
                        }
                        STATUS_PLANNED -> {
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
                    Modifier.padding(10.dp).align(BottomStart),
                ) {
                    if (participantUsers.isNotEmpty()) {
                        Gallery(participantUsers, accountViewModel)
                    }
                }
            }
        }

        Spacer(modifier = DoubleVertSpacer)

        ChannelHeader(
            channelHex = baseNote.idHex,
            showVideo = false,
            showFlag = false,
            sendToChannel = true,
            modifier = Modifier,
            accountViewModel = accountViewModel,
            nav = nav,
        )
    }
}

@Immutable
data class CommunityCard(
    val name: String,
    val description: String?,
    val cover: String?,
    val moderators: ImmutableList<Participant>,
)

@Composable
fun RenderCommunitiesThumb(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    val noteEvent = baseNote.event as? CommunityDefinitionEvent ?: return

    val card by
        baseNote
            .live()
            .metadata
            .map {
                val noteEvent = it.note.event as? CommunityDefinitionEvent

                CommunityCard(
                    name = noteEvent?.dTag() ?: "",
                    description = noteEvent?.description(),
                    cover = noteEvent?.image()?.ifBlank { null },
                    moderators = noteEvent?.moderators()?.toImmutableList() ?: persistentListOf(),
                )
            }
            .distinctUntilChanged()
            .observeAsState(
                CommunityCard(
                    name = noteEvent.dTag(),
                    description = noteEvent.description(),
                    cover = noteEvent.image()?.ifBlank { null },
                    moderators = noteEvent.moderators().toImmutableList(),
                ),
            )

    LeftPictureLayout(
        onImage = {
            card.cover?.let {
                Box(contentAlignment = BottomStart) {
                    AsyncImage(
                        model = it,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(QuoteBorder),
                    )
                }
            } ?: run { DisplayAuthorBanner(baseNote) }
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
            LikeReaction(
                baseNote = baseNote,
                grayTint = MaterialTheme.colorScheme.onSurface,
                accountViewModel = accountViewModel,
                nav,
            )
            Spacer(modifier = StdHorzSpacer)
            ZapReaction(
                baseNote = baseNote,
                grayTint = MaterialTheme.colorScheme.onSurface,
                accountViewModel = accountViewModel,
                nav = nav,
            )
        },
        onDescription = {
            card.description?.let {
                Spacer(modifier = StdVertSpacer)
                Row {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.placeholderText,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 14.sp,
                    )
                }
            }
        },
        onBottomRow = {
            Spacer(modifier = StdVertSpacer)
            LoadModerators(card.moderators, baseNote, accountViewModel) { participantUsers ->
                if (participantUsers.isNotEmpty()) {
                    Gallery(participantUsers, accountViewModel)
                }
            }
        },
    )
}

@Composable
fun LoadModerators(
    moderators: ImmutableList<Participant>,
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
                    if (part.key != baseNote.author?.pubkeyHex) {
                        LocalCache.checkGetOrCreateUser(part.key)
                    } else {
                        null
                    }
                }

            val followingKeySet = accountViewModel.account.liveDiscoveryFollowLists.value?.users
            val allParticipants =
                ParticipantListBuilder().followsThatParticipateOn(baseNote, followingKeySet).minus(hosts)

            val newParticipantUsers =
                if (followingKeySet == null) {
                    val allFollows = accountViewModel.account.userProfile().cachedFollowingKeySet()
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

@Composable
private fun LoadParticipants(
    participants: ImmutableList<Participant>,
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
                    if (part.key != baseNote.author?.pubkeyHex) {
                        LocalCache.checkGetOrCreateUser(part.key)
                    } else {
                        null
                    }
                }

            val hostsAuthor = hosts + (baseNote.author?.let { listOf(it) } ?: emptyList<User>())

            val followingKeySet = accountViewModel.account.liveDiscoveryFollowLists.value?.users

            val allParticipants =
                ParticipantListBuilder()
                    .followsThatParticipateOn(baseNote, followingKeySet)
                    .minus(hostsAuthor)

            val newParticipantUsers =
                if (followingKeySet == null) {
                    val allFollows = accountViewModel.account.userProfile().cachedFollowingKeySet()
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

@Composable
fun RenderChannelThumb(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    val noteEvent = baseNote.event as? ChannelCreateEvent ?: return

    LoadChannel(baseChannelHex = baseNote.idHex, accountViewModel) {
        RenderChannelThumb(baseNote = baseNote, channel = it, accountViewModel, nav)
    }
}

@Composable
fun RenderChannelThumb(
    baseNote: Note,
    channel: Channel,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    val channelUpdates by channel.live.observeAsState()

    val name = remember(channelUpdates) { channelUpdates?.channel?.toBestDisplayName() ?: "" }
    val description = remember(channelUpdates) { channelUpdates?.channel?.summary() }
    val cover by
        remember(channelUpdates) {
            derivedStateOf { channelUpdates?.channel?.profilePicture()?.ifBlank { null } }
        }

    var participantUsers by
        remember(baseNote) {
            mutableStateOf<ImmutableList<User>>(
                persistentListOf(),
            )
        }

    LaunchedEffect(key1 = channelUpdates) {
        launch(Dispatchers.IO) {
            val followingKeySet = accountViewModel.account.liveDiscoveryFollowLists.value?.users
            val allParticipants =
                ParticipantListBuilder()
                    .followsThatParticipateOn(baseNote, followingKeySet)
                    .toImmutableList()

            val newParticipantUsers =
                if (followingKeySet == null) {
                    val allFollows = accountViewModel.account.userProfile().cachedFollowingKeySet()
                    val followingParticipants =
                        ParticipantListBuilder().followsThatParticipateOn(baseNote, allFollows).toList()

                    (followingParticipants + (allParticipants - followingParticipants)).toImmutableList()
                } else {
                    allParticipants.toImmutableList()
                }

            if (!equalImmutableLists(newParticipantUsers, participantUsers)) {
                participantUsers = newParticipantUsers
            }
        }
    }

    LeftPictureLayout(
        onImage = {
            cover?.let {
                AsyncImage(
                    model = it,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(QuoteBorder),
                )
            } ?: run { DisplayAuthorBanner(baseNote) }
        },
        onTitleRow = {
            Text(
                text = name,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )

            Spacer(modifier = StdHorzSpacer)
            LikeReaction(
                baseNote = baseNote,
                grayTint = MaterialTheme.colorScheme.onSurface,
                accountViewModel = accountViewModel,
                nav,
            )
            Spacer(modifier = StdHorzSpacer)
            ZapReaction(
                baseNote = baseNote,
                grayTint = MaterialTheme.colorScheme.onSurface,
                accountViewModel = accountViewModel,
                nav = nav,
            )
        },
        onDescription = {
            description?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.placeholderText,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 14.sp,
                )
            }
        },
        onBottomRow = {
            if (participantUsers.isNotEmpty()) {
                Spacer(modifier = StdVertSpacer)
                Gallery(participantUsers, accountViewModel)
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Gallery(
    users: ImmutableList<User>,
    accountViewModel: AccountViewModel,
) {
    FlowRow(verticalArrangement = Arrangement.Center) {
        users.take(6).forEach { ClickableUserPicture(it, Size35dp, accountViewModel) }

        if (users.size > 6) {
            Text(
                text = " + " + showCount(users.size - 6),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.align(CenterVertically),
            )
        }
    }
}

@Composable
fun DisplayAuthorBanner(note: Note) {
    WatchAuthor(note) {
        BannerImage(it, Modifier.fillMaxSize().clip(QuoteBorder))
    }
}
