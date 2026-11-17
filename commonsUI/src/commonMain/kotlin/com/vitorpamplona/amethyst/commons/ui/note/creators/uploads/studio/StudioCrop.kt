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

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.media_crop_free
import com.vitorpamplona.amethyst.commons.resources.media_crop_hint
import com.vitorpamplona.amethyst.commons.resources.media_crop_reset
import com.vitorpamplona.amethyst.commons.service.uploads.ImageCrop
import com.vitorpamplona.amethyst.commons.service.uploads.SelectedMediaProcessing
import com.vitorpamplona.amethyst.commons.ui.stringRes
import kotlin.math.abs

/** The shapes the Studio's crop can be held to; [ratio] is width over height, null for any. */
enum class CropAspect(
    val ratio: Float?,
    val label: String?,
) {
    FREE(null, null),
    SQUARE(1f, "1:1"),
    PORTRAIT(4f / 5f, "4:5"),
    LANDSCAPE(3f / 2f, "3:2"),
    WIDE(16f / 9f, "16:9"),
    ;

    companion object {
        /** The shape [crop] already has on a picture [imageAspect] wide per unit of height, so a reopened Studio holds it; Free when none matches. */
        fun of(
            crop: ImageCrop?,
            imageAspect: Float?,
        ): CropAspect {
            if (crop == null || imageAspect == null) return FREE
            val ratio = imageAspect * crop.width / crop.height
            return entries.firstOrNull { it.ratio != null && abs(it.ratio - ratio) / it.ratio < 0.01f } ?: FREE
        }
    }
}

/** [this] crop, or null when it keeps the whole picture (so nothing is re-encoded for it). */
internal fun ImageCrop.orNullIfWhole(): ImageCrop? = if (abs(left) < 1e-3f && abs(top) < 1e-3f && abs(1f - right) < 1e-3f && abs(1f - bottom) < 1e-3f) null else this

/**
 * The Studio's stage while Crop is open: [item]'s picture with the frame on it. The frame is
 * [item]'s [com.vitorpamplona.amethyst.commons.service.uploads.MediaUploadOptions.crop] (the whole
 * picture when it has none), and every move is written back to it.
 */
@Composable
internal fun StudioCropStage(
    item: SelectedMediaProcessing,
    aspect: CropAspect,
    enabled: Boolean,
) {
    val model = item.media.uri.toString()
    val imageAspect = item.displayAspectRatio
    if (imageAspect == null) {
        // Loads the picture once to learn its shape.
        CroppedImage(model, null, null, ContentScale.Fit, Modifier.fillMaxSize().padding(24.dp), onAspect = { item.displayAspectRatio = it })
        return
    }
    // A shape picked before the picture's own was known takes effect now.
    LaunchedEffect(item, aspect, imageAspect) {
        val ratio = aspect.ratio
        if (item.options.crop == null && ratio != null) item.options = item.options.copy(crop = ImageCrop.centered(imageAspect, ratio).orNullIfWhole())
    }
    val shape = remember(aspect) { ImageCropShape(aspect.ratio, circle = false) }
    CropEditor(
        model = model,
        imageAspect = imageAspect,
        crop = item.options.crop ?: ImageCrop.FULL,
        shape = shape,
        enabled = enabled,
        onCrop = { item.options = item.options.copy(crop = it.orNullIfWhole()) },
        modifier = Modifier.fillMaxSize(),
    )
}

/** The Crop tool's panel: the shapes to hold the frame to, and Reset. */
@Composable
internal fun CropTool(
    item: SelectedMediaProcessing,
    aspect: CropAspect,
    enabled: Boolean,
    onAspect: (CropAspect) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        ) {
            CropAspect.entries.forEach { option ->
                FilterChip(
                    selected = option == aspect,
                    enabled = enabled,
                    onClick = {
                        onAspect(option)
                        val ratio = option.ratio
                        val imageAspect = item.displayAspectRatio
                        if (ratio != null && imageAspect != null) {
                            item.options = item.options.copy(crop = ImageCrop.centered(imageAspect, ratio).orNullIfWhole())
                        }
                    },
                    label = { Text(option.label ?: stringRes(Res.string.media_crop_free)) },
                )
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringRes(Res.string.media_crop_hint),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                enabled = enabled && item.options.crop != null,
                onClick = {
                    onAspect(CropAspect.FREE)
                    item.options = item.options.copy(crop = null)
                },
            ) {
                Text(stringRes(Res.string.media_crop_reset))
            }
        }
    }
}
