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

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.media_crop_reset
import com.vitorpamplona.amethyst.commons.resources.media_trim_hint
import com.vitorpamplona.amethyst.commons.service.uploads.MediaUploader
import com.vitorpamplona.amethyst.commons.service.uploads.SelectedMediaProcessing
import com.vitorpamplona.amethyst.commons.service.uploads.VideoTrim
import com.vitorpamplona.amethyst.commons.ui.stringRes
import kotlinx.coroutines.delay
import kotlin.math.abs

/** The Studio's trim, shared by its stage and its panel: the video's length and the moment the stage shows. */
@Stable
internal class TrimSession {
    /** The moment of the video the stage shows: the edge last dragged; null shows the trim's start. */
    var focusMs by mutableStateOf<Long?>(null)
}

/** How long [item]'s video runs, once [uploader] has read it. */
@Composable
internal fun rememberVideoDuration(
    item: SelectedMediaProcessing,
    uploader: MediaUploader,
): Long? {
    val duration by produceState<Long?>(null, item.media) { value = uploader.videoDurationMs(item.media.uri) }
    return duration
}

/** One frame of [item]'s video near [atMs] as JPEG bytes, keeping the last one while the next loads. */
@Composable
internal fun rememberVideoFrame(
    item: SelectedMediaProcessing,
    uploader: MediaUploader,
    atMs: Long,
    maxEdge: Int,
): ByteArray? {
    var shown by remember(item.media) { mutableStateOf<ByteArray?>(null) }
    val bucket = atMs / FRAME_STEP_MS
    LaunchedEffect(item.media, bucket) {
        // Lets a drag settle before decoding.
        delay(80)
        uploader.videoFrame(item.media.uri, bucket * FRAME_STEP_MS, maxEdge)?.let { shown = it }
    }
    return shown
}

/** Frames are decoded at this step, so a drag doesn't ask for one per pixel. */
private const val FRAME_STEP_MS = 100L

