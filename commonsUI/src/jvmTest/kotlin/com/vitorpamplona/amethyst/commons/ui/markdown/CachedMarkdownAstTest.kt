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
package com.vitorpamplona.amethyst.commons.ui.markdown

import com.vitorpamplona.amethyst.commons.richtext.MarkdownChunk
import com.vitorpamplona.amethyst.commons.richtext.MarkdownChunks
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class CachedMarkdownAstTest {
    @Test
    fun aDocumentIsParsedOnce() {
        val md = "# Title\n\nSome *emphasis* and a [link](https://example.com)."
        assertSame(CachedMarkdownAst.parse(md), CachedMarkdownAst.parse(md))
    }

    @Test
    fun aHashCollisionIsNotServedAsAHit() {
        // "Aa" and "BB" share a String.hashCode().
        val a = CachedMarkdownAst.parse("Aa")
        val b = CachedMarkdownAst.parse("BB")
        assertNotSame(a, b)
        assertSame(a, CachedMarkdownAst.parse("Aa"))
    }

    @Test
    fun warmParsesTheProseAroundCodeBlocksAsTheCardWill() {
        val md = "Intro to warm.\n\n```kotlin\nval x = 1\n```\n\nOutro after warm."
        val prose =
            assertNotNull(MarkdownChunks.cachedSplit(md, atHeadings = false))
                .filterIsInstance<MarkdownChunk.Prose>()
                .map { it.markdown }
                .filter { it.isNotBlank() }
        assertTrue(prose.size >= 2)
        prose.forEach { assertNull(CachedMarkdownAst.cachedOrNull(it)) }

        CachedMarkdownAst.warm(md)

        prose.forEach { assertNotNull(CachedMarkdownAst.cachedOrNull(it), "not warmed: $it") }
    }

    @Test
    fun warmParsesADocumentWithoutCodeWhole() {
        val md = "A note to warm, with **bold** text."
        assertNull(CachedMarkdownAst.cachedOrNull(md))
        CachedMarkdownAst.warm(md)
        assertNotNull(CachedMarkdownAst.cachedOrNull(md))
    }
}
