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
        // The suffix's leading "\n\n" (a page block boundary) is collapsed to a single space so
        // it doesn't render as blank lines; the quote's own content is left verbatim.
        assertEquals(
            "Your food is prechewed for you. The caged tiger prefers a pot of meat slop to an antelope they have to chase. And it’s not like there’s anyw",
            webHighlight.contextOrReconstructed(),
        )
    }

    @Test
    fun collapsesRunsOfWhitespaceScrapedFromThePage() {
        // A real highlight whose prefix carries five newlines between two paragraphs of the
        // source page. Without collapsing, the reconstructed context renders a stack of blank
        // lines above the marked quote.
        val excessWhitespace =
            HighlightEvent(
                id = "fc2366a5ac54de837842492e525f8f5d141d4a9bba5b1238e135adaf4225763f",
                pubKey = "6e468422dfb74a5738702a8823b9b28168abab8655faacb6853cd0ee15deee93",
                createdAt = 1785270682,
                tags =
                    arrayOf(
                        arrayOf("r", "https://geohot.github.io//blog/jekyll/update/2026/06/06/our-great-war.html"),
                        arrayOf("textquoteselector", "-", "n way, the better.\n\n\n\n\nHowever, ", ". A single totalizing control sy"),
                    ),
                content = "it will end badly for everyone if the systems of comfort prevent structural exit for the people who don’t want it",
                sig = "0428ad8aef2a12f36a4dc86105f8b429a4b4161f1fa72b08414fb2dd6c1a2276838b167682cb49ab94aa5c4e1d6351bbec0b7906a4b5e6cec1ab64a9d4c63d9d",
            )

        assertEquals(
            "n way, the better. However, it will end badly for everyone if the systems of comfort prevent structural exit for the people who don’t want it. A single totalizing control sy",
            excessWhitespace.contextOrReconstructed(),
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

    @Test
    fun prefersTheAuthorMarkedPTagOverEarlierMentions() {
        // A real kind:9802 highlighting a nostr note: three `mention` p tags precede the
        // `author`-marked one. author() must return the author, not the first mention.
        val highlight =
            HighlightEvent(
                id = "710dd9bcaa29618ad660db1a10fa0df12e684b161755369e14a459a98f80cc78",
                pubKey = "7fa56f5d6962ab1e3cd424e758c3002b8665f7b0d8dcee9fe9e288d7751ac194",
                createdAt = 1772184734,
                tags =
                    arrayOf(
                        arrayOf("p", "0c45d7d45edb0fadda4215d36ca0d9aba0c771b85d3717764b8a128d5e443e4d", "", "mention"),
                        arrayOf("p", "99bb5591c9116600f845107d31f9b59e2f7c7e09a1ff802e84f1d43da557ca64", "", "mention"),
                        arrayOf("p", "4d7842051782e0d3feb034d150adc2b6bae4ee3b49786793bffa468b6f5b96b3", "", "mention"),
                        arrayOf("e", "54f1c0fbc3305dd98b3ce8e63ef04e9f4b149dc8de38b4275fae49beddb794eb", "wss://nos.lol/", "source"),
                        arrayOf("p", "dd664d5e4016433a8cd69f005ae1480804351789b59de5af06276de65633d319", "", "author"),
                    ),
                content = "Family and friendship and faith give men a sense of purpose.",
                sig = "00",
            )

        assertEquals("dd664d5e4016433a8cd69f005ae1480804351789b59de5af06276de65633d319", highlight.author())
    }

    @Test
    fun fallsBackToTheFirstPTagWhenNoAuthorMarkerIsPresent() {
        // Amethyst's own highlight publisher tags only the author, with no role marker.
        val highlight =
            HighlightEvent(
                id = "00",
                pubKey = "00",
                createdAt = 0,
                tags =
                    arrayOf(
                        arrayOf("a", "30023:eaa06714ac905aa5583860391e161edc7a815359b7c3e9b9b202c0558aefbeac:bitcoin-here-now"),
                        arrayOf("p", "eaa06714ac905aa5583860391e161edc7a815359b7c3e9b9b202c0558aefbeac"),
                    ),
                content = "a highlight of an article",
                sig = "00",
            )

        assertEquals("eaa06714ac905aa5583860391e161edc7a815359b7c3e9b9b202c0558aefbeac", highlight.author())
    }
}
