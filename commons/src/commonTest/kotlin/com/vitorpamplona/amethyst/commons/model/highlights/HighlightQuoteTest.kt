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
package com.vitorpamplona.amethyst.commons.model.highlights

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HighlightQuoteTest {
    @Test
    fun marksTheQuoteInsideItsContext() {
        val quote = HighlightQuote.of("the merge happens slowly", "We think the merge happens slowly. It does.")

        assertEquals("We think the merge happens slowly. It does.", quote.text)
        assertEquals(9 until 33, quote.marked)
        assertEquals("the merge happens slowly", quote.text.substring(quote.marked!!))
    }

    @Test
    fun marksEverythingWhenThereIsNoContext() {
        val quote = HighlightQuote.of("a bare quote", null)

        assertEquals("a bare quote", quote.text)
        assertNull(quote.marked)
    }

    @Test
    fun dropsContextThatDoesNotContainTheQuote() {
        val quote = HighlightQuote.of("not in here", "some entirely different sentence")

        assertEquals("not in here", quote.text)
        assertNull(quote.marked)
    }

    /** The old renderer used String.replace, which marked every occurrence at once. */
    @Test
    fun marksOnlyOneOccurrenceOfARepeatedQuote() {
        val quote = HighlightQuote.of("freedom", "freedom begets freedom")

        assertEquals(0 until 7, quote.marked)
    }

    @Test
    fun usesTheSelectorPrefixToPickTheRightOccurrence() {
        val quote = HighlightQuote.of("freedom", "freedom begets freedom", prefix = "freedom begets ")

        assertEquals(15 until 22, quote.marked)
        assertEquals("freedom", quote.text.substring(quote.marked!!))
    }

    @Test
    fun fallsBackToTheFirstOccurrenceWhenThePrefixMatchesNothing() {
        val quote = HighlightQuote.of("freedom", "freedom begets freedom", prefix = "nowhere in the text")

        assertEquals(0 until 7, quote.marked)
    }

    @Test
    fun handlesAQuoteSpanningNewlines() {
        val context = "First line here.\nSecond line here."
        val quote = HighlightQuote.of("here.\nSecond", context)

        assertEquals(context, quote.text)
        assertEquals("here.\nSecond", quote.text.substring(quote.marked!!))
    }

    @Test
    fun handlesAnEmptyHighlight() {
        val quote = HighlightQuote.of("", "some context")

        assertEquals("some context", quote.text)
        assertNull(quote.marked)
    }
}
