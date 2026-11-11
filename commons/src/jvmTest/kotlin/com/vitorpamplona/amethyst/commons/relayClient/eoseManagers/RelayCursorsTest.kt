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
package com.vitorpamplona.amethyst.commons.relayClient.eoseManagers

import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A cursor vouches for what one holder of notes already has from each relay: the time and the
 * filters it asked. Asking for less keeps it; asking for more asks only the new part from scratch.
 */
class RelayCursorsTest {
    private val relay = NormalizedRelayUrl("wss://relay.example.com/")
    private val other = NormalizedRelayUrl("wss://other.example.com/")

    private val alice = "a".repeat(64)
    private val bob = "b".repeat(64)
    private val carol = "c".repeat(64)

    private fun notes(vararg authors: String) = FilterShape(authors = authors.toSet(), kinds = setOf(1, 6))

    @Test
    fun aNewHolderAsksFromScratch() {
        val cursors = RelayCursors()
        assertTrue(cursors.isEmpty())
        assertEquals(listOf(CursorPart(notes(alice), null)), cursors.plan(relay, notes(alice)))
    }

    @Test
    fun theSameOrANarrowerRequestContinuesFromTheCursor() {
        val cursors = RelayCursors()
        cursors.newEose(relay, listOf(notes(alice, bob)), 1_000)

        assertEquals(listOf(CursorPart(notes(alice, bob), 1_000)), cursors.plan(relay, notes(alice, bob)))
        assertEquals(listOf(CursorPart(notes(alice), 1_000)), cursors.plan(relay, notes(alice)))
        val fewerKinds = FilterShape(authors = setOf(alice), kinds = setOf(1))
        assertEquals(listOf(CursorPart(fewerKinds, 1_000)), cursors.plan(relay, fewerKinds))
    }

    @Test
    fun aCursorIsPerRelay() {
        val cursors = RelayCursors()
        cursors.newEose(relay, listOf(notes(alice)), 1_000)
        assertEquals(listOf(CursorPart(notes(alice), null)), cursors.plan(other, notes(alice)))
    }

    @Test
    fun aNewAuthorIsAskedFromScratchAndOnlyThem() {
        val cursors = RelayCursors()
        cursors.newEose(relay, listOf(notes(alice, bob)), 1_000)

        assertEquals(
            listOf(CursorPart(notes(alice, bob), 1_000), CursorPart(notes(carol), null)),
            cursors.plan(relay, notes(alice, bob, carol)),
        )
    }

    @Test
    fun anotherKindOrNoAuthorsAsksEverythingFromScratch() {
        val cursors = RelayCursors()
        cursors.newEose(relay, listOf(notes(alice)), 1_000)

        val moreKinds = FilterShape(authors = setOf(alice), kinds = setOf(1, 6, 20))
        assertEquals(listOf(CursorPart(moreKinds, null)), cursors.plan(relay, moreKinds))
        val anyone = FilterShape(kinds = setOf(1, 6))
        assertEquals(listOf(CursorPart(anyone, null)), cursors.plan(relay, anyone))
    }

    @Test
    fun tagsNarrowWhenAddedAndWidenWhenDropped() {
        val cursors = RelayCursors()
        val toMe = FilterShape(kinds = setOf(1), tags = mapOf("p" to setOf(alice)))
        cursors.newEose(relay, listOf(toMe), 1_000)

        val toMeWithHashtag = FilterShape(kinds = setOf(1), tags = mapOf("p" to setOf(alice), "t" to setOf("nostr")))
        assertEquals(listOf(CursorPart(toMeWithHashtag, 1_000)), cursors.plan(relay, toMeWithHashtag))

        val toAnyone = FilterShape(kinds = setOf(1))
        assertEquals(listOf(CursorPart(toAnyone, null)), cursors.plan(relay, toAnyone))
        val toMoreUsers = FilterShape(kinds = setOf(1), tags = mapOf("p" to setOf(alice, bob)))
        assertEquals(listOf(CursorPart(toMoreUsers, null)), cursors.plan(relay, toMoreUsers))
    }

    @Test
    fun anyOfSeveralSentFiltersCanCoverARequest() {
        val cursors = RelayCursors()
        val profiles = FilterShape(authors = setOf(alice), kinds = setOf(0))
        cursors.newEose(relay, listOf(notes(alice), profiles), 1_000)

        assertEquals(listOf(CursorPart(profiles, 1_000)), cursors.plan(relay, profiles))
    }

    @Test
    fun anAuthorDroppedAndAddedBackIsAskedAgain() {
        // Their notes left the feed when they left the request: nothing vouches for them anymore.
        val cursors = RelayCursors()
        cursors.newEose(relay, listOf(notes(alice, bob)), 1_000)
        cursors.newEose(relay, listOf(notes(alice)), 2_000)

        assertEquals(
            listOf(CursorPart(notes(alice), 2_000), CursorPart(notes(bob), null)),
            cursors.plan(relay, notes(alice, bob)),
        )
    }

    @Test
    fun rewindingOnlyMovesCursorsBack() {
        val cursors = RelayCursors()
        cursors.newEose(relay, listOf(notes(alice)), 1_000)
        cursors.newEose(other, listOf(notes(alice)), 400)

        cursors.rewindTo(500)

        assertEquals(500L, cursors.since(relay))
        assertEquals(400L, cursors.since(other))
        assertEquals(listOf(CursorPart(notes(alice), 500)), cursors.plan(relay, notes(alice)))
    }

    @Test
    fun clearingVouchesForNothing() {
        val cursors = RelayCursors()
        cursors.newEose(relay, listOf(notes(alice)), 1_000)
        cursors.clear()

        assertNull(cursors.since(relay))
        assertEquals(listOf(CursorPart(notes(alice), null)), cursors.plan(relay, notes(alice)))
    }

    @Test
    fun aShapeIgnoresTheWindow() {
        val filter = Filter(authors = listOf(alice), kinds = listOf(1), tags = mapOf("t" to listOf("nostr")), since = 5, until = 9, limit = 20)
        assertEquals(
            FilterShape(authors = setOf(alice), kinds = setOf(1), tags = mapOf("t" to setOf("nostr"))),
            FilterShape.of(filter),
        )
    }
}
