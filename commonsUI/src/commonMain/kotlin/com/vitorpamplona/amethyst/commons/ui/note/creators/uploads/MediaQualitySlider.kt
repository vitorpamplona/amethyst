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
package com.vitorpamplona.amethyst.commons.ui.note.creators.uploads

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.vitorpamplona.amethyst.commons.audio.player.formatFileSize
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.media_compression_image_max_size
import com.vitorpamplona.amethyst.commons.resources.media_compression_preview_compressed
import com.vitorpamplona.amethyst.commons.resources.media_compression_preview_original
import com.vitorpamplona.amethyst.commons.resources.media_compression_preview_tap_compressed
import com.vitorpamplona.amethyst.commons.resources.media_compression_preview_tap_original
import com.vitorpamplona.amethyst.commons.resources.media_compression_quality_high
import com.vitorpamplona.amethyst.commons.resources.media_compression_quality_low
import com.vitorpamplona.amethyst.commons.resources.media_compression_quality_medium
import com.vitorpamplona.amethyst.commons.resources.media_compression_quality_uncompressed
import com.vitorpamplona.amethyst.commons.resources.media_compression_quality_very_high
import com.vitorpamplona.amethyst.commons.resources.media_compression_quality_very_low
import com.vitorpamplona.amethyst.commons.service.uploads.CompressorQuality
import com.vitorpamplona.amethyst.commons.service.uploads.ImageCompressionPreview
import com.vitorpamplona.amethyst.commons.service.uploads.ImageFileStats
import com.vitorpamplona.amethyst.commons.service.uploads.MediaUploader
import com.vitorpamplona.amethyst.commons.service.uploads.MultiOrchestrator
import com.vitorpamplona.amethyst.commons.service.uploads.SelectedMedia
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.QuoteBorder
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

/**
 * The media-quality slider, one stop per [CompressorQuality.sliderSteps] level, labelled with the
 * level and the size images shrink to. When [media] holds a still image the platform re-encodes,
 * the slider also shows that image compressed at the chosen level, with its size and pixel
 * dimensions beside the original's; tapping the preview flips between the two.
 */
@Composable
fun MediaQualitySlider(
    position: Int,
    onPositionChange: (Int) -> Unit,
    media: MultiOrchestrator?,
    uploader: MediaUploader,
    modifier: Modifier = Modifier,
) {
    val steps = CompressorQuality.sliderSteps
    val quality = CompressorQuality.fromSlider(position)

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = qualityLabel(quality), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())

        Slider(
            value = position.coerceIn(0, steps.lastIndex).toFloat(),
            onValueChange = { onPositionChange(it.toInt()) },
            valueRange = 0f..steps.lastIndex.toFloat(),
            steps = (steps.size - 2).coerceAtLeast(0),
        )

        val image = remember(media) { media?.let { firstStillImage(it) } }
        if (image != null && quality.imageMaxDimension != null) {
            CompressionPreview(image, quality, uploader)
        }
    }
}

@Composable
private fun qualityLabel(quality: CompressorQuality): String {
    val name =
        when (quality) {
            CompressorQuality.VERY_LOW -> stringRes(Res.string.media_compression_quality_very_low)
            CompressorQuality.LOW -> stringRes(Res.string.media_compression_quality_low)
            CompressorQuality.MEDIUM -> stringRes(Res.string.media_compression_quality_medium)
            CompressorQuality.HIGH -> stringRes(Res.string.media_compression_quality_high)
            CompressorQuality.VERY_HIGH -> stringRes(Res.string.media_compression_quality_very_high)
            CompressorQuality.UNCOMPRESSED -> stringRes(Res.string.media_compression_quality_uncompressed)
        }
    val maxDimension = quality.imageMaxDimension ?: return name
    return stringRes(Res.string.media_compression_image_max_size, name, maxDimension)
}

private fun firstStillImage(media: MultiOrchestrator): SelectedMedia? =
    (0 until media.size())
        .asSequence()
        .map { media.get(it).media }
        .firstOrNull { it.isImage() == true && it.isCompressible() }

/**
 * Compresses [image] at [quality] in the background, after the slider has rested for a moment, and
 * keeps each result for as long as the composer shows this image, so moving back to a level shows
 * it at once. The temp files go when the preview leaves the screen.
 */
@Composable
private fun CompressionPreview(
    image: SelectedMedia,
    quality: CompressorQuality,
    uploader: MediaUploader,
) {
    // A level maps to null when the platform could not compress the image.
    val results = remember(image) { mutableStateMapOf<CompressorQuality, ImageCompressionPreview?>() }

    DisposableEffect(image) {
        onDispose {
            results.values.forEach { preview -> preview?.let { uploader.discardTempFile(it.compressedUri) } }
            results.clear()
        }
    }

    LaunchedEffect(image, quality) {
        if (results.containsKey(quality)) return@LaunchedEffect
        delay(300)
        // Not cancelled half-way: a temp file written as the slider moves on is still discarded below.
        val preview = withContext(NonCancellable) { uploader.previewImageCompression(image.uri, image.mimeType, quality) }
        if (isActive) {
            results[quality] = preview
        } else {
            preview?.let { uploader.discardTempFile(it.compressedUri) }
        }
    }

    if (!results.containsKey(quality)) {
        Box(Modifier.fillMaxWidth().heightIn(min = 120.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
        }
        return
    }
    val preview = results[quality] ?: return

    var showOriginal by remember(image) { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        AsyncImage(
            model = if (showOriginal) image.uri.toString() else preview.compressedUri.toString(),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp)
                    .clip(QuoteBorder)
                    .clickable { showOriginal = !showOriginal },
        )
        Text(
            text =
                stringRes(
                    if (showOriginal) Res.string.media_compression_preview_tap_compressed else Res.string.media_compression_preview_tap_original,
                ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
        )
        StatsLine(stringRes(Res.string.media_compression_preview_compressed), preview.compressed, highlight = !showOriginal)
        StatsLine(stringRes(Res.string.media_compression_preview_original), preview.original, highlight = showOriginal)
    }
}

@Composable
private fun StatsLine(
    label: String,
    stats: ImageFileStats,
    highlight: Boolean,
) {
    val parts =
        listOfNotNull(
            if (stats.width != null && stats.height != null) "${stats.width} × ${stats.height}" else null,
            stats.bytes?.let { formatFileSize(it) },
        )
    Text(
        text = "$label: " + parts.joinToString(" · "),
        style = MaterialTheme.typography.bodySmall,
        color = if (highlight) MaterialTheme.colorScheme.onSurface else Color.Gray,
    )
}
