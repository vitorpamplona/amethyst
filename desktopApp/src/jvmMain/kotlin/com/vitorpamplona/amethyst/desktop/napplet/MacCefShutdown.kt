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

import org.cef.CefApp
import org.cef.CefApp.CefAppState

/**
 * Shuts Chromium down on macOS, where JCEF's own [CefApp.dispose] kills the app: it runs CEF's
 * shutdown on the AWT event thread, and CEF closes a window of its own there (`TempWindowMac`),
 * which macOS 27 aborts the process for, since AppKit windows may only be closed on the main thread.
 *
 * This runs the same native shutdown on the main thread, then marks the app terminated so that
 * jcefmaven's exit hook, which calls [CefApp.dispose] again, does nothing. JCEF keeps both steps
 * private, so they are reached by reflection; a JCEF that renames them fails here, and the caller
 * logs it, rather than crashing.
 */
internal object MacCefShutdown {
    private const val CLIENTS_WAIT_MS = 1_500L

    fun shutdown(app: CefApp) {
        // Every browser must be gone first: the windows close theirs on the AWT thread, and a client
        // leaves this set only once its browsers have closed.
        val deadline = System.currentTimeMillis() + CLIENTS_WAIT_MS
        while (openClients(app) > 0 && System.currentTimeMillis() < deadline) Thread.sleep(20)

        if (CefApp.getState() >= CefAppState.SHUTTING_DOWN) return
        val nativeShutdown = CefApp::class.java.getDeclaredMethod("N_Shutdown").apply { isAccessible = true }
        val setState = CefApp::class.java.getDeclaredMethod("setState", CefAppState::class.java).apply { isAccessible = true }
        MacMainThread.run { nativeShutdown.invoke(app) }
        setState.invoke(null, CefAppState.TERMINATED)
    }

    private fun openClients(app: CefApp): Int {
        val clients = CefApp::class.java.getDeclaredField("clients_").apply { isAccessible = true }
        return synchronized(app) { (clients.get(app) as Collection<*>).size }
    }
}
