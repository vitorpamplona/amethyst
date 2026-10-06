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
package com.vitorpamplona.quartz.experimental.citations

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CitationSearchTest {
    private val pk1 = "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c"
    private val pk2 = "99bb5591c9116600f845107d31f9b59e2f7c7e09a1ff802e84f1d43da557ca64"
    private val eid = "43575072239da152afe3d7b5c70ed2beb48db2b10e60c60da45229c09c877d2a"
    private val eid2 = "98b574c3527f0ffb30b7271084e3f07480733c7289f8de424d29eae82e36c758"
    private val relay = "wss://relay.damus.io/"
    private val zero = "00".repeat(32)
    private val sig = "00".repeat(64)

    private fun visited(event: com.vitorpamplona.quartz.nip50Search.SearchableEvent): String {
        val out = mutableListOf<String>()
        event.forEachIndexableField {
            if (it != null) out.add(it)
            true
        }
        return out.joinToString("\n")
    }

    @Test
    fun externalCitationIndexesAuthorAndPublisherAndLinksItsTimestamp() {
        val event =
            ExternalCitationEvent(
                zero,
                pk1,
                1,
                arrayOf(
                    arrayOf("u", "https://example.com/a"),
                    arrayOf("title", "The Article"),
                    arrayOf("summary", "What it says"),
                    arrayOf("author", "Jane Doe"),
                    arrayOf("published_by", "Example Press"),
                    arrayOf("open_timestamp", eid),
                ),
                "my note",
                sig,
            )
        assertEquals("The Article\nWhat it says\nmy note\nJane Doe\nExample Press", event.indexableContent())
        assertEquals(event.indexableContent(), visited(event))
        assertEquals(listOf(eid), event.linkedEventIds())
        assertTrue(event.eventHints().isEmpty())
    }

    @Test
    fun hardcopyCitationIndexesChapterEditorAndContainer() {
        val event =
            HardcopyCitationEvent(
                zero,
                pk1,
                1,
                arrayOf(
                    arrayOf("title", "Book"),
                    arrayOf("chapter_title", "Chapter One"),
                    arrayOf("author", "A. Writer"),
                    arrayOf("editor", "E. Ditor"),
                    arrayOf("published_in", "Journal of Things", "7"),
                    arrayOf("published_by", "Pub House"),
                ),
                "",
                sig,
            )
        assertEquals("Book\nChapter One\n\nA. Writer\nE. Ditor\nJournal of Things\nPub House", event.indexableContent())
        assertEquals(event.indexableContent(), visited(event))
    }

    @Test
    fun promptCitationIndexesTheModel() {
        val event = PromptCitationEvent(zero, pk1, 1, arrayOf(arrayOf("llm", "Claude"), arrayOf("author", "Me")), "the prompt", sig)
        assertEquals("the prompt\nMe\nClaude", event.indexableContent())
        assertEquals(event.indexableContent(), visited(event))
    }
}
