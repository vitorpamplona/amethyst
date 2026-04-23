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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.disk.DiskCache
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
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
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import net.engawapg.lib.zoomable.rememberZoomState
import net.engawapg.lib.zoomable.toggleScale
import net.engawapg.lib.zoomable.zoomable

// Hard ceiling on each base-rendered page bitmap, in pixels. Higher = sharper when
// the user pinch-zooms inside the dialog, but each page costs ~maxDim^2 * 4 bytes
// of RAM (PdfRenderer requires ARGB_8888). 3072 gives ~26 MB per A4-shaped page.
private const val VIEWER_MAX_DIM_PX = 3072

// Hard ceiling for the per-page zoom-aware detail render. When the user zooms in
// past HI_RES_ZOOM_THRESHOLD we re-render the current page at
// (VIEWER_MAX_DIM_PX * scale) capped at this value. 4096 keeps the bitmap within
// common GPU texture limits (so drawing stays hardware-accelerated) and caps
// memory at ~48 MB for an A4-shaped page. Above 4096 most mid-range GPUs fall
// back to software rendering, which is what caused the pan/zoom jitter.
private const val HI_RES_MAX_DIM_PX = 4096
private const val HI_RES_ZOOM_THRESHOLD = 1.5f
private const val HI_RES_DEBOUNCE_MS = 200L

// Zoom level the viewer animates to when the user double-taps. Matches the
// threshold region where we swap in the hi-res bitmap.
private const val DOUBLE_TAP_ZOOM_SCALE = 2.5f

// How many recently-rendered pages to keep around. Pager already pre-composes the
// current page plus one neighbor; this just speeds up small back/forward swipes.
// At VIEWER_MAX_DIM_PX = 3072 this caps memory at ~80 MB worth of page bitmaps.
private const val PAGE_CACHE_SIZE = 3

private class PageBitmapCache(
    private val maxSize: Int,
) {
    private val cache =
        object : java.util.LinkedHashMap<Int, Bitmap>(maxSize, 0.75f, true) {
            override fun removeEldestEntry(eldest: Map.Entry<Int, Bitmap>): Boolean = size > maxSize
        }

    @Synchronized fun get(key: Int): Bitmap? = cache[key]

    @Synchronized fun put(
        key: Int,
        value: Bitmap,
    ) {
        cache[key] = value
    }
}

private class PdfDocumentHandle(
    val snapshot: DiskCache.Snapshot,
    val pfd: ParcelFileDescriptor,
    val renderer: PdfRenderer,
) {
    // Snapshot eagerly. Compose's saveable PagerState reads pageCount during
    // teardown, which can run *after* close() — so we can't query the renderer
    // lazily without tripping IllegalStateException("Document already closed").
    val pageCount: Int = renderer.pageCount
    val mutex: Mutex = Mutex()

    @Volatile var closed: Boolean = false
        private set

    fun close() {
        closed = true
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

    // Capture the handle as a local val so the onDispose lambda closes *this* handle,
    // not whatever the delegated property reads at dispose time. Without this, the
    // DisposableEffect keyed on handleState runs its onDispose when handleState
    // transitions from null -> handle, and `handleState?.close()` reads the new handle
    // and closes it right after it was created.
    val handleForDispose = handleState
    DisposableEffect(handleForDispose) {
        onDispose {
            handleForDispose?.close()
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
        val pageCache = remember(handle) { PageBitmapCache(PAGE_CACHE_SIZE) }

        Box(modifier = Modifier.fillMaxSize()) {
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
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .systemBarsPadding()
                        .padding(horizontal = Size15dp, vertical = Size10dp),
                horizontalArrangement = spacedBy(Size10dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    contentPadding = PaddingValues(horizontal = Size5dp),
                    colors = ButtonDefaults.outlinedButtonColors().copy(containerColor = MaterialTheme.colorScheme.background),
                ) {
                    Icon(
                        symbol = MaterialSymbols.AutoMirrored.ArrowBack,
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
                        symbol = MaterialSymbols.Share,
                        modifier = Size20Modifier,
                        contentDescription = stringRes(R.string.quick_action_share),
                    )
                }
            }
        }
    }
}

@OptIn(FlowPreview::class)
@Composable
private fun PdfPageView(
    handle: PdfDocumentHandle,
    pageIndex: Int,
    cache: PageBitmapCache,
) {
    val cached = cache.get(pageIndex)

    @Suppress("ProduceStateDoesNotAssignValue")
    val baseBitmap by produceState<Bitmap?>(initialValue = cached, key1 = handle, key2 = pageIndex) {
        if (value != null) return@produceState
        val rendered = renderPageCatching(handle, pageIndex, VIEWER_MAX_DIM_PX)
        rendered?.let { cache.put(pageIndex, it) }
        value = rendered
    }

    val zoomState = rememberZoomState()

    // Re-render the page at a higher resolution once the user zooms in and settles,
    // so pinch-zoomed text stays crisp instead of getting GPU-upscaled from the base
    // bitmap. Released when zoom drops back under threshold or the page leaves view.
    var hiResBitmap by remember(handle, pageIndex) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(handle, pageIndex, baseBitmap) {
        if (baseBitmap == null) return@LaunchedEffect
        snapshotFlow { zoomState.scale }
            .debounce(HI_RES_DEBOUNCE_MS)
            .distinctUntilChanged()
            .collectLatest { scale ->
                if (scale < HI_RES_ZOOM_THRESHOLD) {
                    hiResBitmap = null
                } else {
                    val target = (VIEWER_MAX_DIM_PX * scale).toInt().coerceAtMost(HI_RES_MAX_DIM_PX)
                    // Skip if the hi-res render wouldn't beat what we already have.
                    val base = baseBitmap ?: return@collectLatest
                    val baseLongest = maxOf(base.width, base.height)
                    if (target <= baseLongest) {
                        hiResBitmap = null
                    } else {
                        hiResBitmap = renderPageCatching(handle, pageIndex, target)
                    }
                }
            }
    }

    val current = hiResBitmap ?: baseBitmap
    // Cache the ImageBitmap wrapper so each recomposition doesn't allocate a new
    // one and push Compose into thinking the texture changed.
    val imageBitmap = remember(current) { current?.asImageBitmap() }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (imageBitmap != null) {
            Image(
                bitmap = imageBitmap,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                // Medium = bilinear. High is bicubic/Mitchell and gets recomputed on every
                // frame during pan/zoom, which is the main source of jitter when the
                // source bitmap is several megapixels. Bilinear on a 3072-4096 px source
                // looks effectively identical on-screen.
                filterQuality = FilterQuality.Medium,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .zoomable(
                            zoomState = zoomState,
                            onDoubleTap = { position ->
                                zoomState.toggleScale(targetScale = DOUBLE_TAP_ZOOM_SCALE, position = position)
                            },
                        ),
            )
        } else {
            CircularProgressIndicator(color = Color.White)
        }
    }
}

private suspend fun renderPageCatching(
    handle: PdfDocumentHandle,
    pageIndex: Int,
    maxDim: Int,
): Bitmap? =
    try {
        handle.mutex.withLock {
            if (handle.closed) {
                null
            } else {
                withContext(Dispatchers.IO) {
                    handle.renderer.openPage(pageIndex).use { page ->
                        val (width, height) = cappedRenderSize(page.width, page.height, maxDim)
                        // PdfRenderer requires ARGB_8888 bitmaps — RGB_565 is silently
                        // rejected and produces blank output.
                        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(android.graphics.Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bmp
                    }
                }
            }
        }
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        Log.w("PdfViewerDialog", "Failed to render page $pageIndex at $maxDim px", e)
        null
    }
