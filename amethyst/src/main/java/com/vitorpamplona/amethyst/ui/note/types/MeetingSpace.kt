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
package com.vitorpamplona.amethyst.ui.note.types

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.event.observeNote
import com.vitorpamplona.amethyst.ui.actions.CrossfadeIfEnabled
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.routeFor
import com.vitorpamplona.amethyst.ui.note.ClickableUserPicture
import com.vitorpamplona.amethyst.ui.note.UsernameDisplay
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.nip53LiveActivities.LiveFlag
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.nip53LiveActivities.ScheduledFlag
import com.vitorpamplona.amethyst.ui.screen.loggedIn.home.CrossfadeCheckIfVideoIsOnline
import com.vitorpamplona.amethyst.ui.screen.loggedIn.notifications.equalImmutableLists
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.SmallBorder
import com.vitorpamplona.amethyst.ui.theme.StdHorzSpacer
import com.vitorpamplona.amethyst.ui.theme.placeholderText
import com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.MeetingRoomEvent
import com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.MeetingSpaceEvent
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.tags.ParticipantTag
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.tags.StatusTag
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.tags.StatusTag as MeetingSpaceStatusTag

@Composable
fun RenderMeetingSpaceEvent(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Row(modifier = Modifier.padding(top = 5.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            RenderMeetingSpaceEventInner(baseNote = baseNote, accountViewModel, nav)
        }
    }
}

@Composable
fun RenderMeetingSpaceEventInner(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val noteEvent = baseNote.event as? MeetingSpaceEvent ?: return

    val eventUpdates by observeNote(baseNote, accountViewModel)

    val roomName = remember(eventUpdates) { noteEvent.room() }
    val summary = remember(eventUpdates) { noteEvent.summary() }
    val status = remember(eventUpdates) { noteEvent.status() }
    val starts = remember(eventUpdates) { noteEvent.starts() }
    val recording = remember(eventUpdates) { noteEvent.recording() }
    val participants = remember(eventUpdates) { noteEvent.participants() }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .padding(vertical = 5.dp)
                .fillMaxWidth(),
    ) {
        roomName?.let {
            Text(
                text = it,
                fontWeight = FontWeight.Bold,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(modifier = StdHorzSpacer)

        CrossfadeIfEnabled(targetState = status, label = "MeetingSpaceStatus", accountViewModel = accountViewModel) {
            when (it) {
                MeetingSpaceStatusTag.STATUS.OPEN -> {
                    MeetingSpaceOpenFlag()
                }

                MeetingSpaceStatusTag.STATUS.PRIVATE -> {
                    MeetingSpacePrivateFlag()
                }

                MeetingSpaceStatusTag.STATUS.CLOSED -> {
                    MeetingSpaceClosedFlag()
                }

                MeetingSpaceStatusTag.STATUS.PLANNED -> {
                    MeetingSpacePlannedFlag(startsUnixSec = starts)
                }

                null -> {}
            }
        }
    }

    summary?.let {
        Text(
            text = it,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.placeholderText,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp),
        )
    }

    RenderParticipants(participants, accountViewModel, nav)

    // In-feed CTA. Live / scheduled rooms get a Join button; closed
    // rooms with a recording get a "Listen to recording" button so
    // audience members who missed the live session can listen back.
    // Closed rooms without a recording stay button-less — there's
    // nothing to enter.
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.End,
    ) {
        if (status == MeetingSpaceStatusTag.STATUS.CLOSED) {
            recording?.let {
                ListenToRecordingButton(url = it, accountViewModel = accountViewModel)
            }
        } else {
            com.vitorpamplona.amethyst.ui.screen.loggedIn.nests.room.JoinNestButton(
                event = noteEvent,
                accountViewModel = accountViewModel,
            )
        }
    }
}

/**
 * Hand off a kind-30312 `recording` URL to the system media
 * player via ACTION_VIEW. Most users have a podcast / audio app
 * registered for `https://...mp3`-style URLs; the system picker
 * lets them choose. Stays out of the in-app audio path on
 * purpose — Amethyst doesn't ship its own audio player surface.
 */
