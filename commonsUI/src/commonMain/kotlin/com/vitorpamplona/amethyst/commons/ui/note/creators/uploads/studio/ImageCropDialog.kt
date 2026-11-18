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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.cancel
import com.vitorpamplona.amethyst.commons.resources.media_crop_failed
import com.vitorpamplona.amethyst.commons.resources.media_crop_hint
import com.vitorpamplona.amethyst.commons.resources.media_crop_reset
import com.vitorpamplona.amethyst.commons.resources.save
import com.vitorpamplona.amethyst.commons.service.uploads.ImageCrop
import com.vitorpamplona.amethyst.commons.service.uploads.MediaUploader
import com.vitorpamplona.amethyst.commons.service.uploads.SelectedMedia
import com.vitorpamplona.amethyst.commons.service.uploads.refineMediaType
import com.vitorpamplona.amethyst.commons.ui.actions.uploads.GallerySelectSingle
import com.vitorpamplona.amethyst.commons.ui.stringRes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlin.math.abs

/** The frame a picked picture is cropped to: a locked [ratio] (width over height, null for any) and whether it shows in a circle. */
@Immutable
data class ImageCropShape(
    val ratio: Float?,
    val circle: Boolean,
) {
    companion object {
        /** A profile picture: square, shown in a circle. */
        val Avatar = ImageCropShape(1f, circle = true)

        /** A group or channel picture: square, shown in a circle. */
        val Icon = ImageCropShape(1f, circle = true)

        /** A profile or group banner: three times as wide as tall. */
        val Banner = ImageCropShape(3f, circle = false)
    }
}

/**
 * The platform's single-picture picker, then [ImageCropDialog] for a still picture. Reports the
 * cropped copy, the picked file when it has no crop step (GIF, SVG, video), or null on cancel.
 */
@Composable
fun GallerySelectSingleCropped(
    shape: ImageCropShape,
    uploader: MediaUploader,
    imagesOnly: Boolean = true,
    onImage: (SelectedMedia?) -> Unit,
) {
    var picked by remember { mutableStateOf<SelectedMedia?>(null) }
    var checking by remember { mutableStateOf<SelectedMedia?>(null) }
    val cropping = picked
    val unchecked = checking
    if (unchecked != null) {
        // Its header first: an animated WebP looks like a photo by its type, and a crop would keep one frame.
        LaunchedEffect(unchecked) {
            val media = uploader.refineMediaType(unchecked)
            checking = null
            if (uploader.canCrop(media)) picked = media else onImage(media)
        }
    } else if (cropping == null) {
        GallerySelectSingle(imagesOnly = imagesOnly) { media ->
            when {
                media == null -> onImage(null)
                uploader.canCrop(media) -> checking = media
                else -> onImage(media)
            }
        }
    } else {
        ImageCropDialog(
            media = cropping,
            shape = shape,
            uploader = uploader,
            onDone = onImage,
            onCancel = { onImage(null) },
        )
    }
}

/**
 * Crops one picture to [shape] before it is uploaded: the Studio's stage with the frame on it.
 * Save writes the crop ([MediaUploader.cropImage]) and hands the copy to [onDone]; a picture this
 * platform cannot crop is said so, and Save then hands it over as it is.
 */
@Composable
fun ImageCropDialog(
    media: SelectedMedia,
    shape: ImageCropShape,
    uploader: MediaUploader,
    onDone: (SelectedMedia) -> Unit,
    onCancel: () -> Unit,
) {
    var saving by remember { mutableStateOf(false) }
    StudioDialog(onDismissRequest = onCancel, canDismiss = !saving) {
        ImageCropStudio(media, shape, uploader, onDone, onCancel, saving = saving, onSaving = { saving = it })
    }
}

