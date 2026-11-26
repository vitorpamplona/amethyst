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
 * What [SparseVideoCache] knows of one URL: its full length once a server said it, its content
 * type, when it was last opened (for least-recently-used eviction) and which bytes are on disk.
 *
 * Persisted next to the bytes as a few `key=value` lines ([encode] / [decode]), so the cache
 * survives a restart without a database.
 */
class VideoCacheEntry(
    val url: String,
    var length: Long? = null,
    var mimeType: String? = null,
    var lastAccess: Long = 0L,
    val ranges: ByteRanges = ByteRanges(),
) {
    /** Whether the whole file is on disk. */
    val isComplete: Boolean get() = length.let { it != null && ranges.covers(0, it) }

    fun encode(): String {
        // Read here: inside buildString, `length` would be the builder's own.
        val knownLength = this.length
        val knownMimeType = this.mimeType
        return buildString {
            append(VERSION).append('\n')
            append("url=").append(url).append('\n')
            knownLength?.let { append("length=").append(it).append('\n') }
            knownMimeType?.let { append("mime=").append(it).append('\n') }
            append("access=").append(lastAccess).append('\n')
            append("ranges=").append(ranges.encode()).append('\n')
        }
    }

    companion object {
        private const val VERSION = "amethyst-video-cache-1"

        /** The entry [encode] wrote, or null for anything else: an older format, a torn write. */
        fun decode(text: String): VideoCacheEntry? {
            val lines = text.lines()
            if (lines.firstOrNull() != VERSION) return null
            val values =
                lines
                    .drop(1)
                    .mapNotNull { line ->
                        val eq = line.indexOf('=')
                        if (eq <= 0) null else line.substring(0, eq) to line.substring(eq + 1)
                    }.toMap()
            val url = values["url"]?.takeIf { it.isNotBlank() } ?: return null
            // A file without its ranges line was cut short while being written: nothing in it is known.
            val ranges = values["ranges"]?.let { ByteRanges.decode(it) } ?: return null
            val length = values["length"]?.toLongOrNull()?.takeIf { it >= 0 }
            if (length != null) ranges.truncate(length)
            return VideoCacheEntry(
                url = url,
                length = length,
                mimeType = values["mime"]?.takeIf { it.isNotBlank() },
                lastAccess = values["access"]?.toLongOrNull() ?: 0L,
                ranges = ranges,
            )
        }
    }
}
