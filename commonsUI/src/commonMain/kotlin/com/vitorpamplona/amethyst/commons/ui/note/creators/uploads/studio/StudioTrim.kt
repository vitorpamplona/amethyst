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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.media_crop_reset
import com.vitorpamplona.amethyst.commons.resources.media_trim_hint
import com.vitorpamplona.amethyst.commons.resources.pause
import com.vitorpamplona.amethyst.commons.resources.play
import com.vitorpamplona.amethyst.commons.service.uploads.MediaUploader
import com.vitorpamplona.amethyst.commons.service.uploads.SelectedMediaProcessing
import com.vitorpamplona.amethyst.commons.service.uploads.VideoTrim
import com.vitorpamplona.amethyst.commons.ui.note.platform.LocalNotePlatform
import com.vitorpamplona.amethyst.commons.ui.stringRes
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToLong

/** The Studio's trim, shared by its stage and its panel: the moment the stage shows, and whether the kept part plays. */
@Stable
internal class TrimSession {
    /** The moment of the video the stage shows: the edge last dragged; null shows the trim's start. */
    var focusMs by mutableStateOf<Long?>(null)

    /** The kept part plays on the stage, over and over; dragging an edge stops it. */
    var playing by mutableStateOf(false)
        private set

    /** Where the playing video is, for the filmstrip's playhead; null when the platform doesn't say. */
    var playheadMs by mutableStateOf<Long?>(null)

    fun stop() {
        playing = false
        playheadMs = null
    }

    fun toggle() {
        if (playing) stop() else playing = true
    }
}

/** Whether the Trim tool can play [item]'s kept part here: a GIF always (its trimmed copy loops), a video where the platform has a player for it. */
@Composable
internal fun canPlayTrim(
    item: SelectedMediaProcessing,
    uploader: MediaUploader,
): Boolean = if (item.media.isGif()) uploader.gifTrim else LocalNotePlatform.current.canPlayTrimPreview

/**
 * What a GIF plays as once trimmed: the item's shared copy holding only the frames its
 * [com.vitorpamplona.amethyst.commons.service.uploads.MediaUploadOptions.trim] keeps
 * ([SelectedMediaProcessing.trimmedGif]), so every preview loops what will be sent and the upload
 * sends that same copy (a crop is drawn over it, as on the picked file). The picked file while there
 * is no trim and until the first copy is written; the last copy while the next one is.
 */
@Composable
internal fun rememberTrimmedGif(
    item: SelectedMediaProcessing,
    uploader: MediaUploader?,
): String {
    val media = item.media
    val trim = item.options.trim
    if (uploader == null || !media.isGif()) return media.uri.toString()
    // Held while shown, so the copy outlives neither this preview nor the last one using it.
    DisposableEffect(item, uploader) {
        item.holdTrimmedGif()
        onDispose { item.releaseTrimmedGif() }
    }
    var shown by remember(item) { mutableStateOf(item.trimmedGifNow(trim)) }
    LaunchedEffect(item, trim, uploader) {
        if (trim == null) return@LaunchedEffect
        item.trimmedGifNow(trim)?.let {
            shown = it
            return@LaunchedEffect
        }
        // Lets a drag settle before writing a copy.
        delay(300)
        item.trimmedGif(uploader, trim)?.let { shown = it }
    }
    return if (trim == null) media.uri.toString() else (shown ?: media.uri).toString()
}

/** How long [item]'s video runs, once [uploader] has read it; at once when the open Studio read it before. */
@Composable
internal fun rememberVideoDuration(
    item: SelectedMediaProcessing,
    uploader: MediaUploader,
): Long? {
    val reads = LocalStudioReads.current?.durations
    val media = item.media
    // Per file: the Studio switches files in place, and produceState would keep the last file's length.
    val duration = remember(media, reads) { mutableStateOf(reads?.now(media)) }
    LaunchedEffect(duration) {
        if (duration.value == null) duration.value = reads.getOrRead(media) { uploader.videoDurationMs(media.uri) }
    }
    return duration.value
}

/**
 * One frame of [item]'s video near [atMs] as JPEG bytes, keeping the last one while the next loads.
 * In the Studio, a frame another part of it read (the still under every tool, Trim opening on the
 * same moment) shows at once, and one being read is waited for rather than decoded again.
 */
