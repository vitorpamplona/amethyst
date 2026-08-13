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
package com.vitorpamplona.amethyst.ui.components.pdf

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.core.graphics.createBitmap
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.richtext.MediaUrlPdf
import com.vitorpamplona.amethyst.commons.ui.components.LoadingAnimation
import com.vitorpamplona.amethyst.model.MediaAspectRatioCache
import com.vitorpamplona.amethyst.ui.components.ClickableUrl
import com.vitorpamplona.amethyst.ui.components.FileAttachmentRow
import com.vitorpamplona.amethyst.ui.components.ShareMediaAction
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.theme.DoubleVertSpacer
import com.vitorpamplona.amethyst.ui.theme.Size40dp
import com.vitorpamplona.amethyst.ui.theme.Size6dp
import com.vitorpamplona.amethyst.ui.theme.innerPostModifier
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Hard ceiling on the inline thumbnail bitmap, in pixels. Prevents OOM on very tall/large pages.
private const val THUMBNAIL_MAX_DIM_PX = 1600

// Floor for the width/height ratio the card lays out with. A pathologically tall page (a receipt,
// a single-column banner) would otherwise reserve a screenful of height for a sliver of content.
private const val MIN_PREVIEW_ASPECT_RATIO = 0.2f

// Shape to assume when the page reports no usable size at all. US Letter portrait — the
// overwhelmingly common page shape, and the least surprising box to hold open for an unknown one.
private const val DEFAULT_PAGE_ASPECT_RATIO = 612f / 792f

/**
 * The ratio the card actually lays out with, given a page's natural width/height. The placeholder
 * and the loaded thumbnail both go through here so the box reserved while loading is the exact box
 * the rendered page lands in — the clamp has to be applied on both sides or the reservation is
 * wrong for the very pages it exists to protect.
 *
 * This is also the one place that guarantees `Modifier.aspectRatio` is handed a finite, positive
 * number. It throws `IllegalArgumentException` on `0f` and on `NaN`, and the clamp below does not
 * catch either: `NaN.coerceAtLeast(x)` is `NaN`, because every comparison against `NaN` is false.
 * Both are reachable from real input — a malformed PDF whose first page measures 0x0, or an imeta
 * `dim` that survives DimensionTag.parse's only-rejects-literal-"0x0" check as 0x0 anyway
 * (`"0.4x0.4"` truncates to it). Without this guard either one takes down the whole feed's
 * composition, from a tag any relay can carry.
 */
internal fun previewAspectRatio(pageAspectRatio: Float): Float =
    if (!pageAspectRatio.isFinite() || pageAspectRatio <= 0f) {
        DEFAULT_PAGE_ASPECT_RATIO
    } else {
        pageAspectRatio.coerceAtLeast(MIN_PREVIEW_ASPECT_RATIO)
    }

data class PdfPreview(
    val thumbnail: Bitmap,
    val pageCount: Int,
    val pageWidth: Int,
    val pageHeight: Int,
) {
    val aspectRatio: Float = pageWidth.toFloat() / pageHeight.toFloat()
}

private sealed class PdfLoadState {
    data object Loading : PdfLoadState()

    data class Ready(
        val preview: PdfPreview,
    ) : PdfLoadState()

    data object Failed : PdfLoadState()
}

