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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.media_alt_badge
import com.vitorpamplona.amethyst.commons.resources.media_cw_badge
import com.vitorpamplona.amethyst.commons.resources.media_marker_audio
import com.vitorpamplona.amethyst.commons.resources.media_marker_file
import com.vitorpamplona.amethyst.commons.resources.media_marker_missing
import com.vitorpamplona.amethyst.commons.resources.media_marker_photo
import com.vitorpamplona.amethyst.commons.resources.media_marker_video
import com.vitorpamplona.amethyst.commons.resources.media_post_nothing_posted
import com.vitorpamplona.amethyst.commons.resources.media_post_upload_failed
import com.vitorpamplona.amethyst.commons.resources.remove
import com.vitorpamplona.amethyst.commons.service.uploads.MediaUploader
import com.vitorpamplona.amethyst.commons.service.uploads.SelectedMedia
import com.vitorpamplona.amethyst.commons.service.uploads.SelectedMediaProcessing
import com.vitorpamplona.amethyst.commons.service.uploads.UploadingState
import com.vitorpamplona.amethyst.commons.ui.actions.MediaMarkerChips
import com.vitorpamplona.amethyst.commons.ui.actions.uploads.UploadProgressOverMedia
import com.vitorpamplona.amethyst.commons.ui.actions.uploads.rememberVideoThumbnail
import com.vitorpamplona.amethyst.commons.ui.note.creators.uploads.qualityName
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.uploads.errorResource
import com.vitorpamplona.amethyst.commons.util.formatFileSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext

/** "Photo 2", "Video 1", "File 3": what a file's marker and its tile are called. */
@Composable
fun mediaLabel(
    media: SelectedMedia,
    ordinal: Int,
): String =
    when {
        media.isVideo() == true -> stringRes(Res.string.media_marker_video, ordinal)
        media.isImage() == true -> stringRes(Res.string.media_marker_photo, ordinal)
        media.isAudio() == true -> stringRes(Res.string.media_marker_audio, ordinal)
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
 * When a file failed to upload on Post: names it and why. Nothing was posted; Post tries again.
 * The failed file's card is outlined too.
 */
@Composable
fun AttachedMediaFailureBanner(
    items: List<SelectedMediaProcessing>,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return
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
    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(12.dp), modifier = modifier.fillMaxWidth()) {
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

/**
 * A file attached to a post, drawn inside the text where it will appear in the note. It shows
 * what matters before Post: the picture, whether it has alt text (amber when missing: the nudge to
 * describe it), a content warning, the quality it goes up at and, while Post runs, its upload.
 * Tapping it reopens the file in the media studio. [selected]: the cursor sits against it, so
 * Backspace removes it.
 */
@Composable
fun AttachedMediaCard(
    item: SelectedMediaProcessing,
    selected: Boolean,
    locked: Boolean,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    uploader: MediaUploader? = null,
) {
    val status by item.orchestrator.status.collectAsState()
    val state = status.state
    val shape = RoundedCornerShape(16.dp)
    val media = item.media
    val failed = state is UploadingState.Error
    val label = mediaLabel(media, 1)
    val isPicture = media.isImage() == true || media.isVideo() == true
    Box(
        modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .border(
                width = if (failed || selected) 2.dp else 0.dp,
                color =
                    when {
                        failed -> MaterialTheme.colorScheme.error
                        selected -> MaterialTheme.colorScheme.primary
                        else -> Color.Transparent
                    },
                shape = shape,
            ).clickable(enabled = !locked, onClick = onOpen)
            .semantics { contentDescription = label },
    ) {
        when {
            media.isImage() == true ->
                CroppedImage(
                    // A trimmed GIF loops only the part that will be sent.
                    model = rememberTrimmedGif(item, uploader),
                    crop = item.options.crop,
                    imageAspect = item.displayAspectRatio,
                    contentScale = ContentScale.Crop,
                    // The decoded size, after EXIF orientation: the card takes this shape, cropped.
                    onAspect = { item.displayAspectRatio = it },
                    modifier = Modifier.fillMaxSize(),
                    animated = media.isAnimated(),
                )
            media.isVideo() == true -> {
                // The first frame that will be sent, where the platform decodes frames (desktop has no thumbnail otherwise).
                val decodesFrames = uploader != null && uploader.videoTrim
                val sentFrame = if (decodesFrames) rememberVideoFrame(item, uploader, item.options.trim?.startMs ?: 0L, CARD_FRAME_EDGE) else null
                // The platform's thumbnail only where it can't decode frames itself: not both.
                val frame = if (!decodesFrames) rememberVideoThumbnail(media) else null
                if (frame != null && frame.width > 0 && frame.height > 0) {
                    SideEffect { item.displayAspectRatio = frame.width.toFloat() / frame.height }
                }
                if (sentFrame != null) {
                    CroppedImage(sentFrame, null, null, ContentScale.Crop, Modifier.fillMaxSize(), onAspect = { item.displayAspectRatio = it })
                }
                frame?.let { Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                if (state is UploadingState.Ready) {
                    Icon(
                        MaterialSymbols.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        filled = true,
                        modifier =
                            Modifier
                                .align(Alignment.Center)
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.5f))
                                .padding(10.dp),
                    )
                }
            }
            else -> FileRowContent(item, uploader, Modifier.fillMaxSize().padding(start = 12.dp, end = 48.dp))
        }

        if (!locked && state !is UploadingState.Finished) {
            Icon(
                MaterialSymbols.Close,
                contentDescription = stringRes(Res.string.remove),
                tint = Color.White,
                modifier =
                    Modifier
                        .align(if (isPicture) Alignment.TopEnd else Alignment.CenterEnd)
                        .padding(8.dp)
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.55f))
                        .clickable(onClick = onRemove)
                        .padding(5.dp),
            )
        }

        // A file's row carries its own content-warning badge; the badges here sit on pictures.
        if (isPicture) {
            Row(Modifier.align(Alignment.BottomStart).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                val hasAlt = item.options.altOrNull != null
                Pill(
                    stringRes(Res.string.media_alt_badge),
                    background = if (hasAlt) Color.Black.copy(alpha = 0.6f) else Color(0xFFFFB74D),
                    color = if (hasAlt) Color.White else Color.Black,
                )
                if (item.options.sensitive) Pill(stringRes(Res.string.media_cw_badge))
                if (media.isCompressible()) Pill(qualityName(item.options.quality))
                item.options.trim?.let { Pill(formatLength(it.durationMs)) }
            }
        }

        when (state) {
            is UploadingState.Ready, is UploadingState.Error -> Unit
            is UploadingState.Finished ->
                Icon(MaterialSymbols.CheckCircle, contentDescription = null, tint = Color(0xFF81C784), filled = true, modifier = Modifier.align(if (isPicture) Alignment.BottomEnd else Alignment.CenterEnd).padding(8.dp).size(24.dp))
            else ->
                // The wheel says how far, the word what for: compressing, uploading, the server's checks.
                // A file's row is one line tall: the word goes beside the wheel, not under it.
                UploadProgressOverMedia(status, wheelSize = if (isPicture) 44.dp else 24.dp, inline = !isPicture)
        }
    }
}