@Composable
private fun ListenToRecordingButton(
    url: String,
    accountViewModel: AccountViewModel,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val noAppMessage = stringRes(R.string.nest_no_app_to_open_link)
    androidx.compose.material3.OutlinedButton(onClick = {
        val launched =
            runCatching {
                context.startActivity(
                    android.content
                        .Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }.isSuccess
        if (!launched) {
            accountViewModel.toastManager.toast(
                R.string.nest_chat_send_failed_title,
                noAppMessage,
                user = null,
            )
        }
    }) {
        Text(stringRes(R.string.nest_listen_to_recording))
    }
}

@Composable
fun RenderMeetingRoomEvent(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Row(modifier = Modifier.padding(top = 5.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            RenderMeetingRoomEventInner(baseNote = baseNote, accountViewModel, nav)
        }
    }
}

@Composable
fun RenderMeetingRoomEventInner(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val noteEvent = baseNote.event as? MeetingRoomEvent ?: return

    val eventUpdates by observeNote(baseNote, accountViewModel)

    val subject = remember(eventUpdates) { noteEvent.title() }
    val media = remember(eventUpdates) { noteEvent.streaming() }
    val status = remember(eventUpdates) { noteEvent.status() }
    val starts = remember(eventUpdates) { noteEvent.starts() }
    val participants = remember(eventUpdates) { noteEvent.participants() }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .padding(vertical = 5.dp)
                .fillMaxWidth(),
    ) {
        subject?.let {
            Text(
                text = it,
                fontWeight = FontWeight.Bold,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(modifier = StdHorzSpacer)

        CrossfadeIfEnabled(targetState = status, label = "MeetingRoomStatus", accountViewModel = accountViewModel) {
            when (it) {
                StatusTag.STATUS.LIVE -> {
                    media?.let {
                        CrossfadeCheckIfVideoIsOnline(it, accountViewModel) {
                            LiveFlag()
                        }
                    } ?: LiveFlag()
                }

                StatusTag.STATUS.PLANNED -> {
                    ScheduledFlag(starts)
                }

                StatusTag.STATUS.ENDED -> {}

                null -> {}
            }
        }
    }

    RenderParticipants(participants, accountViewModel, nav)
}

@Composable
private fun RenderParticipants(
    participants: List<ParticipantTag>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    var participantUsers by remember {
        mutableStateOf<ImmutableList<Pair<ParticipantTag, User>>>(
            persistentListOf(),
        )
    }

    LaunchedEffect(key1 = participants) {
        accountViewModel.loadParticipants(participants) { newParticipantUsers ->
            if (!equalImmutableLists(newParticipantUsers, participantUsers)) {
                participantUsers = newParticipantUsers
            }
        }
    }

    val locale = LocalConfiguration.current.locales.get(0)

    participantUsers.forEach {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .padding(vertical = 5.dp)
                    .clickable { nav.nav(routeFor(it.second)) },
        ) {
            ClickableUserPicture(it.second, 25.dp, accountViewModel)
            Spacer(StdHorzSpacer)
            UsernameDisplay(it.second, Modifier.weight(1f), accountViewModel = accountViewModel)
            Spacer(StdHorzSpacer)
            it.first.role?.let {
                Text(
                    text =
                        it.replaceFirstChar {
                            if (it.isLowerCase()) it.titlecase(locale) else it.toString()
                        },
                    color = MaterialTheme.colorScheme.placeholderText,
                    maxLines = 1,
                )
            }
        }
    }
}

private val MeetingSpaceOpenModifier =
    Modifier
        .clip(SmallBorder)
        .background(Color(0xFF4CAF50))
        .padding(horizontal = 5.dp)

private val MeetingSpacePrivateModifier =
    Modifier
        .clip(SmallBorder)
        .background(Color(0xFFFF9800))
        .padding(horizontal = 5.dp)

private val MeetingSpaceClosedModifier =
    Modifier
        .clip(SmallBorder)
        .background(Color.Black)
        .padding(horizontal = 5.dp)

@Composable
fun MeetingSpaceOpenFlag() {
    Text(
        text = stringRes(id = R.string.meeting_space_open_tag),
        color = Color.White,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        modifier = MeetingSpaceOpenModifier,
    )
}

@Composable
fun MeetingSpacePrivateFlag() {
    Text(
        text = stringRes(id = R.string.meeting_space_private_tag),
        color = Color.White,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        modifier = MeetingSpacePrivateModifier,
    )
}

@Composable
fun MeetingSpaceClosedFlag() {
    Text(
        text = stringRes(id = R.string.meeting_space_closed_tag),
        color = Color.White,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        modifier = MeetingSpaceClosedModifier,
    )
}

/**
 * Status badge for kind-30312 rooms with `status=planned`. Renders
 * "SCHEDULED" plus a localized "Starts <human-time>" subline when
 * the event ships a `["starts", "<unix-seconds>"]` tag. Falls back
 * to just "SCHEDULED" when the start time is missing or in the
 * past — matches nostrnests' web client (which also pivots to
 * "Live now" once the moment passes).
 */
@Composable
fun MeetingSpacePlannedFlag(startsUnixSec: Long?) {
    Column(horizontalAlignment = Alignment.End) {
        Text(
            text = stringRes(id = R.string.meeting_space_planned_tag),
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            modifier =
                remember {
                    Modifier
                        .clip(SmallBorder)
                        .background(Color(0xFF1E88E5))
                        .padding(horizontal = 5.dp)
                },
        )
        val now = System.currentTimeMillis() / 1000L
        if (startsUnixSec != null && startsUnixSec > now) {
            val pretty =
                remember(startsUnixSec) {
                    java.text.DateFormat
                        .getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT)
                        .format(java.util.Date(startsUnixSec * 1000L))
                }
            Text(
                text = stringRes(R.string.meeting_space_planned_starts_at, pretty),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
