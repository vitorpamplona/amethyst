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

import com.halilibo.richtext.commonmark.CommonmarkAstNodeParser
import com.halilibo.richtext.markdown.node.AstNode
import com.vitorpamplona.amethyst.commons.richtext.MarkdownChunk
import com.vitorpamplona.amethyst.commons.richtext.MarkdownChunks
import com.vitorpamplona.quartz.utils.cache.ConcurrentLruCache

/**
 * Parsed markdown, shared by every viewer, so a document is parsed once rather than every time
 * it enters composition: a long article used to re-parse whenever it scrolled back into view.
 *
 * Keyed by the text, with the text re-checked on every hit, as in `CachedRichTextParser`: a
 * 32-bit hash is trivially collidable. Keyed by text rather than by event because the same body
 * arrives translated, cropped by "Show more" or split at its code blocks, and each of those is a
 * different document. The tree is never changed after parsing, so compositions share it.
 *
 * [warm] runs off the UI thread from the feed prefetcher, so the screen usually finds the
 * document already parsed. A miss still parses in place, as before, so nothing draws blank.
 */
object CachedMarkdownAst {
    private class Entry(
        val markdown: String,
        val ast: AstNode,
    )

    // Long articles are the documents worth keeping: a feed of notes re-parses cheaply.
    private val cache = ConcurrentLruCache<Int, Entry>(100)

    /** The parsed [markdown] if it is cached, without parsing it. */
    fun cachedOrNull(markdown: String): AstNode? = cache.get(markdown.hashCode())?.takeIf { it.markdown == markdown }?.ast

    fun parse(markdown: String): AstNode {
        cachedOrNull(markdown)?.let { return it }
        val key = markdown.hashCode()
        val ast = CommonmarkAstNodeParser(AmethystMarkdownParseOptions).parse(markdown)
        cache.put(key, Entry(markdown, ast))
        return ast
    }

    /**
     * Parses [content] the way a feed card's [RenderContentAsMarkdown] will: split at its code
     * blocks when it has any, each prose part a document of its own.
     */
    fun warm(content: String) {
        val chunks = MarkdownChunks.cachedSplit(content, atHeadings = false)
        if (chunks == null) {
            parse(content)
        } else {
            chunks.forEach { chunk ->
                if (chunk is MarkdownChunk.Prose && chunk.markdown.isNotBlank()) parse(chunk.markdown)
            }
        }
    }

    fun trimToSize(maxItems: Int) = cache.trimToSize(maxItems)
}
