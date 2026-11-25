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
 * A compact, one-line-per-block text form of a [Document], for tests, the parity harness and
 * debugging: `P["Some " em["em"] " and " strong["strong"]]`. A Nostr inline shows its segment's
 * class and text: `nostr:HashTagSegment("#tag")`.
 */
object DocumentDump {
    fun dump(document: Document): String = buildString { document.blocks.forEach { block(it, 0, this) } }

    private fun block(
        b: Block,
        depth: Int,
        out: StringBuilder,
    ) {
        out.append("  ".repeat(depth))
        when (b) {
            is Paragraph -> out.append("P").append(inlines(b.inlines)).append('\n')
            is Heading ->
                out
                    .append("H")
                    .append(b.level)
                    .append(inlines(b.inlines))
                    .append('\n')
            is ListBlock -> {
                out.append(if (b.ordered) "OL(${b.start})" else "UL").append(if (b.tight) "" else " loose").append('\n')
                b.items.forEach { item ->
                    out.append("  ".repeat(depth + 1)).append("- ")
                    if (item.checked != null) out.append(if (item.checked) "[x] " else "[ ] ")
                    out.append('\n')
                    item.blocks.forEach { block(it, depth + 2, out) }
                }
            }
            is Quote -> {
                out.append("QUOTE\n")
                b.blocks.forEach { block(it, depth + 1, out) }
            }
            is Callout -> {
                out
                    .append("CALLOUT(")
                    .append(b.kind)
                    .append(")")
                    .append(b.title?.let { inlines(it) } ?: "")
                    .append('\n')
                b.blocks.forEach { block(it, depth + 1, out) }
            }
            is Div -> {
                out.append("DIV").append(b.classes).append('\n')
                b.blocks.forEach { block(it, depth + 1, out) }
            }
            is CodeBlock ->
                out
                    .append("CODE(")
                    .append(b.language ?: "")
                    .append(")")
                    .append(quote(b.code))
                    .append('\n')
            is MathBlock -> out.append("MATH").append(quote(b.latex)).append('\n')
            is ThematicBreak -> out.append("HR\n")
            is Table -> {
                out.append("TABLE").append(b.alignments.map { it.name.first() }).append('\n')
                out
                    .append("  ".repeat(depth + 1))
                    .append("| ")
                    .append(b.header.joinToString(" | ") { inlines(it.inlines) })
                    .append('\n')
                b.rows.forEach { row ->
                    out
                        .append("  ".repeat(depth + 1))
                        .append("| ")
                        .append(row.joinToString(" | ") { inlines(it.inlines) })
                        .append('\n')
                }
                b.caption?.let {
                    out
                        .append("  ".repeat(depth + 1))
                        .append("caption")
                        .append(inlines(it))
                        .append('\n')
                }
            }
            is DefinitionList -> {
                out.append("DL\n")
                b.items.forEach { d ->
                    out
                        .append("  ".repeat(depth + 1))
                        .append("term")
                        .append(inlines(d.term))
                        .append('\n')
                    d.definition.forEach { block(it, depth + 2, out) }
                }
            }
            is Footnotes -> {
                out.append("FOOTNOTES\n")
                b.notes.forEach { n ->
                    out
                        .append("  ".repeat(depth + 1))
                        .append(n.number)
                        .append(". [")
                        .append(n.label)
                        .append("]\n")
                    n.blocks.forEach { block(it, depth + 2, out) }
                }
            }
            is BlockContainer -> {
                out.append(b::class.simpleName).append('\n')
                b.blocks.forEach { block(it, depth + 1, out) }
            }
            else -> out.append(b::class.simpleName).append('\n')
        }
    }

    fun inlines(list: List<Inline>): String = list.joinToString(" ", "[", "]") { inline(it) }

    private fun inline(i: Inline): String =
        when (i) {
            is Text -> quote(i.text)
            is Emphasis -> "em" + inlines(i.children)
            is Strong -> "strong" + inlines(i.children)
            is Strikethrough -> "del" + inlines(i.children)
            is Highlight -> "mark" + inlines(i.children)
            is Insert -> "ins" + inlines(i.children)
            is Superscript -> "sup" + inlines(i.children)
            is Subscript -> "sub" + inlines(i.children)
            is Span -> "span" + i.classes + inlines(i.children)
            is Code -> "code" + quote(i.code)
            is Link -> "link(" + i.destination + (i.title?.let { " " + quote(it) } ?: "") + ")" + inlines(i.children)
            is Image -> "img(" + i.url + ")" + quote(i.alt)
            is SoftBreak -> "SB"
            is HardBreak -> "BR"
            is Math -> "math" + quote(i.latex)
            is FootnoteReference -> "fn(" + i.number + ")"
            is NostrInline -> "nostr:" + i.segment::class.simpleName + "(" + quote(i.segment.segmentText) + ")"
            is InlineContainer -> (i::class.simpleName ?: "?") + inlines(i.children)
            else -> i::class.simpleName ?: "?"
        }

    private fun quote(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""
}
