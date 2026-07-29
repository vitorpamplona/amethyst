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
package com.vitorpamplona.quartz.nip84Highlights.parse

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SharedHighlightParserTest {
    @Test
    fun emptyInputYieldsEmptyResult() {
        val result = SharedHighlightParser.parse("   ")
        assertTrue(result.isEmpty())
        assertNull(result.quote)
        assertNull(result.url)
    }

    @Test
    fun selectionOnly() {
        val result = SharedHighlightParser.parse("Nostr is a simple, open protocol.")
        assertEquals("Nostr is a simple, open protocol.", result.quote)
        assertNull(result.url)
        assertTrue(result.isNotEmpty())
    }

    @Test
    fun urlOnly() {
        val result = SharedHighlightParser.parse("https://example.com/post")
        assertNull(result.quote)
        assertEquals("https://example.com/post", result.url)
    }

    @Test
    fun selectionThenUrlOnSeparateLines() {
        val result =
            SharedHighlightParser.parse("Nostr is a simple, open protocol.\n\nhttps://example.com/post")
        assertEquals("Nostr is a simple, open protocol.", result.quote)
        assertEquals("https://example.com/post", result.url)
    }

    @Test
    fun stripsWrappingQuotesFromSelection() {
        val result = SharedHighlightParser.parse("\"Nostr is great\" https://example.com/post")
        assertEquals("Nostr is great", result.quote)
        assertEquals("https://example.com/post", result.url)
    }

    @Test
    fun stripsCurlyQuotesAndGuillemets() {
        assertEquals("inside", SharedHighlightParser.parse("“inside”").quote)
        assertEquals("inside", SharedHighlightParser.parse("«inside»").quote)
    }

    @Test
    fun cleansTrackersFromSharedUrl() {
        val result =
            SharedHighlightParser.parse("Some quote\n\nhttps://example.com/post?utm_source=twitter&id=9")
        assertEquals("Some quote", result.quote)
        assertEquals("https://example.com/post?id=9", result.url)
    }

    @Test
    fun linkToHighlightWithoutSeparateSelectionUsesFragmentText() {
        val result =
            SharedHighlightParser.parse(
                "https://example.com/post#:~:text=the%20-,highlighted%20passage,-and%20on",
            )
        assertEquals("highlighted passage", result.quote)
        assertEquals("https://example.com/post", result.url)
        assertEquals("the ", result.prefix)
        assertEquals("and on", result.suffix)
        assertTrue(result.hasSelector())
    }

    @Test
    fun explicitSelectionWinsOverFragmentTextButKeepsAnchors() {
        // The browser shared the exact selection AND a link-to-highlight; keep the readable
        // selection as the passage but retain the prefix/suffix anchors from the fragment.
        val result =
            SharedHighlightParser.parse(
                "The full readable passage.\n\nhttps://example.com/post#:~:text=before-,The%20full,-after",
            )
        assertEquals("The full readable passage.", result.quote)
        assertEquals("https://example.com/post", result.url)
        assertEquals("before", result.prefix)
        assertEquals("after", result.suffix)
    }

    @Test
    fun trailingSentencePunctuationNotSwallowedIntoUrl() {
        val result = SharedHighlightParser.parse("See (https://example.com/post).")
        assertEquals("https://example.com/post", result.url)
    }

    @Test
    fun keepsBalancedTrailingParenInUrl() {
        // A Wikipedia article whose slug ends in "(planet)" — the closing paren is part of
        // the URL, not sentence punctuation.
        val result =
            SharedHighlightParser.parse("Mercury is small.\n\nhttps://en.wikipedia.org/wiki/Mercury_(planet)")
        assertEquals("https://en.wikipedia.org/wiki/Mercury_(planet)", result.url)
    }

    @Test
    fun stripsOnlyTheWrappingParenNotTheSlugParen() {
        val result =
            SharedHighlightParser.parse("(https://en.wikipedia.org/wiki/Mercury_(planet))")
        assertEquals("https://en.wikipedia.org/wiki/Mercury_(planet)", result.url)
    }

    @Test
    fun dropsTheOpenParenOrphanedByTheUrlTrim() {
        // The wrapping ")" leaves with the URL in trimUrlEnd; the "(" it opened must not be
        // left dangling as the tail of the published passage.
        val result = SharedHighlightParser.parse("See this quote (https://example.com/article)")
        assertEquals("https://example.com/article", result.url)
        assertEquals("See this quote", result.quote)
    }

    @Test
    fun keepsAMatchedBracketPairInThePassage() {
        // Nothing was orphaned here, so the passage keeps its own parenthetical intact.
        val result = SharedHighlightParser.parse("He said (see below) https://example.com/a")
        assertEquals("https://example.com/a", result.url)
        assertEquals("He said (see below)", result.quote)
    }

    @Test
    fun keepsTheSlugParenCaseQuoteIntact() {
        // Regression guard for the opposite direction: nothing was trimmed off this URL, so
        // the passage must be untouched too.
        val result =
            SharedHighlightParser.parse("Mercury is small.\n\nhttps://en.wikipedia.org/wiki/Mercury_(planet)")
        assertEquals("https://en.wikipedia.org/wiki/Mercury_(planet)", result.url)
        assertEquals("Mercury is small.", result.quote)
    }

    @Test
    fun urlInsideSelectionStaysWithQuoteWhenSourceAppended() {
        // The last URL is treated as the source; an earlier URL inside the passage is kept.
        val result =
            SharedHighlightParser.parse("Visit https://inside.example first.\n\nhttps://source.example/a")
        assertEquals("Visit https://inside.example first.", result.quote)
        assertEquals("https://source.example/a", result.url)
    }

    @Test
    fun noSelectorWhenPlainUrl() {
        val result = SharedHighlightParser.parse("quote\n\nhttps://example.com")
        assertFalse(result.hasSelector())
        assertNull(result.prefix)
        assertNull(result.suffix)
    }
}
