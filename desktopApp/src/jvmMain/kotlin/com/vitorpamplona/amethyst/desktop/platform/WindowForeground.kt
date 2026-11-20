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

import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.awt.Desktop
import java.awt.Frame
import java.awt.Window
import java.awt.event.FocusEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import kotlin.coroutines.resume

/**
 * Makes the app the active one and focuses [this] window. The activation is asynchronous, and a
 * focus request made before it lands is dropped (the app comes forward with no key window), so the
 * request repeats briefly until the window is active. Returns whether it became active.
 */
suspend fun Window.activate(): Boolean {
    requestAppForeground()
    repeat(ACTIVATION_ATTEMPTS) {
        if (isActive) return true
        toFront()
        // A mouse click is what asks for focus here. macOS's AWT drops focus requests of any other
        // cause while the app is not yet active (CPlatformWindow.rejectFocusRequest), which is
        // exactly the moment this runs in, so the window came forward without becoming key.
        requestFocus(FocusEvent.Cause.MOUSE_EVENT)
        delay(ACTIVATION_RETRY_MS)
    }
    return isActive
}

/**
 * Puts [this] window in front of every app's windows and focuses it, for a question that arrives
 * while the user is in another app (a NIP-46 client asking the bunker): un-minimized, its owner
 * (the main window) first, then itself.
 *
 * The OS may refuse a background app the focus: Windows' foreground lock only flashes the taskbar
 * button, and X11 window managers prevent focus stealing. So the window is also pinned above the
 * others until it has had the focus once (the user clicked it, or the OS gave it), or until it
 * closes: a question nobody sees is a silent "no". Native Wayland grants neither; there it stays
 * best-effort.
 */
suspend fun Window.bringToForeground() {
    // The owner coming back from the tray or the Dock takes the focus as it shows: let it show
    // first, then take the focus back, or it lands on the owner instead of the question.
    owner?.let { owner ->
        (owner as? Frame)?.restore()
        withTimeoutOrNull(OWNER_SHOW_MS) { while (!owner.isShowing) delay(ACTIVATION_RETRY_MS) }
    }
    (this as? Frame)?.restore()

    val pin = !isAlwaysOnTop && isAlwaysOnTopSupported
    if (pin) isAlwaysOnTop = true
    try {
        owner?.toFront()
        if (!activate() && pin) awaitFocusOrClose()
    } finally {
        if (pin) isAlwaysOnTop = false
    }
}

/** Asks the OS to make the app the active one; on macOS this activates it over the frontmost app. */
private fun requestAppForeground() {
    try {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.APP_REQUEST_FOREGROUND)) {
            Desktop.getDesktop().requestForeground(true)
        }
    } catch (e: Exception) {
        Log.w("WindowForeground", "Could not bring the app forward", e)
    }
}

private fun Frame.restore() {
    if (extendedState and Frame.ICONIFIED != 0) extendedState = extendedState and Frame.ICONIFIED.inv()
}

/** Suspends until [this] window gains the focus or closes, whichever comes first. */
private suspend fun Window.awaitFocusOrClose() {
    if (isFocused || !isDisplayable) return
    suspendCancellableCoroutine { cont ->
        val listener =
            object : WindowAdapter() {
                override fun windowGainedFocus(e: WindowEvent) = done()

                override fun windowClosed(e: WindowEvent) = done()

                private fun done() {
                    removeWindowFocusListener(this)
                    removeWindowListener(this)
                    if (cont.isActive) cont.resume(Unit)
                }
            }
        addWindowFocusListener(listener)
        addWindowListener(listener)
        cont.invokeOnCancellation {
            removeWindowFocusListener(listener)
            removeWindowListener(listener)
        }
        // Focused between the check above and the listener going in.
        if (isFocused) listener.windowGainedFocus(WindowEvent(this, WindowEvent.WINDOW_GAINED_FOCUS))
    }
}

private const val ACTIVATION_ATTEMPTS = 10
private const val ACTIVATION_RETRY_MS = 50L
private const val OWNER_SHOW_MS = 1_000L
