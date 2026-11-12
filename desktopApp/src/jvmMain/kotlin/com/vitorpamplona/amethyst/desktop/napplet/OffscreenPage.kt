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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.awtEventOrNull
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
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.frontpage.InlinePaperScroll
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.frontpage.PaperWheel
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
 *
 * With [pageWheel] (the inline front page), once the page reports how far it scrolls, the screen
 * around it and the page scroll as one: the screen's scroll takes every turn and hands the page its
 * share ([PaperWheel]), clicked or not, and only a turn the screen can't follow comes to the page.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun OffscreenPage(
    view: OffscreenNappletView,
    modifier: Modifier,
    wheelNeedsFocus: Boolean,
    pageWheel: PaperWheel? = null,
    onFocusChanged: (Boolean) -> Unit = {},
) {
    // Read only where it is drawn, so a new frame redraws the box instead of recomposing the page.
    val frame = view.frame.collectAsState()
    val cursor by view.cursor.collectAsState()
    val density = LocalDensity.current.density
    val focus = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    val scrollKnown by view.scrollKnown.collectAsState()
    if (pageWheel != null && scrollKnown) {
        DisposableEffect(pageWheel, view, density) {
            val page = PageScroll(view, density)
            pageWheel.page = page
            onDispose { if (pageWheel.page === page) pageWheel.page = null }
        }
    }
    val shared = pageWheel?.takeIf { scrollKnown }

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
            .pointerInput(view, density, shared) {
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
                                if (native !is MouseWheelEvent) {
                                    // Not a wheel: nothing to scroll.
                                } else if (shared != null && !native.isShiftDown) {
                                    // Left to the screen's scroll, which hands the page its share; only a
                                    // turn the screen can't follow at all is the page's alone.
                                    val towardEnd = native.preciseWheelRotation > 0
                                    if (shared.pageTakesWheel(towardEnd)) {
                                        view.scrollBy(view.notchPixels(native.preciseWheelRotation))
                                        event.changes.forEach { it.consume() }
                                    }
                                } else if (focused || !wheelNeedsFocus) {
                                    view.wheel(native, x, y)
                                    event.changes.forEach { it.consume() }
                                }
                        }
                    }
                }
            }.drawBehind {
                frame.value?.let { drawImage(it, dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt())) }
            },
    )
}

/** [view]'s document for the screen's [PaperWheel], in the screen's pixels ([density] to the page's CSS pixels). */
private class PageScroll(
    private val view: OffscreenNappletView,
    private val density: Float,
) : InlinePaperScroll {
    override fun room(towardEnd: Boolean): Float = (view.scrollRoom(towardEnd) * density).toFloat()

    override fun scrollBy(px: Float): Float = (view.scrollBy(px / density.toDouble()) * density).toFloat()
}
