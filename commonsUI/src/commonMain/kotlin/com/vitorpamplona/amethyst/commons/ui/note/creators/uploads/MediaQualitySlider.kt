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

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil3.compose.AsyncImage
import com.vitorpamplona.amethyst.commons.audio.player.formatFileSize
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.close
import com.vitorpamplona.amethyst.commons.resources.media_compression_image_max_size
import com.vitorpamplona.amethyst.commons.resources.media_compression_preview_compressed
import com.vitorpamplona.amethyst.commons.resources.media_compression_preview_original
import com.vitorpamplona.amethyst.commons.resources.media_compression_preview_tap_compressed
import com.vitorpamplona.amethyst.commons.resources.media_compression_preview_tap_original
import com.vitorpamplona.amethyst.commons.resources.media_compression_preview_uncompressed
import com.vitorpamplona.amethyst.commons.resources.media_compression_preview_zoom
import com.vitorpamplona.amethyst.commons.resources.media_compression_preview_zoom_hint
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
import com.vitorpamplona.amethyst.commons.ui.components.edgeToEdgeDialogProperties
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.QuoteBorder
import kotlinx.coroutines.delay
import net.engawapg.lib.zoomable.MouseWheelZoom
import net.engawapg.lib.zoomable.rememberZoomState
import net.engawapg.lib.zoomable.zoomable

/**
 * The media-quality slider, one stop per [CompressorQuality.sliderSteps] level, labelled with the
 * level and the size images shrink to. When [media] holds a still image the platform re-encodes,
 * the slider also shows that image compressed at the chosen level, with its size and pixel
 * dimensions beside the original's; tapping the preview flips between the two, and its zoom button
 * opens both full screen to compare the detail.
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

        // Read each time: removing an image changes the same orchestrator, so a remembered pick goes stale.
        val image = media?.let { firstStillImage(it) }
        // Stays composed on the uncompressed level too, so its cached previews survive a trip there and back.
        if (image != null) {
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

private val PreviewHeight = 260.dp

/**
 * Compresses [image] at [quality] in the background, after the slider has rested for a moment, and
 * keeps each result for as long as the composer shows this image, so moving back to a level shows
 * it at once. The temp files go when the composer drops the image.
 *
 * Once a first preview is on screen the block keeps its size: the image box has a fixed height, the
 * last result stays up (under a spinner) while the next level compresses, and the uncompressed level
 * shows the original in the same box instead of removing it. The zoom button opens the comparison
 * full screen.
 */
@Composable
private fun CompressionPreview(
    image: SelectedMedia,
    quality: CompressorQuality,
    uploader: MediaUploader,
) {
    // A level maps to null when the platform could not compress the image.
    val results = remember(image) { mutableStateMapOf<CompressorQuality, ImageCompressionPreview?>() }
    var lastPreview by remember(image) { mutableStateOf<ImageCompressionPreview?>(null) }
    var showOriginal by remember(image) { mutableStateOf(false) }
    var zoomed by remember(image) { mutableStateOf(false) }

    DisposableEffect(image) {
        onDispose {
            results.values.forEach { preview -> preview?.let { uploader.discardTempFile(it.compressedUri) } }
            results.clear()
        }
    }

    val compresses = quality.imageMaxDimension != null

    LaunchedEffect(image, quality) {
        if (!compresses || results.containsKey(quality)) return@LaunchedEffect
        delay(300)
        // Cancelled when the slider moves on or the composer closes, so an abandoned preview stops
        // decoding (on desktop it would hold up the upload behind it); the uploader deletes what it
        // wrote when cancelled.
        results[quality] = uploader.previewImageCompression(image.uri, image.mimeType, quality)
    }

    val loading = compresses && !results.containsKey(quality)
    val current = if (compresses) results[quality] else null
    if (current != null && current !== lastPreview) {
        SideEffect { lastPreview = current }
    }

    // Nothing to compare: the platform can't compress this image, or no level was ever previewed.
    if (compresses && !loading && current == null) return
    if (!compresses && lastPreview == null) return

    val stats = current ?: lastPreview
    // While a level compresses, the previous level's result stays up so the box doesn't empty.
    val compressedUri = if (compresses) stats?.compressedUri?.toString() else null
    val originalUri = image.uri.toString()
    val seeOriginal = showOriginal || compressedUri == null
    val toggle: (() -> Unit)? = if (compressedUri != null) ({ showOriginal = !showOriginal }) else null

    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(PreviewHeight)
                    .clip(QuoteBorder)
                    .clickable(enabled = toggle != null) { toggle?.invoke() },
            contentAlignment = Alignment.Center,
        ) {
            ComparisonImages(originalUri, compressedUri, seeOriginal, Modifier.fillMaxSize())

            if (loading) {
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            }

            IconButton(
                onClick = { zoomed = true },
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.4f)),
            ) {
                Icon(
                    symbol = MaterialSymbols.OpenInFull,
                    contentDescription = stringRes(Res.string.media_compression_preview_zoom),
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        ComparisonLegend(
            hint = hintText(compresses, compressedUri != null, seeOriginal),
            original = stats?.original,
            compressed = if (compresses) stats?.compressed else null,
            showOriginal = seeOriginal,
            stale = loading,
            hintColor = MaterialTheme.colorScheme.primary,
            highlightColor = MaterialTheme.colorScheme.onSurface,
        )
    }

    if (zoomed) {
        CompressionZoomDialog(
            originalUri = originalUri,
            compressedUri = compressedUri,
            original = stats?.original,
            compressed = if (compresses) stats?.compressed else null,
            showOriginal = seeOriginal,
            stale = loading,
            onToggle = toggle,
            onDismiss = { zoomed = false },
        )
    }
}

