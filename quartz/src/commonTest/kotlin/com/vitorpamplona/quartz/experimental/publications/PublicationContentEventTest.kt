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

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.utils.EventFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PublicationContentEventTest {
    /** The NKBIP-01 spec's own example section. */
    private val specExampleJson =
        """
        {
          "id": "${"a".repeat(64)}",
          "pubkey": "8ae74c618a4713f32129aa3d9b8c25f6bd0c4d1f2e3a4b5c6d7e8f90a1b2c3d4",
          "created_at": 1708083476,
          "kind": 30041,
          "tags": [
            ["title", "The Farmer and The Snake"],
            ["d", "aesops-fables-the-farmer-and-the-snake"],
            ["wikilink", "fable", "${"b".repeat(64)}", "wss://thecitadel.nostr1.com", "${"c".repeat(64)}"]
          ],
          "content": "ONE WINTER a Farmer found a Snake stiff and frozen with cold.",
          "sig": "${"d".repeat(128)}"
        }
        """.trimIndent()

    @Test
    fun kindIsRegisteredWithTheEventFactory() {
        assertTrue(EventFactory.isKnownKind(PublicationContentEvent.KIND))
    }

    @Test
    fun parsesTheSpecExample() {
        val event = Event.fromJson(specExampleJson)
        assertTrue(event is PublicationContentEvent, "EventFactory produced ${event::class.simpleName}")

        assertEquals("The Farmer and The Snake", event.title())
        assertEquals("aesops-fables-the-farmer-and-the-snake", event.dTag())
        assertEquals(listOf("fable"), event.wikilinkTargets())
        assertTrue(event.indexableContent().startsWith("The Farmer and The Snake\n"))
    }

    @Test
    fun fallsBackToTheIdentifierWhenTheTitleIsMissing() {
        val event = PublicationContentEvent("id", "pk", 0L, arrayOf(arrayOf("d", "chapter-one")), "body", "sig")

        assertEquals("Chapter One", event.titleOrIdentifier())
    }

    @Test
    fun parsesTheWikilinkTagWithItsAddressingSlots() {
        val event = Event.fromJson(specExampleJson) as PublicationContentEvent
        val link = event.wikilinks().single()

        assertEquals("fable", link.target)
        assertEquals("b".repeat(64), link.pubKey)
        assertEquals("c".repeat(64), link.eventId)
        assertEquals("wss://thecitadel.nostr1.com/", link.relay?.url)
    }

    @Test
    fun dropsMalformedAddressingSlotsRatherThanShiftingThem() {
        // Positional slots: reading an event id as a pubkey would address the wrong thing.
        val event =
            PublicationContentEvent(
                "id",
                "pk",
                0L,
                arrayOf(arrayOf("wikilink", "fable", "not-a-pubkey", "", "also-not-an-id")),
                "body",
                "sig",
            )
        val link = event.wikilinks().single()

        assertEquals("fable", link.target)
        assertNull(link.pubKey)
        assertNull(link.eventId)
        assertNull(link.relay)
    }

    @Test
    fun matchesABodyReferenceToItsTagIgnoringCaseAndSeparators() {
        val event =
            PublicationContentEvent(
                "id",
                "pk",
                0L,
                arrayOf(arrayOf("wikilink", "the-farmer", "b".repeat(64))),
                "body",
                "sig",
            )

        assertEquals("the-farmer", event.wikilinkFor("The Farmer")?.target)
        assertEquals("the-farmer", event.wikilinkFor("the_farmer")?.target)
        assertEquals("the-farmer", event.wikilinkFor("THE-FARMER")?.target)
        assertNull(event.wikilinkFor("the snake"))
    }

    @Test
    fun buildsASection() {
        val template = PublicationContentEvent.build("Chapter 1", "book-chapter-1", "Once upon a time.")
        val tags = template.tags.associate { it[0] to it[1] }

        assertEquals("book-chapter-1", tags["d"])
        assertEquals("Chapter 1", tags["title"])
        assertEquals("Once upon a time.", template.content)
    }
}
