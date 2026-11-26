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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MarkdownChunksTest {
    @Test
    fun highlightableCode() {
        assertTrue(MarkdownChunks.isHighlightable(null, "  {\"a\": 1}"))
        assertTrue(MarkdownChunks.isHighlightable("yaml", "a: b"))
        assertTrue(MarkdownChunks.isHighlightable("kotlin", "{"))
        assertTrue(!MarkdownChunks.isHighlightable("brainfuck", "{"))
        assertTrue(!MarkdownChunks.isHighlightable(null, "plain"))
    }

    @Test
    fun setextHeadingsAndHeadingsInCode() {
        val md = "Encryption\n----------\n\nText.\n\n```\n# not a heading\n```"
        val headings = MarkdownChunks.headings(md)
        assertEquals(listOf("Encryption"), headings.map { it.text })
        assertEquals("encryption", headings[0].anchor)
    }

    @Test
    fun anchorsMatchGithub() {
        assertEquals("encryption", MarkdownChunks.anchorOf("Encryption"))
        assertEquals("nip-44-v2-encryption", MarkdownChunks.anchorOf("NIP-44 v2: Encryption"))
        assertEquals("kinds", MarkdownChunks.anchorOf("Kinds"))
    }
}
