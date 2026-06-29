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
package com.vitorpamplona.quartz.nip34Git.patch

/** A half-open `[start, end)` character span within a line. */
class CharSpan(
    val start: Int,
    val end: Int,
) {
    val isEmpty: Boolean get() = end <= start
}

/**
 * Computes intra-line (word/character level) change spans for a hunk, so the diff
 * renderer can emphasize *what* changed inside a modified line rather than just
 * coloring the whole line. Contiguous delete-runs are paired with the following
 * add-runs (delete[i] ↔ add[i]); each pair is reduced to the differing middle by
 * trimming the common prefix and suffix.
 */
object IntralineDiff {
    /** The changed middle of [old] vs [new] (common prefix/suffix trimmed). */
    fun changedSpans(
        old: String,
        new: String,
    ): Pair<CharSpan, CharSpan> {
        if (old == new) return CharSpan(0, 0) to CharSpan(0, 0)
        val n = old.length
        val m = new.length
        var prefix = 0
        while (prefix < n && prefix < m && old[prefix] == new[prefix]) prefix++
        var suffix = 0
        while (suffix < n - prefix && suffix < m - prefix && old[n - 1 - suffix] == new[m - 1 - suffix]) suffix++
        return CharSpan(prefix, n - suffix) to CharSpan(prefix, m - suffix)
    }

    /**
     * Returns, per line index in [lines], the changed span to emphasize. Only
     * paired modified lines get an entry; pure add/delete blocks and context
     * lines are absent.
     */
    fun emphasis(lines: List<GitDiffLine>): Map<Int, CharSpan> {
        val result = HashMap<Int, CharSpan>()
        var i = 0
        while (i < lines.size) {
            if (lines[i].type != GitDiffLineType.DELETE) {
                i++
                continue
            }
            val delStart = i
            while (i < lines.size && lines[i].type == GitDiffLineType.DELETE) i++
            val delEnd = i
            val addStart = i
            while (i < lines.size && lines[i].type == GitDiffLineType.ADD) i++
            val addEnd = i

            val pairs = minOf(delEnd - delStart, addEnd - addStart)
            for (k in 0 until pairs) {
                val delIdx = delStart + k
                val addIdx = addStart + k
                val (oldSpan, newSpan) = changedSpans(lines[delIdx].content, lines[addIdx].content)
                if (!oldSpan.isEmpty) result[delIdx] = oldSpan
                if (!newSpan.isEmpty) result[addIdx] = newSpan
            }
        }
        return result
    }
}