/** [ImageCropDialog]'s content, without the dialog, for tests. */
@Composable
fun ImageCropStudio(
    media: SelectedMedia,
    shape: ImageCropShape,
    uploader: MediaUploader,
    onDone: (SelectedMedia) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    saving: Boolean = false,
    onSaving: (Boolean) -> Unit = {},
    initialAspect: Float? = null,
) {
    val scope = rememberCoroutineScope()
    var imageAspect by remember(media) { mutableStateOf(initialAspect) }
    var crop by remember(media) { mutableStateOf<ImageCrop?>(null) }
    var failed by remember(media) { mutableStateOf(false) }

    fun startingCrop(aspect: Float) = shape.ratio?.let { ImageCrop.centered(aspect, it) } ?: ImageCrop.FULL

    val aspect = imageAspect
    if (aspect != null && crop == null) crop = startingCrop(aspect)

    fun save() {
        val chosen = crop
        if (failed || chosen == null) {
            onDone(media)
            return
        }
        scope.launch {
            onSaving(true)
            val result =
                try {
                    uploader.cropImage(media.uri, media.mimeType, chosen)
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    null
                } finally {
                    onSaving(false)
                }
            if (result == null) {
                failed = true
            } else {
                onDone(SelectedMedia(result.uri, result.contentType, temporary = true))
            }
        }
    }

    Column(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onCancel, enabled = !saving) {
                Icon(MaterialSymbols.Close, contentDescription = stringRes(Res.string.cancel))
            }
            Spacer(Modifier.weight(1f))
            Button(onClick = ::save, enabled = !saving, modifier = Modifier.padding(end = 8.dp).height(36.dp)) {
                if (saving) {
                    CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(stringRes(Res.string.save))
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth().background(Color.Black), contentAlignment = Alignment.Center) {
            val current = crop
            if (aspect == null || current == null || failed) {
                // Loads the picture to learn its shape; shown whole when it cannot be cropped.
                AsyncImage(
                    model = media.uri.toString(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    onSuccess = { state ->
                        val size = state.painter.intrinsicSize
                        if (size.width > 0f && size.height > 0f) imageAspect = size.width / size.height
                    },
                    onError = { failed = true },
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                )
            } else {
                CropEditor(
                    model = media.uri.toString(),
                    imageAspect = aspect,
                    crop = current,
                    shape = shape,
                    enabled = !saving,
                    onCrop = { crop = it },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (failed) {
                    Text(
                        stringRes(Res.string.media_crop_failed),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f).padding(vertical = 12.dp),
                    )
                } else {
                    Text(
                        stringRes(Res.string.media_crop_hint),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = { aspect?.let { crop = startingCrop(it) } },
                        enabled = !saving && aspect != null,
                    ) {
                        Text(stringRes(Res.string.media_crop_reset))
                    }
                }
            }
        }
    }
}

internal enum class CropHandle { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT, MOVE }

/** The smallest the frame gets, as a fraction of the picture's shorter displayed side. */
private const val MIN_CROP = 0.1f

/**
 * The picture fitted to the space, the area outside [crop] dimmed (outside a circle when
 * [ImageCropShape.circle]), a rule-of-thirds grid, and corner handles. Dragging a corner resizes
 * the frame, keeping [ImageCropShape.ratio]; dragging inside moves it.
 */
@Composable
fun CropEditor(
    model: Any,
    imageAspect: Float,
    crop: ImageCrop,
    shape: ImageCropShape,
    onCrop: (ImageCrop) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val density = LocalDensity.current
    val latestCrop by rememberUpdatedState(crop)
    val latestOnCrop by rememberUpdatedState(onCrop)
    BoxWithConstraints(modifier.padding(24.dp), contentAlignment = Alignment.Center) {
        val boxAspect = maxWidth / maxHeight
        val (shownW, shownH) = if (imageAspect > boxAspect) maxWidth to maxWidth / imageAspect else maxHeight * imageAspect to maxHeight
        Box(Modifier.requiredSize(shownW, shownH)) {
            AsyncImage(
                model = model,
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize(),
            )
            var handle by remember { mutableStateOf<CropHandle?>(null) }
            Canvas(
                Modifier.fillMaxSize().pointerInput(imageAspect, shape, enabled) {
                    if (!enabled) return@pointerInput
                    val touch = with(density) { 32.dp.toPx() }
                    detectDragGestures(
                        onDragStart = { start ->
                            val r = latestCrop
                            val w = size.width.toFloat()
                            val h = size.height.toFloat()
                            val corners =
                                listOf(
                                    CropHandle.TOP_LEFT to Offset(r.left * w, r.top * h),
                                    CropHandle.TOP_RIGHT to Offset(r.right * w, r.top * h),
                                    CropHandle.BOTTOM_LEFT to Offset(r.left * w, r.bottom * h),
                                    CropHandle.BOTTOM_RIGHT to Offset(r.right * w, r.bottom * h),
                                )
                            handle = corners
                                .minByOrNull { (it.second - start).getDistance() }
                                ?.takeIf { (it.second - start).getDistance() < touch }
                                ?.first
                                ?: if (Rect(r.left * w, r.top * h, r.right * w, r.bottom * h).contains(start)) CropHandle.MOVE else null
                        },
                        onDragEnd = { handle = null },
                        onDragCancel = { handle = null },
                        onDrag = { change, drag ->
                            val which = handle ?: return@detectDragGestures
                            change.consume()
                            latestOnCrop(dragCrop(latestCrop, which, drag.x / size.width, drag.y / size.height, imageAspect, shape.ratio))
                        },
                    )
                },
            ) {
                val rect = Rect(crop.left * size.width, crop.top * size.height, crop.right * size.width, crop.bottom * size.height)
                val dim = Color.Black.copy(alpha = 0.6f)
                if (shape.circle) {
                    val hole =
                        Path().apply {
                            fillType = PathFillType.EvenOdd
                            addRect(Rect(Offset.Zero, size))
                            addOval(rect)
                        }
                    drawPath(hole, dim)
                    drawOval(Color.White, rect.topLeft, rect.size, style = Stroke(2.dp.toPx()))
                } else {
                    drawRect(dim, Offset.Zero, Size(size.width, rect.top))
                    drawRect(dim, Offset(0f, rect.bottom), Size(size.width, size.height - rect.bottom))
                    drawRect(dim, Offset(0f, rect.top), Size(rect.left, rect.height))
                    drawRect(dim, Offset(rect.right, rect.top), Size(size.width - rect.right, rect.height))
                }
                val grid = Color.White.copy(alpha = 0.35f)
                for (i in 1..2) {
                    drawLine(grid, Offset(rect.left + rect.width * i / 3, rect.top), Offset(rect.left + rect.width * i / 3, rect.bottom), 1.dp.toPx())
                    drawLine(grid, Offset(rect.left, rect.top + rect.height * i / 3), Offset(rect.right, rect.top + rect.height * i / 3), 1.dp.toPx())
                }
                drawRect(Color.White.copy(alpha = if (shape.circle) 0.5f else 1f), rect.topLeft, rect.size, style = Stroke(1.5.dp.toPx()))
                val len = 20.dp.toPx()
                val thick = 4.dp.toPx()
                listOf(
                    rect.topLeft to Offset(1f, 1f),
                    rect.topRight to Offset(-1f, 1f),
                    rect.bottomLeft to Offset(1f, -1f),
                    rect.bottomRight to Offset(-1f, -1f),
                ).forEach { (corner, dir) ->
                    drawLine(Color.White, corner, corner + Offset(len * dir.x, 0f), thick)
                    drawLine(Color.White, corner, corner + Offset(0f, len * dir.y), thick)
                }
            }
        }
    }
}

/**
 * [crop] after [handle] moved by ([dx], [dy]) of the picture's displayed size: a corner resizes
 * (holding the opposite corner and the [ratio], in displayed pixels, of a picture [imageAspect]
 * wide per unit of height), the frame moves whole, and it never leaves the picture.
 */
internal fun dragCrop(
    crop: ImageCrop,
    handle: CropHandle,
    dx: Float,
    dy: Float,
    imageAspect: Float,
    ratio: Float?,
): ImageCrop {
    if (handle == CropHandle.MOVE) {
        val nx = (crop.left + dx).coerceIn(0f, 1f - crop.width)
        val ny = (crop.top + dy).coerceIn(0f, 1f - crop.height)
        return ImageCrop(nx, ny, nx + crop.width, ny + crop.height)
    }
    // The minimum in each axis's fraction, so the frame's shorter side stops at the same size.
    val minW = MIN_CROP * minOf(1f, 1f / imageAspect)
    val minH = MIN_CROP * minOf(1f, imageAspect)
    val anchorLeft = handle == CropHandle.TOP_RIGHT || handle == CropHandle.BOTTOM_RIGHT
    val anchorTop = handle == CropHandle.BOTTOM_LEFT || handle == CropHandle.BOTTOM_RIGHT
    // The dragged edges move; the opposite ones hold, and the frame never turns inside out.
    var left = crop.left
    var top = crop.top
    var right = crop.right
    var bottom = crop.bottom
    if (anchorLeft) right = (right + dx).coerceIn(minOf(left + minW, 1f), 1f) else left = (left + dx).coerceIn(0f, maxOf(right - minW, 0f))
    if (anchorTop) bottom = (bottom + dy).coerceIn(minOf(top + minH, 1f), 1f) else top = (top + dy).coerceIn(0f, maxOf(bottom - minH, 0f))

    if (ratio == null) return ImageCrop(left, top, right, bottom)

    // The axis the drag changed more leads; the other follows the ratio, and both shrink when
    // that would run off the picture.
    var w = right - left
    var h = bottom - top
    val widthLeads = abs(w - crop.width) / crop.width >= abs(h - crop.height) / crop.height
    if (widthLeads) h = w * imageAspect / ratio else w = h * ratio / imageAspect
    val roomW = if (anchorLeft) 1f - left else right
    val roomH = if (anchorTop) 1f - top else bottom
    if (w > roomW) {
        w = roomW
        h = w * imageAspect / ratio
    }
    if (h > roomH) {
        h = roomH
        w = h * ratio / imageAspect
    }
    if (anchorLeft) right = left + w else left = right - w
    if (anchorTop) bottom = top + h else top = bottom - h
    return ImageCrop(left, top, right, bottom)
}
