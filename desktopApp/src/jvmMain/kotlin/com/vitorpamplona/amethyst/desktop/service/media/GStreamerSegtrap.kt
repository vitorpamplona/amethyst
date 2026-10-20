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
 * Turns off GStreamer's segfault trap before the Linux video engine starts GStreamer.
 *
 * While GStreamer runs a plugin's init in this process it swaps the process-wide SIGSEGV handler
 * for its own, which prints "Caught a segmentation fault while loading plugin file" and calls
 * `exit(-1)`. The JVM raises SIGSEGV on purpose (safepoint polls, implicit null checks) and
 * handles it itself, so any such fault on any thread inside that window ends the app, blaming
 * whichever plugin was loading. `libgstnvcodec` spends long enough initialising CUDA that the
 * first feed video lost that race on every start (#4566). composemediaplayer calls `gst_init`
 * without turning the trap off, so it is done here, on the `libgstreamer` it links against.
 */
internal object GStreamerSegtrap {
    private const val TAG = "GStreamerSegtrap"

    private val disabled: Unit by lazy {
        if (!PlatformInfo.host.isLinux) return@lazy
        try {
            val gst = Native.load("libgstreamer-1.0.so.0", Gst::class.java)
            gst.gst_segtrap_set_enabled(0)
            Log.i(TAG) { "GStreamer segfault trap off: ${gst.gst_segtrap_is_enabled() == 0}" }
        } catch (e: Throwable) {
            // No GStreamer: the video engine fails to start on its own and reports that.
            Log.w(TAG, "Could not turn off the GStreamer segfault trap", e)
        }
    }

    /** Idempotent; call before every video engine is created. */
    fun disable() = disabled

    @Suppress("FunctionName")
    private interface Gst : Library {
        fun gst_segtrap_set_enabled(enabled: Int)

        fun gst_segtrap_is_enabled(): Int
    }
}
