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
package com.vitorpamplona.amethyst.commons.ui.render

import kotlin.concurrent.Volatile

/**
 * Whether Skia draws this app's windows on the CPU: native Wayland, or a desktop whose GPU path
 * failed. There every pixel is computed per frame, so a stretched image or an antialiased edge costs
 * milliseconds (desktopApp/plans/2026-11-20-desktop-scroll-benchmark.md), and the drawing takes the
 * cheaper ways [com.vitorpamplona.amethyst.commons.service.image.PixelExactImage] and
 * [com.vitorpamplona.amethyst.commons.ui.components.mediaClip] offer. On a GPU those ways only cost
 * memory and draw calls, so they stay off. iOS draws on Metal and leaves it off.
 */
object SoftwareDrawing {
    /** On when any open window draws in software: images are drawn for the slowest one. */
    @Volatile
    var active: Boolean = false

    // What each open window draws with. Read and written on the UI thread, where windows open.
    private val windows = HashMap<Any, Boolean>()

    /** [window] draws in [software] now (it opened, or its renderer changed). */
    fun report(
        window: Any,
        software: Boolean,
    ) {
        windows[window] = software
        active = windows.values.any { it }
    }

    /** [window] closed: what it drew with counts no more. */
    fun forget(window: Any) {
        windows.remove(window)
        active = windows.values.any { it }
    }
}
