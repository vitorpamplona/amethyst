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

class LineDiffTest {
    private fun reconstructNew(hunk: GitDiffHunk) = hunk.lines.filter { it.type != GitDiffLineType.DELETE }.map { it.content }

    private fun reconstructOld(hunk: GitDiffHunk) = hunk.lines.filter { it.type != GitDiffLineType.ADD }.map { it.content }

    @Test
    fun mergesNearbyChangesIntoOneHunk() {
        val old = listOf("alpha", "bravo", "charlie", "delta", "echo", "foxtrot", "golf", "hotel")
        val new = listOf("alpha", "bravo", "CHARLIE", "delta", "echo", "foxtrot", "NEW LINE", "golf", "hotel")

        val hunks = LineDiff.hunks(old, new)
        assertEquals(1, hunks.size)
        val hunk = hunks.single()
        // matches `git diff -U3`
        assertEquals("@@ -1,8 +1,9 @@", hunk.header)
        assertEquals(1, hunk.oldStart)
        assertEquals(1, hunk.newStart)

        // The whole file is one hunk here, so it must reconstruct both sides exactly.
        assertEquals(old, reconstructOld(hunk))
        assertEquals(new, reconstructNew(hunk))

        val adds = hunk.lines.filter { it.type == GitDiffLineType.ADD }.map { it.content }
        val dels = hunk.lines.filter { it.type == GitDiffLineType.DELETE }.map { it.content }
        assertEquals(listOf("CHARLIE", "NEW LINE"), adds)
        assertEquals(listOf("charlie"), dels)
    }

    @Test
    fun splitsFarApartChangesIntoTwoHunks() {
        val old = (1..60).map { "line $it" }
        val new =
            old.toMutableList().apply {
                this[1] = "line 2 CHANGED"
                this[50] = "line 51 CHANGED"
            }

        val hunks = LineDiff.hunks(old, new)
        assertEquals(2, hunks.size)
        // First hunk around line 2 (within the leading context), second around line 51.
        assertTrue("first hunk near top, was ${hunks[0].oldStart}", hunks[0].oldStart <= 2)
        assertTrue("second hunk near 51, was ${hunks[1].oldStart}", hunks[1].oldStart in 47..51)
    }

    @Test
    fun pureAdditionIntoEmptyFile() {
        val hunks = LineDiff.hunks(emptyList(), listOf("a", "b", "c"))
        val hunk = hunks.single()
        assertEquals("@@ -0,0 +1,3 @@", hunk.header)
        assertEquals(3, hunk.lines.count { it.type == GitDiffLineType.ADD })
        assertEquals(0, hunk.lines.count { it.type != GitDiffLineType.ADD })
    }

    @Test
    fun fullDeletion() {
        val hunks = LineDiff.hunks(listOf("a", "b"), emptyList())
        val hunk = hunks.single()
        assertEquals("@@ -1,2 +0,0 @@", hunk.header)
        assertEquals(2, hunk.lines.count { it.type == GitDiffLineType.DELETE })
    }

    @Test
    fun identicalFilesProduceNoHunks() {
        val same = listOf("x", "y", "z")
        assertTrue(LineDiff.hunks(same, same).isEmpty())
    }

    @Test
    fun reconstructsAcrossManyEdits() {
        // Deterministic pseudo-random-ish edits, then verify each hunk reconstructs locally.
        val old = (1..40).map { "item-$it" }
        val new =
            old.toMutableList().apply {
                removeAt(35)
                add(10, "inserted-A")
                this[5] = "item-6-edited"
                add("appended")
            }
        val hunks = LineDiff.hunks(old, new, context = 3)
        // Each hunk's reconstructed old/new slices must match the corresponding source slices.
        for (hunk in hunks) {
            val oldSlice = old.subList(hunk.oldStart - 1, hunk.oldStart - 1 + reconstructOld(hunk).size)
            assertEquals(oldSlice, reconstructOld(hunk))
        }
    }
}