@Composable
internal fun rememberVideoFrame(
    item: SelectedMediaProcessing,
    uploader: MediaUploader,
    atMs: Long,
    maxEdge: Int,
): ByteArray? {
    val reads = LocalStudioReads.current?.frames
    val media = item.media
    val bucket = atMs / FRAME_STEP_MS
    val key = StudioReads.FrameKey(media, bucket, maxEdge)
    var shown by remember(media) { mutableStateOf(reads?.now(key)) }
    LaunchedEffect(media, bucket, reads) {
        reads?.now(key)?.let {
            shown = it
            return@LaunchedEffect
        }
        // Lets a drag settle before decoding, unless that frame is already on its way.
        if (reads == null || !reads.has(key)) delay(80)
        reads.getOrRead(key) { uploader.videoFrame(media.uri, bucket * FRAME_STEP_MS, maxEdge) }?.let { shown = it }
    }
    return shown
}

/** Frames are decoded at this step, so a drag doesn't ask for one per pixel. */
private const val FRAME_STEP_MS = 100L

/**
 * The stage while Trim is open: the frame at the edge being moved and that moment or, while
 * [TrimSession.playing], the kept part playing over and over. Tapping it plays or stops.
 */
@Composable
internal fun StudioTrimStage(
    item: SelectedMediaProcessing,
    uploader: MediaUploader,
    session: TrimSession,
) {
    val duration = rememberVideoDuration(item, uploader)
    val canPlay = canPlayTrim(item, uploader) && duration != null
    val start = item.options.trim?.startMs ?: 0L
    val end = item.options.trim?.endMs ?: duration ?: 0L
    val playing = session.playing && canPlay
    // Written as the edges move, not when Play is pressed, so it plays the part kept at once.
    val trimmedGif = if (item.media.isGif()) rememberTrimmedGif(item, uploader) else null
    // A stage left playing (another tool, another file) starts stopped next time.
    DisposableEffect(session) { onDispose { session.stop() } }

    // While it plays, the still under the player is the moment it starts from, not the edge dragged last.
    val at = if (playing) start else session.focusMs ?: start
    Box(
        Modifier.fillMaxSize().then(
            if (canPlay) {
                Modifier.clickable(interactionSource = null, indication = null) { session.toggle() }
            } else {
                Modifier
            },
        ),
    ) {
        // The frame stays under the player: the stage never goes black while a player opens, or
        // when one can't play the file.
        val frame = rememberVideoFrame(item, uploader, at, STAGE_FRAME_EDGE)
        if (frame != null) {
            AsyncImage(model = frame, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
        }
        if (playing && duration != null) {
            if (trimmedGif != null) {
                // The trimmed copy itself: it loops the kept frames as the note will.
                CroppedImage(trimmedGif, null, null, ContentScale.Fit, Modifier.fillMaxSize(), animated = true)
            } else {
                LocalNotePlatform.current.TrimPreviewPlayer(
                    videoUri = item.media.uri.toString(),
                    startMs = start,
                    endMs = end,
                    durationMs = duration,
                    onPositionMs = { session.playheadMs = it },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        } else if (canPlay) {
            Icon(
                MaterialSymbols.PlayArrow,
                contentDescription = stringRes(Res.string.play),
                tint = Color.White,
                filled = true,
                modifier =
                    Modifier
                        .align(Alignment.Center)
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f))
                        .padding(12.dp),
            )
        }
        Text(
            if (playing) "${formatClock(start)} – ${formatClock(end)}" else formatClock(at),
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

internal const val STAGE_FRAME_EDGE = 1280
private const val STRIP_FRAMES = 8
private const val STRIP_FRAME_EDGE = 160

private enum class TrimHandle { START, END, MOVE }

/**
 * The Trim tool's panel: a filmstrip of the video with the kept part framed. Dragging an end moves
 * it (never closer than [VideoTrim.MIN_MS], or [VideoTrim.MIN_GIF_MS] for a GIF, to the other), dragging inside moves both, and the
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

    val canPlay = canPlayTrim(item, uploader) && duration != null

    Column(Modifier.fillMaxWidth()) {
        // Read in the filmstrip's drawing only: the playhead moves many times a second and only redraws there.
        Filmstrip(item, uploader, duration, start, end, enabled, playheadMs = { session.playheadMs.takeIf { session.playing } }) { newStart, newEnd, focus ->
            val total = duration ?: return@Filmstrip
            // The stage shows the edge being moved; it plays the new part when asked again.
            session.stop()
            item.options = item.options.copy(trim = VideoTrim(newStart, newEnd).orNullIfWhole(total))
            session.focusMs = focus
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (canPlay) {
                IconButton(onClick = session::toggle) {
                    if (session.playing) {
                        Icon(MaterialSymbols.Pause, contentDescription = stringRes(Res.string.pause), filled = true)
                    } else {
                        Icon(MaterialSymbols.PlayArrow, contentDescription = stringRes(Res.string.play), filled = true)
                    }
                }
            }
            Text(
                if (duration != null) "${formatClock(start)} – ${formatClock(end)} · ${formatLength(end - start)}" else stringRes(Res.string.media_trim_hint),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                enabled = enabled && trim != null,
                onClick = {
                    session.stop()
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
    /** Where the kept part is playing, drawn as a line; null draws none. Read while drawing, so it moves without a recomposition. */
    playheadMs: () -> Long?,
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
        // Where the finger went down. The drag starts only past the touch slop, up to ~20 px on:
        // judged there, a handle grabbed and pulled inward read as "move both", which does nothing
        // while all of it is kept.
        val downX = remember { floatArrayOf(Float.NaN) }
        val minMs = if (item.media.isGif()) VideoTrim.MIN_GIF_MS else VideoTrim.MIN_MS
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitEachGesture { downX[0] = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial).position.x }
                }.pointerInput(duration, enabled, minMs) {
                    val total = duration ?: return@pointerInput
                    if (!enabled || total <= 0) return@pointerInput
                    val touch = with(density) { 24.dp.toPx() }

                    fun xOf(ms: Long) = ms.toFloat() / total * size.width

                    // The trim when the drag began and how far it has gone since: each step is
                    // worked out from there, not added to the last one. Steps of a few ms each
                    // were snapped back to "all of it" (see VideoTrim.orNullIfWhole) one by one,
                    // so on a one-second GIF the start never left the edge.
                    var fromStart = 0L
                    var fromEnd = 0L
                    var dragged = 0f
                    detectDragGestures(
                        onDragStart = { at ->
                            val x = downX[0].takeUnless { it.isNaN() } ?: at.x
                            fromStart = latestStart
                            fromEnd = latestEnd
                            dragged = 0f
                            val sx = xOf(latestStart)
                            val ex = xOf(latestEnd)
                            handle =
                                when {
                                    abs(x - sx) <= touch && abs(x - sx) <= abs(x - ex) -> TrimHandle.START
                                    abs(x - ex) <= touch -> TrimHandle.END
                                    x in sx..ex -> TrimHandle.MOVE
                                    else -> null
                                }
                        },
                        onDragEnd = { handle = null },
                        onDragCancel = { handle = null },
                        onDrag = { change, drag ->
                            val which = handle ?: return@detectDragGestures
                            change.consume()
                            dragged += drag.x
                            val dMs = (dragged / size.width * total).roundToLong()
                            val min = minMs.coerceAtMost(total)
                            when (which) {
                                TrimHandle.START -> {
                                    val s = (fromStart + dMs).coerceIn(0L, fromEnd - min)
                                    latestOnChange(s, fromEnd, s)
                                }
                                TrimHandle.END -> {
                                    val e = (fromEnd + dMs).coerceIn(fromStart + min, total)
                                    latestOnChange(fromStart, e, e)
                                }
                                TrimHandle.MOVE -> {
                                    val length = fromEnd - fromStart
                                    val s = (fromStart + dMs).coerceIn(0L, total - length)
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
            val playhead = playheadMs()
            if (playhead != null) {
                val px = (playhead.coerceIn(start, end).toFloat() / total * size.width).coerceIn(sx + grip, ex - grip)
                drawLine(notch, Offset(px, 0f), Offset(px, size.height), 2.dp.toPx())
            }
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
