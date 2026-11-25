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
package com.vitorpamplona.amethyst.commons.richtext

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.utils.EventFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TextRenderingTest {
    private fun event(kind: Int): Event = EventFactory.probe(kind)

    @Test
    fun markdownKindsAreAlwaysMarkdown() {
        listOf(30023, 30024, 1618, 1621, 30063, 30402, 54).forEach { kind ->
            assertEquals(TextRendering.MARKDOWN, TextRendering.of(event(kind)), "kind $kind")
        }
    }

    @Test
    fun plainTextKindsKeepTheGuess() {
        // Their NIPs ask for plain text, but Amethyst still draws the markdown people write in them.
        listOf(1, 14, 24, 1111).forEach { kind ->
            assertEquals(TextRendering.GUESS, TextRendering.of(event(kind)), "kind $kind")
        }
    }

    @Test
    fun otherFormatsAndUnnamedKindsKeepTheGuess() {
        // Djot (30818) and AsciiDoc (30041) are converted to markdown before a viewer gets them;
        // a patch (1617) and a reaction (7) name no format.
        listOf(30818, 30041, 1617, 7).forEach { kind ->
            assertEquals(TextRendering.GUESS, TextRendering.of(event(kind)), "kind $kind")
        }
        assertEquals(TextRendering.GUESS, TextRendering.of(null))
    }

    @Test
    fun anArticleWithoutMarkupIsStillMarkdown() {
        val prose = "Just a paragraph with no markup at all."
        assertFalse(TextRendering.GUESS.isMarkdown(prose))
        assertTrue(TextRendering.MARKDOWN.isMarkdown(prose))
        assertTrue(TextRendering.GUESS.isMarkdown("## A heading\n\nand text"))
    }

    @Test
    fun markdownTheGuessMisses() {
        // An indented code block and a reference-style link: markdown the guess does not look for,
        // so a note with only these renders as plain text while an article renders the markdown.
        val md = "Run this:\n\n    ./gradlew test\n\nThen read the [guide][1].\n\n[1]: https://example.com/guide"
        assertFalse(TextRendering.GUESS.isMarkdown(md))
        assertTrue(TextRendering.MARKDOWN.isMarkdown(md))
    }
}
