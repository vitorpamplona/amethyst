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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PictureAsPdf
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.richtext.MediaUrlPdf
import com.vitorpamplona.amethyst.ui.components.ClickableUrl
import com.vitorpamplona.amethyst.ui.components.DisplayUrlWithLoadingSymbol
import com.vitorpamplona.amethyst.ui.components.ShareMediaAction
import com.vitorpamplona.amethyst.ui.components.WaitAndDisplay
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.theme.DoubleVertSpacer
import com.vitorpamplona.amethyst.ui.theme.MaxWidthWithHorzPadding
import com.vitorpamplona.amethyst.ui.theme.Size20Modifier
import com.vitorpamplona.amethyst.ui.theme.innerPostModifier
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PdfPreviewCard(
    content: MediaUrlPdf,
    accountViewModel: AccountViewModel,
    onOpen: () -> Unit,
) {
    val context = LocalContext.current
    val sharePopupExpanded = remember { mutableStateOf(false) }

    @Suppress("ProduceStateDoesNotAssignValue")
    val state by produceState<PdfLoadState>(initialValue = PdfLoadState.Loading, key1 = content.url) {
        value =
            try {
                val file =
                    PdfFetcher.fetch(context, content.url) { url ->
                        accountViewModel.httpClientBuilder.okHttpClientForPreview(url)
                    }
                withContext(Dispatchers.IO) {
                    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                        PdfRenderer(pfd).use { renderer ->
                            val pageCount = renderer.pageCount
                            if (pageCount <= 0) {
                                PdfLoadState.Failed
                            } else {
                                renderer.openPage(0).use { page ->
                                    val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
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

    when (val current = state) {
        is PdfLoadState.Loading -> {
            WaitAndDisplay {
                DisplayUrlWithLoadingSymbol(content, accountViewModel.toastManager::toast)
            }
        }

        is PdfLoadState.Failed -> {
            ClickableUrl(urlText = content.url, url = content.url)
        }

        is PdfLoadState.Ready -> {
            val filename = remember(content.url) { extractFilename(content.url) }
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
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(current.preview.aspectRatio.coerceAtLeast(0.2f)),
                )

                Row(
                    modifier = MaxWidthWithHorzPadding.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.PictureAsPdf,
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
                            text = pageCountLabel(current.preview.pageCount),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }

                Spacer(modifier = DoubleVertSpacer)
            }
        }
    }
}

private fun extractFilename(url: String): String {
    val afterQuery = url.substringBefore('?').substringBefore('#')
    val name = afterQuery.substringAfterLast('/', afterQuery)
    return if (name.isBlank()) url else name
}

private fun pageCountLabel(pageCount: Int): String = if (pageCount == 1) "1 page" else "$pageCount pages"
