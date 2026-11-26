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

/*
 * Nodes that only some formats have. Each still draws as its children (or its blocks) where a
 * renderer does not know it.
 */

/** Marked text (Djot `{=...=}`, GFM `==...==`). */
@Immutable
data class Highlight(
    override val children: List<Inline>,
) : InlineContainer

/** Inserted text (Djot `{+...+}`). */
@Immutable
data class Insert(
    override val children: List<Inline>,
) : InlineContainer

/** Superscript (Djot `^...^`, AsciiDoc `^...^`). */
@Immutable
data class Superscript(
    override val children: List<Inline>,
) : InlineContainer

/** Subscript (Djot `~...~`, AsciiDoc `~...~`). */
@Immutable
data class Subscript(
    override val children: List<Inline>,
) : InlineContainer

/**
 * Text with attributes and nothing else (Djot `[...]{.class}`). [classes] and [attributes] are the
 * extension point: a renderer registered for a class can draw it its own way.
 */
@Immutable
data class Span(
    override val children: List<Inline>,
    val classes: List<String> = emptyList(),
    val attributes: Map<String, String> = emptyMap(),
) : InlineContainer

/** A block with attributes (Djot `::: name`). A `{.spreadsheet}` block arrives as one of these. */
@Immutable
data class Div(
    override val blocks: List<Block>,
    val classes: List<String> = emptyList(),
    val attributes: Map<String, String> = emptyMap(),
) : BlockContainer

/** Terms and their definitions (Djot `: term`). */
@Immutable
data class DefinitionList(
    val items: List<Definition>,
) : Block

@Immutable
data class Definition(
    val term: List<Inline>,
    val definition: List<Block>,
)

/** A call to footnote [number] (`[^label]`), drawn as its number. */
@Immutable
data class FootnoteReference(
    val label: String,
    val number: Int,
) : Inline

/** The footnotes, in the order they are first called, gathered at the end of the document. */
@Immutable
data class Footnotes(
    val notes: List<Footnote>,
) : Block

@Immutable
data class Footnote(
    val label: String,
    val number: Int,
    val blocks: List<Block>,
)

/**
 * A callout: GitHub's `> [!NOTE]` alerts, AsciiDoc's `NOTE:` admonitions. [kind] is the lower-case
 * name (note, tip, important, warning, caution) and [title] the author's own title, if any.
 */
@Immutable
data class Callout(
    val kind: String,
    override val blocks: List<Block>,
    val title: List<Inline>? = null,
) : BlockContainer
