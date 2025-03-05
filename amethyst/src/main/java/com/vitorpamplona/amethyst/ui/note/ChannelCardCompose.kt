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

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.BottomStart
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Alignment.Companion.TopEnd
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.distinctUntilChanged
import androidx.lifecycle.map
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Channel
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.ParticipantListBuilder
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.ui.actions.CrossfadeIfEnabled
import com.vitorpamplona.amethyst.ui.components.SensitivityWarning
import com.vitorpamplona.amethyst.ui.layouts.LeftPictureLayout
import com.vitorpamplona.amethyst.ui.navigation.INav
import com.vitorpamplona.amethyst.ui.note.elements.BannerImage
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.public.ChannelHeader
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.public.EndedFlag
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.public.LiveFlag
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.public.OfflineFlag
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.public.ScheduledFlag
import com.vitorpamplona.amethyst.ui.screen.loggedIn.dvms.observeAppDefinition
import com.vitorpamplona.amethyst.ui.screen.loggedIn.home.CheckIfVideoIsOnline
import com.vitorpamplona.amethyst.ui.screen.loggedIn.notifications.equalImmutableLists
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.DoubleVertSpacer
import com.vitorpamplona.amethyst.ui.theme.HalfPadding
import com.vitorpamplona.amethyst.ui.theme.HalfTopPadding
import com.vitorpamplona.amethyst.ui.theme.QuoteBorder
import com.vitorpamplona.amethyst.ui.theme.RowColSpacing
import com.vitorpamplona.amethyst.ui.theme.RowColSpacing5dp
import com.vitorpamplona.amethyst.ui.theme.Size25dp
import com.vitorpamplona.amethyst.ui.theme.Size5dp
import com.vitorpamplona.amethyst.ui.theme.StdHorzSpacer
import com.vitorpamplona.amethyst.ui.theme.StdPadding
import com.vitorpamplona.amethyst.ui.theme.StdVertSpacer
import com.vitorpamplona.amethyst.ui.theme.bitcoinColor
import com.vitorpamplona.amethyst.ui.theme.grayText
import com.vitorpamplona.amethyst.ui.theme.nip05
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip28PublicChat.admin.ChannelCreateEvent
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.LiveActivitiesEvent
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.tags.ParticipantTag
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.tags.StatusTag
import com.vitorpamplona.quartz.nip72ModCommunities.definition.CommunityDefinitionEvent
import com.vitorpamplona.quartz.nip89AppHandlers.definition.AppDefinitionEvent
import com.vitorpamplona.quartz.nip99Classifieds.ClassifiedsEvent
import com.vitorpamplona.quartz.nip99Classifieds.tags.PriceTag
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
    nav: INav,
) {
    WatchNoteEvent(baseNote = baseNote, accountViewModel = accountViewModel) {
        if (forceEventKind == null || baseNote.event?.kind == forceEventKind) {
            CheckHiddenFeedWatchBlockAndReport(
                note = baseNote,
                modifier = modifier,
                ignoreAllBlocksAndReports = isHiddenFeed,
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
    nav: INav,
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
    nav: INav,
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
    nav: INav,
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
        is AppDefinitionEvent -> {
            InnerCardRow(baseNote, accountViewModel, nav)
        }
    }
}

@Composable
fun InnerCardRow(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
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
    nav: INav,
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
    nav: INav,
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
        is AppDefinitionEvent -> {
            RenderContentDVMThumb(baseNote, accountViewModel, nav)
        }
    }
}

@Immutable
data class ClassifiedsThumb(
    val image: String?,
    val title: String?,
    val price: PriceTag?,
)

@Composable
fun RenderClassifiedsThumb(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
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
            }.distinctUntilChanged()
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
                    price = PriceTag("800000", "SATS", null),
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
        Modifier
            .fillMaxWidth()
            .aspectRatio(1f),
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
            Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(0.6f))
                .padding(Size5dp),
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
                        val newAmount = it.amount.toBigDecimalOrNull()?.let { showAmountInteger(it) } ?: it.amount

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
    val participants: ImmutableList<ParticipantTag>,
    val status: String?,
    val starts: Long?,
)

