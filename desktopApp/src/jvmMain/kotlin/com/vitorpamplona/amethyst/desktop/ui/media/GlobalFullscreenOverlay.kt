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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import com.vitorpamplona.amethyst.commons.ui.components.ZoomSource
import com.vitorpamplona.amethyst.commons.ui.components.rememberZoomTransition
import com.vitorpamplona.amethyst.commons.ui.components.zoomTransitionContainer
import com.vitorpamplona.amethyst.commons.ui.components.zoomTransitionLayer
import com.vitorpamplona.amethyst.desktop.platform.AppWindow
import com.vitorpamplona.amethyst.desktop.platform.WaylandDisplay
import com.vitorpamplona.amethyst.desktop.platform.awtPixelScale
import com.vitorpamplona.amethyst.desktop.service.media.GlobalMediaPlayer
import com.vitorpamplona.amethyst.desktop.service.media.VideoPixelAspect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.awt.GraphicsEnvironment

/**
 * The playing video over the whole screen, in a borderless window of its own that covers the
 * screen. As on the phone, the video grows in one motion from the card it was playing in
 * ([GlobalMediaPlayer.fullscreenSource]) to the whole screen, over whatever is around the app,
 * as the black backdrop fades in. Once it fills the screen the window goes OS full screen to hide the
 * menu bar, a change nobody sees since it already covers the screen. Leaving runs the other way.
 *
 * Its own window rather than the app's: taking the app's window to full screen resizes it under the
 * animation, and the feed re-laid out at the screen's size flashed through as the OS animated.
 */
@Composable
fun GlobalFullscreenOverlay() {
    val isFullscreen by GlobalMediaPlayer.isFullscreen.collectAsState()
    // Only whether a video is loaded: the whole window must not recompose on every clock tick.
    val hasVideo by remember {
        GlobalMediaPlayer.videoState.map { it.url != null }.distinctUntilChanged()
    }.collectAsState(GlobalMediaPlayer.videoState.value.url != null)

    if (!isFullscreen || !hasVideo) return

    val mainWindow = LocalAwtWindow.current
    val isImmersiveFullscreen = LocalIsImmersiveFullscreen.current
    // Pixels per AWT unit, to turn the window's AWT position into the overlay's pixels.
    val awtScale = awtPixelScale(mainWindow)

    // The screen the app's window is on, in the units windows are placed in.
    val device = remember { mainWindow?.graphicsConfiguration?.device ?: GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice }
    val screen = remember { device.defaultConfiguration.bounds }

    // The card's rect in the overlay window, which covers [screen]. Read when the overlay opens
    // and again when it closes.
    // Wayland never tells a window where it is on the screen, so there the video fades in instead.
    val card = remember { GlobalMediaPlayer.fullscreenSource }
    val source: () -> ZoomSource? = {
        val origin = mainWindow?.takeUnless { WaylandDisplay.isActive }?.locationOnScreen
        if (origin != null) card?.capture()?.translate((origin.x - screen.x) * awtScale, (origin.y - screen.y) * awtScale) else null
    }
    val cornerRadius = remember { GlobalMediaPlayer.fullscreenSourceCornerRadius }

    // Nor does it let a window place itself over the screen: there the overlay is full screen from
    // the moment it opens until it is dismissed, opaque (full screen makes a window opaque anyway),
    // and the video fades in and out over black.
    val onWayland = WaylandDisplay.isActive
    val windowState =
        rememberWindowState(
            placement = if (onWayland) WindowPlacement.Fullscreen else WindowPlacement.Floating,
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

    AppWindow(
        onCloseRequest = { GlobalMediaPlayer.exitFullscreen() },
        state = windowState,
        title = "Amethyst",
        undecorated = true,
        transparent = !onWayland,
        resizable = false,
        alwaysOnTop = true,
        fullscreenDevice = device,
    ) {
        val overlay = window
        DisposableEffect(overlay) {
            onDispose { if (device.fullScreenWindow === overlay) device.fullScreenWindow = null }
        }

        FullscreenVideo(
            source = source,
            cornerRadius = cornerRadius,
            opaqueBackdrop = onWayland,
            onGrown = { if (!onWayland && device.isFullScreenSupported) device.fullScreenWindow = overlay },
            beforeShrink = { if (!onWayland && device.fullScreenWindow === overlay) device.fullScreenWindow = null },
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
    source: () -> ZoomSource?,
    cornerRadius: Dp,
    opaqueBackdrop: Boolean,
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
                .drawBehind { drawRect(Color.Black.copy(alpha = if (opaqueBackdrop) 1f else transition.progress)) }
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
                val url = GlobalMediaPlayer.videoState.value.url
                VideoSurface(
                    playerState = player,
                    url = url,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .onGloballyPositioned { transition.onMediaPositioned(it, VideoPixelAspect.displayRatio(url, GlobalMediaPlayer.videoState.value.aspectRatio)) },
                )
            } ?: Box(Modifier.fillMaxSize().onGloballyPositioned { transition.onMediaPositioned(it, null) })
        }

        // Video controls overlay, fading with the transition.
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = transition.progress }) {
            FullscreenControls(onLeave = { leave() })
        }
    }
}

/** Its own composable, so each clock tick recomposes the controls and not the video around them. */
@Composable
private fun FullscreenControls(onLeave: () -> Unit) {
    val videoState by GlobalMediaPlayer.videoState.collectAsState()
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
            if (mode == ViewMode.DEFAULT) onLeave()
        },
    )
}
