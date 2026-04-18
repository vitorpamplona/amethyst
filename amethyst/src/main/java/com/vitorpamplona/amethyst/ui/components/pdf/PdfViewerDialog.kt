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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.disk.DiskCache
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.richtext.MediaUrlPdf
import com.vitorpamplona.amethyst.ui.components.ShareMediaAction
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.Size10dp
import com.vitorpamplona.amethyst.ui.theme.Size15dp
import com.vitorpamplona.amethyst.ui.theme.Size20Modifier
import com.vitorpamplona.amethyst.ui.theme.Size5dp
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import net.engawapg.lib.zoomable.rememberZoomState
import net.engawapg.lib.zoomable.zoomable

// Hard ceiling on each rendered page bitmap, in pixels. Prevents OOM on very large pages.
private const val VIEWER_MAX_DIM_PX = 2048

private class PdfDocumentHandle(
    val snapshot: DiskCache.Snapshot,
    val pfd: ParcelFileDescriptor,
    val renderer: PdfRenderer,
) {
    val pageCount: Int get() = renderer.pageCount
    val mutex: Mutex = Mutex()

    fun close() {
        runCatching { renderer.close() }.onFailure { Log.w("PdfViewerDialog", "renderer close failed", it) }
        runCatching { pfd.close() }.onFailure { Log.w("PdfViewerDialog", "pfd close failed", it) }
        runCatching { snapshot.close() }.onFailure { Log.w("PdfViewerDialog", "snapshot close failed", it) }
    }
}

@Composable
fun PdfViewerDialog(
    content: MediaUrlPdf,
    accountViewModel: AccountViewModel,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties =
            DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
            ),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
            PdfViewerContent(
                content = content,
                accountViewModel = accountViewModel,
                onDismiss = onDismiss,
            )
        }
    }
}

@Composable
private fun PdfViewerContent(
    content: MediaUrlPdf,
    accountViewModel: AccountViewModel,
    onDismiss: () -> Unit,
) {
    @Suppress("ProduceStateDoesNotAssignValue")
    val handleState by produceState<PdfDocumentHandle?>(initialValue = null, key1 = content.url) {
        value =
            try {
                withContext(Dispatchers.IO) {
                    val snapshot =
                        PdfFetcher.fetchSnapshot(content.url) { url ->
                            accountViewModel.httpClientBuilder.okHttpClientForPreview(url)
                        }
                    try {
                        val pfd = ParcelFileDescriptor.open(snapshot.data.toFile(), ParcelFileDescriptor.MODE_READ_ONLY)
                        val renderer = PdfRenderer(pfd)
                        PdfDocumentHandle(snapshot, pfd, renderer)
                    } catch (t: Throwable) {
                        runCatching { snapshot.close() }
                        throw t
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.w("PdfViewerDialog", "Failed to open PDF: ${content.url}", e)
                null
            }
    }

    DisposableEffect(handleState) {
        onDispose {
            handleState?.close()
        }
    }

    val sharePopupExpanded = remember { mutableStateOf(false) }

    ShareMediaAction(
        accountViewModel = accountViewModel,
        popupExpanded = sharePopupExpanded,
        content = content,
        onDismiss = { sharePopupExpanded.value = false },
    )

    val handle = handleState
    if (handle == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color.White)
        }
    } else if (handle.pageCount == 0) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "Unable to open PDF",
                color = Color.White,
            )
        }
    } else {
        val pagerState = rememberPagerState { handle.pageCount }
        val pageCache = remember(handle) { mutableStateMapOf<Int, Bitmap>() }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { pageIndex ->
            PdfPageView(
                handle = handle,
                pageIndex = pageIndex,
                cache = pageCache,
            )
        }

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Size15dp, vertical = Size10dp)
                    .statusBarsPadding()
                    .systemBarsPadding(),
            horizontalArrangement = spacedBy(Size10dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(
                onClick = onDismiss,
                contentPadding = PaddingValues(horizontal = Size5dp),
                colors = ButtonDefaults.outlinedButtonColors().copy(containerColor = MaterialTheme.colorScheme.background),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringRes(R.string.back),
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = "${pagerState.currentPage + 1} / ${handle.pageCount}",
                color = Color.White,
                modifier =
                    Modifier
                        .background(Color.Black.copy(alpha = 0.4f), shape = MaterialTheme.shapes.small)
                        .padding(horizontal = Size10dp, vertical = Size5dp),
            )

            Spacer(modifier = Modifier.weight(1f))

            OutlinedButton(
                onClick = { sharePopupExpanded.value = true },
                contentPadding = PaddingValues(horizontal = Size5dp),
                colors = ButtonDefaults.outlinedButtonColors().copy(containerColor = MaterialTheme.colorScheme.background),
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    modifier = Size20Modifier,
                    contentDescription = stringRes(R.string.quick_action_share),
                )
            }
        }
    }
}

@Composable
private fun PdfPageView(
    handle: PdfDocumentHandle,
    pageIndex: Int,
    cache: MutableMap<Int, Bitmap>,
) {
    val cached = cache[pageIndex]

    @Suppress("ProduceStateDoesNotAssignValue")
    val bitmap by produceState<Bitmap?>(initialValue = cached, key1 = handle, key2 = pageIndex) {
        if (value != null) return@produceState
        val rendered =
            try {
                handle.mutex.withLock {
                    withContext(Dispatchers.IO) {
                        handle.renderer.openPage(pageIndex).use { page ->
                            val (width, height) = cappedRenderSize(page.width, page.height, VIEWER_MAX_DIM_PX)
                            val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                            bmp.eraseColor(android.graphics.Color.WHITE)
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            bmp
                        }
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.w("PdfViewerDialog", "Failed to render page $pageIndex", e)
                null
            }

        rendered?.let { cache[pageIndex] = it }
        value = rendered
    }

    val zoomState = rememberZoomState()
    val current = bitmap
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (current != null) {
            Image(
                bitmap = current.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .zoomable(zoomState),
            )
        } else {
            CircularProgressIndicator(color = Color.White)
        }
    }
}
