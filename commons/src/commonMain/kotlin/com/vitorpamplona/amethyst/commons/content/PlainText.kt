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

/**
 * A document's words without its markup, its blocks joined by spaces: the preview a card shows for
 * an article that has no summary. The same for every format.
 */
object PlainText {
    fun of(document: Document): String {
        val out = StringBuilder()
        // Blocks are walked with a stack, not recursion: a hostile document may nest deeply.
        val stack = ArrayDeque<Any>()
        document.blocks.asReversed().forEach { stack.addLast(it) }
        while (stack.isNotEmpty()) {
            when (val node = stack.removeLast()) {
                is Paragraph -> part(out, node.inlines)
                is Heading -> part(out, node.inlines)
                is CodeBlock -> part(out, node.code)
                is Table -> {
                    node.header.forEach { part(out, it.inlines) }
                    node.rows.forEach { row -> row.forEach { part(out, it.inlines) } }
                }
                is ListBlock -> node.items.asReversed().forEach { item -> item.blocks.asReversed().forEach { stack.addLast(it) } }
                is DefinitionList ->
                    node.items.asReversed().forEach { definition ->
                        definition.definition.asReversed().forEach { stack.addLast(it) }
                        stack.addLast(Paragraph(definition.term))
                    }
                is Footnotes -> {}
                is BlockContainer -> node.blocks.asReversed().forEach { stack.addLast(it) }
            }
        }
        return out.toString().trim()
    }

    private fun part(
        out: StringBuilder,
        text: String,
    ) {
        val t = text.trim()
        if (t.isEmpty()) return
        if (out.isNotEmpty()) out.append(' ')
        out.append(t)
    }

    private fun part(
        out: StringBuilder,
        inlines: List<Inline>,
    ) = part(out, text(inlines))

    /** The words of [inlines], as they read. */
    fun text(inlines: List<Inline>): String = buildString { inlines.forEach { appendInline(it) } }

    private fun StringBuilder.appendInline(inline: Inline) {
        when (inline) {
            is Text -> append(inline.text)
            is Code -> append(inline.code)
            is Math -> append(inline.latex)
            is SoftBreak, is HardBreak -> append(' ')
            is Image -> append(inline.alt)
            is NostrInline -> append(inline.segment.segmentText)
            is FootnoteReference -> {}
            is InlineContainer -> inline.children.forEach { appendInline(it) }
        }
    }
}

/**
 * This document without a first heading that only repeats [title]: an article whose screen already
 * shows its title tag would otherwise show it twice, as articles often open with `# Title`.
 */
fun Document.withoutLeadingTitle(title: String?): Document {
    if (title.isNullOrBlank()) return this
    val first = blocks.firstOrNull() as? Heading ?: return this
    val words = PlainText.text(first.inlines)
    return if (sameWords(words, title)) Document(blocks.drop(1)) else this
}

private fun sameWords(
    a: String,
    b: String,
): Boolean =
    a
        .split(SPACES)
        .filter { it.isNotEmpty() }
        .joinToString(" ")
        .equals(b.split(SPACES).filter { it.isNotEmpty() }.joinToString(" "), ignoreCase = true)

private val SPACES = Regex("\\s+")
