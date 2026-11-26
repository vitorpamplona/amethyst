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
package com.vitorpamplona.amethyst.commons.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil3.compose.AsyncImage
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.close
import com.vitorpamplona.amethyst.commons.richtext.BaseMediaContent
import com.vitorpamplona.amethyst.commons.richtext.MediaUrlImage
import com.vitorpamplona.amethyst.commons.ui.stringRes
import kotlinx.collections.immutable.ImmutableList
import net.engawapg.lib.zoomable.ZoomState
import net.engawapg.lib.zoomable.rememberZoomState
import net.engawapg.lib.zoomable.zoomable

/**
 * A touch image viewer built only from shared pieces: the note's images in a pager, each one
 * pinch- and double-tap-zoomable, growing out of the thumbnail the user tapped ([source]) and
 * shrinking back into the thumbnail of the image on screen when it closes. Front ends without a
 * viewer of their own (iOS) open it; Android and the desktop keep theirs for now.
 */
@Composable
fun ImageViewerDialog(
    content: BaseMediaContent,
    images: ImmutableList<BaseMediaContent>,
    source: ZoomSourceAnchor?,
    sourceCornerRadius: Dp,
    onDismiss: () -> Unit,
) {
    val viewable = remember(images, content) { images.filterIsInstance<MediaUrlImage>().ifEmpty { listOfNotNull(content as? MediaUrlImage) } }
    if (viewable.isEmpty()) return
    val start = remember(viewable, content) { viewable.indexOfFirst { it.url == (content as? MediaUrlImage)?.url }.coerceAtLeast(0) }

    // Closes into the thumbnail of the image on screen: after paging, that one's own, or a fade
    // when the note doesn't show it.
    var shown by remember { mutableIntStateOf(start) }
    val group = LocalZoomSourceGroup.current
    val transition =
        rememberZoomTransition(
            source = { zoomSourceFor(viewable.getOrNull(shown), viewable.getOrNull(start), source, group) },
            sourceCornerRadius = sourceCornerRadius,
            onDismissed = onDismiss,
        )

    Dialog(
        onDismissRequest = transition::dismiss,
        properties = mediaViewerDialogProperties(),
    ) {
        SetDialogToEdgeToEdge()
        val pager = rememberPagerState(initialPage = start) { viewable.size }
        LaunchedEffect(pager) { snapshotFlow { pager.currentPage }.collect { shown = it } }
        val zoomStates = remember { mutableStateMapOf<Int, ZoomState>() }

        Box(
            Modifier
                .fillMaxSize()
                .zoomTransitionContainer(transition)
                .drawBehind { drawRect(Color.Black.copy(alpha = BACKDROP_ALPHA * transition.progress)) },
        ) {
            Box(Modifier.fillMaxSize().zoomTransitionLayer(transition) { zoomStates[pager.currentPage] }) {
                HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
                    val zoomState = rememberZoomState()
                    zoomStates[page] = zoomState
                    var aspectRatio by remember { mutableFloatStateOf(0f) }
                    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
                    // The grow-in starts once the opened image is laid out and knows its shape.
                    LaunchedEffect(aspectRatio, coordinates) {
                        val placed = coordinates
                        if (page == start && aspectRatio > 0f && placed != null) transition.onMediaPositioned(placed, aspectRatio)
                    }
                    AsyncImage(
                        model = viewable[page].url,
                        contentDescription = viewable[page].description,
                        contentScale = ContentScale.Fit,
                        onSuccess = { state ->
                            val size = state.painter.intrinsicSize
                            if (size.width > 0f && size.height > 0f) {
                                aspectRatio = size.width / size.height
                                zoomState.setContentSize(size)
                            }
                        },
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .onGloballyPositioned { coordinates = it }
                                .zoomable(zoomState),
                    )
                }
            }

            IconButton(
                onClick = transition::dismiss,
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(8.dp)
                        .graphicsLayer { alpha = transition.progress },
            ) {
                Icon(symbol = MaterialSymbols.Close, contentDescription = stringRes(Res.string.close), tint = Color.White)
            }
        }
    }
}

private const val BACKDROP_ALPHA = 0.95f
