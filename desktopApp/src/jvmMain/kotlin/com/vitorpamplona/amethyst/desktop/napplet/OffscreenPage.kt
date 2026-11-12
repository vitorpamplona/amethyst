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

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.awtEventOrNull
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent
import kotlin.math.roundToInt

/**
 * An off-screen Chromium page ([OffscreenNappletView]) drawn into Compose like an image, with the
 * pointer and the keyboard forwarded to it. Drawn by Compose, it clips, layers and moves with the
 * rest of the app (a note in the feed, a screen in a tab, a deck column, a transition), which
 * Chromium's own native view could not.
 *
 * The page takes the keyboard once clicked. [wheelNeedsFocus] leaves the wheel to what holds the
 * page until then (a note's feed scrolls past it); a screen's page takes the wheel at once.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun OffscreenPage(
    view: OffscreenNappletView,
    modifier: Modifier,
    wheelNeedsFocus: Boolean,
    onFocusChanged: (Boolean) -> Unit = {},
) {
    // Read only where it is drawn, so a new frame redraws the box instead of recomposing the page.
    val frame = view.frame.collectAsState()
    val cursor by view.cursor.collectAsState()
    val density = LocalDensity.current.density
    val focus = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }

    Box(
        modifier
            .onSizeChanged { view.resize((it.width / density).roundToInt(), (it.height / density).roundToInt(), density.toDouble()) }
            // Before focusTarget(): key events reach the focused node and what wraps it, not what it wraps.
            .onKeyEvent { event ->
                // Not nativeKeyEvent: that is Compose's own event now, and a cast of it to AWT's failed
                // for every key, so none reached the page and Enter went on to open the note.
                val native = event.awtEventOrNull ?: return@onKeyEvent false
                view.key(native)
                true
            }.focusRequester(focus)
            .onFocusChanged {
                focused = it.isFocused
                view.focus(it.isFocused)
                onFocusChanged(it.isFocused)
            }
            // Not focusable(): that pins a note in the feed while focused, so a clicked game would play
            // on, unseen, after its note scrolled away.
            .focusTarget()
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
                                // Already focused, the request changes nothing; Chromium may have
                                // dropped the focus meanwhile (a navigation), so it hears it again.
                                if (focused) view.refocus()
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
                                if ((focused || !wheelNeedsFocus) && native is MouseWheelEvent) {
                                    view.wheel(native, x, y)
                                    event.changes.forEach { it.consume() }
                                }
                        }
                    }
                }
            }.clipToBounds()
            .drawBehind {
                // Scaled to the box's width only, from the top: a frame Chromium painted for the
                // box's last size (the paper grows from its loading height to its page height) is
                // shown in its own proportions until the next one comes, never stretched to fit.
                frame.value?.let {
                    val width = size.width.roundToInt()
                    val height = (it.height.toFloat() * width / it.width.coerceAtLeast(1)).roundToInt()
                    drawImage(it, dstSize = IntSize(width, height))
                }
            },
    )
}
