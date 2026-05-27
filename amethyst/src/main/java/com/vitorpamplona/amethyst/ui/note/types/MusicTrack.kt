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
import androidx.compose.foundation.shape.CircleShape
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
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.toImmutableListOfLists
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.service.playback.composable.LoadThumbAndThenVideoView
import com.vitorpamplona.amethyst.service.playback.composable.VideoView
import com.vitorpamplona.amethyst.ui.components.MyAsyncImage
import com.vitorpamplona.amethyst.ui.components.TranslatableRichTextViewer
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.note.elements.DefaultImageHeader
import com.vitorpamplona.amethyst.ui.note.elements.DefaultImageHeaderBackground
import com.vitorpamplona.amethyst.ui.note.elements.DisplayUncitedHashtags
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.Font10SP
import com.vitorpamplona.amethyst.ui.theme.Size5dp
import com.vitorpamplona.amethyst.ui.theme.grayText
import com.vitorpamplona.amethyst.ui.theme.replyModifier
import com.vitorpamplona.quartz.experimental.music.track.MusicTrackEvent
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.hasHashtags
import kotlinx.collections.immutable.toImmutableList

private val COVER_ASPECT_RATIO = 1f

@Composable
fun RenderMusicTrack(
    note: Note,
    makeItShort: Boolean,
    canPreview: Boolean,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val noteEvent = note.event as? MusicTrackEvent ?: return

    MusicTrackHeader(
        noteEvent = noteEvent,
        note = note,
        makeItShort = makeItShort,
        canPreview = canPreview,
        backgroundColor = backgroundColor,
        accountViewModel = accountViewModel,
        nav = nav,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MusicTrackHeader(
    noteEvent: MusicTrackEvent,
    note: Note,
    makeItShort: Boolean,
    canPreview: Boolean,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val title = remember(noteEvent) { noteEvent.title() }
    val artist = remember(noteEvent) { noteEvent.artist() }
    val url = remember(noteEvent) { noteEvent.url() }
    val videoUrl = remember(noteEvent) { noteEvent.videoUrl() }
    val image = remember(noteEvent) { noteEvent.image() }
    val album = remember(noteEvent) { noteEvent.album() }
    val trackNumber = remember(noteEvent) { noteEvent.trackNumber() }
    val released = remember(noteEvent) { noteEvent.released() }
    val duration = remember(noteEvent) { noteEvent.duration() }
    val format = remember(noteEvent) { noteEvent.format() }
    val isExplicit = remember(noteEvent) { noteEvent.isExplicit() }
    val description = remember(noteEvent) { noteEvent.content.ifBlank { null } }
    val topics =
        remember(noteEvent) {
            noteEvent.tags
                .mapNotNull { if (it.size > 1 && it[0] == "t" && it[1] != MusicTrackEvent.GENRE_TAG) it[1] else null }
                .distinct()
                .take(4)
                .toImmutableList()
        }
    val playableUri = videoUrl ?: url
    val mimeType =
        remember(format, videoUrl) {
            when {
                videoUrl != null -> "video/${format ?: "mp4"}"
                format != null -> "audio/$format"
                else -> null
            }
        }

    Column(MaterialTheme.colorScheme.replyModifier) {
        if (playableUri != null) {
            if (image != null) {
                LoadThumbAndThenVideoView(
                    videoUri = playableUri,
                    mimeType = mimeType,
                    title = title,
                    thumbUri = image,
                    authorName = note.author?.toBestDisplayName(),
                    roundedCorner = true,
                    contentScale = ContentScale.FillWidth,
                    nostrUriCallback = "nostr:${note.toNEvent()}",
                    accountViewModel = accountViewModel,
                )
            } else {
                VideoView(
                    videoUri = playableUri,
                    mimeType = mimeType,
                    title = title,
                    authorName = note.author?.toBestDisplayName(),
                    roundedCorner = true,
                    contentScale = ContentScale.FillWidth,
                    nostrUriCallback = "nostr:${note.toNEvent()}",
                    accountViewModel = accountViewModel,
                )
            }
        } else {
            MusicTrackCover(image, note, accountViewModel)
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

            artist?.let {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        symbol = MaterialSymbols.MusicNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.grayText,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.padding(start = 6.dp))
                    Text(
                        text = it,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            if (album != null || released != null || duration != null || isExplicit) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    album?.let {
                        val trackPart = trackNumber?.let { n -> " · #$n" } ?: ""
                        MetaText(text = it + trackPart)
                    }
                    if (album != null && (released != null || duration != null || isExplicit)) MetaSeparator()
                    released?.let { MetaText(text = it) }
                    if (released != null && (duration != null || isExplicit)) MetaSeparator()
                    duration?.let { MetaText(text = formatDuration(it)) }
                    if (duration != null && isExplicit) MetaSeparator()
                    if (isExplicit) ExplicitBadge()
                }
            }

            description?.takeIf { !makeItShort }?.let {
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

                if (noteEvent.hasHashtags()) {
                    Row(Modifier.fillMaxWidth()) {
                        DisplayUncitedHashtags(noteEvent, it, callbackUri, accountViewModel, nav)
                    }
                }
            }

            if (topics.isNotEmpty()) {
                Spacer(Modifier.padding(top = 4.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Size5dp),
                    verticalArrangement = Arrangement.spacedBy(Size5dp),
                ) {
                    topics.forEach { TopicChip(it) }
                }
            }
        }
    }
}

@Composable
private fun MusicTrackCover(
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

@Composable
private fun MetaText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.grayText,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun MetaSeparator() {
    Text(
        text = " · ",
        color = MaterialTheme.colorScheme.grayText,
        style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
private fun ExplicitBadge() {
    Text(
        text = "E",
        style = MaterialTheme.typography.labelSmall,
        fontSize = Font10SP,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier =
            Modifier
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
                .padding(horizontal = 4.dp, vertical = 1.dp),
    )
}

@Composable
private fun TopicChip(topic: String) {
    Text(
        text = "#$topic",
        style = MaterialTheme.typography.labelSmall,
        fontSize = Font10SP,
        fontWeight = FontWeight.Normal,
        color = MaterialTheme.colorScheme.grayText,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier =
            Modifier
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

private fun formatDuration(seconds: Int): String {
    val minutes = seconds / 60
    val secs = seconds % 60
    return "%d:%02d".format(minutes, secs)
}
