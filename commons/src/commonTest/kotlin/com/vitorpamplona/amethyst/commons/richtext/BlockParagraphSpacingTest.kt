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

import com.vitorpamplona.amethyst.commons.model.EmptyTagList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class BlockParagraphSpacingTest {
    private fun parse(text: String) = RichTextParser().parseText(text, EmptyTagList, null).paragraphs

    @Test
    fun blankLineAfterTrailingLinkPreviewIsDropped() {
        val paragraphs = parse("See if you can restore your follow list here: https://metadata.nostr.com/#\n\nSomething zeroed it out.")

        // The parser keeps the author's blank line as its own paragraph...
        assertEquals(3, paragraphs.size)
        assertTrue(paragraphs[0].words.last() is LinkSegment)
        assertTrue(paragraphs[1].isBlankLine())

        // ...which the renderer drops under the card.
        val rendered = dropBlankLineAfterBlocks(paragraphs, canPreview = true)
        assertEquals(2, rendered.size)
        assertSame(paragraphs[0], rendered[0])
        assertSame(paragraphs[2], rendered[1])
    }

    @Test
    fun blankLineAfterInlineLinkIsKeptWithoutPreviews() {
        val paragraphs = parse("here: https://metadata.nostr.com/\n\nSomething zeroed it out.")

        assertSame(paragraphs, dropBlankLineAfterBlocks(paragraphs, canPreview = false))
    }

    @Test
    fun blankLineAfterTrailingImageIsDropped() {
        val paragraphs = parse("look at this https://example.com/cat.jpg\n\nnice, right?")

        assertEquals(3, paragraphs.size)
        val rendered = dropBlankLineAfterBlocks(paragraphs, canPreview = false)
        assertEquals(listOf(paragraphs[0], paragraphs[2]), rendered)
    }

    @Test
    fun onlyOneBlankLineIsDropped() {
        val paragraphs = parse("here: https://metadata.nostr.com/\n\n\nSomething zeroed it out.")

        assertEquals(4, paragraphs.size)
        val rendered = dropBlankLineAfterBlocks(paragraphs, canPreview = true)
        assertEquals(listOf(paragraphs[0], paragraphs[2], paragraphs[3]), rendered)
    }

    @Test
    fun linkInTheMiddleOfALineKeepsTheBlankLine() {
        val paragraphs = parse("here: https://metadata.nostr.com/ is the site\n\nSomething zeroed it out.")

        assertSame(paragraphs, dropBlankLineAfterBlocks(paragraphs, canPreview = true))
    }

    @Test
    fun blankLineBeforeAPreviewIsKept() {
        val paragraphs = parse("Something zeroed it out.\n\nhttps://metadata.nostr.com/")

        assertSame(paragraphs, dropBlankLineAfterBlocks(paragraphs, canPreview = true))
    }
}
