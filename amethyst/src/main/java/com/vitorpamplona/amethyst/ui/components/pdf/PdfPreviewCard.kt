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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.richtext.MediaUrlPdf
import com.vitorpamplona.amethyst.ui.components.ClickableUrl
import com.vitorpamplona.amethyst.ui.components.ShareMediaAction
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.theme.DoubleVertSpacer
import com.vitorpamplona.amethyst.ui.theme.MaxWidthWithHorzPadding
import com.vitorpamplona.amethyst.ui.theme.Size20Modifier
import com.vitorpamplona.amethyst.ui.theme.innerPostModifier
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Hard ceiling on the inline thumbnail bitmap, in pixels. Prevents OOM on very tall/large pages.
private const val THUMBNAIL_MAX_DIM_PX = 1600

data class PdfPreview(
    val thumbnail: Bitmap,
    val pageCount: Int,
    val aspectRatio: Float,
)

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

    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val targetWidthPx =
        remember(density, configuration) {
            val screenPx =
                with(density) {
                    configuration.screenWidthDp.dp
                        .toPx()
                        .toInt()
                }
            screenPx.coerceAtMost(THUMBNAIL_MAX_DIM_PX).coerceAtLeast(1)
        }

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
            PdfSkeletonCard(filename)
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
                Image(
                    bitmap = current.preview.thumbnail.asImageBitmap(),
                    contentDescription = content.description ?: filename,
                    contentScale = ContentScale.FillWidth,
                    filterQuality = FilterQuality.High,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(current.preview.aspectRatio.coerceAtLeast(0.2f)),
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
private fun PdfSkeletonCard(filename: String) {
    Column(modifier = MaterialTheme.colorScheme.innerPostModifier.fillMaxWidth()) {
        FilenameRow(filename = filename, subtitle = "Loading…")
        Spacer(modifier = DoubleVertSpacer)
    }
}

@Composable
private fun FilenameRow(
    filename: String,
    subtitle: String,
) {
    Row(
        modifier = MaxWidthWithHorzPadding.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            symbol = MaterialSymbols.PictureAsPdf,
            contentDescription = null,
            modifier = Size20Modifier,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = filename,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

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
                val bitmap = Bitmap.createBitmap(renderW, renderH, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(android.graphics.Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                PdfLoadState.Ready(
                    PdfPreview(
                        thumbnail = bitmap,
                        pageCount = pageCount,
                        aspectRatio = page.width.toFloat() / page.height.toFloat(),
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
