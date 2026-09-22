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
package com.vitorpamplona.amethyst.commons.sno.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import com.vitorpamplona.amethyst.commons.blurhash.PlatformImage
import com.vitorpamplona.amethyst.commons.service.image.toComposeImageBitmap
import com.vitorpamplona.amethyst.commons.sno.SnoRasterizer
import com.vitorpamplona.quartz.cyberspace.deck0003Sno.SnoPayload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** How many degrees a one-finger drag of one pixel turns the object. */
private const val DEGREES_PER_PIXEL = 0.5f

/** Past straight up or straight down there is nothing more to see. */
private const val MAX_PITCH = 89f

private const val MIN_ZOOM = 0.5f
private const val MAX_ZOOM = 8f

/**
 * The raster size while a gesture is in flight.
 *
 * Turning an object is the thing this view is for, so the frames during a turn
 * are the ones that must not stutter. They are also the ones nobody is
 * inspecting closely: the sharp frame is the one you stop on. So the raster
 * drops while a finger is down and goes back to full size when it lifts.
 */
private const val GESTURE_RASTER_PX = 384

/**
 * The largest raster produced when the object is at rest, before zoom.
 *
 * A real object at 1080px costs under 5ms, so full resolution is affordable on
 * anything a phone will ask for; this only bounds the memory a very large
 * window or a deep zoom could otherwise demand.
 */
private const val MAX_RESTING_RASTER_PX = 1440

/**
 * A Simple Nostr Object the reader can turn, move and zoom.
 *
 * One finger turns it, two move and scale it, and a double tap puts it back.
 *
 * Only turning needs a new raster — it changes which faces point at you.
 * Moving and scaling are a [graphicsLayer] transform of the frame already
 * drawn, which costs nothing per frame; when the gesture ends the object is
 * redrawn at the resolution the zoom now deserves, so it sharpens where it
 * settled rather than staying an upscaled bitmap.
 *
 * Unlike [SnoThumbnail] this does not go through Coil: a turn produces a new
 * angle every frame and each would become its own cache entry, evicting the
 * image cache within a second. Nothing here is worth caching — the object is
 * the event, and redrawing it is cheaper than remembering it.
 */
@Composable
fun SnoObjectViewer(
    payload: SnoPayload,
    eventId: String,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    background: Color = Color.Transparent,
) {
    var yaw by remember(eventId) { mutableFloatStateOf(SnoRasterizer.DEFAULT_YAW_DEGREES) }
    var pitch by remember(eventId) { mutableFloatStateOf(SnoRasterizer.DEFAULT_PITCH_DEGREES) }
    var zoom by remember(eventId) { mutableFloatStateOf(1f) }
    var panX by remember(eventId) { mutableFloatStateOf(0f) }
    var panY by remember(eventId) { mutableFloatStateOf(0f) }
    var gesturing by remember(eventId) { mutableStateOf(false) }
    val backgroundArgb = if (background == Color.Transparent) 0 else background.toArgb()

    BoxWithConstraints(
        modifier =
            modifier
                .pointerInput(eventId) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        gesturing = true
                        try {
                            do {
                                val event = awaitPointerEvent(PointerEventPass.Main)
                                val moved = event.changes.any { it.positionChanged() }
                                if (moved) {
                                    if (event.changes.size > 1) {
                                        // Two fingers move and scale the object.
                                        val scale = event.calculateZoom()
                                        if (scale != 1f) zoom = (zoom * scale).coerceIn(MIN_ZOOM, MAX_ZOOM)
                                        val pan = event.calculatePan()
                                        panX += pan.x
                                        panY += pan.y
                                    } else {
                                        // One finger turns it.
                                        val pan = event.calculatePan()
                                        yaw = wrapDegrees(yaw + pan.x * DEGREES_PER_PIXEL)
                                        pitch = (pitch - pan.y * DEGREES_PER_PIXEL).coerceIn(-MAX_PITCH, MAX_PITCH)
                                    }
                                    event.changes.forEach { it.consume() }
                                }
                            } while (event.changes.any { it.pressed })
                        } finally {
                            gesturing = false
                        }
                    }
                }.pointerInput(eventId) {
                    detectTapGestures(
                        onDoubleTap = {
                            yaw = SnoRasterizer.DEFAULT_YAW_DEGREES
                            pitch = SnoRasterizer.DEFAULT_PITCH_DEGREES
                            zoom = 1f
                            panX = 0f
                            panY = 0f
                        },
                    )
                },
        contentAlignment = Alignment.Center,
    ) {
        val onScreenPx = with(LocalDensity.current) { minOf(maxWidth, maxHeight).roundToPx() }
        if (onScreenPx <= 0) return@BoxWithConstraints

        // Coarse while a finger is down, full — and scaled by how far in the
        // reader has zoomed — once it lifts.
        val restingPx = minOf((onScreenPx * zoom).toInt(), MAX_RESTING_RASTER_PX)
        val rasterPx = if (gesturing) minOf(onScreenPx, GESTURE_RASTER_PX) else restingPx

        // Held across angles rather than recomputed in composition: the previous
        // frame stays on screen while the next is drawn, so a heavy object makes
        // the turn coarse instead of freezing the gesture.
        var bitmap by remember(eventId) { mutableStateOf<ImageBitmap?>(null) }

        LaunchedEffect(payload, rasterPx, yaw, pitch, backgroundArgb) {
            bitmap =
                withContext(Dispatchers.Default) {
                    val pixels =
                        SnoRasterizer.render(
                            payload = payload,
                            width = rasterPx,
                            height = rasterPx,
                            yawDegrees = yaw,
                            pitchDegrees = pitch,
                            background = backgroundArgb,
                        )
                    PlatformImage.create(pixels, rasterPx, rasterPx).toComposeImageBitmap()
                }
        }

        bitmap?.let { drawn ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Image(
                    bitmap = drawn,
                    contentDescription = contentDescription,
                    contentScale = ContentScale.Fit,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer(
                                scaleX = zoom,
                                scaleY = zoom,
                                translationX = panX,
                                translationY = panY,
                            ),
                )
            }
        }
    }
}

private fun wrapDegrees(value: Float): Float {
    var wrapped = value % 360f
    if (wrapped < 0f) wrapped += 360f
    return wrapped
}
