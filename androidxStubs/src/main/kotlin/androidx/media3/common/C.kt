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
package androidx.media3.common

/** JVM stand-in for the androidx.media3.common.C constant bag. */
object C {
    const val TIME_UNSET = Long.MIN_VALUE + 1
    const val INDEX_UNSET = -1
    const val LENGTH_UNSET = -1
    const val RATE_UNSET = -Float.MAX_VALUE

    const val TRACK_TYPE_UNKNOWN = -1
    const val TRACK_TYPE_AUDIO = 1
    const val TRACK_TYPE_VIDEO = 2
    const val TRACK_TYPE_TEXT = 3

    const val ENCODING_PCM_16BIT = 0x2
    const val ENCODING_PCM_FLOAT = 0x4

    const val WAKE_MODE_NONE = 0
    const val WAKE_MODE_LOCAL = 1
    const val WAKE_MODE_NETWORK = 2

    const val VIDEO_SCALING_MODE_SCALE_TO_FIT = 1
    const val VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING = 2
}