/** A marker whose file is gone (an undo brought back the marker of a removed file): it is dropped at Post. */
@Composable
fun MissingMediaCard(
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(MaterialSymbols.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(10.dp))
        Text(stringRes(Res.string.media_marker_missing), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Icon(
            MaterialSymbols.Close,
            contentDescription = stringRes(Res.string.remove),
            modifier =
                Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onRemove)
                    .padding(5.dp),
        )
    }
}

/** The icon for a file with no picture to show: sound, a PDF, anything else. */
internal fun fileSymbol(media: SelectedMedia): MaterialSymbol =
    when {
        media.isAudio() == true -> MaterialSymbols.AudioFile
        media.isDocument() -> MaterialSymbols.PictureAsPdf
        else -> MaterialSymbols.AutoMirrored.InsertDriveFile
    }

/** A sound file or a document as one row: what it is, its name, and its type and size. */
@Composable
private fun FileRowContent(
    item: SelectedMediaProcessing,
    uploader: MediaUploader?,
    modifier: Modifier,
) {
    val media = item.media
    // Content-resolver queries on Android: off the main thread.
    val fallbackName = media.uri.toString().substringAfterLast('/')
    val name by produceState(fallbackName, media, uploader) { uploader?.let { value = withContext(Dispatchers.IO) { it.displayName(media.uri) } ?: fallbackName } }
    val size by produceState<Long?>(null, media, uploader) { value = uploader?.let { withContext(Dispatchers.IO) { it.fileSize(media.uri) } } }
    val kind = name.substringAfterLast('.', "").takeIf { it.length in 1..5 }?.uppercase() ?: media.mimeType?.substringAfter('/')?.uppercase()
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(fileSymbol(media), contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    listOfNotNull(kind, size?.let(::formatFileSize)).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                if (item.options.sensitive) {
                    Spacer(Modifier.width(6.dp))
                    Pill(stringRes(Res.string.media_cw_badge))
                }
            }
        }
    }
}

/** How tall a file's row is in the text: [FileRowContent] and its padding. */
internal val FILE_CARD_HEIGHT = 64.dp

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

private const val CARD_FRAME_EDGE = 720
