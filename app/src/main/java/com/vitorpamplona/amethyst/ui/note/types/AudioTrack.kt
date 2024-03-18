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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.ui.components.LoadThumbAndThenVideoView
import com.vitorpamplona.amethyst.ui.components.TranslatableRichTextViewer
import com.vitorpamplona.amethyst.ui.components.VideoView
import com.vitorpamplona.amethyst.ui.note.ClickableUserPicture
import com.vitorpamplona.amethyst.ui.note.UsernameDisplay
import com.vitorpamplona.amethyst.ui.note.elements.DisplayUncitedHashtags
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.theme.placeholderText
import com.vitorpamplona.quartz.events.AudioHeaderEvent
import com.vitorpamplona.quartz.events.AudioTrackEvent
import com.vitorpamplona.quartz.events.Participant
import com.vitorpamplona.quartz.events.toImmutableListOfLists
import kotlinx.collections.immutable.toImmutableList
import java.util.Locale

@Composable
fun RenderAudioTrack(
    note: Note,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    val noteEvent = note.event as? AudioTrackEvent ?: return

    AudioTrackHeader(noteEvent, note, accountViewModel, nav)
}

@Composable
fun AudioTrackHeader(
    noteEvent: AudioTrackEvent,
    note: Note,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    val media = remember { noteEvent.media() }
    val cover = remember { noteEvent.cover() }
    val subject = remember { noteEvent.subject() }
    val content = remember { noteEvent.content() }
    val participants = remember { noteEvent.participants() }

    var participantUsers by remember { mutableStateOf<List<Pair<Participant, User>>>(emptyList()) }

    LaunchedEffect(key1 = participants) {
        accountViewModel.loadParticipants(participants) { participantUsers = it }
    }

    Row(modifier = Modifier.padding(top = 5.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row {
                subject?.let {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 5.dp, bottom = 5.dp),
                    ) {
                        Text(
                            text = it,
                            fontWeight = FontWeight.Bold,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            participantUsers.forEach {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier =
                        Modifier
                            .padding(top = 5.dp, start = 10.dp, end = 10.dp)
                            .clickable {
                                nav("User/${it.second.pubkeyHex}")
                            },
                ) {
                    ClickableUserPicture(it.second, 25.dp, accountViewModel)
                    Spacer(Modifier.width(5.dp))
                    UsernameDisplay(it.second, Modifier.weight(1f))
                    Spacer(Modifier.width(5.dp))
                    it.first.role?.let {
                        Text(
                            text = it.capitalize(Locale.ROOT),
                            color = MaterialTheme.colorScheme.placeholderText,
                            maxLines = 1,
                        )
                    }
                }
            }

            media?.let { media ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    cover?.let { cover ->
                        LoadThumbAndThenVideoView(
                            videoUri = media,
                            title = noteEvent.subject(),
                            thumbUri = cover,
                            authorName = note.author?.toBestDisplayName(),
                            roundedCorner = true,
                            nostrUriCallback = "nostr:${note.toNEvent()}",
                            accountViewModel = accountViewModel,
                        )
                    }
                        ?: VideoView(
                            videoUri = media,
                            title = noteEvent.subject(),
                            authorName = note.author?.toBestDisplayName(),
                            roundedCorner = true,
                            accountViewModel = accountViewModel,
                        )
                }
            }
        }
    }
}

@Composable
fun RenderAudioHeader(
    note: Note,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    val noteEvent = note.event as? AudioHeaderEvent ?: return

    AudioHeader(noteEvent, note, accountViewModel, nav)
}

@Composable
fun AudioHeader(
    noteEvent: AudioHeaderEvent,
    note: Note,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    val media = remember { noteEvent.stream() ?: noteEvent.download() }
    val waveform = remember { noteEvent.wavefrom()?.toImmutableList()?.ifEmpty { null } }
    val content = remember { noteEvent.content().ifBlank { null } }

    val defaultBackground = MaterialTheme.colorScheme.background
    val background = remember { mutableStateOf(defaultBackground) }
    val tags = remember(noteEvent) { noteEvent.tags().toImmutableListOfLists() }

    Row(modifier = Modifier.padding(top = 5.dp)) {
        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            media?.let { media ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    VideoView(
                        videoUri = media,
                        waveform = waveform,
                        title = noteEvent.subject(),
                        authorName = note.author?.toBestDisplayName(),
                        roundedCorner = true,
                        accountViewModel = accountViewModel,
                        nostrUriCallback = note.toNostrUri(),
                    )
                }
            }

            content?.let {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 5.dp),
                ) {
                    TranslatableRichTextViewer(
                        content = it,
                        canPreview = true,
                        quotesLeft = 1,
                        tags = tags,
                        backgroundColor = background,
                        id = note.idHex,
                        accountViewModel = accountViewModel,
                        nav = nav,
                    )
                }
            }

            if (noteEvent.hasHashtags()) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    val hashtags = remember(noteEvent) { noteEvent.hashtags().toImmutableList() }
                    DisplayUncitedHashtags(hashtags, content ?: "", nav)
                }
            }
        }
    }
}
