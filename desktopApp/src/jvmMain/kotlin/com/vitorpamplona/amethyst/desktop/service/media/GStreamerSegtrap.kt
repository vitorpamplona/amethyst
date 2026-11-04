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
package com.vitorpamplona.amethyst.desktop.service.media

import com.sun.jna.Library
import com.sun.jna.Native
import com.vitorpamplona.amethyst.desktop.platform.PlatformInfo
import com.vitorpamplona.quartz.utils.Log

/**
 * While GStreamer runs a plugin's init in-process it traps SIGSEGV and calls `exit(-1)`, but the
 * JVM raises SIGSEGV on purpose (safepoint polls, null checks), so the app died on start (#4566).
 * composemediaplayer calls `gst_init` with the trap on; drop this once it turns the trap off itself.
 */
internal object GStreamerSegtrap {
    // Lazy, so a Linux launch that never plays a video never loads GStreamer.
    private val disabled: Unit by lazy {
        if (!PlatformInfo.host.isLinux) return@lazy
        try {
            Native.load("libgstreamer-1.0.so.0", Gst::class.java).gst_segtrap_set_enabled(0)
            Log.i("GStreamerSegtrap", "GStreamer segfault trap off")
        } catch (e: Throwable) {
            // No GStreamer: the video engine fails to start on its own and reports that.
            Log.w("GStreamerSegtrap", "Could not turn off the GStreamer segfault trap", e)
        }
    }

    /** Call before every video engine is created; the work runs once. */
    fun disable() = disabled

    @Suppress("FunctionName")
    private interface Gst : Library {
        fun gst_segtrap_set_enabled(enabled: Int)
    }
}
