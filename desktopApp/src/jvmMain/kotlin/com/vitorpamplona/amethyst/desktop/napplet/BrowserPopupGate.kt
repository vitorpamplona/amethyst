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

/**
 * The one budget every browser window's popups share. JCEF doesn't say whether a popup came from a
 * click, so a page could call `window.open` on a timer; with a budget per window, each popup would
 * bring its own and the windows would double every second. Here a page may open at most one window
 * per [minIntervalMs] across all of them, and only while fewer than [maxOpen] windows that pages
 * opened are still open. Windows the user opens don't count.
 */
class BrowserPopupGate(
    private val maxOpen: Int = 4,
    private val minIntervalMs: Long = 1_000L,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private var lastAt = Long.MIN_VALUE / 2
    private val open = HashSet<Any>()

    /** Whether a page may open a window now; when it may, [onOpen] makes it, and it counts until [closed]. */
    @Synchronized
    fun <T : Any> tryOpen(onOpen: () -> T?): T? {
        val now = clock()
        if (open.size >= maxOpen || now - lastAt < minIntervalMs) return null
        val window = onOpen() ?: return null
        lastAt = now
        open += window
        return window
    }

    @Synchronized
    fun closed(window: Any) {
        open -= window
    }
}
