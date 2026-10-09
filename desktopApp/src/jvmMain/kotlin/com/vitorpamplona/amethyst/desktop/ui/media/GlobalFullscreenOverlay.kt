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

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import com.vitorpamplona.amethyst.commons.ui.components.rememberZoomTransition
import com.vitorpamplona.amethyst.commons.ui.components.zoomTransitionContainer
import com.vitorpamplona.amethyst.commons.ui.components.zoomTransitionLayer
import com.vitorpamplona.amethyst.desktop.service.media.GlobalMediaPlayer
import com.vitorpamplona.amethyst.desktop.service.media.MediaPlaybackState
import io.github.kdroidfilter.composemediaplayer.VideoPlayerSurface
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.awt.GraphicsEnvironment

/**
 * The playing video over the whole screen, in a borderless window of its own that covers the
 * screen. As on the phone, the video grows in one motion from the card it was playing in
 * ([GlobalMediaPlayer.fullscreenSourceBounds]) to the whole screen, over whatever is around the app,
 * as the black backdrop fades in. Once it fills the screen the window goes OS full screen to hide the
 * menu bar, a change nobody sees since it already covers the screen. Leaving runs the other way.
 *
 * Its own window rather than the app's: taking the app's window to full screen resizes it under the
 * animation, and the feed re-laid out at the screen's size flashed through as the OS animated.
 */
@Composable
fun GlobalFullscreenOverlay() {
    val isFullscreen by GlobalMediaPlayer.isFullscreen.collectAsState()
    val videoState by GlobalMediaPlayer.videoState.collectAsState()

    if (!isFullscreen || videoState.url == null) return

    val mainWindow = LocalAwtWindow.current
    val isImmersiveFullscreen = LocalIsImmersiveFullscreen.current
    val density = LocalDensity.current.density

    // The screen the app's window is on, in the units windows are placed in.
    val device = remember { mainWindow?.graphicsConfiguration?.device ?: GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice }
    val screen = remember { device.defaultConfiguration.bounds }

    // The card's rect in the overlay window, which covers [screen].
    val source =
        remember {
            val card = GlobalMediaPlayer.fullscreenSourceBounds
            val origin = mainWindow?.locationOnScreen
            if (card != null && origin != null) card.translate((origin.x - screen.x) * density, (origin.y - screen.y) * density) else null
        }
    val cornerRadius = remember { GlobalMediaPlayer.fullscreenSourceCornerRadius }

    val windowState =
        rememberWindowState(
            position = WindowPosition(screen.x.dp, screen.y.dp),
            size = DpSize(screen.width.dp, screen.height.dp),
        )

    DisposableEffect(Unit) {
        isImmersiveFullscreen.value = true
        onDispose {
            isImmersiveFullscreen.value = false
            mainWindow?.toFront()
        }
    }

    Window(
        onCloseRequest = { GlobalMediaPlayer.exitFullscreen() },
        state = windowState,
        title = "Amethyst",
        undecorated = true,
        transparent = true,
        resizable = false,
        alwaysOnTop = true,
    ) {
        val overlay = window
        DisposableEffect(overlay) {
            onDispose { if (device.fullScreenWindow === overlay) device.fullScreenWindow = null }
        }

        FullscreenVideo(
            source = source,
            cornerRadius = cornerRadius,
            videoState = videoState,
            onGrown = { if (device.isFullScreenSupported) device.fullScreenWindow = overlay },
            beforeShrink = { if (device.fullScreenWindow === overlay) device.fullScreenWindow = null },
            onDismissed = {
                // Hidden the moment the video is back in its card: tearing the window down takes a
                // while, and the screen stayed covered until it was gone.
                overlay.isVisible = false
                GlobalMediaPlayer.exitFullscreen()
            },
        )
    }
}

@Composable
private fun FullscreenVideo(
    source: Rect?,
    cornerRadius: Dp,
    videoState: MediaPlaybackState,
    onGrown: () -> Unit,
    beforeShrink: suspend () -> Unit,
    onDismissed: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    val transition = rememberZoomTransition(source, cornerRadius, onDismissed)
    var leaving by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    // Grown to fill the window: now the window goes full screen.
    LaunchedEffect(transition) {
        snapshotFlow { transition.progress >= 1f }.first { it }
        onGrown()
        focusRequester.requestFocus()
    }

    fun leave() {
        if (leaving) return
        leaving = true
        scope.launch {
            beforeShrink()
            transition.dismiss()
        }
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .zoomTransitionContainer(transition)
                .drawBehind { drawRect(Color.Black.copy(alpha = transition.progress)) }
                .focusRequester(focusRequester)
                // A focus target, or requestFocus() above finds none and the keys never arrive.
                .focusable()
                .onKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                    when (event.key) {
                        Key.Escape -> {
                            leave()
                            true
                        }

                        Key.F -> {
                            leave()
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
        // Video frame — same player state as feed card; kdroidFilter draws to Canvas.
        // If the engine failed to initialize, render a blank backdrop instead of
        // crashing the overlay.
        Box(Modifier.fillMaxSize().zoomTransitionLayer(transition)) {
            GlobalMediaPlayer.activeVideoPlayerState?.let { player ->
                VideoPlayerSurface(
                    playerState = player,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .onGloballyPositioned { transition.onMediaPositioned(it, videoState.aspectRatio) },
                    contentScale = ContentScale.Fit,
                )
            } ?: Box(Modifier.fillMaxSize().onGloballyPositioned { transition.onMediaPositioned(it, null) })
        }

        // Video controls overlay, fading with the transition.
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = transition.progress }) {
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
                onSkip = { GlobalMediaPlayer.skipVideo(it) },
                speed = videoState.speed,
                onSpeedChange = { GlobalMediaPlayer.setVideoSpeed(it) },
                onVolumeChange = { GlobalMediaPlayer.setVideoVolume(it) },
                onMuteToggle = { GlobalMediaPlayer.toggleVideoMute() },
                onViewModeChange = { mode ->
                    if (mode == ViewMode.DEFAULT) leave()
                },
            )
        }
    }
}
