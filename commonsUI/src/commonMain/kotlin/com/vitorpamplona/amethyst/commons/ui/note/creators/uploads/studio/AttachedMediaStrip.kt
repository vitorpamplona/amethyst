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
package com.vitorpamplona.amethyst.commons.ui.note.creators.uploads.studio

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.media_alt_badge
import com.vitorpamplona.amethyst.commons.resources.media_cw_badge
import com.vitorpamplona.amethyst.commons.resources.media_marker_file
import com.vitorpamplona.amethyst.commons.resources.media_marker_missing
import com.vitorpamplona.amethyst.commons.resources.media_marker_photo
import com.vitorpamplona.amethyst.commons.resources.media_marker_video
import com.vitorpamplona.amethyst.commons.resources.media_post_nothing_posted
import com.vitorpamplona.amethyst.commons.resources.media_post_upload_failed
import com.vitorpamplona.amethyst.commons.resources.remove
import com.vitorpamplona.amethyst.commons.service.uploads.SelectedMedia
import com.vitorpamplona.amethyst.commons.service.uploads.SelectedMediaProcessing
import com.vitorpamplona.amethyst.commons.service.uploads.UploadingState
import com.vitorpamplona.amethyst.commons.ui.actions.MediaMarkerChips
import com.vitorpamplona.amethyst.commons.ui.actions.uploads.rememberVideoThumbnail
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.uploads.errorResource

/** "Photo 2", "Video 1", "File 3": what a file's marker and its tile are called. */
@Composable
fun mediaLabel(
    media: SelectedMedia,
    ordinal: Int,
): String =
    when {
        media.isVideo() == true -> stringRes(Res.string.media_marker_video, ordinal)
        media.isImage() == true -> stringRes(Res.string.media_marker_photo, ordinal)
        else -> stringRes(Res.string.media_marker_file, ordinal)
    }

/**
 * Draws a composer's media markers as chips named like their tiles. The label runs outside
 * composition (inside the field's OutputTransformation), so the strings are resolved here and
 * only the number is filled in there.
 */
@Composable
fun rememberMediaMarkerChips(attached: (Int) -> SelectedMedia?): MediaMarkerChips {
    val photo = stringRes(Res.string.media_marker_photo)
    val video = stringRes(Res.string.media_marker_video)
    val file = stringRes(Res.string.media_marker_file)
    val missing = stringRes(Res.string.media_marker_missing)
    val chip = MaterialTheme.colorScheme.secondaryContainer
    val onChip = MaterialTheme.colorScheme.onSecondaryContainer
    return remember(photo, video, file, missing, chip, onChip, attached) {
        MediaMarkerChips(chip, onChip) { id, ordinal ->
            val media = attached(id)
            val template =
                when {
                    media == null -> return@MediaMarkerChips missing
                    media.isVideo() == true -> video
                    media.isImage() == true -> photo
                    else -> file
                }
            template.replace("%1\$d", ordinal.toString())
        }
    }
}

/**
 * The files attached to a post, in the order of their markers in the text and numbered like them.
 * Each tile shows what matters before Post: the picture, whether it has alt text, a content warning,
 * and, while Post runs, its upload. Tapping a tile reopens it in the media studio. When an upload
 * failed, a banner names the file and why; nothing was posted.
 */
@Composable
fun AttachedMediaStrip(
    items: List<SelectedMediaProcessing>,
    locked: Boolean,
    onOpen: (SelectedMediaProcessing) -> Unit,
    onRemove: (SelectedMediaProcessing) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FailedUploadBanner(items)
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items.forEachIndexed { index, item ->
                key(item) { AttachedTile(item, index + 1, locked, { onOpen(item) }, { onRemove(item) }) }
            }
        }
    }
}

@Composable
private fun FailedUploadBanner(items: List<SelectedMediaProcessing>) {
    // Each file's state is a flow: collect them all, then find the first that failed.
    val states =
        items.map {
            it.orchestrator.progressState
                .collectAsState()
                .value
        }
    val index = states.indexOfFirst { it is UploadingState.Error }
    if (index < 0) return
    val error = states[index] as UploadingState.Error
    val name = mediaLabel(items[index].media, index + 1)
    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(MaterialSymbols.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    stringRes(Res.string.media_post_upload_failed, name) + ": " + stringRes(error.errorResource, *error.params),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(stringRes(Res.string.media_post_nothing_posted), color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun AttachedTile(
    item: SelectedMediaProcessing,
    ordinal: Int,
    locked: Boolean,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
) {
    val state by item.orchestrator.progressState.collectAsState()
    val progress by item.orchestrator.progress.collectAsState()
    val shape = RoundedCornerShape(12.dp)
    val media = item.media
    val label = mediaLabel(media, ordinal)
    Box(
        Modifier
            .size(112.dp)
            .clip(shape)
            .border(if (state is UploadingState.Error) 2.dp else 1.dp, if (state is UploadingState.Error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable(enabled = !locked, onClick = onOpen)
            .semantics { contentDescription = label },
    ) {
        when {
            media.isImage() == true -> AsyncImage(media.uri.toString(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            media.isVideo() == true ->
                rememberVideoThumbnail(media)?.let { Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                    ?: Icon(MaterialSymbols.Movie, contentDescription = null, modifier = Modifier.align(Alignment.Center))
            else -> Icon(MaterialSymbols.AutoMirrored.InsertDriveFile, contentDescription = null, modifier = Modifier.align(Alignment.Center))
        }

        Pill(ordinal.toString(), Modifier.align(Alignment.TopStart).padding(6.dp))

        if (!locked && state !is UploadingState.Finished) {
            Icon(
                MaterialSymbols.Close,
                contentDescription = stringRes(Res.string.remove),
                tint = Color.White,
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.55f))
                        .clickable(onClick = onRemove)
                        .padding(4.dp),
            )
        }

        Row(Modifier.align(Alignment.BottomStart).padding(6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (media.isImage() == true || media.isVideo() == true) {
                // Amber when missing: the nudge to describe it.
                val hasAlt = item.options.altOrNull != null
                Pill(
                    stringRes(Res.string.media_alt_badge),
                    background = if (hasAlt) Color.Black.copy(alpha = 0.6f) else Color(0xFFFFB74D),
                    color = if (hasAlt) Color.White else Color.Black,
                )
            }
            if (item.options.sensitive) Pill(stringRes(Res.string.media_cw_badge))
        }

        when (state) {
            is UploadingState.Ready, is UploadingState.Error -> Unit
            is UploadingState.Finished ->
                Icon(MaterialSymbols.CheckCircle, contentDescription = null, tint = Color(0xFF81C784), filled = true, modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp).size(22.dp))
            else ->
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(progress = { progress.toFloat() }, color = Color.White, trackColor = Color.White.copy(alpha = 0.25f), modifier = Modifier.size(36.dp))
                }
        }
    }
}

@Composable
private fun Pill(
    text: String,
    modifier: Modifier = Modifier,
    background: Color = Color.Black.copy(alpha = 0.6f),
    color: Color = Color.White,
) {
    Text(
        text,
        color = color,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier.clip(RoundedCornerShape(6.dp)).background(background).padding(horizontal = 6.dp, vertical = 2.dp),
    )
}
