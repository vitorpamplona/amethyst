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
package com.vitorpamplona.amethyst.service.crashreports

import android.os.Handler
import android.os.Looper
import com.vitorpamplona.quartz.utils.Log

/**
 * Wraps the main thread's `Looper.loop()` so a narrow set of known, benign
 * framework crashes can be swallowed instead of taking the whole process down.
 *
 * Only one case is handled today: `IllegalArgumentException("endOffset must be
 * greater than startOffset")` thrown from
 * `androidx.compose.foundation.text.input.internal.LegacyCursorAnchorInfoBuilder`.
 * It fires when the IME has requested `CURSOR_UPDATE_MONITOR` on a TextField
 * whose visible bounds momentarily collapse to a zero-width region (during a
 * layout transition or sheet animation): `addCharacterBounds` computes
 * `startOffset == endOffset` and `MultiParagraph.fillBoundingBoxes` rejects
 * the empty range. The IME just misses a cursor update — the app does not
 * need to die.
 *
 * Any other Throwable propagates so the default uncaught-exception handler
 * (and our crash report saver) still see it.
 */
object MainThreadCrashGuard {
    fun install() {
        Handler(Looper.getMainLooper()).post {
            while (true) {
                try {
                    Looper.loop()
                    // Looper.loop() returned cleanly — main thread is shutting down.
                    return@post
                } catch (e: Throwable) {
                    if (!isSwallowable(e)) throw e
                    Log.w("MainThreadCrashGuard", "Swallowed known framework crash: ${e.javaClass.name}: ${e.message}", e)
                }
            }
        }
    }

    private fun isSwallowable(e: Throwable): Boolean = isComposeLegacyCursorAnchorInfoCrash(e)

    private fun isComposeLegacyCursorAnchorInfoCrash(e: Throwable): Boolean {
        if (e !is IllegalArgumentException) return false
        if (e.message != "endOffset must be greater than startOffset") return false
        // Cap the scan: in production this stack is ~25 frames; 40 is generous and bounds the
        // cost of every main-thread exception we don't swallow.
        val frames = e.stackTrace
        val limit = minOf(frames.size, 40)
        for (i in 0 until limit) {
            val frame = frames[i]
            val cls = frame.className
            if (cls.contains("LegacyCursorAnchorInfo") ||
                cls.contains("MultiParagraph") ||
                cls.contains("AndroidParagraph") ||
                cls.endsWith("TextLayout")
            ) {
                return true
            }
        }
        return false
    }
}
