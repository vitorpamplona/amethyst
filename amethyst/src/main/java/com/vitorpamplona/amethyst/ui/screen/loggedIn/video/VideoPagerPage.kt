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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
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

private fun pageMediaHashes(note: Note): Pair<String?, String?> =
    when (val event = note.event) {
        is VideoEvent -> {
            val meta = event.imetaTags().firstOrNull()
            meta?.blurhash to meta?.thumbhash
        }

        is PictureEvent -> {
            val meta = event.imetaTags().firstOrNull()
            meta?.blurhash to meta?.thumbhash
        }

        is FileHeaderEvent -> {
            event.blurhash() to event.thumbhash()
        }

        else -> {
            null to null
        }
    }

@Composable
fun VideoPagerPage(
    baseNote: Note,
    padding: PaddingValues,
    accountViewModel: AccountViewModel,
    nav: INav,
    content: @Composable () -> Unit,
) {
    var chromeVisible by remember(baseNote.idHex) { mutableStateOf(true) }

    LaunchedEffect(chromeVisible, baseNote.idHex) {
        if (chromeVisible) {
            delay(AUTO_HIDE_MS)
            chromeVisible = false
        }
    }

    val (blurhash, thumbhash) =
        remember(baseNote.idHex) { pageMediaHashes(baseNote) }

    Box(modifier = Modifier.fillMaxSize()) {
        // Backdrop layer — full-bleed so blur extends behind the (transparent) top bar.
        if (blurhash != null || thumbhash != null) {
            BlurhashBackdrop(blurhash = blurhash, description = null, thumbhash = thumbhash)
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.25f)),
            )
        }

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
                Surface(
                    color = Color.Black.copy(alpha = 0.55f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    ReactionsRow(
                        baseNote = baseNote,
                        showReactionDetail = true,
                        addPadding = true,
                        editState = null,
                        accountViewModel = accountViewModel,
                        nav = nav,
                    )
                }
            }
        }
    }
}
