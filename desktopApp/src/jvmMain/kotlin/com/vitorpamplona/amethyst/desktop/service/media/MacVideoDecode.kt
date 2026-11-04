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
import java.util.concurrent.ConcurrentHashMap

/**
 * Whether macOS's engine can decode a video codec. AVFoundation decodes AV1 only in hardware, which
 * Apple silicon has from the M3 on: on an M1 or M2 an AV1 video plays its sound under a picture that
 * never comes, and the engine reports no error. Chromium decodes it in software instead.
 */
internal object MacVideoDecode {
    private const val TAG = "MacVideoDecode"

    // Codecs AVFoundation decodes only where VideoToolbox has the hardware for them.
    private val HARDWARE_ONLY = setOf("av01")

    private interface VideoToolbox : Library {
        // The C symbol's own name.
        @Suppress("FunctionName")
        fun VTIsHardwareDecodeSupported(codecType: Int): Boolean
    }

    private val videoToolbox: VideoToolbox? by lazy {
        runCatching { Native.load("VideoToolbox", VideoToolbox::class.java) }
            .onFailure { Log.w(TAG, "VideoToolbox did not load", it) }
            .getOrNull()
    }

    private val answers = ConcurrentHashMap<String, Boolean>()

    /** Whether the native engine can decode video in [codec], an MP4 sample entry type (`avc1`, `av01`). */
    fun canDecode(codec: String): Boolean {
        if (!PlatformInfo.isMacOS || codec !in HARDWARE_ONLY) return true
        return answers.getOrPut(codec) {
            // Unknown: leave it to the engine, as before.
            val supported = runCatching { videoToolbox?.VTIsHardwareDecodeSupported(fourCc(codec)) }.getOrNull() ?: true
            Log.d(TAG) { "$codec: hardware decode ${if (supported) "available" else "missing"}" }
            supported
        }
    }

    private fun fourCc(code: String): Int = code.fold(0) { acc, c -> (acc shl 8) or (c.code and 0xFF) }
}
