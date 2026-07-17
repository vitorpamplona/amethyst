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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HighlightEventTest {
    // The kind:9802 highlight emitted by a web highlighter client: no `context` tag,
    // but W3C Web Annotation selectors carrying the surrounding prefix/suffix.
    private val webHighlight =
        HighlightEvent(
            id = "8d7ae10a57ef178a17563a6ecbf9a399bb1796a2e032ca72703b00913b4cfd42",
            pubKey = "6e468422dfb74a5738702a8823b9b28168abab8655faacb6853cd0ee15deee93",
            createdAt = 1784322253,
            tags =
                arrayOf(
                    arrayOf("r", "https://geohot.github.io//blog/jekyll/update/2026/05/03/punk-or-why-i-dont-stream.html"),
                    arrayOf("textquoteselector", "-", "Your food is prechewed for you. ", "\n\nAnd it’s not like there’s anyw"),
                    arrayOf("textpositionselector", "1861", "1938"),
                    arrayOf("rangeselector", "/main[1]/div[1]/article[1]/div[1]/p[5]", "/main[1]/div[1]/article[1]/div[1]/p[5]", "257", "334"),
                ),
            content = "The caged tiger prefers a pot of meat slop to an antelope they have to chase.",
            sig = "3d7040846e0e9fea7ebd58b3f3377290e6677f6b1fbef6bd6957cc76d262a7c190f4d17609a5cee941f9e24d6e131b78784a9e6e8d289efa8af813ad3c2f23ea",
        )

    @Test
    fun parsesReferenceUrlAndIgnoresUnknownSelectorsForSource() {
        assertEquals(
            "https://geohot.github.io//blog/jekyll/update/2026/05/03/punk-or-why-i-dont-stream.html",
            webHighlight.inUrl(),
        )
        assertNull(webHighlight.context())
        assertNull(webHighlight.comment())
        assertNull(webHighlight.author())
    }

    @Test
    fun parsesTextQuoteSelectorTreatingDashExactAsPlaceholder() {
        val selector = webHighlight.textQuoteSelector()
        assertEquals(null, selector?.exact) // "-" placeholder means the quote is in .content
        assertEquals("Your food is prechewed for you. ", selector?.prefix)
        assertEquals("\n\nAnd it’s not like there’s anyw", selector?.suffix)
    }

    @Test
    fun reconstructsContextFromSelectorWhenNoContextTag() {
        assertEquals(
            "Your food is prechewed for you. The caged tiger prefers a pot of meat slop to an antelope they have to chase.\n\nAnd it’s not like there’s anyw",
            webHighlight.contextOrReconstructed(),
        )
    }

    @Test
    fun prefersExplicitContextTagOverSelectorReconstruction() {
        val withContext =
            HighlightEvent(
                id = "00",
                pubKey = "00",
                createdAt = 0,
                tags =
                    arrayOf(
                        arrayOf("context", "An explicit paragraph of context around the quote."),
                        arrayOf("textquoteselector", "-", "before ", " after"),
                    ),
                content = "the quote",
                sig = "00",
            )

        assertEquals("An explicit paragraph of context around the quote.", withContext.contextOrReconstructed())
    }

    @Test
    fun returnsNullContextWhenNeitherContextTagNorSelectorPresent() {
        val bare =
            HighlightEvent(
                id = "00",
                pubKey = "00",
                createdAt = 0,
                tags = arrayOf(arrayOf("r", "https://example.com")),
                content = "a bare highlight",
                sig = "00",
            )

        assertNull(bare.textQuoteSelector())
        assertNull(bare.contextOrReconstructed())
    }
}
