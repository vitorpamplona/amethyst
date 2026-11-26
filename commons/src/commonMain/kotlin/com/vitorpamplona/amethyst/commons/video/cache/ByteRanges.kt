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
 * The byte ranges of a file that are on disk: sorted, disjoint spans, each `[start, end)`. Two
 * spans that touch are merged, so [runEnd] reaches across everything stored contiguously.
 *
 * Not thread-safe: [SparseVideoCache] guards every instance with its lock.
 */
class ByteRanges {
    private val starts = ArrayList<Long>()
    private val ends = ArrayList<Long>()

    /** How many bytes the spans hold together. */
    var cachedBytes: Long = 0L
        private set

    val isEmpty: Boolean get() = starts.isEmpty()

    /** The highest end of any span: how long the file on disk must be. */
    val maxEnd: Long get() = ends.lastOrNull() ?: 0L

    /** Records `[start, end)` as stored; merges it with every span it overlaps or touches. */
    fun add(
        start: Long,
        end: Long,
    ) {
        if (end <= start) return
        var newStart = start
        var newEnd = end
        // The first span that ends at or after [start] is the first one that can merge.
        var i = firstEndingAtOrAfter(start)
        while (i < starts.size && starts[i] <= newEnd) {
            newStart = minOf(newStart, starts[i])
            newEnd = maxOf(newEnd, ends[i])
            cachedBytes -= ends[i] - starts[i]
            starts.removeAt(i)
            ends.removeAt(i)
        }
        starts.add(i, newStart)
        ends.add(i, newEnd)
        cachedBytes += newEnd - newStart
    }

    /** Where the stored run that holds [offset] ends; [offset] itself when that byte is not stored. */
    fun runEnd(offset: Long): Long {
        val i = firstEndingAfter(offset)
        return if (i < starts.size && starts[i] <= offset) ends[i] else offset
    }

    /** The start of the first stored span past [offset], or null when nothing after it is stored. */
    fun nextStartAfter(offset: Long): Long? {
        val i = firstEndingAfter(offset)
        if (i >= starts.size) return null
        return if (starts[i] > offset) starts[i] else starts.getOrNull(i + 1)
    }

    /** Whether every byte of `[start, end)` is stored. */
    fun covers(
        start: Long,
        end: Long,
    ): Boolean = end <= start || runEnd(start) >= end

    /** Forgets everything at or past [length]: the file turned out shorter, or its tail is gone. */
    fun truncate(length: Long) {
        while (starts.isNotEmpty() && starts.last() >= length) {
            cachedBytes -= ends.last() - starts.last()
            starts.removeAt(starts.size - 1)
            ends.removeAt(ends.size - 1)
        }
        if (ends.isNotEmpty() && ends.last() > length) {
            cachedBytes -= ends.last() - length
            ends[ends.size - 1] = length
        }
    }

    /** `0-100,200-300`, read back by [decode]. */
    fun encode(): String = starts.indices.joinToString(",") { "${starts[it]}-${ends[it]}" }

    fun copy(): ByteRanges = ByteRanges().also { copy -> starts.indices.forEach { copy.add(starts[it], ends[it]) } }

    private fun firstEndingAtOrAfter(offset: Long): Int {
        var lo = 0
        var hi = ends.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (ends[mid] < offset) lo = mid + 1 else hi = mid
        }
        return lo
    }

    private fun firstEndingAfter(offset: Long): Int {
        var lo = 0
        var hi = ends.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (ends[mid] <= offset) lo = mid + 1 else hi = mid
        }
        return lo
    }

    companion object {
        /** Reads [encode]'s form; a span it cannot parse is skipped, never trusted. */
        fun decode(text: String): ByteRanges {
            val ranges = ByteRanges()
            text.split(',').forEach { span ->
                val dash = span.indexOf('-')
                if (dash <= 0) return@forEach
                val start = span.substring(0, dash).trim().toLongOrNull() ?: return@forEach
                val end = span.substring(dash + 1).trim().toLongOrNull() ?: return@forEach
                if (start >= 0) ranges.add(start, end)
            }
            return ranges
        }
    }
}
