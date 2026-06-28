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

/**
 * Computes a line-level unified diff between two texts using Myers' O(ND)
 * difference algorithm, producing the same [GitDiffHunk] model the embedded
 * patch parser uses. This lets the app render a diff for pull requests that
 * reference a clone URL + commit instead of embedding a `git format-patch`.
 */
object LineDiff {
    private class Op(
        val type: GitDiffLineType,
        val oldIndex: Int, // 0-based index into old lines, -1 for inserts
        val newIndex: Int, // 0-based index into new lines, -1 for deletes
    )

    /**
     * Builds unified-diff hunks between [oldLines] and [newLines], with [context]
     * unchanged lines around each change (git's default is 3).
     */
    fun hunks(
        oldLines: List<String>,
        newLines: List<String>,
        context: Int = 3,
    ): List<GitDiffHunk> {
        val ops = computeOps(oldLines, newLines)
        return groupHunks(ops, oldLines, newLines, context)
    }

    /** Myers O(ND) diff: returns an ordered op list (CONTEXT / DELETE / ADD). */
    private fun computeOps(
        a: List<String>,
        b: List<String>,
    ): List<Op> {
        val n = a.size
        val m = b.size
        if (n == 0 && m == 0) return emptyList()
        val max = n + m
        val offset = max
        val trace = ArrayList<IntArray>()
        val v = IntArray(2 * max + 1)

        var found = false
        for (d in 0..max) {
            trace.add(v.copyOf())
            var k = -d
            while (k <= d) {
                val idx = k + offset
                var x =
                    if (k == -d || (k != d && v[idx - 1] < v[idx + 1])) {
                        v[idx + 1]
                    } else {
                        v[idx - 1] + 1
                    }
                var y = x - k
                while (x < n && y < m && a[x] == b[y]) {
                    x++
                    y++
                }
                v[idx] = x
                if (x >= n && y >= m) {
                    found = true
                    break
                }
                k += 2
            }
            if (found) break
        }

        // Backtrack to recover the edit script.
        val reversed = ArrayList<Op>()
        var x = n
        var y = m
        for (d in trace.indices.reversed()) {
            val vv = trace[d]
            val k = x - y
            val idx = k + offset
            val prevK =
                if (k == -d || (k != d && vv[idx - 1] < vv[idx + 1])) {
                    k + 1
                } else {
                    k - 1
                }
            val prevIdx = prevK + offset
            val prevX = vv[prevIdx]
            val prevY = prevX - prevK

            while (x > prevX && y > prevY) {
                reversed.add(Op(GitDiffLineType.CONTEXT, x - 1, y - 1))
                x--
                y--
            }
            if (d > 0) {
                if (x == prevX) {
                    reversed.add(Op(GitDiffLineType.ADD, -1, y - 1))
                } else {
                    reversed.add(Op(GitDiffLineType.DELETE, x - 1, -1))
                }
            }
            x = prevX
            y = prevY
        }
        reversed.reverse()
        return reversed
    }

    private fun groupHunks(
        ops: List<Op>,
        oldLines: List<String>,
        newLines: List<String>,
        context: Int,
    ): List<GitDiffHunk> {
        if (ops.none { it.type != GitDiffLineType.CONTEXT }) return emptyList()

        val changeIndexes = ops.indices.filter { ops[it].type != GitDiffLineType.CONTEXT }
        val hunks = ArrayList<GitDiffHunk>()

        var i = 0
        while (i < changeIndexes.size) {
            val start = changeIndexes[i]
            var end = start
            // Extend the hunk while the next change is within 2*context of the previous.
            var j = i
            while (j + 1 < changeIndexes.size && changeIndexes[j + 1] - changeIndexes[j] <= 2 * context + 1) {
                j++
                end = changeIndexes[j]
            }
            val from = (start - context).coerceAtLeast(0)
            val to = (end + context).coerceAtMost(ops.size - 1)

            val lines = ArrayList<GitDiffLine>()
            var oldStart = -1
            var newStart = -1
            var oldCount = 0
            var newCount = 0
            for (idx in from..to) {
                val op = ops[idx]
                val oldNo = if (op.oldIndex >= 0) op.oldIndex + 1 else null
                val newNo = if (op.newIndex >= 0) op.newIndex + 1 else null
                val content = if (op.type == GitDiffLineType.ADD) newLines[op.newIndex] else oldLines[op.oldIndex]
                lines.add(GitDiffLine(op.type, content, oldNo, newNo))
                if (op.type != GitDiffLineType.ADD) {
                    if (oldStart < 0) oldStart = op.oldIndex + 1
                    oldCount++
                }
                if (op.type != GitDiffLineType.DELETE) {
                    if (newStart < 0) newStart = op.newIndex + 1
                    newCount++
                }
            }
            if (oldStart < 0) oldStart = 0
            if (newStart < 0) newStart = 0
            val header = "@@ -$oldStart,$oldCount +$newStart,$newCount @@"
            hunks.add(GitDiffHunk(header, oldStart, newStart, lines))

            i = j + 1
        }
        return hunks
    }
}
