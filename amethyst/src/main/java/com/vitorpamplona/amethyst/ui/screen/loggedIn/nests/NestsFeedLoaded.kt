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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.nests

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.BottomStart
import androidx.compose.ui.Alignment.Companion.TopEnd
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.ui.feeds.FeedState
import com.vitorpamplona.amethyst.model.AddressableNote
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.event.observeNoteAndMap
import com.vitorpamplona.amethyst.ui.actions.CrossfadeIfEnabled
import com.vitorpamplona.amethyst.ui.components.SensitivityWarning
import com.vitorpamplona.amethyst.ui.layouts.rememberFeedContentPadding
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.navigation.routes.routeFor
import com.vitorpamplona.amethyst.ui.note.DisplayAuthorBanner
import com.vitorpamplona.amethyst.ui.note.Gallery
import com.vitorpamplona.amethyst.ui.note.LikeReaction
import com.vitorpamplona.amethyst.ui.note.UserPicture
import com.vitorpamplona.amethyst.ui.note.UsernameDisplay
import com.vitorpamplona.amethyst.ui.note.ZapReaction
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.nip53LiveActivities.EndedFlag
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.nip53LiveActivities.LiveFlag
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.nip53LiveActivities.PrivateFlag
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.nip53LiveActivities.ScheduledFlag
import com.vitorpamplona.amethyst.ui.screen.loggedIn.discover.nip53LiveActivities.LoadParticipants
import com.vitorpamplona.amethyst.ui.screen.loggedIn.nests.datasource.NestRoomFilterAssemblerSubscription
import com.vitorpamplona.amethyst.ui.screen.loggedIn.nests.room.activity.NestActivity
import com.vitorpamplona.amethyst.ui.screen.loggedIn.nests.room.activity.NestBridge
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.ui.theme.DoubleVertSpacer
import com.vitorpamplona.amethyst.ui.theme.FeedPadding
import com.vitorpamplona.amethyst.ui.theme.QuoteBorder
import com.vitorpamplona.amethyst.ui.theme.RowColSpacing
import com.vitorpamplona.amethyst.ui.theme.Size34dp
import com.vitorpamplona.amethyst.ui.theme.Size35dp
import com.vitorpamplona.amethyst.ui.theme.StdHorzSpacer
import com.vitorpamplona.amethyst.ui.theme.StdPadding
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.tags.dTag.dTag
import com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.MeetingSpaceEvent
import com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.tags.StatusTag
import com.vitorpamplona.quartz.nip53LiveActivities.presence.MeetingRoomPresenceEvent
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.tags.ParticipantTag
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NestsFeedLoaded(
    loaded: FeedState.Loaded,
    listState: LazyListState,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val items by loaded.feed.collectAsStateWithLifecycle()

    LazyColumn(
        contentPadding = rememberFeedContentPadding(FeedPadding),
        state = listState,
    ) {
        itemsIndexed(items.list, key = { _, item -> item.idHex }) { _, item ->
            Row(Modifier.fillMaxWidth().animateItem()) {
                NestFeedCard(
                    baseNote = item,
                    modifier = Modifier.fillMaxWidth(),
                    accountViewModel = accountViewModel,
                    nav = nav,
                )
            }

            HorizontalDivider(
                thickness = DividerThickness,
            )
        }
    }
}

/**
 * Audio-rooms list card. Mirrors [ObserveAndRenderSpace] visually and
 * gates a tap on the underlying [MeetingSpaceEvent] by the same liveness
 * heuristic the badge uses: cards rendering as [EndedFlag] (status =
 * ENDED, status = LIVE with stale presence, or unknown status) route
 * through the read-only [NestLobbyScreen] so re-entry can't accidentally
 * boot the audio pipeline or trigger a host-side kind-30312 refresh on
 * a dead room. Cards that the UI still shows as live, scheduled, or
 * private launch [NestActivity] directly — the lobby's purpose is only
 * to gate stale rooms.
 */