/** The stage while Trim is open: the frame at the edge being moved, and that moment. */
@Composable
internal fun StudioTrimStage(
    item: SelectedMediaProcessing,
    uploader: MediaUploader,
    session: TrimSession,
) {
    val at = session.focusMs ?: item.options.trim?.startMs ?: 0L
    val frame = rememberVideoFrame(item, uploader, at, STAGE_FRAME_EDGE)
    Box(Modifier.fillMaxSize()) {
        if (frame != null) {
            AsyncImage(model = frame, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
        }
        Text(
            formatClock(at),
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier =
                Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

private const val STAGE_FRAME_EDGE = 1280
private const val STRIP_FRAMES = 8
private const val STRIP_FRAME_EDGE = 160

private enum class TrimHandle { START, END, MOVE }

/**
 * The Trim tool's panel: a filmstrip of the video with the kept part framed. Dragging an end moves
 * it (never closer than [VideoTrim.MIN_MS] to the other), dragging inside moves both, and the
 * stage follows the edge being moved. Writes [item]'s
 * [com.vitorpamplona.amethyst.commons.service.uploads.MediaUploadOptions.trim]; null when all of it is kept.
 */
@Composable
internal fun TrimTool(
    item: SelectedMediaProcessing,
    uploader: MediaUploader,
    session: TrimSession,
    enabled: Boolean,
) {
    val duration = rememberVideoDuration(item, uploader)
    val trim = item.options.trim
    val start = trim?.startMs ?: 0L
    val end = trim?.endMs ?: duration ?: 0L

    Column(Modifier.fillMaxWidth()) {
        Filmstrip(item, uploader, duration, start, end, enabled) { newStart, newEnd, focus ->
            val total = duration ?: return@Filmstrip
            item.options = item.options.copy(trim = VideoTrim(newStart, newEnd).orNullIfWhole(total))
            session.focusMs = focus
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (duration != null) "${formatClock(start)} – ${formatClock(end)} · ${formatLength(end - start)}" else stringRes(Res.string.media_trim_hint),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                enabled = enabled && trim != null,
                onClick = {
                    item.options = item.options.copy(trim = null)
                    session.focusMs = null
                },
            ) {
                Text(stringRes(Res.string.media_crop_reset))
            }
        }
    }
}

@Composable
private fun Filmstrip(
    item: SelectedMediaProcessing,
    uploader: MediaUploader,
    duration: Long?,
    start: Long,
    end: Long,
    enabled: Boolean,
    onChange: (start: Long, end: Long, focus: Long) -> Unit,
) {
    val frames = remember(item.media) { mutableStateListOf<ByteArray?>() }
    LaunchedEffect(item.media, duration) {
        val total = duration ?: return@LaunchedEffect
        if (frames.isNotEmpty()) return@LaunchedEffect
        repeat(STRIP_FRAMES) { frames.add(null) }
        // All at once: a GIF is decoded from its start, so one pass serves the whole strip.
        val times = List(STRIP_FRAMES) { i -> total * (2 * i + 1) / (2 * STRIP_FRAMES) }
        uploader.videoFrames(item.media.uri, times, STRIP_FRAME_EDGE).forEachIndexed { i, bytes -> frames[i] = bytes }
    }

    val density = LocalDensity.current
    val latestStart by rememberUpdatedState(start)
    val latestEnd by rememberUpdatedState(end)
    val latestOnChange by rememberUpdatedState(onChange)
    val selection = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(8.dp)

    Box(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(shape)
            .background(Color.Black),
    ) {
        Row(Modifier.fillMaxSize()) {
            for (i in 0 until STRIP_FRAMES) {
                val bytes = frames.getOrNull(i)
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    if (bytes != null) AsyncImage(model = bytes, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
            }
        }
        var handle by remember { mutableStateOf<TrimHandle?>(null) }
        Canvas(
            Modifier.fillMaxSize().pointerInput(duration, enabled) {
                val total = duration ?: return@pointerInput
                if (!enabled || total <= 0) return@pointerInput
                val touch = with(density) { 24.dp.toPx() }

                fun xOf(ms: Long) = ms.toFloat() / total * size.width
                detectDragGestures(
                    onDragStart = { at ->
                        val sx = xOf(latestStart)
                        val ex = xOf(latestEnd)
                        handle =
                            when {
                                abs(at.x - sx) <= touch && abs(at.x - sx) <= abs(at.x - ex) -> TrimHandle.START
                                abs(at.x - ex) <= touch -> TrimHandle.END
                                at.x in sx..ex -> TrimHandle.MOVE
                                else -> null
                            }
                    },
                    onDragEnd = { handle = null },
                    onDragCancel = { handle = null },
                    onDrag = { change, drag ->
                        val which = handle ?: return@detectDragGestures
                        change.consume()
                        val dMs = (drag.x / size.width * total).toLong()
                        val min = VideoTrim.MIN_MS.coerceAtMost(total)
                        when (which) {
                            TrimHandle.START -> {
                                val s = (latestStart + dMs).coerceIn(0L, latestEnd - min)
                                latestOnChange(s, latestEnd, s)
                            }
                            TrimHandle.END -> {
                                val e = (latestEnd + dMs).coerceIn(latestStart + min, total)
                                latestOnChange(latestStart, e, e)
                            }
                            TrimHandle.MOVE -> {
                                val length = latestEnd - latestStart
                                val s = (latestStart + dMs).coerceIn(0L, total - length)
                                latestOnChange(s, s + length, s)
                            }
                        }
                    },
                )
            },
        ) {
            val total = duration ?: return@Canvas
            if (total <= 0) return@Canvas
            val sx = start.toFloat() / total * size.width
            val ex = end.toFloat() / total * size.width
            val dim = Color.Black.copy(alpha = 0.6f)
            drawRect(dim, Offset.Zero, Size(sx, size.height))
            drawRect(dim, Offset(ex, 0f), Size(size.width - ex, size.height))
            val stroke = 3.dp.toPx()
            drawRoundRect(selection, Offset(sx + stroke / 2, stroke / 2), Size(ex - sx - stroke, size.height - stroke), CornerRadius(6.dp.toPx()), style = Stroke(stroke))
            val grip = 10.dp.toPx()
            drawRoundRect(selection, Offset(sx, 0f), Size(grip, size.height), CornerRadius(4.dp.toPx()))
            drawRoundRect(selection, Offset(ex - grip, 0f), Size(grip, size.height), CornerRadius(4.dp.toPx()))
            val notch = Color.White
            val notchH = size.height / 3
            drawLine(notch, Offset(sx + grip / 2, (size.height - notchH) / 2), Offset(sx + grip / 2, (size.height + notchH) / 2), 2.dp.toPx())
            drawLine(notch, Offset(ex - grip / 2, (size.height - notchH) / 2), Offset(ex - grip / 2, (size.height + notchH) / 2), 2.dp.toPx())
        }
    }
}

/** [ms] as m:ss.d. */
internal fun formatClock(ms: Long): String {
    val tenths = (ms.coerceAtLeast(0) + 50) / 100
    val seconds = tenths / 10
    return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}.${tenths % 10}"
}

/** A length as 7.5 s, or 1:05 past a minute. */
internal fun formatLength(ms: Long): String {
    val tenths = (ms.coerceAtLeast(0) + 50) / 100
    return if (tenths < 600) "${tenths / 10}.${tenths % 10} s" else "${tenths / 600}:${((tenths / 10) % 60).toString().padStart(2, '0')}"
}
