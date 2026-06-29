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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IntralineDiffTest {
    private fun text(
        old: String,
        new: String,
    ) = old.substring(IntralineDiff.changedSpans(old, new).first.let { it.start until it.end }) to
        new.substring(IntralineDiff.changedSpans(old, new).second.let { it.start until it.end })

    @Test
    fun trimsCommonPrefixAndSuffix() {
        val (oldChanged, newChanged) = text("the quick brown fox", "the quick red fox")
        assertEquals("brown", oldChanged)
        assertEquals("red", newChanged)
    }

    @Test
    fun pureInsertionHasEmptyOldSpan() {
        val (old, new) = IntralineDiff.changedSpans("abcd", "abXYcd")
        assertTrue(old.isEmpty)
        assertEquals("XY", "abXYcd".substring(new.start until new.end))
    }

    @Test
    fun identicalLinesProduceEmptySpans() {
        val (old, new) = IntralineDiff.changedSpans("same", "same")
        assertTrue(old.isEmpty)
        assertTrue(new.isEmpty)
    }

    @Test
    fun emphasisPairsDeletesWithAdds() {
        val lines =
            listOf(
                GitDiffLine(GitDiffLineType.CONTEXT, "ctx", 1, 1),
                GitDiffLine(GitDiffLineType.DELETE, "value = 1", 2, null),
                GitDiffLine(GitDiffLineType.ADD, "value = 2", null, 2),
            )
        val emphasis = IntralineDiff.emphasis(lines)
        // line 1 (delete) and line 2 (add) get the differing "1"/"2" highlighted
        assertEquals("1", "value = 1".substring(emphasis[1]!!.start until emphasis[1]!!.end))
        assertEquals("2", "value = 2".substring(emphasis[2]!!.start until emphasis[2]!!.end))
        assertTrue(!emphasis.containsKey(0))
    }
}
