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
package com.vitorpamplona.amethyst.ui.note.types

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.ui.actions.CrossfadeIfEnabled
import com.vitorpamplona.amethyst.ui.components.VideoView
import com.vitorpamplona.amethyst.ui.components.mockAccountViewModel
import com.vitorpamplona.amethyst.ui.note.ClickableUserPicture
import com.vitorpamplona.amethyst.ui.note.DisplayAuthorBanner
import com.vitorpamplona.amethyst.ui.note.UsernameDisplay
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chatrooms.LiveFlag
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chatrooms.ScheduledFlag
import com.vitorpamplona.amethyst.ui.screen.loggedIn.home.CheckIfUrlIsOnline
import com.vitorpamplona.amethyst.ui.screen.loggedIn.home.CrossfadeCheckIfUrlIsOnline
import com.vitorpamplona.amethyst.ui.screen.loggedIn.notifications.equalImmutableLists
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.StdHorzSpacer
import com.vitorpamplona.amethyst.ui.theme.ThemeComparisonColumn
import com.vitorpamplona.amethyst.ui.theme.imageModifier
import com.vitorpamplona.amethyst.ui.theme.placeholderText
import com.vitorpamplona.quartz.events.Event
import com.vitorpamplona.quartz.events.LiveActivitiesEvent
import com.vitorpamplona.quartz.events.Participant
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.util.Locale

@Composable
@Preview
fun RenderLiveActivityEventPreview() {
    val event = Event.fromJson("{\"id\":\"19406ad34ce3c653d62eb73c1816ac27dcf473c2ccdccf5af7d90d2633c62561\",\"pubkey\":\"6b66886b3add72c779d205be574ec2d7cec619061ac3e75717389b26445989e4\",\"created_at\":1719084750,\"kind\":30311,\"tags\":[[\"r\",\"podcast:guid:72d5e069-f907-5ee7-b0d7-45404f4f0aa5\"],[\"r\",\"podcast:item:guid:bfc33d6e-e00f-4f11-a2ff-94865b7867aa\"],[\"d\",\"bfc33d6e-e00f-4f11-a2ff-94865b7867aa\"],[\"title\",\"The Online Identity Time Bomb\"],[\"summary\",\"Online identity is a ticking time bomb. But are trustworthy, open-source solutions ready to disarm it, or will we be stuck with lackluster, proprietary systems?\\n Live chat: https:/jblive.tv\"],[\"streaming\",\"https://jblive.fm\"],[\"starts\",\"1719167400\"],[\"status\",\"planned\"],[\"image\",\"https://station.us-iad-1.linodeobjects.com/art/lup-mp3.jpg\"]],\"content\":\"\",\"sig\":\"2ce3fae9ad4512541aaae4dbd9484f50df62ab95ba935d7512b736f087f151c1d15ec2bf62d3135474f16c3e070f335b24349c5493461873ddca3051804ca944\"}") as LiveActivitiesEvent
    val accountViewModel = mockAccountViewModel()
    val nav: (String) -> Unit = {}
    val baseNote: Note?

    runBlocking {
        withContext(Dispatchers.IO) {
            LocalCache.justConsume(event, null)
            baseNote = LocalCache.getOrCreateNote("19406ad34ce3c653d62eb73c1816ac27dcf473c2ccdccf5af7d90d2633c62561")
        }
    }

    if (baseNote != null) {
        ThemeComparisonColumn(
            toPreview = {
                RenderLiveActivityEvent(baseNote = baseNote, accountViewModel, nav)
            },
        )
    }
}

@Composable
fun RenderLiveActivityEvent(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    Row(modifier = Modifier.padding(top = 5.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            RenderLiveActivityEventInner(baseNote = baseNote, accountViewModel, nav)
        }
    }
}

@Composable
fun RenderLiveActivityEventInner(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    val noteEvent = baseNote.event as? LiveActivitiesEvent ?: return

    val eventUpdates by baseNote.live().metadata.observeAsState()

    val media = remember(eventUpdates) { noteEvent.streaming() }
    val cover = remember(eventUpdates) { noteEvent.image() }
    val subject = remember(eventUpdates) { noteEvent.title() }
    val content = remember(eventUpdates) { noteEvent.summary() }
    val participants = remember(eventUpdates) { noteEvent.participants() }
    val status = remember(eventUpdates) { noteEvent.status() }
    val starts = remember(eventUpdates) { noteEvent.starts() }

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

        CrossfadeIfEnabled(targetState = status, label = "RenderLiveActivityEventInner", accountViewModel = accountViewModel) {
            when (it) {
                LiveActivitiesEvent.STATUS_LIVE -> {
                    media?.let { CrossfadeCheckIfUrlIsOnline(it, accountViewModel) { LiveFlag() } }
                }

                LiveActivitiesEvent.STATUS_PLANNED -> {
                    ScheduledFlag(starts)
                }
            }
        }
    }

    media?.let { media ->
        if (status == LiveActivitiesEvent.STATUS_LIVE) {
            CheckIfUrlIsOnline(media, accountViewModel) { isOnline ->
                if (isOnline) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        VideoView(
                            videoUri = media,
                            mimeType = null,
                            title = subject,
                            artworkUri = cover,
                            authorName = baseNote.author?.toBestDisplayName(),
                            roundedCorner = true,
                            isFiniteHeight = false,
                            accountViewModel = accountViewModel,
                            nostrUriCallback = "nostr:${baseNote.toNEvent()}",
                        )
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier =
                            Modifier
                                .padding(10.dp)
                                .height(100.dp),
                    ) {
                        Text(
                            text = stringRes(id = R.string.live_stream_is_offline),
                            color = MaterialTheme.colorScheme.onBackground,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        } else {
            if (status == LiveActivitiesEvent.STATUS_ENDED || (status == LiveActivitiesEvent.STATUS_PLANNED && (starts ?: 0) < TimeUtils.eightHoursAgo())) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier =
                        Modifier
                            .padding(vertical = 10.dp)
                            .heightIn(min = 100.dp),
                ) {
                    cover?.let {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AsyncImage(model = it, contentDescription = null, modifier = MaterialTheme.colorScheme.imageModifier)
                        }
                    } ?: run { DisplayAuthorBanner(baseNote) }

                    Text(
                        text = stringRes(id = R.string.live_stream_has_ended),
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }

    var participantUsers by remember {
        mutableStateOf<ImmutableList<Pair<Participant, User>>>(
            persistentListOf(),
        )
    }

    LaunchedEffect(key1 = eventUpdates) {
        accountViewModel.loadParticipants(participants) { newParticipantUsers ->
            if (!equalImmutableLists(newParticipantUsers, participantUsers)) {
                participantUsers = newParticipantUsers
            }
        }
    }

    participantUsers.forEach {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .padding(vertical = 5.dp)
                    .clickable { nav("User/${it.second.pubkeyHex}") },
        ) {
            ClickableUserPicture(it.second, 25.dp, accountViewModel)
            Spacer(StdHorzSpacer)
            UsernameDisplay(it.second, Modifier.weight(1f), accountViewModel = accountViewModel)
            Spacer(StdHorzSpacer)
            it.first.role?.let {
                Text(
                    text = it.capitalize(Locale.ROOT),
                    color = MaterialTheme.colorScheme.placeholderText,
                    maxLines = 1,
                )
            }
        }
    }
}
