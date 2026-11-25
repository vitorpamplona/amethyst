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
package com.vitorpamplona.amethyst.commons.ui.playback.composable

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.close
import com.vitorpamplona.amethyst.commons.richtext.MediaUrlVideo
import com.vitorpamplona.amethyst.commons.ui.components.PlatformBackHandler
import com.vitorpamplona.amethyst.commons.ui.components.SetDialogToEdgeToEdge
import com.vitorpamplona.amethyst.commons.ui.components.mediaViewerDialogProperties
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel

/**
 * A video on its own, filling the screen on black, with the shared player's fullscreen controls
 * (swipe for brightness, quality, captions, speed) and a close button. For front ends whose media
 * viewer only pages pictures; the same player instance carries over from the feed card.
 */
@Composable
fun VideoViewerDialog(
    content: MediaUrlVideo,
    accountViewModel: AccountViewModel,
    onDismiss: () -> Unit,
) {
    // Read out here: inside an edge-to-edge dialog the insets can come back empty (iOS), which put
    // the close button and the player's top buttons under the status bar, out of reach.
    val safeArea = WindowInsets.safeDrawing.asPaddingValues()
    Dialog(onDismissRequest = onDismiss, properties = mediaViewerDialogProperties()) {
        SetDialogToEdgeToEdge()
        // The system back: Android's gesture, iOS's edge swipe.
        PlatformBackHandler(onBack = onDismiss)
        val controllerVisible = remember(content.url) { mutableStateOf(true) }
        // The black reaches the screen edges; the player and its controls stay in the safe area.
        Box(Modifier.fillMaxSize().background(Color.Black).padding(safeArea), contentAlignment = Alignment.Center) {
            VideoViewInner(
                videoUri = content.url,
                mimeType = content.mimeType,
                title = content.description,
                artworkUri = content.artworkUri,
                authorName = content.authorName,
                contentScale = ContentScale.Fit,
                borderModifier = Modifier.fillMaxSize(),
                automaticallyStartPlayback = true,
                controllerVisible = controllerVisible,
                isFullscreen = true,
                blurhash = content.blurhash,
                dim = content.dim,
                hash = content.hash,
                captions = content.captions,
                isLiveStream = content.isLiveStream,
                accountViewModel = accountViewModel,
            )
            IconButton(
                onClick = onDismiss,
                modifier =
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp),
            ) {
                Icon(symbol = MaterialSymbols.Close, contentDescription = stringRes(Res.string.close), tint = Color.White)
            }
        }
    }
}
