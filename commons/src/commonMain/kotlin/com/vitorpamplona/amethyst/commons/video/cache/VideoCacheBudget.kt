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
package com.vitorpamplona.amethyst.commons.video.cache

/**
 * How large the video disk cache may grow: Android's rule (`VideoCache.calculateCacheSize` in the
 * app module), so both platforms keep the same scroll window. [PERCENT] of the disk space that is
 * free when the cache opens, never below [MIN_BYTES] (still useful on a full device) nor above
 * [MAX_BYTES] (a feed is append-only, so a larger cache mostly holds videos nobody rewatches).
 */
object VideoCacheBudget {
    const val PERCENT = 0.20
    const val MIN_BYTES = 256L * 1024 * 1024
    const val MAX_BYTES = 4L * 1024 * 1024 * 1024

    /** The budget for a disk with [availableBytes] free; an unknown amount (0 or less) gets the floor. */
    fun forAvailableBytes(availableBytes: Long): Long = (availableBytes.coerceAtLeast(0L) * PERCENT).toLong().coerceIn(MIN_BYTES, MAX_BYTES)
}
