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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.video

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.size.Size
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.ui.components.BlurhashBackdrop
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.note.ReactionsRow
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.quartz.nip68Picture.PictureEvent
import com.vitorpamplona.quartz.nip71Video.VideoEvent
import com.vitorpamplona.quartz.nip94FileMetadata.FileHeaderEvent
import kotlinx.coroutines.delay

private const val AUTO_HIDE_MS = 3000L
private const val REACTIONS_SCALE = 1.5f
private const val BACKDROP_SCRIM_ALPHA = 0.25f
private const val BACKDROP_POSTER_DECODE_PX = 128
private val BackdropPosterBlur = 24.dp
internal val ReactionsOverlayClearance = 80.dp

@Composable
private fun EnlargedReactionsRow(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val base = LocalDensity.current
    val scaled =
        remember(base) {
            Density(
                density = base.density * REACTIONS_SCALE,
                fontScale = base.fontScale,
            )
        }
    CompositionLocalProvider(LocalDensity provides scaled) {
        ReactionsRow(
            baseNote = baseNote,
            showReactionDetail = false,
            addPadding = true,
            editState = null,
            tint = Color.White,
            accountViewModel = accountViewModel,
            nav = nav,
        )
    }
}

private data class PageBackdrop(
    val blurhash: String?,
    val thumbhash: String?,
    val posterUrl: String?,
)

private fun pageBackdropData(note: Note): PageBackdrop =
    when (val event = note.event) {
        is VideoEvent -> {
            val meta = event.imetaTags().firstOrNull()
            PageBackdrop(meta?.blurhash, meta?.thumbhash, meta?.image?.firstOrNull())
        }

        is PictureEvent -> {
            val meta = event.imetaTags().firstOrNull()
            PageBackdrop(meta?.blurhash, meta?.thumbhash, null)
        }

        is FileHeaderEvent -> {
            PageBackdrop(event.blurhash(), event.thumbhash(), null)
        }

        else -> {
            PageBackdrop(null, null, null)
        }
    }

private val PageBackdropGradient =
    Brush.verticalGradient(
        colors = listOf(Color(0xFF1E1A2E), Color(0xFF0A0814)),
    )

@Composable
fun VideoPagerPage(
    baseNote: Note,
    padding: PaddingValues,
    accountViewModel: AccountViewModel,
    nav: INav,
    content: @Composable () -> Unit,
) {
    var chromeVisible by remember(baseNote.idHex) { mutableStateOf(true) }

    LaunchedEffect(chromeVisible) {
        if (chromeVisible) {
            delay(AUTO_HIDE_MS)
            chromeVisible = false
        }
    }

    val backdrop = remember(baseNote.idHex) { pageBackdropData(baseNote) }

    // Backdrop is full-bleed (extends behind the transparent top bar). Gradient base prevents
    // a fallback to scaffold black when no media hash or poster is available.
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(PageBackdropGradient),
    ) {
        when {
            backdrop.thumbhash != null || backdrop.blurhash != null -> {
                BlurhashBackdrop(
                    blurhash = backdrop.blurhash,
                    description = null,
                    thumbhash = backdrop.thumbhash,
                )
            }

            backdrop.posterUrl != null -> {
                // The poster is invisible behind the 24dp blur + dark scrim; ask Coil to decode
                // a tiny version so we don't pay full-resolution decode + GPU RenderEffect cost.
                val context = LocalContext.current
                val request =
                    remember(backdrop.posterUrl) {
                        ImageRequest
                            .Builder(context)
                            .data(backdrop.posterUrl)
                            .size(Size(BACKDROP_POSTER_DECODE_PX, BACKDROP_POSTER_DECODE_PX))
                            .build()
                    }
                AsyncImage(
                    model = request,
                    contentDescription = null,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .blur(BackdropPosterBlur),
                    contentScale = ContentScale.Crop,
                )
            }
        }

        // Dim scrim improves chrome legibility over bright media.
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = BACKDROP_SCRIM_ALPHA)),
        )

        // Foreground layer — respects scaffold insets so card + reactions stay clear of the bars.
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),
        ) {
            content()

            if (!chromeVisible) {
                // Tap-catcher only when overlay is hidden — keeps card controls tappable when visible.
                Box(
                    modifier =
                        Modifier
                            .matchParentSize()
                            .pointerInput(baseNote.idHex) {
                                detectTapGestures(onTap = { chromeVisible = true })
                            },
                )
            }

            AnimatedVisibility(
                visible = chromeVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                EnlargedReactionsRow(
                    baseNote = baseNote,
                    accountViewModel = accountViewModel,
                    nav = nav,
                )
            }
        }
    }
}
