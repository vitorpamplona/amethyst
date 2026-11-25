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

import com.vitorpamplona.quartz.kinds.ContentFormat
import com.vitorpamplona.quartz.kinds.ContentFormats
import com.vitorpamplona.quartz.nip01Core.core.Event

/**
 * How a viewer draws a body of text: always as markdown, or as markdown only when
 * [CachedRichTextParser.isMarkdown] finds markdown in it.
 *
 * [of] is Amethyst's rule over the format a kind's NIP names ([ContentFormats]):
 * - A kind whose NIP says Markdown (NIP-23 articles, NIP-34 issues and pull requests, NIP-99
 *   classifieds, …) always renders as markdown. Nothing is guessed.
 * - The plain-text kinds keep the guess: notes (1), DMs (14), public messages (24) and comments
 *   (1111). Their NIPs ask for plain text, but people write markdown in them and Amethyst has
 *   always drawn it (maintainer, 2026-11-25).
 * - Djot and AsciiDoc bodies are converted to markdown before they reach a viewer (`Wiki.kt`, the
 *   publication screens). A raw one that reaches the viewer anyway keeps the guess.
 * - A kind whose NIP names no format keeps the guess.
 *
 * The plan for replacing the guess with a parser per format is
 * `commons/plans/2026-11-25-content-parsing.md`.
 */
enum class TextRendering {
    /** Markdown when [CachedRichTextParser.isMarkdown] finds it, Nostr text otherwise. */
    GUESS,

    /** Always markdown. */
    MARKDOWN,
    ;

    /** Whether [content] renders as markdown under this rule. */
    fun isMarkdown(content: String): Boolean = this == MARKDOWN || CachedRichTextParser.isMarkdown(content)

    companion object {
        fun of(event: Event?): TextRendering =
            when (event?.let { ContentFormats.of(it) }) {
                ContentFormat.MARKDOWN -> MARKDOWN
                else -> GUESS
            }
    }
}