@Composable
private fun NestFeedCard(
    baseNote: Note,
    modifier: Modifier,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val meetingEvent = baseNote.event as? MeetingSpaceEvent ?: return
    val addressableNote = baseNote as? AddressableNote ?: return

    val context = LocalContext.current
    val status = meetingEvent.checkStatus(meetingEvent.status())

    val isUiClosed =
        when (status) {
            StatusTag.STATUS.LIVE -> {
                val latestPresence by observeRoomLatestPresence(addressableNote, accountViewModel)
                val presence = latestPresence
                presence != null && presence <= TimeUtils.now() - PRESENCE_FRESHNESS_WINDOW_SECONDS
            }

            StatusTag.STATUS.PLANNED,
            StatusTag.STATUS.PRIVATE,
            -> {
                false
            }

            else -> {
                true
            }
        }

    val onClick =
        remember(meetingEvent, isUiClosed) {
            {
                val service = meetingEvent.service()
                val endpoint = meetingEvent.endpoint()
                val dTag = meetingEvent.address().dTag
                if (!service.isNullOrBlank() && !endpoint.isNullOrBlank() && dTag.isNotBlank()) {
                    if (isUiClosed) {
                        nav.nav(Route.NestLobby(meetingEvent.address().toValue()))
                    } else {
                        NestBridge.set(accountViewModel)
                        NestActivity.launch(
                            context = context,
                            addressValue = meetingEvent.address().toValue(),
                        )
                    }
                } else {
                    nav.nav { routeFor(baseNote, accountViewModel.account) }
                }
            }
        }

    Column(modifier.clickable(onClick = onClick)) {
        Column(StdPadding) {
            SensitivityWarning(
                note = baseNote,
                accountViewModel = accountViewModel,
            ) {
                ObserveAndRenderSpace(addressableNote, accountViewModel, nav)
            }
        }
    }
}

@Composable
fun ObserveAndRenderSpace(
    baseNote: AddressableNote,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val card by observeNoteAndMap(baseNote, accountViewModel) {
        when (val noteEvent = it.event) {
            is MeetingSpaceEvent -> {
                NestCard(
                    id = noteEvent.address(),
                    name = noteEvent.dTag(),
                    cover = noteEvent.image()?.ifBlank { null },
                    media = null,
                    subject = noteEvent.room()?.ifBlank { null },
                    content = noteEvent.summary(),
                    participants = noteEvent.participants().toImmutableList(),
                    status = noteEvent.checkStatus(noteEvent.status()),
                    starts = null,
                )
            }

            else -> {
                null
            }
        }
    }

    card?.let {
        RenderLiveSpacesThumb(
            it,
            baseNote,
            accountViewModel,
            nav,
        )
    }
}

