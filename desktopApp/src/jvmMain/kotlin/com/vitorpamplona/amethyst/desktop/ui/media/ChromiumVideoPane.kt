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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.desktop.napplet.DesktopCefEngine
import com.vitorpamplona.amethyst.desktop.service.media.ChromiumVideoSession
import java.awt.event.MouseEvent
import kotlin.math.roundToInt

/** The app's Chromium, for videos the native engine can't decode; null where there is none. */
val LocalChromiumVideoEngine = staticCompositionLocalOf<DesktopCefEngine?> { null }

/**
 * Plays [url] in the app's Chromium ([ChromiumVideoSession]) and draws its frames here, with
 * Chromium's own video controls, which get the pointer. [onFailed] reports a video Chromium can't
 * play either. Leaving the composition closes the page and stops the video.
 */
@Composable
fun ChromiumVideoPane(
    engine: DesktopCefEngine,
    url: String,
    muted: Boolean,
    onFailed: () -> Unit,
    modifier: Modifier = Modifier,
    playing: Boolean = true,
    onPlaying: () -> Unit = {},
    onRatio: (Float) -> Unit = {},
) {
    val failed by rememberUpdatedState(onFailed)
    val started by rememberUpdatedState(onPlaying)
    val session = remember(engine, url) { ChromiumVideoSession(engine, url, muted) }
    DisposableEffect(session) {
        session.open()
        onDispose { session.close() }
    }

    val state by session.state.collectAsState()
    LaunchedEffect(state) {
        when (state) {
            is ChromiumVideoSession.State.Failed -> failed()
            ChromiumVideoSession.State.Playing -> started()
            ChromiumVideoSession.State.Loading -> {}
        }
    }
    val ratio by session.ratio.collectAsState()
    LaunchedEffect(ratio) { ratio?.let(onRatio) }
    LaunchedEffect(session, playing) { session.setPlaying(playing) }

    val view = session.view
    // Read only where it is drawn, so a new frame redraws the box instead of recomposing the pane.
    val frame = view.frame.collectAsState()
    val painted by remember(frame) { derivedStateOf { frame.value != null } }
    val cursor by view.cursor.collectAsState()
    val density = LocalDensity.current.density

    Box(
        modifier
            .background(Color.Black)
            .onSizeChanged { view.resize((it.width / density).roundToInt(), (it.height / density).roundToInt(), density.toDouble()) }
            .pointerHoverIcon(PointerIcon(cursor))
            .pointerInput(view, density) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val native = event.nativeEvent as? MouseEvent ?: continue
                        val position = event.changes.firstOrNull()?.position ?: continue
                        val x = (position.x / density).roundToInt()
                        val y = (position.y / density).roundToInt()
                        when (event.type) {
                            PointerEventType.Press -> {
                                view.mouse(native, MouseEvent.MOUSE_PRESSED, x, y)
                                event.changes.forEach { it.consume() }
                            }
                            PointerEventType.Release -> {
                                view.mouse(native, MouseEvent.MOUSE_RELEASED, x, y)
                                event.changes.forEach { it.consume() }
                            }
                            PointerEventType.Move, PointerEventType.Enter ->
                                view.mouse(native, if (native.id == MouseEvent.MOUSE_DRAGGED) MouseEvent.MOUSE_DRAGGED else MouseEvent.MOUSE_MOVED, x, y)
                            PointerEventType.Exit -> view.mouse(native, MouseEvent.MOUSE_EXITED, x, y)
                        }
                    }
                }
            }.drawBehind {
                frame.value?.let { drawImage(it, dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt())) }
            },
        contentAlignment = Alignment.Center,
    ) {
        if (!painted) {
            CircularProgressIndicator(Modifier.size(32.dp), color = Color.White)
        }
    }
}
