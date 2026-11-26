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
package com.vitorpamplona.amethyst.commons.content

import androidx.compose.runtime.Immutable
import com.vitorpamplona.amethyst.commons.richtext.Segment

/**
 * A body of text parsed once, whatever its format: Markdown, Djot or AsciiDoc all land here, and one
 * renderer draws them. See `commons/plans/2026-11-25-content-parsing.md`.
 *
 * [Block] and [Inline] are open: the core nodes in this file are shared by every format, and a
 * format adds the nodes only it has (`FormatNodes.kt`). A renderer that does not know a node draws
 * its children, or its text, so a parser can add a node before any UI draws it.
 *
 * Nodes are immutable and hold no UI: the model is headless, like the rest of `commons`, and a
 * parsed document is shared between compositions and threads.
 */
@Immutable
data class Document(
    val blocks: List<Block>,
) {
    companion object {
        val EMPTY = Document(emptyList())

        /**
         * The deepest a document nests, blocks and inlines together. Text comes from strangers, and a
         * note of 20,000 `>` would overflow the stack of every recursive walk, the renderer's
         * included: past this depth the mappers keep only the words.
         */
        const val MAX_DEPTH = 32
    }
}

/** A block: a paragraph, a heading, a list, ... */
interface Block

/** A piece of a block's text: a run of text, emphasis, a link, a Nostr reference, ... */
interface Inline

/** Nodes that hold other inlines. A renderer that does not know one draws [children]. */
interface InlineContainer : Inline {
    val children: List<Inline>
}

/** Nodes that hold other blocks. A renderer that does not know one draws [blocks]. */
interface BlockContainer : Block {
    val blocks: List<Block>
}

// ---- Core blocks ------------------------------------------------------------------------------

@Immutable
data class Paragraph(
    val inlines: List<Inline>,
) : Block

/** [level] is 1 to 6. [anchor] is the id a link (`#anchor`) can jump to, when the format gives one. */
@Immutable
data class Heading(
    val level: Int,
    val inlines: List<Inline>,
    val anchor: String? = null,
) : Block

/**
 * A bullet list, or a numbered one starting at [start]. A tight list draws its items without the
 * space between paragraphs.
 */
@Immutable
data class ListBlock(
    val items: List<ListItem>,
    val ordered: Boolean = false,
    val start: Int = 1,
    val tight: Boolean = true,
) : Block

/** One item. [checked] is null for an ordinary item, true or false for a task list's item. */
@Immutable
data class ListItem(
    val blocks: List<Block>,
    val checked: Boolean? = null,
)

@Immutable
data class Quote(
    override val blocks: List<Block>,
) : BlockContainer

/** Code, drawn as written. [language] names its syntax, when the author gave one. */
@Immutable
data class CodeBlock(
    val code: String,
    val language: String? = null,
) : Block

@Immutable
data class Table(
    val header: List<TableCell>,
    val rows: List<List<TableCell>>,
    val alignments: List<ColumnAlignment>,
    val caption: List<Inline>? = null,
) : Block

@Immutable
data class TableCell(
    val inlines: List<Inline>,
)

enum class ColumnAlignment { NONE, LEFT, CENTER, RIGHT }

@Immutable
data object ThematicBreak : Block

/** A displayed equation, in LaTeX. */
@Immutable
data class MathBlock(
    val latex: String,
) : Block

// ---- Core inlines -----------------------------------------------------------------------------

@Immutable
data class Text(
    val text: String,
) : Inline

@Immutable
data class Emphasis(
    override val children: List<Inline>,
) : InlineContainer

@Immutable
data class Strong(
    override val children: List<Inline>,
) : InlineContainer

@Immutable
data class Strikethrough(
    override val children: List<Inline>,
) : InlineContainer

@Immutable
data class Code(
    val code: String,
) : Inline

@Immutable
data class Link(
    val destination: String,
    override val children: List<Inline>,
    val title: String? = null,
) : InlineContainer

/** An image the author placed in the text. [alt] describes it. */
@Immutable
data class Image(
    val url: String,
    val alt: String,
    val title: String? = null,
) : Inline

/** A line break in the source that flows as a space. */
@Immutable
data object SoftBreak : Inline

/** A line break that stays one. */
@Immutable
data object HardBreak : Inline

/** An equation in the text, in LaTeX. [display] sets it on a line of its own. */
@Immutable
data class Math(
    val latex: String,
    val display: Boolean = false,
) : Inline

/**
 * What Nostr text carries in any format: a `nostr:` reference, a mention, a hashtag, a custom emoji,
 * a URL or a media link, an invoice, a Cashu token, ... as [com.vitorpamplona.amethyst.commons.richtext.RichTextParser]
 * classified it. The renderer draws it the way a note draws the same segment, so an article and a
 * note agree on every one of them.
 */
@Immutable
data class NostrInline(
    val segment: Segment,
) : Inline
