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
package com.vitorpamplona.amethyst.commons.model.observables

import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NoteSetMatchingFilterTest {
    private val me = "a".repeat(64)
    private val other = "b".repeat(64)

    private fun note(
        id: Char,
        author: String,
    ): Note =
        Note(id.toString().repeat(64)).also {
            it.event = TextNoteEvent(it.idHex, author, 1L, emptyArray(), "hi", "")
        }

    private val byMe = IFilter { it.event?.pubKey == me }

    @Test
    fun nothingUntilTheFirstWalkIsDone() {
        val set = NoteSetMatchingFilter(byMe)
        set.consider(note('1', me))
        assertNull(set.snapshot(), "a half-walked set must not stand in for the cache")

        set.markReady()
        assertEquals(1, set.snapshot()?.size)
    }

    @Test
    fun keepsWhatMatchesAndFollowsDeletions() {
        val set = NoteSetMatchingFilter(byMe).also { it.markReady() }
        val mine = note('1', me)
        val theirs = note('2', other)

        set.new(mine.event!!, mine)
        set.new(theirs.event!!, theirs)
        assertEquals(setOf(mine), set.snapshot())

        set.remove(mine)
        assertEquals(emptySet(), set.snapshot())
    }

    @Test
    fun aDependentIsCheckedAgainWhenItsTargetArrives() {
        // Matches only once its parent is known: what a bare reaction to my note looks like.
        val reply = note('3', other)
        val parent = note('4', me)
        val repliesToMine = IFilter { it.replyTo?.any { p -> p.event?.pubKey == me } == true }
        val set = NoteSetMatchingFilter(repliesToMine, dependents = { if (it === parent) listOf(reply) else emptyList() }).also { it.markReady() }

        set.new(reply.event!!, reply)
        assertEquals(emptySet(), set.snapshot(), "the parent is not known yet")

        reply.replyTo = listOf(parent)
        set.new(parent.event!!, parent)
        assertEquals(setOf(reply), set.snapshot())
    }

    @Test
    fun aWeakSetLetsGoOfNotesNothingElseHolds() {
        val set = NoteSetMatchingFilter(byMe, weak = true).also { it.markReady() }
        val kept = note('5', me)
        addUnreachable(set)
        set.new(kept.event!!, kept)

        // The unreachable one goes with the next GC, as it would from LocalCache.
        repeat(50) {
            if (set.snapshot()?.size == 1) return@repeat
            System.gc()
            Thread.sleep(20)
        }
        assertEquals(setOf(kept), set.snapshot())
    }

    // Its own frame, so nothing on the test's stack keeps the note reachable.
    private fun addUnreachable(set: NoteSetMatchingFilter) {
        val gone = note('6', me)
        set.new(gone.event!!, gone)
    }
}
