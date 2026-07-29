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
import com.vitorpamplona.quartz.nip84Highlights.parse.SharedHighlightParser
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HighlightEventBuilderTest {
    private val signer = NostrSignerInternal(KeyPair())

    @Test
    fun buildsBarePassageWithNoTags() =
        runTest {
            val event = HighlightEvent.create(quote = "just a passage", signer = signer)

            assertEquals(HighlightEvent.KIND, event.kind)
            assertEquals("just a passage", event.quote())
            assertTrue(event.tags.isEmpty())
        }

    @Test
    fun buildsSourceReferenceTag() =
        runTest {
            val event =
                HighlightEvent.create(
                    quote = "a passage",
                    url = "https://example.com/post",
                    signer = signer,
                )

            assertEquals("https://example.com/post", event.inUrl())
        }

    @Test
    fun buildsSelectorFromPrefixSuffix() =
        runTest {
            val event =
                HighlightEvent.create(
                    quote = "the passage",
                    url = "https://example.com/post",
                    prefix = "before ",
                    suffix = " after",
                    signer = signer,
                )

            val selector = event.textQuoteSelector()
            assertNull(selector?.exact) // placeholder — the passage is in .content
            assertEquals("before ", selector?.prefix)
            assertEquals(" after", selector?.suffix)
        }

    @Test
    fun omitsSelectorWhenNoPrefixOrSuffix() =
        runTest {
            val event =
                HighlightEvent.create(
                    quote = "the passage",
                    url = "https://example.com/post",
                    signer = signer,
                )

            assertNull(event.textQuoteSelector())
        }

    @Test
    fun buildsCommentAndContextTags() =
        runTest {
            val event =
                HighlightEvent.create(
                    quote = "the passage",
                    url = "https://example.com/post",
                    comment = "my note about it",
                    context = "The surrounding paragraph with the passage in it.",
                    signer = signer,
                )

            assertEquals("my note about it", event.comment())
            assertEquals("The surrounding paragraph with the passage in it.", event.context())
        }

    @Test
    fun blankOptionalsAreSkipped() =
        runTest {
            val event =
                HighlightEvent.create(
                    quote = "the passage",
                    url = "   ",
                    comment = "",
                    context = "  ",
                    signer = signer,
                )

            assertTrue(event.tags.isEmpty())
        }

    @Test
    fun buildsNostrSourceTags() =
        runTest {
            val article = "30023:6e468422dfb74a5738702a8823b9b28168abab8655faacb6853cd0ee15deee93:my-article"
            val author = "6e468422dfb74a5738702a8823b9b28168abab8655faacb6853cd0ee15deee93"
            val version = "8d7ae10a57ef178a17563a6ecbf9a399bb1796a2e032ca72703b00913b4cfd42"

            val event =
                HighlightEvent.create(
                    quote = "a passage from an article",
                    address = article,
                    event = version,
                    author = author,
                    signer = signer,
                )

            assertEquals(article, event.inPostAddress()?.toValue())
            assertEquals(version, event.inPostVersion()?.eventId)
            assertEquals(author, event.author())
            // The p tag carries the NIP-84 "author" role so attribution survives mention p tags.
            assertTrue(event.tags.any { it[0] == "p" && it[1] == author && it.getOrNull(3) == "author" })
        }

    @Test
    fun buildProducesUnsignedTemplateWithSameTags() {
        val template =
            HighlightEvent.build(
                quote = "the passage",
                url = "https://example.com/post",
                prefix = "before ",
                comment = "note",
            )

        assertEquals(HighlightEvent.KIND, template.kind)
        assertEquals("the passage", template.content)
        assertTrue(template.tags.any { it[0] == "r" && it[1] == "https://example.com/post" })
        assertTrue(template.tags.any { it[0] == "textquoteselector" })
        assertTrue(template.tags.any { it[0] == "comment" && it[1] == "note" })
    }

    @Test
    fun roundTripsFromSharedHighlightParser() =
        runTest {
            val parsed =
                SharedHighlightParser.parse(
                    "https://example.com/post?utm_source=x#:~:text=the%20-,highlighted%20passage,-follows",
                )

            val event =
                HighlightEvent.create(
                    quote = parsed.quote!!,
                    url = parsed.url,
                    prefix = parsed.prefix,
                    suffix = parsed.suffix,
                    signer = signer,
                )

            assertEquals("highlighted passage", event.quote())
            assertEquals("https://example.com/post", event.inUrl())
            assertEquals("the ", event.textQuoteSelector()?.prefix)
            assertEquals("follows", event.textQuoteSelector()?.suffix)
        }
}
