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
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import com.vitorpamplona.amethyst.commons.blurhash.PlatformImage
import com.vitorpamplona.amethyst.commons.service.image.toComposeImageBitmap
import com.vitorpamplona.amethyst.commons.sno.SnoRasterizer
import com.vitorpamplona.quartz.cyberspace.deck0003Sno.SnoPayload

/** How many degrees a drag of one pixel turns the object. */
private const val DEGREES_PER_PIXEL = 0.5f

/** Past straight up or straight down there is nothing more to see. */
private const val MAX_PITCH = 89f

/**
 * A Simple Nostr Object the reader can turn.
 *
 * Unlike [SnoThumbnail] this draws straight to a bitmap instead of going
 * through Coil, because a drag produces a new angle every frame and each one
 * would otherwise become its own cache entry: at 360dp that is around half a
 * megabyte a frame, which would evict the whole image cache in a second of
 * dragging. Nothing here is worth caching — the object is the event, and
 * re-rasterising it is cheaper than remembering it.
 *
 * The raster runs on the composition's thread. At the format's ceiling that is
 * 512 vertices and 1024 triangles into a few hundred pixels square, which is
 * well inside a frame; the limits of §1.8 are what make that safe to assume.
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
    val backgroundArgb = if (background == Color.Transparent) 0 else background.toArgb()

    BoxWithConstraints(
        modifier =
            modifier.pointerInput(eventId) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    yaw = wrapDegrees(yaw + dragAmount.x * DEGREES_PER_PIXEL)
                    pitch = (pitch - dragAmount.y * DEGREES_PER_PIXEL).coerceIn(-MAX_PITCH, MAX_PITCH)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        val pixelSize = with(LocalDensity.current) { minOf(maxWidth, maxHeight).roundToPx() }
        if (pixelSize <= 0) return@BoxWithConstraints

        val bitmap =
            remember(payload, pixelSize, yaw, pitch, backgroundArgb) {
                val pixels =
                    SnoRasterizer.render(
                        payload = payload,
                        width = pixelSize,
                        height = pixelSize,
                        yawDegrees = yaw,
                        pitchDegrees = pitch,
                        background = backgroundArgb,
                    )
                PlatformImage.create(pixels, pixelSize, pixelSize).toComposeImageBitmap()
            }

        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Image(
                bitmap = bitmap,
                contentDescription = contentDescription,
                contentScale = ContentScale.Fit,
                filterQuality = FilterQuality.None,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

private fun wrapDegrees(value: Float): Float {
    var wrapped = value % 360f
    if (wrapped < 0f) wrapped += 360f
    return wrapped
}
