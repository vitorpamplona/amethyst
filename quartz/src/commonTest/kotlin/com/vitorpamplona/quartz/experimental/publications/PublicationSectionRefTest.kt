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
package com.vitorpamplona.quartz.experimental.publications

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PublicationSectionRefTest {
    private val author = "5736".padEnd(64, 'a')
    private val other = "9911".padEnd(64, 'b')
    private val eventId = "aff2".padEnd(64, 'c')

    private fun index(vararg tags: Array<String>) = PublicationIndexEvent("id", author, 0L, arrayOf(arrayOf("d", "book"), *tags), "", "sig")

    private fun coord(d: String) = "${PublicationContentEvent.KIND}:$author:$d"

    @Test
    fun readsAddressEntriesInTagOrder() {
        val event = index(arrayOf("a", coord("ch-1")), arrayOf("a", coord("ch-2")))
        val refs = event.sections()

        assertEquals(2, refs.size)
        assertEquals(listOf("ch-1", "ch-2"), refs.map { it.address?.dTag })
    }

    @Test
    fun readsEventEntriesToo() {
        // An index may list its chapters entirely by event id; reading only `a` loses the book.
        val refs = index(arrayOf("e", eventId)).sections()

        assertEquals(1, refs.size)
        assertEquals(eventId, refs[0].eventId)
        assertNull(refs[0].address)
    }

    @Test
    fun interleavesAddressAndEventEntriesInTagOrder() {
        val refs =
            index(
                arrayOf("a", coord("ch-1")),
                arrayOf("e", eventId),
                arrayOf("a", coord("ch-3")),
            ).sections()

        assertEquals(3, refs.size)
        assertEquals("ch-1", refs[0].address?.dTag)
        assertEquals(eventId, refs[1].eventId)
        assertEquals("ch-3", refs[2].address?.dTag)
    }

    @Test
    fun uppercaseTagsAreTheOriginalSourceNotSections() {
        // On a derivative work `A`/`E` name what it was forked from. Reading them as contents
        // would splice the wrong book in.
        val refs =
            index(
                arrayOf("A", coord("original")),
                arrayOf("E", eventId),
                arrayOf("a", coord("ch-1")),
            ).sections()

        assertEquals(1, refs.size)
        assertEquals("ch-1", refs[0].address?.dTag)
    }

    @Test
    fun readsATitleFromTheSecondSlot() {
        // The whole point: the contents are readable before any section is fetched.
        val refs = index(arrayOf("a", coord("ch-1"), "Chapter One")).sections()

        assertEquals("Chapter One", refs[0].title)
    }

    @Test
    fun doesNotMistakeARelayHintForATitle() {
        // The documented slot-2 meaning is a relay hint, and most indexes use it that way.
        val refs = index(arrayOf("a", coord("ch-1"), "wss://relay.example.com")).sections()

        assertNull(refs[0].title)
    }

    @Test
    fun readsTheDocumentedEventIdFromTheThirdSlot() {
        val refs = index(arrayOf("a", coord("ch-1"), "wss://relay.example.com", eventId)).sections()

        assertEquals(eventId, refs[0].eventId)
        assertEquals(1, refs[0].level, "a pinned revision is still a top-level entry")
    }

    @Test
    fun readsANestingLevelFromTheThirdSlot() {
        // Publishers also put a depth there; a small integer is a level, 64 hex is an event id.
        val refs = index(arrayOf("a", coord("ch-1"), "", "2")).sections()

        assertEquals(2, refs[0].level)
        assertNull(refs[0].eventId)
    }

    @Test
    fun clampsAnAbsurdLevel() {
        assertEquals(PublicationSectionRef.MAX_LEVEL, index(arrayOf("a", coord("x"), "", "99")).sections()[0].level)
        assertEquals(1, index(arrayOf("a", coord("x"), "", "0")).sections()[0].level)
    }

    @Test
    fun defaultsToLevelOne() {
        assertEquals(1, index(arrayOf("a", coord("ch-1"))).sections()[0].level)
    }

    @Test
    fun skipsUnparseableEntries() {
        val refs =
            index(
                arrayOf("a", "not-a-coordinate"),
                arrayOf("e", "not-an-event-id"),
                arrayOf("a", coord("ch-1")),
            ).sections()

        assertEquals(1, refs.size)
    }

    @Test
    fun aNestedIndexIsAValidSection() {
        // NKBIP-01 allows a part that holds chapters.
        val nested = "${PublicationIndexEvent.KIND}:$other:part-one"
        val refs = index(arrayOf("a", nested)).sections()

        assertEquals(PublicationIndexEvent.KIND, refs[0].address?.kind)
        assertTrue(PublicationIndexEvent.KIND in PublicationSectionRef.SECTION_KINDS)
    }

    @Test
    fun sectionKindsCoverWhatAnIndexMayList() {
        // Long-form, wiki and spec events are all listable per NKBIP-01 and the clients.
        assertTrue(PublicationContentEvent.KIND in PublicationSectionRef.SECTION_KINDS)
        assertEquals(5, PublicationSectionRef.SECTION_KINDS.size)
    }

    @Test
    fun sectionCountMatchesWhatIsListed() {
        val event = index(arrayOf("a", coord("a")), arrayOf("e", eventId), arrayOf("A", coord("nope")))

        assertEquals(2, event.sectionCount())
    }

    @Test
    fun keyDistinguishesEntries() {
        val refs = index(arrayOf("a", coord("ch-1")), arrayOf("e", eventId)).sections()

        assertEquals(coord("ch-1"), refs[0].key())
        assertEquals(eventId, refs[1].key())
    }
}
