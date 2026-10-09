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
package com.vitorpamplona.amethyst.desktop.platform

import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.WindowInfo
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.awt.Desktop
import java.awt.Window
import java.awt.event.FocusEvent

/**
 * A click on the window while it is in the background only brings it forward, as in native apps,
 * instead of also pressing whatever is under the pointer. Compose Desktop passes that click
 * straight through.
 *
 * The window's own focus state can't tell that click apart: macOS focuses the window just before it
 * delivers the press (measured: the press already reads focused). What lags is Compose's focus
 * change, which lands right after the press. So the filter tracks the focus it has seen through
 * that change: a press while it still reads "in the background" is the click that brought the
 * window forward. One switched back to with Cmd-Tab has seen its focus long before any click.
 *
 * Sits before every child in the [PointerEventPass.Initial] pass: the press, and the rest of its
 * gesture, are consumed, which the app's click and drag handlers skip. Only one click per stay in
 * the background is taken, and scrolling an inactive window still works, as the OS allows it.
 */
fun Modifier.clickToActivate(
    window: Window,
    windowInfo: WindowInfo,
): Modifier =
    pointerInput(window, windowInfo) {
        val pointers = this
        var seenFocused = windowInfo.isWindowFocused
        coroutineScope {
            launch { snapshotFlow { windowInfo.isWindowFocused }.collect { seenFocused = it } }

            pointers.awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (event.type != PointerEventType.Press || seenFocused) continue

                    seenFocused = true
                    event.changes.forEach { it.consume() }
                    // A real click has already activated the window; a programmatic one has not.
                    if (!window.isActive) launch { activate(window) }
                    // Swallow the rest of this press (moves, the release) so nothing under it reacts.
                    while (true) {
                        val rest = awaitPointerEvent(PointerEventPass.Initial)
                        rest.changes.forEach { it.consume() }
                        if (rest.changes.none { it.pressed }) break
                    }
                }
            }
        }
    }

/**
 * Brings [window] to the front and focuses it; on macOS also makes the app the active one. The
 * activation is asynchronous, and a focus request made before it lands is dropped (the app comes
 * forward with no key window), so the request repeats briefly until the window is active.
 */
private suspend fun activate(window: Window) {
    try {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.APP_REQUEST_FOREGROUND)) {
            Desktop.getDesktop().requestForeground(true)
        }
    } catch (e: Exception) {
        Log.w("ClickToActivate", "Could not bring the app forward", e)
    }
    repeat(ACTIVATION_ATTEMPTS) {
        if (window.isActive) return
        window.toFront()
        // A mouse click is what asks for focus here. macOS's AWT drops focus requests of any other
        // cause while the app is not yet active (CPlatformWindow.rejectFocusRequest), which is
        // exactly the moment this runs in, so the window came forward without becoming key.
        window.requestFocus(FocusEvent.Cause.MOUSE_EVENT)
        delay(ACTIVATION_RETRY_MS)
    }
}

private const val ACTIVATION_ATTEMPTS = 10
private const val ACTIVATION_RETRY_MS = 50L
