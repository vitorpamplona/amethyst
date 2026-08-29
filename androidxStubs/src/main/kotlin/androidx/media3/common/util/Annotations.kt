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
package androidx.media3.common.util

import androidx.media3.common.C

/**
 * JVM stand-ins for media3's stability annotations. Inert: they only mark API
 * stability, and the app already opts in wherever it uses them.
 */
@Retention(AnnotationRetention.BINARY)
annotation class UnstableApi

object Util {
    /** media3 returns C.TIME_UNSET for an unknown duration; mirror that. */
    fun usToMs(timeUs: Long): Long = if (timeUs == C.TIME_UNSET) timeUs else timeUs / 1000

    fun msToUs(timeMs: Long): Long = if (timeMs == C.TIME_UNSET) timeMs else timeMs * 1000

    /**
     * media3's clock formatting: `m:ss` under an hour, `h:mm:ss` above it, and
     * a leading minus for a negative position. Reimplemented rather than
     * approximated because it is what the player's position and duration
     * labels read, and "1:5" instead of "1:05" is a visible bug.
     */
    fun getStringForTime(
        builder: StringBuilder,
        formatter: java.util.Formatter,
        timeMs: Long,
    ): String {
        val time = if (timeMs == C.TIME_UNSET) 0L else timeMs
        val prefix = if (time < 0) "-" else ""
        val totalSeconds = (kotlin.math.abs(time) + 500) / 1000
        val seconds = totalSeconds % 60
        val minutes = (totalSeconds / 60) % 60
        val hours = totalSeconds / 3600

        builder.setLength(0)
        return if (hours > 0) {
            formatter.format("%s%d:%02d:%02d", prefix, hours, minutes, seconds).toString()
        } else {
            formatter.format("%s%d:%02d", prefix, minutes, seconds).toString()
        }
    }
}
