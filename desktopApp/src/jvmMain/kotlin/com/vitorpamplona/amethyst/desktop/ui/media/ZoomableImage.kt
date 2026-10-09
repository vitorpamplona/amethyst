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
package com.vitorpamplona.amethyst.desktop.ui.media

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import com.vitorpamplona.amethyst.commons.richtext.isAnimatedGifUrl

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ZoomableImage(
    url: String,
    modifier: Modifier = Modifier,
    onTap: (() -> Unit)? = null,
    onMediaPositioned: ((LayoutCoordinates, aspectRatio: Float?) -> Unit)? = null,
) {
    // Where the image's box sits and the loaded image's proportions: a viewer that animates the
    // image out of its thumbnail needs both, so it hears of the box only once the image is known.
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var aspectRatio by remember { mutableStateOf<Float?>(null) }

    fun report() {
        val c = coordinates ?: return
        if (loaded) onMediaPositioned?.invoke(c, aspectRatio)
    }

    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .pointerInput(onTap) {
                    detectTapGestures(
                        onDoubleTap = {
                            scale = 1f
                            offsetX = 0f
                            offsetY = 0f
                        },
                        onTap = {
                            // Single tap → caller handles (e.g. copy URL).
                            // Consumed either way so backdrop dismiss never fires.
                            onTap?.invoke()
                        },
                    )
                }.pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.type == PointerEventType.Scroll) {
                                val scrollDelta =
                                    event.changes
                                        .firstOrNull()
                                        ?.scrollDelta
                                        ?.y ?: 0f
                                val zoomFactor = if (scrollDelta > 0) 0.9f else 1.1f
                                scale = (scale * zoomFactor).coerceIn(0.5f, 10f)
                                event.changes.forEach { it.consume() }
                            }
                        }
                    }
                }.pointerInput(Unit) {
                    detectDragGestures { _, dragAmount ->
                        if (scale > 1f) {
                            offsetX += dragAmount.x
                            offsetY += dragAmount.y
                        }
                    }
                },
        contentAlignment = Alignment.Center,
    ) {
        val imageModifier =
            Modifier
                .fillMaxSize()
                // Before the user's zoom: the box as laid out, not as zoomed.
                .onGloballyPositioned {
                    coordinates = it
                    report()
                }.graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offsetX,
                    translationY = offsetY,
                )
        if (isAnimatedGifUrl(url)) {
            // No load callback here: the box stands in for the image.
            loaded = true
            AnimatedGifImage(
                url = url,
                contentDescription = null,
                modifier = imageModifier,
                contentScale = ContentScale.Fit,
            )
        } else {
            AsyncImage(
                model = url,
                contentDescription = null,
                modifier = imageModifier,
                contentScale = ContentScale.Fit,
                onState = { state ->
                    when (state) {
                        is AsyncImagePainter.State.Success -> {
                            val size = state.painter.intrinsicSize
                            aspectRatio = if (size.isSpecified && size.width > 0f && size.height > 0f) size.width / size.height else null
                            loaded = true
                            report()
                        }
                        is AsyncImagePainter.State.Error -> {
                            loaded = true
                            report()
                        }
                        else -> {}
                    }
                },
            )
        }
    }
}
