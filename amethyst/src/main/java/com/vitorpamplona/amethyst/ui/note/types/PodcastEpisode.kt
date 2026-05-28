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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.model.toImmutableListOfLists
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.service.playback.composable.GetVideoController
import com.vitorpamplona.amethyst.service.playback.composable.PauseControllerWhenInBackground
import com.vitorpamplona.amethyst.service.playback.composable.WaveformData
import com.vitorpamplona.amethyst.service.playback.composable.mediaitem.GetMediaItem
import com.vitorpamplona.amethyst.ui.components.MyAsyncImage
import com.vitorpamplona.amethyst.ui.components.TranslatableRichTextViewer
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.note.elements.DefaultImageHeader
import com.vitorpamplona.amethyst.ui.note.elements.DefaultImageHeaderBackground
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.Size5dp
import com.vitorpamplona.amethyst.ui.theme.replyModifier
import com.vitorpamplona.quartz.nipF4Podcasts.episode.PodcastEpisodeEvent

private val COVER_ASPECT_RATIO = 1f

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

    PodcastEpisodeBody(
        noteEvent = noteEvent,
        note = note,
        makeItShort = makeItShort,
        canPreview = canPreview,
        backgroundColor = backgroundColor,
        accountViewModel = accountViewModel,
        nav = nav,
    )
}

@Composable
private fun PodcastEpisodeBody(
    noteEvent: PodcastEpisodeEvent,
    note: Note,
    makeItShort: Boolean,
    canPreview: Boolean,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val title = remember(noteEvent) { noteEvent.title() }
    val image = remember(noteEvent) { noteEvent.image() }
    val description = remember(noteEvent) { noteEvent.description() }
    // Pick the first audio URL. If a publisher provides multiple containers, this is the
    // ordering they emitted — clients with codec preferences can extend this later.
    val firstAudio = remember(noteEvent) { noteEvent.audios().firstOrNull() }
    // Suppress the markdown content block if it's blank (the title + description already
    // describe a short episode); otherwise let RichText render it below.
    val markdown =
        remember(noteEvent) {
            noteEvent.content.ifBlank { null }
        }

    Column(MaterialTheme.colorScheme.replyModifier) {
        PodcastEpisodeCover(image, note, accountViewModel)
        firstAudio?.let { audio ->
            // No waveform tag in NIP-F4, synthesize a flat baseline. ExoPlayer drives
            // actual playback progress; the bars are purely decorative.
            val waveform = remember(note.idHex) { flatWaveform() }
            val callbackUri = remember(note) { note.toNostrUri() }
            val playerBorder =
                remember {
                    Modifier.clip(RoundedCornerShape(bottomStart = 15.dp, bottomEnd = 15.dp))
                }

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
                    waveformData = waveform,
                ) { mediaItem ->
                    GetVideoController(
                        mediaItem = mediaItem,
                        muted = false,
                    ) { controller ->
                        PauseControllerWhenInBackground(controller)
                        RenderVoicePlayer(
                            mediaItem = mediaItem,
                            controllerState = controller,
                            waveform = waveform,
                            borderModifier = playerBorder,
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
                val callbackUri = remember(note) { note.toNostrUri() }

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

@Composable
private fun PodcastEpisodeCover(
    image: String?,
    note: Note,
    accountViewModel: AccountViewModel,
) {
    val imageShape = RoundedCornerShape(topStart = 15.dp, topEnd = 15.dp)
    val imageModifier =
        Modifier
            .fillMaxWidth()
            .aspectRatio(COVER_ASPECT_RATIO)
            .clip(imageShape)

    Box(imageModifier) {
        if (image != null) {
            MyAsyncImage(
                imageUrl = image,
                contentDescription = stringRes(R.string.preview_card_image_for, image),
                contentScale = ContentScale.Crop,
                mainImageModifier = Modifier.fillMaxSize(),
                loadedImageModifier = imageModifier,
                accountViewModel = accountViewModel,
                onLoadingBackground = { DefaultImageHeaderBackground(note, accountViewModel, imageModifier) },
                onError = { DefaultImageHeader(note, accountViewModel, imageModifier) },
            )
        } else {
            DefaultImageHeader(note, accountViewModel, imageModifier)
        }
    }
}

private const val WAVEFORM_SAMPLES = 96

private fun flatWaveform(): WaveformData =
    // Even baseline — purely decorative since NIP-F4 doesn't carry waveform data.
    WaveformData(List(WAVEFORM_SAMPLES) { 0.4f })