@Composable
fun RenderLiveActivityThumb(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
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
            }.distinctUntilChanged()
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
            } ?: run { DisplayAuthorBanner(baseNote) }

            Box(Modifier.padding(10.dp)) {
                CrossfadeIfEnabled(targetState = card.status, label = "RenderLiveActivityThumb", accountViewModel = accountViewModel) {
                    when (it) {
                        StatusTag.STATUS.LIVE.code -> {
                            val url = card.media
                            if (url.isNullOrBlank()) {
                                LiveFlag()
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
                        StatusTag.STATUS.ENDED.code -> {
                            EndedFlag()
                        }
                        StatusTag.STATUS.PLANNED.code -> {
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
                        Gallery(participantUsers, Modifier, accountViewModel)
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
    val moderators: ImmutableList<HexKey>,
)

@Immutable
data class DVMCard(
    val name: String,
    val description: String?,
    val cover: String?,
    val amount: String?,
    val personalized: Boolean?,
)

@Composable
fun RenderCommunitiesThumb(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
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
                    cover = noteEvent?.image()?.imageUrl,
                    moderators = noteEvent?.moderatorKeys()?.toImmutableList() ?: persistentListOf(),
                )
            }.distinctUntilChanged()
            .observeAsState(
                CommunityCard(
                    name = noteEvent.dTag(),
                    description = noteEvent.description(),
                    cover = noteEvent.image()?.imageUrl,
                    moderators = noteEvent.moderatorKeys().toImmutableList(),
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
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .clip(QuoteBorder),
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
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
                    Gallery(participantUsers, HalfTopPadding, accountViewModel)
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

            val followingKeySet =
                accountViewModel.account.liveDiscoveryFollowLists.value
                    ?.authors
            val allParticipants =
                ParticipantListBuilder().followsThatParticipateOn(baseNote, followingKeySet).minus(hosts)

            val newParticipantUsers =
                if (followingKeySet == null) {
                    val allFollows = accountViewModel.account.liveKind3Follows.value.authors
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

            val followingKeySet =
                accountViewModel.account.liveDiscoveryFollowLists.value
                    ?.authors

            val allParticipants =
                ParticipantListBuilder()
                    .followsThatParticipateOn(baseNote, followingKeySet)
                    .minus(hostsAuthor)

            val newParticipantUsers =
                if (followingKeySet == null) {
                    val allFollows = accountViewModel.account.liveKind3Follows.value.authors
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
fun RenderContentDVMThumb(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    // downloads user metadata to pre-load the NIP-65 relays.
    val user =
        baseNote.author
            ?.live()
            ?.metadata
            ?.observeAsState()

    val card = observeAppDefinition(appDefinitionNote = baseNote)

    LeftPictureLayout(
        imageFraction = 0.20f,
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
            } ?: run {
                user?.value?.user?.let {
                    BannerImage(
                        it,
                        Modifier
                            .fillMaxSize()
                            .clip(QuoteBorder),
                    )
                }
            }
        },
        onTitleRow = {
            Text(
                text = card.name,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = StdVertSpacer)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = RowColSpacing5dp,
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
            card.description?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.grayText,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 14.sp,
                    lineHeight = 16.sp,
                    modifier = HalfTopPadding,
                )
            }
        },
        onBottomRow = {
            card.amount?.let {
                var color = Color.DarkGray
                var amount = it
                if (card.amount == "free" || card.amount == "0") {
                    color = MaterialTheme.colorScheme.secondary
                    amount = "Free"
                } else if (card.amount == "flexible") {
                    color = MaterialTheme.colorScheme.primaryContainer
                    amount = "Flexible"
                } else if (card.amount == "") {
                    color = MaterialTheme.colorScheme.grayText
                    amount = "Unknown"
                } else {
                    color = MaterialTheme.colorScheme.primary
                    amount = card.amount + " Sats"
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Absolute.Right,
                ) {
                    Text(
                        textAlign = TextAlign.End,
                        text = " $amount ",
                        color = color,
                        maxLines = 3,
                        modifier =
                            Modifier
                                .weight(1f, fill = false)
                                .border(Dp(.1f), color, shape = RoundedCornerShape(20)),
                        fontSize = 12.sp,
                    )
                }
            }
            Spacer(modifier = StdHorzSpacer)
            card.personalized?.let {
                var color = Color.DarkGray
                var name = "generic"
                if (card.personalized == true) {
                    color = MaterialTheme.colorScheme.bitcoinColor
                    name = "Personalized"
                } else {
                    color = MaterialTheme.colorScheme.nip05
                    name = "Generic"
                }
                Spacer(modifier = StdVertSpacer)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Absolute.Right,
                ) {
                    Text(
                        textAlign = TextAlign.End,
                        text = " $name ",
                        color = color,
                        maxLines = 3,
                        modifier =
                            Modifier
                                .padding(start = 4.dp)
                                .weight(1f, fill = false)
                                .border(Dp(.1f), color, shape = RoundedCornerShape(20)),
                        fontSize = 12.sp,
                    )
                }
            }
        },
    )
}

@Composable
fun RenderChannelThumb(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
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
    nav: INav,
) {
    val channelUpdates by channel.live.observeAsState()

    val name = remember(channelUpdates) { channelUpdates?.channel?.toBestDisplayName() ?: "" }
    val description = remember(channelUpdates) { channelUpdates?.channel?.summary()?.ifBlank { null } }
    var cover by
        remember(channelUpdates) {
            mutableStateOf(channelUpdates?.channel?.profilePicture()?.ifBlank { null })
        }

    var participantUsers by
        remember(baseNote) {
            mutableStateOf<ImmutableList<User>>(
                persistentListOf(),
            )
        }

    LaunchedEffect(key1 = channelUpdates) {
        launch(Dispatchers.IO) {
            val followingKeySet =
                accountViewModel.account.liveDiscoveryFollowLists.value
                    ?.authors
            val allParticipants =
                ParticipantListBuilder()
                    .followsThatParticipateOn(baseNote, followingKeySet)
                    .toImmutableList()

            val newParticipantUsers =
                if (followingKeySet == null) {
                    val allFollows = accountViewModel.account.liveKind3Follows.value.authors
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
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .clip(QuoteBorder),
                    onState = {
                        if (it is AsyncImagePainter.State.Error) {
                            cover = null
                        }
                    },
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
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
                text = description ?: stringRes(R.string.chat_about_topic, name),
                color = MaterialTheme.colorScheme.grayText,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                fontSize = 14.sp,
                lineHeight = 18.sp,
                modifier = HalfTopPadding,
            )
        },
        onBottomRow = {
            if (participantUsers.isNotEmpty()) {
                Gallery(participantUsers, HalfTopPadding, accountViewModel)
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Gallery(
    users: ImmutableList<User>,
    modifier: Modifier,
    accountViewModel: AccountViewModel,
) {
    FlowRow(modifier, verticalArrangement = Arrangement.Center) {
        users.take(6).forEach { ClickableUserPicture(it, Size25dp, accountViewModel) }

        if (users.size > 6) {
            Text(
                text = " + " + showCount(users.size - 6),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 3.dp).align(CenterVertically),
            )
        }
    }
}

@Composable
fun DisplayAuthorBanner(note: Note) {
    WatchAuthor(note) {
        BannerImage(
            it,
            Modifier
                .fillMaxSize()
                .clip(QuoteBorder),
        )
    }
}
