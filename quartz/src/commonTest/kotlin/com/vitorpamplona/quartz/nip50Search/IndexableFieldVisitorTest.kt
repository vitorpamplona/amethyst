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
package com.vitorpamplona.quartz.nip50Search

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.utils.EventFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The read path ([SearchableEvent.forEachIndexableField]) and the write path
 * ([SearchableEvent.indexableContent]) must never disagree about what an event says.
 *
 * They cannot simply share code — one joins a string because a store wants one, the other refuses
 * to build a string at all — so the agreement is pinned here instead. A kind whose visitor drifts
 * from its joined content would make local search find things the relay does not, or miss things
 * it does, with nothing else to catch it.
 */
class IndexableFieldVisitorTest {
    private val pubkey = "46fcbe3065eaf1ae7811465924e48923363ff3f526bd6f73d7c184b16bd8ce4d"
    private val id = "98b574c3527f0ffb30b7271084e3f07480733c7289f8de424d29eae82e36c758"

    private fun event(
        kind: Int,
        tags: Array<Array<String>>,
        content: String,
    ): Event = EventFactory.create(id, pubkey, 1683596206, kind, tags, content, "")

    /** Every field the visitor hands over, in order, nulls dropped exactly as `listOfNotNull` does. */
    private fun visited(event: SearchableEvent): List<String> {
        val out = mutableListOf<String>()
        event.forEachIndexableField { field ->
            if (field != null) out.add(field)
            true
        }
        return out
    }

    private fun assertAgrees(
        kind: Int,
        tags: Array<Array<String>>,
        content: String,
    ) {
        val e = event(kind, tags, content)
        assertTrue(e is SearchableEvent, "kind $kind did not resolve to a SearchableEvent (is it in EventFactory?)")
        assertEquals(
            e.indexableContent(),
            visited(e).joinToString("\n"),
            "kind $kind: the visitor's fields do not rejoin to what the stores index",
        )
    }

    @Test
    fun theKindsThatOverrideTheVisitorRejoinToTheirIndexedContent() {
        // A note with a subject, and one without — the null field is where a join drifts.
        assertAgrees(1, arrayOf(arrayOf("subject", "A subject")), "the body")
        assertAgrees(1, emptyArray(), "the body")

        // title + summary + content, and each subset of it.
        listOf(30023, 30818, 30402, 30311).forEach { kind ->
            assertAgrees(kind, arrayOf(arrayOf("title", "T"), arrayOf("summary", "S")), "body")
            assertAgrees(kind, arrayOf(arrayOf("title", "T")), "body")
            assertAgrees(kind, emptyArray(), "body")
        }

        // Highlight: comment + context + content.
        assertAgrees(9802, arrayOf(arrayOf("comment", "C"), arrayOf("context", "X")), "quoted")
        assertAgrees(9802, emptyArray(), "quoted")

        // Community definition: name + description + rules + content.
        assertAgrees(34550, arrayOf(arrayOf("name", "N"), arrayOf("description", "D"), arrayOf("rules", "R")), "c")
        assertAgrees(34550, emptyArray(), "c")
    }

    @Test
    fun theDefaultVisitorHandsOverTheJoinedContentUntouched() {
        // A kind that does not override still answers the read path correctly, via the default.
        val e = event(1111, arrayOf(arrayOf("t", "bitcoin")), "a comment")
        assertTrue(e is SearchableEvent)
        assertEquals(e.indexableContent(), visited(e).joinToString("\n"))
    }

    @Test
    fun theWalkStopsAtTheFirstFieldThatSatisfiesIt() {
        val e = event(30023, arrayOf(arrayOf("title", "T"), arrayOf("summary", "S")), "body")
        assertTrue(e is SearchableEvent)
        var seen = 0
        e.forEachIndexableField {
            seen++
            // Stop immediately: a hit on the title must not go on to build the body.
            false
        }
        assertEquals(1, seen, "the visitor kept walking after being told to stop")
    }
}
