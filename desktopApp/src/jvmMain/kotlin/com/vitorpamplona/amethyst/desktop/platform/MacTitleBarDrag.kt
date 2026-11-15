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

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sun.jna.Function
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.vitorpamplona.amethyst.desktop.napplet.MacMainThread
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.cef.browser.mac.CefBrowserWindowMac
import java.awt.MouseInfo
import java.awt.Window
import java.awt.event.MouseEvent
import kotlin.concurrent.thread
import kotlin.math.hypot

/**
 * The macOS title bar band, moved by the app instead of by AppKit.
 *
 * The window draws under a transparent title bar ([applyNativeWindowChrome]) and keeps its tab strip
 * and toolbars in that band, but AppKit still owned it: any drag that started there moved the
 * window, so a tab could never be dragged to a new place (measured: a 250 px drag of a tab moved the
 * window 187 px and left the tabs as they were). [MacTitleBar.takeOver] turns AppKit's dragging off
 * (`NSWindow.movable`), and [titleBarWindowDrag] moves the window from the band itself, for a drag
 * nothing under the pointer claimed. A tab claims its own once past its slop, and reorders.
 */
internal object MacTitleBar {
    private const val TAG = "MacTitleBar"

    @Suppress("FunctionName")
    private interface ObjC : Library {
        fun sel_registerName(name: String): Pointer

        fun objc_getClass(name: String): Pointer
    }

    private val objc by lazy { Native.load("objc", ObjC::class.java) }
    private val msgSend by lazy { Function.getFunction("objc", "objc_msgSend") }

    private fun handleOf(window: Window): Long = runCatching { CefBrowserWindowMac().getWindowHandle(window) }.getOrDefault(0L)

    private fun send(
        receiver: Pointer,
        selector: String,
        vararg args: Any?,
    ): Pointer? = msgSend.invokePointer(arrayOf(receiver, objc.sel_registerName(selector), *args))

    /**
     * Stops AppKit moving [window] from its title bar. Returns whether it did: when it did not, the
     * band keeps AppKit's dragging and [titleBarWindowDrag] must stay out of its way.
     */
    fun takeOver(window: Window): Boolean {
        val handle = handleOf(window)
        if (handle == 0L) {
            Log.w(TAG) { "No native window: macOS keeps moving it from its title bar" }
            return false
        }
        return runCatching {
            // Off the AWT thread: this waits on the AppKit main thread.
            MacMainThread.run { msgSend.invokeVoid(arrayOf(Pointer(handle), objc.sel_registerName("setMovable:"), 0.toByte())) }
        }.onFailure { Log.w(TAG, "Could not take the title bar over from AppKit", it) }.isSuccess
    }

    /**
     * What a double click on the title bar does, as System Settings > Desktop & Dock sets it
     * (`AppleActionOnDoubleClick`): minimize, nothing, or zoom, the default. AppKit did this itself
     * while it owned the band. Off the AWT thread.
     */
    fun doubleClick(window: Window) {
        val handle = handleOf(window)
        if (handle == 0L) return
        runCatching {
            MacMainThread.run {
                val defaults = send(objc.objc_getClass("NSUserDefaults"), "standardUserDefaults") ?: return@run
                val key = send(objc.objc_getClass("NSString"), "stringWithUTF8String:", "AppleActionOnDoubleClick")
                val action = key?.let { send(defaults, "stringForKey:", it) }?.let { send(it, "UTF8String") }?.getString(0, "UTF-8")
                when (action) {
                    "Minimize" -> send(Pointer(handle), "miniaturize:", null)
                    "None" -> Unit
                    else -> send(Pointer(handle), "zoom:", null)
                }
            }
        }.onFailure { Log.w(TAG, "Could not act on the title bar's double click", it) }
    }
}

/** Takes [window]'s title bar over from AppKit on macOS, and reports when it has. */
@Composable
fun TakeOverTitleBar(
    window: Window,
    onTakenOver: () -> Unit,
) {
    if (!PlatformInfo.isMacOS) return
    LaunchedEffect(window) {
        if (withContext(Dispatchers.IO) { MacTitleBar.takeOver(window) }) onTakenOver()
    }
}

/**
 * Moves [window] when a drag that starts within [band] of the top goes [WindowDragSlop] and nothing
 * under it claimed the movement: a press on a button that wobbles stays a click, and a tab dragged
 * past its own, smaller slop reorders instead. A double click there that nothing took (a tab takes
 * its clicks) does what the system's title bar does. Inert when [band] is 0 (full screen, other
 * platforms).
 */
fun Modifier.titleBarWindowDrag(
    window: Window,
    band: Dp,
): Modifier =
    if (band <= 0.dp) {
        this
    } else {
        pointerInput(window, band) {
            awaitEachGesture {
                // After everything under it: a child that takes the drag consumes it first.
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Final)
                if (down.position.y > band.toPx()) return@awaitEachGesture
                val start = MouseInfo.getPointerInfo()?.location ?: return@awaitEachGesture
                val origin = window.location
                val slop = WindowDragSlop.toPx()
                var moving = false
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Final)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) {
                        // Released where it was pressed, and not a click anything under it took.
                        val clicks = (event.nativeEvent as? MouseEvent)?.clickCount ?: 0
                        if (!moving && !change.isConsumed && clicks == 2) thread(isDaemon = true) { MacTitleBar.doubleClick(window) }
                        break
                    }
                    // A tab (or anything else) is dragging: the window stays.
                    if (change.isConsumed && !moving) return@awaitEachGesture
                    // Screen coordinates: the window moving under the pointer must not feed back.
                    val now = MouseInfo.getPointerInfo()?.location ?: break
                    val dx = now.x - start.x
                    val dy = now.y - start.y
                    if (!moving && hypot(dx.toDouble(), dy.toDouble()) < slop) continue
                    moving = true
                    window.setLocation(origin.x + dx, origin.y + dy)
                    change.consume()
                }
            }
        }
    }

// Larger than a tab's reorder slop (8 dp), so a tab claims its drag before the window would move.
private val WindowDragSlop = 12.dp
