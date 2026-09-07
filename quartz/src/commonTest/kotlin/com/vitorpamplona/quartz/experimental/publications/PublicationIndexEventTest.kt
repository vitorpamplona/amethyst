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
import kotlin.test.assertTrue

class PublicationIndexEventTest {
    /** The NKBIP-01 spec's own example index, trimmed to the tags we parse. */
    private val specExampleJson =
        """
        {
          "id": "aff2591ba5a445601da37714669da4b733dc0d2bc9ac476c3c9652cadfd11287",
          "pubkey": "573634b648634cbad10f2451776089ea21090d9407f715e83c577b4611ae6edc",
          "created_at": 1725087283,
          "kind": 30040,
          "tags": [
            ["d", "aesops-fables-by-aesop"],
            ["title", "Aesop's Fables"],
            ["author", "Aesop"],
            ["t", "fables"],
            ["type", "book"],
            ["version", "3rd edition"],
            ["summary", "Selected fables from the ancient Greek philosopher."],
            ["a", "30041:573634b648634cbad10f2451776089ea21090d9407f715e83c577b4611ae6edc:the-farmer-and-the-snake"],
            ["a", "30041:573634b648634cbad10f2451776089ea21090d9407f715e83c577b4611ae6edc:the-fox-and-the-grapes"],
            ["auto-update", "yes"]
          ],
          "content": "",
          "sig": "0c062160e8a965ffe66454216f40b0cfd3d5b4e2e87e8a39fb2f4b75ce227ac3034eac584aa64ba3deab9810f374bf4d702d45d8a6837f241df9c4deae243a4a"
        }
        """.trimIndent()

    @Test
    fun kindIsRegisteredWithTheEventFactory() {
        assertTrue(EventFactory.isKnownKind(PublicationIndexEvent.KIND))
    }

    @Test
    fun parsesTheSpecExample() {
        val event = Event.fromJson(specExampleJson)
        assertTrue(event is PublicationIndexEvent, "EventFactory produced ${event::class.simpleName}")

        assertEquals("Aesop's Fables", event.title())
        assertEquals("Aesop", event.author())
        assertEquals("book", event.type())
        assertEquals("3rd edition", event.version())
        assertEquals("Selected fables from the ancient Greek philosopher.", event.summary())
        assertEquals(listOf("fables"), event.topics())
        assertEquals(2, event.sectionCount())
        assertEquals("aesops-fables-by-aesop", event.dTag())
    }

    @Test
    fun typeDefaultsToBook() {
        val event = PublicationIndexEvent("id", "pk", 0L, arrayOf(arrayOf("d", "x")), "", "sig")

        assertEquals("book", event.type())
    }

    @Test
    fun fallsBackToTheIdentifierWhenTheMandatoryTitleIsMissing() {
        // `title` is required by the spec but absent in the wild, and a rating pointing at such a
        // publication still has to render a name.
        val event = PublicationIndexEvent("id", "pk", 0L, arrayOf(arrayOf("d", "wuthering-heights")), "", "sig")

        assertEquals("Wuthering Heights", event.titleOrIdentifier())
    }

    @Test
    fun humanizesSlugsAndLeavesEverythingElseAlone() {
        assertEquals("Wuthering Heights", PublicationIndexEvent.humanizeIdentifier("wuthering-heights"))
        assertEquals("The Farmer And The Snake", PublicationIndexEvent.humanizeIdentifier("the_farmer_and_the_snake"))
        assertEquals("Already Titled", PublicationIndexEvent.humanizeIdentifier("Already Titled"))
        assertEquals("", PublicationIndexEvent.humanizeIdentifier(""))
    }

    @Test
    fun buildsAnIndexWithEmptyContentAsTheSpecRequires() {
        val template = PublicationIndexEvent.build("Wuthering Heights", "wuthering-heights", author = "Emily Bronte")
        val tags = template.tags.associate { it[0] to it[1] }

        assertEquals("", template.content)
        assertEquals("wuthering-heights", tags["d"])
        assertEquals("Wuthering Heights", tags["title"])
        assertEquals("Emily Bronte", tags["author"])
    }
}