@Composable
fun PdfPreviewCard(
    content: MediaUrlPdf,
    accountViewModel: AccountViewModel,
    onOpen: () -> Unit,
) {
    val showPdf = remember { mutableStateOf(accountViewModel.settings.showImages()) }

    if (showPdf.value) {
        LoadedPdfPreviewCard(content, accountViewModel, onOpen)
    } else {
        PlaceholderPdfCard(content) { showPdf.value = true }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LoadedPdfPreviewCard(
    content: MediaUrlPdf,
    accountViewModel: AccountViewModel,
    onOpen: () -> Unit,
) {
    val sharePopupExpanded = remember { mutableStateOf(false) }

    val containerWidthPx = LocalWindowInfo.current.containerSize.width
    val targetWidthPx =
        remember(containerWidthPx) {
            containerWidthPx.coerceAtMost(THUMBNAIL_MAX_DIM_PX).coerceAtLeast(1)
        }

    // Read in composition — never inside a remember — so this recomposes the moment the render
    // below fills the entry in. On a revisit the entry is already there and the placeholder can
    // reserve the right box from the first frame, which is the whole point of caching it.
    // The cache is consulted ahead of the imeta `dim` because it holds the page size this card
    // measured itself: an author-supplied `dim` that disagrees would guarantee the jump on every
    // single visit, which is exactly what this is here to stop.
    val knownAspectRatio = MediaAspectRatioCache.get(content.url) ?: content.dim?.aspectRatioOrNull()

    @Suppress("ProduceStateDoesNotAssignValue")
    val state by produceState<PdfLoadState>(initialValue = PdfLoadState.Loading, key1 = content.url, key2 = targetWidthPx) {
        value =
            try {
                PdfFetcher
                    .fetchSnapshot(content.url) { url ->
                        accountViewModel.httpClientBuilder.okHttpClientForPreview(url)
                    }.use { snapshot ->
                        withContext(Dispatchers.IO) {
                            renderFirstPage(snapshot.data.toFile(), targetWidthPx)
                        }
                    }.also { result ->
                        // Same cache the image and video paths use, so a PDF that has been rendered
                        // once lays out at its real shape on every later visit instead of growing
                        // from a bare filename row into a full page.
                        if (result is PdfLoadState.Ready) {
                            MediaAspectRatioCache.add(content.url, result.preview.pageWidth, result.preview.pageHeight)
                        }
                    }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.w("PdfPreviewCard", "Failed to render PDF preview: ${content.url}", e)
                PdfLoadState.Failed
            }
    }

    ShareMediaAction(
        accountViewModel = accountViewModel,
        popupExpanded = sharePopupExpanded,
        content = content,
        onDismiss = { sharePopupExpanded.value = false },
    )

    val filename = remember(content.url) { extractFilename(content.url) }

    when (val current = state) {
        is PdfLoadState.Loading -> {
            PdfSkeletonCard(filename, knownAspectRatio)
        }

        is PdfLoadState.Failed -> {
            ClickableUrl(urlText = content.url, url = content.url)
        }

        is PdfLoadState.Ready -> {
            Column(
                modifier =
                    MaterialTheme.colorScheme.innerPostModifier
                        .combinedClickable(
                            onClick = onOpen,
                            onLongClick = { sharePopupExpanded.value = true },
                        ),
            ) {
                // asImageBitmap() allocates a fresh wrapper on every call and the wrapper compares
                // by identity, so calling it inline would defeat the remember(bitmap) that Image
                // uses to hold its BitmapPainter — every recomposition would rebuild the painter.
                val thumbnail = remember(current.preview.thumbnail) { current.preview.thumbnail.asImageBitmap() }

                Image(
                    bitmap = thumbnail,
                    contentDescription = content.description ?: filename,
                    contentScale = ContentScale.FillWidth,
                    filterQuality = FilterQuality.High,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(previewAspectRatio(current.preview.aspectRatio)),
                )

                FilenameRow(filename = filename, subtitle = pageCountLabel(current.preview.pageCount))

                Spacer(modifier = DoubleVertSpacer)
            }
        }
    }
}

@Composable
private fun PlaceholderPdfCard(
    content: MediaUrlPdf,
    onLoad: () -> Unit,
) {
    val filename = remember(content.url) { extractFilename(content.url) }
    Column(
        modifier =
            MaterialTheme.colorScheme.innerPostModifier
                .fillMaxWidth()
                .combinedClickable(onClick = onLoad, onLongClick = onLoad),
    ) {
        FilenameRow(filename = filename, subtitle = "Tap to load PDF")
        Spacer(modifier = DoubleVertSpacer)
    }
}

@Composable
private fun PdfSkeletonCard(
    filename: String,
    aspectRatio: Float?,
) {
    Column(modifier = MaterialTheme.colorScheme.innerPostModifier.fillMaxWidth()) {
        // Only known on a revisit — the first render is what fills the cache — so the first sight of
        // a PDF still grows into place. From then on the page's box is held open while it renders
        // and the feed stays put.
        if (aspectRatio != null) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(previewAspectRatio(aspectRatio)),
                contentAlignment = Alignment.Center,
            ) {
                LoadingAnimation(Size40dp, Size6dp)
            }
        }

        FilenameRow(filename = filename, subtitle = "Loading…")
        Spacer(modifier = DoubleVertSpacer)
    }
}

@Composable
private fun FilenameRow(
    filename: String,
    subtitle: String,
) = FileAttachmentRow(
    symbol = MaterialSymbols.PictureAsPdf,
    title = filename,
    subtitle = subtitle,
)

private fun renderFirstPage(
    file: java.io.File,
    targetWidthPx: Int,
): PdfLoadState =
    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
        PdfRenderer(pfd).use { renderer ->
            val pageCount = renderer.pageCount
            if (pageCount <= 0) return@use PdfLoadState.Failed

            renderer.openPage(0).use { page ->
                val (renderW, renderH) = cappedRenderSize(page.width, page.height, targetWidthPx)
                // PdfRenderer requires ARGB_8888; RGB_565 silently produces blank output.
                val bitmap = createBitmap(renderW, renderH)
                bitmap.eraseColor(android.graphics.Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                PdfLoadState.Ready(
                    PdfPreview(
                        thumbnail = bitmap,
                        pageCount = pageCount,
                        pageWidth = page.width,
                        pageHeight = page.height,
                    ),
                )
            }
        }
    }

/**
 * Returns the bitmap dimensions to render a PDF page at, scaled so the longest side equals
 * [targetDim] while preserving aspect ratio. Always scales, never returns native size: a PDF
 * page's native width/height are in PostScript points (1/72"), which is far below any useful
 * display resolution. Since PDFs are vector, rendering at a larger target is essentially free
 * and avoids a 72-DPI-blurry bitmap.
 */
internal fun cappedRenderSize(
    pageWidth: Int,
    pageHeight: Int,
    targetDim: Int,
): Pair<Int, Int> {
    if (pageWidth <= 0 || pageHeight <= 0) return 1 to 1
    val longest = maxOf(pageWidth, pageHeight)
    val scale = targetDim.toFloat() / longest
    val w = (pageWidth * scale).toInt().coerceAtLeast(1)
    val h = (pageHeight * scale).toInt().coerceAtLeast(1)
    return w to h
}

internal fun extractFilename(url: String): String {
    val afterQuery = url.substringBefore('?').substringBefore('#')
    val name = afterQuery.substringAfterLast('/', afterQuery)
    return if (name.isBlank()) url else name
}

internal fun pageCountLabel(pageCount: Int): String = if (pageCount == 1) "1 page" else "$pageCount pages"
