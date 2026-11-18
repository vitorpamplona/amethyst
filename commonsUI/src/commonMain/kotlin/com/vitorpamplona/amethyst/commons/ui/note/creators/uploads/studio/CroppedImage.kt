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

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import coil3.compose.AsyncImage
import com.vitorpamplona.amethyst.commons.service.uploads.ImageCrop
import com.vitorpamplona.amethyst.commons.ui.note.platform.AnimatedImage
import kotlin.math.roundToInt

/**
 * The picture at [model], showing only its [crop] (null: all of it) as [contentScale] would place
 * that part in the space: [ContentScale.Crop] fills it, anything else fits it. Drawn, not
 * re-encoded, so a crop shows the moment it changes. Needs the picture's displayed
 * [imageAspect] (width over height) to place it; until that is known the picture shows whole,
 * and [onAspect] reports it once the picture loads.
 */
@Composable
fun CroppedImage(
    model: Any?,
    crop: ImageCrop?,
    imageAspect: Float?,
    contentScale: ContentScale,
    modifier: Modifier = Modifier,
    onAspect: (Float) -> Unit = {},
    /** Draws through the platform's player, so a GIF moves; such a player reports no size, so [imageAspect] must come from elsewhere. */
    animated: Boolean = false,
) {
    if (animated && model is String) {
        AnimatedCroppedImage(model, crop, imageAspect, contentScale, modifier)
        return
    }
    if (crop == null || imageAspect == null || imageAspect <= 0f) {
        AsyncImage(
            model = model,
            contentDescription = null,
            contentScale = contentScale,
            onSuccess = { state ->
                val size = state.painter.intrinsicSize
                if (size.width > 0f && size.height > 0f) onAspect(size.width / size.height)
            },
            modifier = modifier,
        )
        return
    }
    Box(modifier.clipToBounds()) {
        AsyncImage(
            model = model,
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            onSuccess = { state ->
                val size = state.painter.intrinsicSize
                if (size.width > 0f && size.height > 0f) onAspect(size.width / size.height)
            },
            modifier = Modifier.cropPlacement(crop, imageAspect, contentScale),
        )
    }
}

@Composable
private fun AnimatedCroppedImage(
    url: String,
    crop: ImageCrop?,
    imageAspect: Float?,
    contentScale: ContentScale,
    modifier: Modifier,
) {
    if (crop == null || imageAspect == null || imageAspect <= 0f) {
        AnimatedImage(url, contentDescription = null, contentScale = contentScale, modifier = modifier, onError = {}, local = true)
        return
    }
    Box(modifier.clipToBounds()) {
        AnimatedImage(url, contentDescription = null, contentScale = ContentScale.FillBounds, modifier = Modifier.cropPlacement(crop, imageAspect, contentScale), onError = {}, local = true)
    }
}

/** Lays the whole picture out so only [crop] of it lands in the space, scaled as [contentScale] would place that part. */
private fun Modifier.cropPlacement(
    crop: ImageCrop,
    imageAspect: Float,
    contentScale: ContentScale,
): Modifier =
    layout { measurable, constraints ->
        if (!constraints.hasBoundedWidth || !constraints.hasBoundedHeight) {
            val placeable = measurable.measure(constraints)
            return@layout layout(placeable.width, placeable.height) { placeable.place(0, 0) }
        }
        val boxW = constraints.maxWidth.toFloat()
        val boxH = constraints.maxHeight.toFloat()
        // The cropped part's own shape, scaled to cover (Crop) or fit the space.
        val partAspect = imageAspect * crop.width / crop.height
        val cover = contentScale == ContentScale.Crop
        val widthLeads = (partAspect > boxW / boxH) != cover
        val partW = if (widthLeads) boxW else boxH * partAspect
        val partH = if (widthLeads) boxW / partAspect else boxH
        // The whole picture at the scale that makes that part this size.
        val fullW = (partW / crop.width).roundToInt().coerceAtLeast(1)
        val fullH = (partH / crop.height).roundToInt().coerceAtLeast(1)
        val placeable = measurable.measure(Constraints.fixed(fullW, fullH))
        val x = ((boxW - partW) / 2f - crop.left * fullW).roundToInt()
        val y = ((boxH - partH) / 2f - crop.top * fullH).roundToInt()
        layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(x, y) }
    }
