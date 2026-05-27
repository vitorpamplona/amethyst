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
package com.vitorpamplona.amethyst.service.playback.composable

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.utils.Log

/**
 * Overlay shown on top of the video surface when the MediaController reports an unrecoverable
 * playback error (codec init failure, unsupported format, decoder error, etc).
 *
 * Offers an "Open in browser" fallback that hands the URL to the system browser via
 * [LocalUriHandler] so the user can still consume the content even if the on-device codec
 * stack can't.
 *
 * Reads error state from [MediaControllerState.playbackError]; populated by [WatchPlaybackErrors].
 */
@Composable
fun RenderPlaybackError(
    controllerState: MediaControllerState,
    videoUri: String,
    modifier: Modifier = Modifier,
) {
    val error by controllerState.playbackError
    val current = error ?: return

    val uriHandler = LocalUriHandler.current
    val errorCodeName = remember(current) { current.errorCodeName }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.75f))
                .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            symbol = MaterialSymbols.VideocamOff,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = Color.White,
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text = stringRes(R.string.error_video_playback_failed),
            color = Color.White,
            style = MaterialTheme.typography.titleSmall,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(4.dp))

        Text(
            text = stringRes(R.string.error_video_playback_failed_description, errorCodeName),
            color = Color.White.copy(alpha = 0.85f),
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(16.dp))

        FilledTonalButton(
            onClick = {
                runCatching { uriHandler.openUri(videoUri) }
                    .onFailure { Log.w("RenderPlaybackError", "openUri failed for $videoUri", it) }
            },
        ) {
            Icon(
                symbol = MaterialSymbols.OpenInBrowser,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.size(8.dp))
            Text(stringRes(R.string.error_video_open_in_browser))
        }
    }
}
