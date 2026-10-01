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
package com.vitorpamplona.quartz.buzz.threading

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Mirrors the tests in Buzz's `buzz-core/src/nip10.rs`, plus the reply-root derivation built on it. */
class BuzzThreadMarkersTest {
    private val a = "a".repeat(64)
    private val b = "b".repeat(64)

    private fun markers(vararg tags: Array<String>) = arrayOf(*tags).buzzThreadMarkers()

    @Test
    fun noETagsYieldNoMarkers() {
        assertEquals(BuzzThreadMarkers(null, null), markers())
    }

    @Test
    fun rootAndReplyBothParsed() {
        val m = markers(arrayOf("e", a, "", "root"), arrayOf("e", b, "", "reply"))
        assertEquals(a, m.root)
        assertEquals(b, m.reply)
        assertEquals(a to b, m.resolve())
    }

    @Test
    fun replyOnlyIsADirectReplyToTheRoot() {
        val m = markers(arrayOf("e", a, "", "reply"))
        assertNull(m.root)
        assertEquals(a to a, m.resolve())
    }

    @Test
    fun rootOnlyIsTopLevel() {
        assertNull(markers(arrayOf("e", a, "", "root")).resolve())
    }

    @Test
    fun bareAndThreeElementTagsAreIgnored() {
        assertEquals(BuzzThreadMarkers(null, null), markers(arrayOf("e", a)))
        // The relay needs the marker at index 3; ["e", id, "reply"] is not a thread link.
        assertEquals(BuzzThreadMarkers(null, null), markers(arrayOf("e", a, "reply")))
    }

    @Test
    fun malformedIdsAreIgnored() {
        assertEquals(BuzzThreadMarkers(null, null), markers(arrayOf("e", "bad", "", "root"), arrayOf("e", "z".repeat(64), "", "reply")))
        // A valid root with a malformed reply is top-level.
        val m = markers(arrayOf("e", a, "", "root"), arrayOf("e", "bad", "", "reply"))
        assertEquals(a, m.root)
        assertNull(m.reply)
        assertNull(m.resolve())
    }

    @Test
    fun lastValidOccurrenceWins() {
        val m = markers(arrayOf("e", a, "", "reply"), arrayOf("e", b, "", "reply"))
        assertEquals(b, m.reply)
    }

    @Test
    fun replyRootFollowsTheRelaysDerivation() {
        val own = "c".repeat(64)
        // Parent is top-level: it starts the thread.
        assertEquals(own, arrayOf<Array<String>>().buzzThreadRootForReplyTo(own))
        // Parent is a direct reply to a: the thread root is a.
        assertEquals(a, arrayOf(arrayOf("e", a, "", "reply")).buzzThreadRootForReplyTo(own))
        // Parent is a nested reply: its root marker is the thread root.
        assertEquals(a, arrayOf(arrayOf("e", a, "", "root"), arrayOf("e", b, "", "reply")).buzzThreadRootForReplyTo(own))
        // Parent carries only a root marker (NIP-10 style): the relay treats it as top-level, so
        // replying to it roots at the parent itself, not at its root marker.
        assertEquals(own, arrayOf(arrayOf("e", a, "", "root")).buzzThreadRootForReplyTo(own))
    }
}
