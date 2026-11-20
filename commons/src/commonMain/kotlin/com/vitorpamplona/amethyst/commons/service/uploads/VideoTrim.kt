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
package com.vitorpamplona.amethyst.commons.service.uploads

import androidx.compose.runtime.Immutable

/** The part of a video to send, from [startMs] to [endMs] of the picked file. */
@Immutable
data class VideoTrim(
    val startMs: Long,
    val endMs: Long,
) {
    val durationMs: Long get() = endMs - startMs

    /** This trim, or null when it keeps all of a [totalMs]-long video (so nothing is rewritten for it). */
    fun orNullIfWhole(totalMs: Long): VideoTrim? = if (startMs <= SLACK_MS && endMs >= totalMs - SLACK_MS) null else this

    companion object {
        /** The shortest a video's trim may be. */
        const val MIN_MS = 1_000L

        /**
         * The shortest a GIF's trim may be. A reaction GIF often runs a second or two, which
         * [MIN_MS] left nothing to cut from; a GIF keeps every frame it touches, shortened to the
         * part kept, so even a tenth of a second is a frame.
         */
        const val MIN_GIF_MS = 100L

        private const val SLACK_MS = 50L
    }
}