@Composable
private fun hintText(
    compresses: Boolean,
    hasCompressed: Boolean,
    showOriginal: Boolean,
): String =
    when {
        !compresses -> stringRes(Res.string.media_compression_preview_uncompressed)
        // The first level is still compressing: keep the line so the layout doesn't grow when it lands.
        !hasCompressed -> ""
        showOriginal -> stringRes(Res.string.media_compression_preview_tap_compressed)
        else -> stringRes(Res.string.media_compression_preview_tap_original)
    }

/**
 * The original and the compressed image stacked in one box, so flipping between them is instant and
 * both sit at exactly the same place and scale. The original also covers for the compressed image
 * while a new level's file is still decoding, so the box never flashes empty.
 */
@Composable
private fun ComparisonImages(
    originalUri: String,
    compressedUri: String?,
    showOriginal: Boolean,
    modifier: Modifier = Modifier,
) {
    var compressedReady by remember(compressedUri) { mutableStateOf(false) }

    Box(modifier) {
        AsyncImage(
            model = originalUri,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize().alpha(if (showOriginal || !compressedReady) 1f else 0f),
        )
        if (compressedUri != null) {
            AsyncImage(
                model = compressedUri,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                onSuccess = { compressedReady = true },
                modifier = Modifier.fillMaxSize().alpha(if (showOriginal || !compressedReady) 0f else 1f),
            )
        }
    }
}

/**
 * The hint and the two stats lines. Every line is always laid out, empty when there is nothing to
 * say yet, so the block keeps its height across levels. [stale] dims the stats while they still
 * describe the previous level.
 */
@Composable
private fun ComparisonLegend(
    hint: String,
    original: ImageFileStats?,
    compressed: ImageFileStats?,
    showOriginal: Boolean,
    stale: Boolean,
    hintColor: Color,
    highlightColor: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = hint,
            style = MaterialTheme.typography.bodySmall,
            color = hintColor,
            textAlign = TextAlign.Center,
        )
        StatsLine(
            label = stringRes(Res.string.media_compression_preview_compressed),
            stats = compressed,
            color = if (!showOriginal && !stale) highlightColor else Color.Gray,
        )
        StatsLine(
            label = stringRes(Res.string.media_compression_preview_original),
            stats = original,
            color = if (showOriginal) highlightColor else Color.Gray,
        )
    }
}

@Composable
private fun StatsLine(
    label: String,
    stats: ImageFileStats?,
    color: Color,
) {
    val parts =
        listOfNotNull(
            if (stats?.width != null && stats.height != null) "${stats.width} × ${stats.height}" else null,
            stats?.bytes?.let { formatFileSize(it) },
        )
    Text(
        text = if (stats == null) "" else "$label: " + parts.joinToString(" · "),
        style = MaterialTheme.typography.bodySmall,
        color = color,
    )
}

/**
 * The comparison full screen: pinch, scroll or double-tap to zoom into the detail compression costs,
 * tap to flip between compressed and original. One zoom state drives both images, so the flip keeps
 * the zoomed-in region in place.
 */
@Composable
private fun CompressionZoomDialog(
    originalUri: String,
    compressedUri: String?,
    original: ImageFileStats?,
    compressed: ImageFileStats?,
    showOriginal: Boolean,
    stale: Boolean,
    onToggle: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = edgeToEdgeDialogProperties(),
    ) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            val zoomState = rememberZoomState()

            // Pan limits follow the picture, not the letterboxed screen around it.
            LaunchedEffect(original) {
                val width = original?.width
                val height = original?.height
                if (width != null && height != null) {
                    zoomState.setContentSize(Size(width.toFloat(), height.toFloat()))
                }
            }

            ComparisonImages(
                originalUri = originalUri,
                compressedUri = compressedUri,
                showOriginal = showOriginal,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .zoomable(
                            zoomState = zoomState,
                            mouseWheelZoom = MouseWheelZoom.Enabled,
                            onTap = { onToggle?.invoke() },
                        ),
            )

            IconButton(
                onClick = onDismiss,
                modifier =
                    Modifier
                        .align(Alignment.TopStart)
                        .statusBarsPadding()
                        .padding(12.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.4f)),
            ) {
                Icon(symbol = MaterialSymbols.Close, contentDescription = stringRes(Res.string.close), tint = Color.White)
            }

            if (stale) {
                CircularProgressIndicator(
                    modifier =
                        Modifier
                            .align(Alignment.TopEnd)
                            .statusBarsPadding()
                            .padding(24.dp)
                            .size(24.dp),
                    strokeWidth = 2.dp,
                    color = Color.White,
                )
            }

            ComparisonLegend(
                hint = stringRes(Res.string.media_compression_preview_zoom_hint),
                original = original,
                compressed = compressed,
                showOriginal = showOriginal,
                stale = stale,
                hintColor = Color.White,
                highlightColor = Color.White,
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.6f))
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
    }
}
