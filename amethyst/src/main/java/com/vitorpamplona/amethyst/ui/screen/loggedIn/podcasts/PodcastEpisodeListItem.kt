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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.podcasts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.service.playback.composable.GetVideoController
import com.vitorpamplona.amethyst.service.playback.composable.PauseControllerWhenInBackground
import com.vitorpamplona.amethyst.service.playback.composable.WaveformData
import com.vitorpamplona.amethyst.service.playback.composable.mediaitem.GetMediaItem
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.routeFor
import com.vitorpamplona.amethyst.ui.note.timeAgo
import com.vitorpamplona.amethyst.ui.note.types.RenderVoicePlayer
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.theme.Size5dp
import com.vitorpamplona.amethyst.ui.theme.grayText
import com.vitorpamplona.quartz.nipF4Podcasts.episode.PodcastEpisodeEvent

// NIP-F4 episodes carry no waveform tag, so render a flat baseline. Shared at file scope so
// every visible row points at the same List<Float> instead of allocating a fresh one per card.
private const val WAVEFORM_SAMPLES = 96
private val FLAT_WAVEFORM = WaveformData(List(WAVEFORM_SAMPLES) { 0.4f })

private val PLAYER_SHAPE = Modifier.clip(RoundedCornerShape(12.dp))

/**
 * One episode as a compact "show list" row inside a podcast's screen: publish date, title,
 * short description, and an inline audio player so the episode plays without leaving the list.
 * The show artwork is the same for every episode, so it's omitted here — the header carries it.
 */
@Composable
fun PodcastEpisodeListItem(
    note: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val noteEvent = note.event as? PodcastEpisodeEvent ?: return

    val title = remember(noteEvent) { noteEvent.title() }
    val description = remember(noteEvent) { noteEvent.description() }
    val firstAudio = remember(noteEvent) { noteEvent.audios().firstOrNull() }
    val image = remember(noteEvent) { noteEvent.image() }

    val context = LocalContext.current
    val dateStr = remember(noteEvent) { timeAgo(noteEvent.createdAt, context, prefix = "") }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { routeFor(note, accountViewModel.account)?.let { nav.nav(it) } }
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(Size5dp),
    ) {
        dateStr.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.grayText,
            )
        }

        title?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        description?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.grayText,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        firstAudio?.let { audio ->
            val callbackUri = remember(noteEvent) { note.toNostrUri() }

            Row(
                Modifier.fillMaxWidth().height(72.dp).padding(top = Size5dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GetMediaItem(
                    videoUri = audio.url,
                    title = title,
                    artworkUri = image,
                    authorName = note.author?.toBestDisplayName(),
                    callbackUri = callbackUri,
                    mimeType = audio.mediaType,
                    aspectRatio = null,
                    proxyPort = accountViewModel.httpClientBuilder.proxyPortForVideo(audio.url),
                    keepPlaying = false,
                    waveformData = FLAT_WAVEFORM,
                ) { mediaItem ->
                    GetVideoController(
                        mediaItem = mediaItem,
                        muted = false,
                    ) { controller ->
                        PauseControllerWhenInBackground(controller)
                        RenderVoicePlayer(
                            mediaItem = mediaItem,
                            controllerState = controller,
                            waveform = FLAT_WAVEFORM,
                            borderModifier = PLAYER_SHAPE,
                            accountViewModel = accountViewModel,
                        )
                    }
                }
            }
        }
    }
}