@Immutable
data class NestCard(
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

/**
 * Window inside which a kind-10312 presence event still counts the
 * room as "live": three consecutive 60 s heartbeat periods. Speakers
 * publish presence on join and re-publish every ~60 s while
 * broadcasting, so anything fresher than this means at least one
 * speaker is still in the room. Older means the host crashed
 * without flipping status to CLOSED, and we should demote the badge
 * to "ended" even though the kind-30312 event still says OPEN.
 */
private const val PRESENCE_FRESHNESS_WINDOW_SECONDS = 180L

/**
 * Liveness-gated badge for an OPEN room. Optimistically renders
 * [LiveFlag] on first paint (we have no presence data yet) and
 * keeps it once a fresh kind-10312 presence lands; flips to
 * [EndedFlag] once we observe that the latest cached presence is
 * older than [PRESENCE_FRESHNESS_WINDOW_SECONDS]. Falls back to
 * [LiveFlag] when the room has no addressable id (defensive — every
 * 30312 event has one in practice).
 */
@Composable
private fun RenderLiveOrEndedFromPresence(
    note: AddressableNote?,
    accountViewModel: AccountViewModel,
) {
    if (note == null) {
        LiveFlag()
        return
    }
    val latestPresence by observeRoomLatestPresence(note, accountViewModel)
    val fresh = latestPresence == null || latestPresence!! > TimeUtils.now() - PRESENCE_FRESHNESS_WINDOW_SECONDS
    if (fresh) LiveFlag() else EndedFlag()
}

/**
 * Most recent kind-10312 presence `createdAt` cached for [address],
 * or null until one arrives. Reuses the room's existing assembler
 * subscription (`NestRoomFilterAssemblerSubscription`) — same
 * subscription that warms up when the user actually opens the room
 * — and reads version notes off the room's [LocalCache] channel,
 * which `LocalCache.consume(MeetingRoomPresenceEvent, …)` attaches
 * by `#a=room`. Trade-off: that REQ also pulls chat + reactions, so
 * a thumbnail probe pays a popular room's full message history. If
 * that load matters, swap to a dedicated limit:1 assembler.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Composable
private fun observeRoomLatestPresence(
    note: AddressableNote,
    accountViewModel: AccountViewModel,
): State<Long?> {
    NestRoomFilterAssemblerSubscription(note, accountViewModel)

    val channel = remember(note.idHex) { LocalCache.getOrCreateLiveChannel(note.address) }
    val flow =
        remember(channel) {
            channel
                .flow()
                .notes.stateFlow
                .mapLatest { _ ->
                    var max: Long? = null
                    channel.presenceNotes.forEach { _, note ->
                        val event = note.event
                        if (event is MeetingRoomPresenceEvent) {
                            val createdAt = event.createdAt
                            if (max == null || createdAt > max!!) max = createdAt
                        }
                    }
                    max
                }.distinctUntilChanged()
                .flowOn(Dispatchers.IO)
        }
    return flow.collectAsStateWithLifecycle(null)
}

@Composable
fun RenderLiveSpacesThumb(
    card: NestCard,
    baseNote: AddressableNote,
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
                CrossfadeIfEnabled(targetState = card.status, accountViewModel = accountViewModel) {
                    when (it) {
                        StatusTag.STATUS.LIVE -> {
                            RenderLiveOrEndedFromPresence(baseNote, accountViewModel)
                        }

                        StatusTag.STATUS.ENDED -> {
                            EndedFlag()
                        }

                        StatusTag.STATUS.PLANNED -> {
                            ScheduledFlag(card.starts)
                        }

                        StatusTag.STATUS.PRIVATE -> {
                            PrivateFlag()
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

        SpaceHostAndReactions(
            card.subject,
            baseNote,
            accountViewModel,
            nav,
        )
    }
}

@Composable
fun SpaceHostAndReactions(
    name: String?,
    baseNote: AddressableNote,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        val creator = baseNote.author
        if (creator == null) {
            Text(
                text = stringRes(R.string.wallet_loading),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        } else {
            UserPicture(
                user = creator,
                size = Size34dp,
                accountViewModel = accountViewModel,
                nav = nav,
            )

            Column(
                modifier =
                    Modifier
                        .padding(start = 10.dp)
                        .height(35.dp)
                        .weight(1f),
                verticalArrangement = Arrangement.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!name.isNullOrBlank()) {
                        Text(
                            text = name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    } else {
                        UsernameDisplay(creator, accountViewModel = accountViewModel)
                    }
                }
            }
        }

        Row(
            modifier =
                Modifier
                    .height(Size35dp)
                    .padding(start = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = RowColSpacing,
        ) {
            LikeReaction(
                baseNote = baseNote,
                grayTint = MaterialTheme.colorScheme.onSurface,
                accountViewModel = accountViewModel,
                nav = nav,
            )
            Spacer(modifier = StdHorzSpacer)
            ZapReaction(
                baseNote = baseNote,
                grayTint = MaterialTheme.colorScheme.onSurface,
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }
    }
}
