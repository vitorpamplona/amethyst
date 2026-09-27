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
package com.vitorpamplona.quartz.nip84Highlights

import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip73ExternalIds.books.BookId
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** NIP-84 sources: `i` tags for NIP-73 ids, and `r` tags that may hold any text. */
class HighlightSourceTest {
    private val signer = NostrSignerInternal(KeyPair())

    private fun highlight(vararg tags: Array<String>) = HighlightEvent("00", "00", 0, arrayOf(*tags), "a passage", "00")

    @Test
    fun readsNip73ExternalIds() {
        val event = highlight(arrayOf("i", "isbn:9780765382030"), arrayOf("i", "custom:thing"))
        val ids = event.inExternalIds()
        assertEquals(1, ids.size)
        assertEquals("isbn:9780765382030", ids.first().toScope())
        assertEquals(listOf("isbn:9780765382030", "custom:thing"), event.inExternalIdValues())
    }

    @Test
    fun aTextReferenceIsNotAUrl() {
        val event = highlight(arrayOf("r", "Dune, chapter 3"))
        assertEquals("Dune, chapter 3", event.inReference())
        assertNull(event.inUrl())
    }

    @Test
    fun aWebReferenceIsStillAUrl() {
        val event = highlight(arrayOf("r", "https://example.com/post"))
        assertEquals("https://example.com/post", event.inReference())
        assertEquals("https://example.com/post", event.inUrl())
    }

    @Test
    fun prefersTheSourceMarkedReferenceOverMentions() {
        val event =
            highlight(
                arrayOf("r", "https://cited.example.com", "mention"),
                arrayOf("r", "https://source.example.com", "source"),
            )
        assertEquals("https://source.example.com", event.inReference())

        val onlyMention = highlight(arrayOf("r", "https://cited.example.com", "mention"))
        assertNull(onlyMention.inReference())
    }

    @Test
    fun classifiesReferences() {
        assertTrue(HighlightEvent.isUrlReference("https://example.com/post?x=1"))
        assertTrue(HighlightEvent.isUrlReference("http://example.com"))
        assertTrue(HighlightEvent.isUrlReference("example.com/post"))
        assertFalse(HighlightEvent.isUrlReference("Dune, chapter 3"))
        assertFalse(HighlightEvent.isUrlReference("The Bitcoin whitepaper"))
        assertFalse(HighlightEvent.isUrlReference("Bitcoin"))
        assertFalse(HighlightEvent.isUrlReference(""))
    }

    @Test
    fun builderKeepsATextReferenceVerbatim() =
        runTest {
            val event = HighlightEvent.create(quote = "a passage", url = "  Dune, chapter 3 ", signer = signer)
            assertContentEquals(arrayOf("r", "Dune, chapter 3"), event.tags.first { it[0] == "r" })
            assertEquals("Dune, chapter 3", event.inReference())
        }

    @Test
    fun builderStillNormalizesAWebReference() =
        runTest {
            val event = HighlightEvent.create(quote = "a passage", url = "example.com/post", signer = signer)
            assertEquals("https://example.com/post", event.inUrl())
        }

    @Test
    fun builderMarksTheSourceOfAQuoteHighlight() =
        runTest {
            val event = HighlightEvent.create(quote = "a passage", url = "https://example.com/post", comment = "so true", signer = signer)
            assertContentEquals(arrayOf("r", "https://example.com/post", "source"), event.tags.first { it[0] == "r" })
        }

    @Test
    fun builderWritesExternalIds() =
        runTest {
            val event = HighlightEvent.create(quote = "a passage", externalIds = listOf(BookId("978-0765382030")), signer = signer)
            assertContentEquals(arrayOf("i", "isbn:9780765382030"), event.tags.first { it[0] == "i" })
            assertEquals("isbn:9780765382030", event.inExternalIds().single().toScope())
        }
}
