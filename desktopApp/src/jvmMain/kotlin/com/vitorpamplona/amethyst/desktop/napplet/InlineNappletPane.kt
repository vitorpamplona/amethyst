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
package com.vitorpamplona.amethyst.desktop.napplet

import androidx.compose.foundation.Image
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import com.vitorpamplona.amethyst.commons.favorites.favoriteCoordinateOf
import com.vitorpamplona.amethyst.commons.ui.note.InlineNappletPosters
import com.vitorpamplona.amethyst.commons.ui.platform.InlineNappletStatus
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip5dNapplets.NappletManifest
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent
import kotlin.math.roundToInt

/**
 * A napplet running inside a note on the desktop: an off-screen Chromium browser
 * ([OffscreenNappletView]) whose frames are drawn here like an image, so the note clips and layers
 * it with the rest of the feed, which a native window could not do.
 *
 * The page gets the pointer, and the keyboard once clicked. The wheel scrolls the page only after
 * it was clicked, and the feed otherwise. Leaving the composition closes the browser and keeps its
 * last frame as the note's poster.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun InlineNappletPane(
    host: DesktopNappletHost,
    manifest: NappletManifest,
    instanceId: String,
    onStatus: (InlineNappletStatus) -> Unit,
    modifier: Modifier,
) = InlineNappletPane(host, favoriteCoordinateOf(manifest as Event), instanceId, onStatus, modifier) { host.openInline(manifest, instanceId) }

/**
 * [InlineNappletPane] for any app a note runs: [open] starts its off-screen session, and the last
 * frame is kept as the poster under [posterKey].
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun InlineNappletPane(
    host: DesktopNappletHost,
    posterKey: String,
    instanceId: String,
    onStatus: (InlineNappletStatus) -> Unit,
    modifier: Modifier,
    open: () -> Result<CefNappletSession>,
) {
    val status by rememberUpdatedState(onStatus)
    var session by remember { mutableStateOf<CefNappletSession?>(null) }

    DisposableEffect(host, posterKey, instanceId) {
        val opened = open().getOrNull()
        session = opened
        if (opened == null) status(InlineNappletStatus.FAILED)
        onDispose {
            if (opened != null) {
                opened.offscreen
                    ?.frame
                    ?.value
                    ?.let { InlineNappletPosters.put(posterKey, it) }
                host.closeInline(opened)
            }
            session = null
        }
    }

    val current = session ?: return
    val view = current.offscreen ?: return
    val state by current.state.collectAsState()
    val frame by view.frame.collectAsState()
    val cursor by view.cursor.collectAsState()

    LaunchedEffect(state, frame != null) {
        status(
            when {
                state is CefNappletSession.State.Failed -> InlineNappletStatus.FAILED
                state is CefNappletSession.State.Showing && frame != null -> InlineNappletStatus.SHOWING
                else -> InlineNappletStatus.LOADING
            },
        )
    }

    val density = LocalDensity.current.density
    val focus = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }

    Box(
        modifier
            .onSizeChanged { view.resize((it.width / density).roundToInt(), (it.height / density).roundToInt(), density.toDouble()) }
            // Before focusable(): key events reach the focused node and what wraps it, not what it wraps.
            .onKeyEvent { event ->
                val native = event.nativeKeyEvent as? KeyEvent ?: return@onKeyEvent false
                view.key(native)
                true
            }.focusRequester(focus)
            .onFocusChanged {
                focused = it.isFocused
                view.focus(it.isFocused)
            }.focusable()
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
                                focus.requestFocus()
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
                            PointerEventType.Scroll ->
                                if (focused && native is MouseWheelEvent) {
                                    view.wheel(native, x, y)
                                    event.changes.forEach { it.consume() }
                                }
                        }
                    }
                }
            },
    ) {
        frame?.let { Image(it, contentDescription = null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize()) }
    }
}
