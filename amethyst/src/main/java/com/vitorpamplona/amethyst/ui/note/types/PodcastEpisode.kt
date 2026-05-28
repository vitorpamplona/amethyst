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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.model.toImmutableListOfLists
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.service.playback.composable.GetVideoController
import com.vitorpamplona.amethyst.service.playback.composable.PauseControllerWhenInBackground
import com.vitorpamplona.amethyst.service.playback.composable.WaveformData
import com.vitorpamplona.amethyst.service.playback.composable.mediaitem.GetMediaItem
import com.vitorpamplona.amethyst.ui.components.TranslatableRichTextViewer
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.theme.Size5dp
import com.vitorpamplona.amethyst.ui.theme.replyModifier
import com.vitorpamplona.quartz.nipF4Podcasts.episode.PodcastEpisodeEvent

// NIP-F4 doesn't carry a waveform tag, so every episode renders with the same flat baseline.
// Hoist to a top-level constant so we share one List<Float>+WaveformData across the whole
// feed instead of allocating a fresh 96-element list per visible card.
private const val WAVEFORM_SAMPLES = 96
private val FLAT_WAVEFORM = WaveformData(List(WAVEFORM_SAMPLES) { 0.4f })

// Bottom-rounded border on the audio player so it visually butts up against the cover's
// top-rounded corners as one card. Constant — keep out of recomposition.
private val PLAYER_BORDER_MODIFIER =
    Modifier.clip(RoundedCornerShape(bottomStart = 15.dp, bottomEnd = 15.dp))

@Composable
fun RenderPodcastEpisode(
    note: Note,
    makeItShort: Boolean,
    canPreview: Boolean,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val noteEvent = note.event as? PodcastEpisodeEvent ?: return

    val title = remember(noteEvent) { noteEvent.title() }
    val image = remember(noteEvent) { noteEvent.image() }
    val description = remember(noteEvent) { noteEvent.description() }
    // Pick the first audio URL. Publishers may emit multiple containers in their preferred
    // order; clients with codec preferences can extend this later.
    val firstAudio = remember(noteEvent) { noteEvent.audios().firstOrNull() }
    // Suppress the markdown block if blank — title + description already describe a short
    // episode. Otherwise hand off to RichText below.
    val markdown = remember(noteEvent) { noteEvent.content.ifBlank { null } }

    Column(MaterialTheme.colorScheme.replyModifier) {
        PodcastCoverCard(image, note, accountViewModel)
        firstAudio?.let { audio ->
            val callbackUri = remember(noteEvent) { note.toNostrUri() }

            Row(
                Modifier.fillMaxWidth().height(80.dp),
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
                            borderModifier = PLAYER_BORDER_MODIFIER,
                            accountViewModel = accountViewModel,
                        )
                    }
                }
            }
        }

        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(Size5dp),
        ) {
            title?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            description?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            markdown?.takeIf { !makeItShort }?.let {
                Spacer(Modifier.padding(top = 4.dp))
                val tags = remember(noteEvent) { noteEvent.tags.toImmutableListOfLists() }
                val callbackUri = remember(noteEvent) { note.toNostrUri() }

                TranslatableRichTextViewer(
                    content = it,
                    canPreview = canPreview,
                    quotesLeft = 1,
                    modifier = Modifier.fillMaxWidth(),
                    tags = tags,
                    backgroundColor = backgroundColor,
                    id = note.idHex,
                    callbackUri = callbackUri,
                    accountViewModel = accountViewModel,
                    nav = nav,
                )
            }
        }
    }
}
