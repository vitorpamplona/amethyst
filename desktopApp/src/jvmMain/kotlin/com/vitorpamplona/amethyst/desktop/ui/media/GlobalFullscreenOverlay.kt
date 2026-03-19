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
package com.vitorpamplona.amethyst.desktop.ui.media

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import com.vitorpamplona.amethyst.desktop.service.media.GlobalMediaPlayer

@Composable
fun GlobalFullscreenOverlay() {
    val isFullscreen by GlobalMediaPlayer.isFullscreen.collectAsState()
    val videoState by GlobalMediaPlayer.videoState.collectAsState()
    val videoFrame by GlobalMediaPlayer.videoFrame.collectAsState()

    if (!isFullscreen || videoState.url == null) return

    val focusRequester = remember { FocusRequester() }
    val awtWindow = LocalAwtWindow.current
    val isImmersiveFullscreen = LocalIsImmersiveFullscreen.current

    // Enter native fullscreen
    LaunchedEffect(isFullscreen) {
        if (isFullscreen) {
            isImmersiveFullscreen.value = true
            awtWindow?.let { FullscreenHelper.enterFullscreen(it) }
            focusRequester.requestFocus()
        }
    }

    // Restore on exit
    DisposableEffect(Unit) {
        onDispose {
            isImmersiveFullscreen.value = false
            if (FullscreenHelper.isFullscreen()) FullscreenHelper.exitFullscreen()
        }
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .focusRequester(focusRequester)
                .onKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                    when (event.key) {
                        Key.Escape -> {
                            GlobalMediaPlayer.exitFullscreen()
                            true
                        }

                        Key.F -> {
                            GlobalMediaPlayer.exitFullscreen()
                            true
                        }

                        Key.Spacebar -> {
                            GlobalMediaPlayer.toggleVideoPlayPause()
                            true
                        }

                        else -> {
                            false
                        }
                    }
                },
        contentAlignment = Alignment.Center,
    ) {
        // Video frame
        videoFrame?.let { frame ->
            Image(
                bitmap = frame,
                contentDescription = "Video fullscreen",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        }

        // Video controls overlay
        VideoControls(
            isPlaying = videoState.isPlaying,
            isBuffering = videoState.isBuffering,
            position = videoState.position,
            duration = videoState.duration,
            currentTime = videoState.currentTime,
            volume = videoState.volume,
            isMuted = videoState.isMuted,
            viewMode = ViewMode.FULLSCREEN,
            onPlayPause = { GlobalMediaPlayer.toggleVideoPlayPause() },
            onSeek = { GlobalMediaPlayer.seekVideo(it) },
            onVolumeChange = { GlobalMediaPlayer.setVideoVolume(it) },
            onMuteToggle = { GlobalMediaPlayer.toggleVideoMute() },
            onViewModeChange = { mode ->
                if (mode == ViewMode.DEFAULT) {
                    GlobalMediaPlayer.exitFullscreen()
                }
            },
        )
    }
}
