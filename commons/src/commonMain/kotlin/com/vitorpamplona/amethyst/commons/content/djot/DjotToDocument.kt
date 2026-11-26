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
package com.vitorpamplona.amethyst.commons.content.djot

import com.vitorpamplona.amethyst.commons.content.Block
import com.vitorpamplona.amethyst.commons.content.Code
import com.vitorpamplona.amethyst.commons.content.CodeBlock
import com.vitorpamplona.amethyst.commons.content.ColumnAlignment
import com.vitorpamplona.amethyst.commons.content.Definition
import com.vitorpamplona.amethyst.commons.content.DefinitionList
import com.vitorpamplona.amethyst.commons.content.Div
import com.vitorpamplona.amethyst.commons.content.Document
import com.vitorpamplona.amethyst.commons.content.Emphasis
import com.vitorpamplona.amethyst.commons.content.Footnote
import com.vitorpamplona.amethyst.commons.content.FootnoteReference
import com.vitorpamplona.amethyst.commons.content.Footnotes
import com.vitorpamplona.amethyst.commons.content.HardBreak
import com.vitorpamplona.amethyst.commons.content.Heading
import com.vitorpamplona.amethyst.commons.content.Highlight
import com.vitorpamplona.amethyst.commons.content.Image
import com.vitorpamplona.amethyst.commons.content.Inline
import com.vitorpamplona.amethyst.commons.content.Insert
import com.vitorpamplona.amethyst.commons.content.Link
import com.vitorpamplona.amethyst.commons.content.ListBlock
import com.vitorpamplona.amethyst.commons.content.ListItem
import com.vitorpamplona.amethyst.commons.content.Math
import com.vitorpamplona.amethyst.commons.content.NostrInlinePass
import com.vitorpamplona.amethyst.commons.content.Paragraph
import com.vitorpamplona.amethyst.commons.content.Quote
import com.vitorpamplona.amethyst.commons.content.SoftBreak
import com.vitorpamplona.amethyst.commons.content.Span
import com.vitorpamplona.amethyst.commons.content.Strikethrough
import com.vitorpamplona.amethyst.commons.content.Strong
import com.vitorpamplona.amethyst.commons.content.Subscript
import com.vitorpamplona.amethyst.commons.content.Superscript
import com.vitorpamplona.amethyst.commons.content.Table
import com.vitorpamplona.amethyst.commons.content.TableCell
import com.vitorpamplona.amethyst.commons.content.Text
import com.vitorpamplona.amethyst.commons.content.ThematicBreak
import com.vitorpamplona.amethyst.commons.richtext.MarkdownChunks
import com.vitorpamplona.djot.AstNode
import com.vitorpamplona.djot.BlockQuote
import com.vitorpamplona.djot.BulletList
import com.vitorpamplona.djot.Caption
import com.vitorpamplona.djot.Cell
import com.vitorpamplona.djot.Delete
import com.vitorpamplona.djot.DisplayMath
import com.vitorpamplona.djot.Doc
import com.vitorpamplona.djot.DoubleQuoted
import com.vitorpamplona.djot.Email
import com.vitorpamplona.djot.Emph
import com.vitorpamplona.djot.InlineMath
import com.vitorpamplona.djot.Mark
import com.vitorpamplona.djot.NonBreakingSpace
import com.vitorpamplona.djot.OrderedList
import com.vitorpamplona.djot.Para
import com.vitorpamplona.djot.ParseOptions
import com.vitorpamplona.djot.RawBlock
import com.vitorpamplona.djot.RawInline
import com.vitorpamplona.djot.Row
import com.vitorpamplona.djot.Section
import com.vitorpamplona.djot.SingleQuoted
import com.vitorpamplona.djot.SmartPunctuation
import com.vitorpamplona.djot.Str
import com.vitorpamplona.djot.Symb
import com.vitorpamplona.djot.TaskList
import com.vitorpamplona.djot.Term
import com.vitorpamplona.djot.Url
import com.vitorpamplona.djot.Verbatim
import com.vitorpamplona.djot.WarningHandler
import com.vitorpamplona.djot.getStringContent
import com.vitorpamplona.djot.parse
import com.vitorpamplona.djot.CodeBlock as DjotCodeBlock
import com.vitorpamplona.djot.Definition as DjotDefinition
import com.vitorpamplona.djot.DefinitionList as DjotDefinitionList
import com.vitorpamplona.djot.Div as DjotDiv
import com.vitorpamplona.djot.FootnoteReference as DjotFootnoteReference
import com.vitorpamplona.djot.HardBreak as DjotHardBreak
import com.vitorpamplona.djot.Heading as DjotHeading
import com.vitorpamplona.djot.Image as DjotImage
import com.vitorpamplona.djot.Inline as DjotInline
import com.vitorpamplona.djot.Insert as DjotInsert
import com.vitorpamplona.djot.Link as DjotLink
import com.vitorpamplona.djot.ListItem as DjotListItem
import com.vitorpamplona.djot.SoftBreak as DjotSoftBreak
import com.vitorpamplona.djot.Span as DjotSpan
import com.vitorpamplona.djot.Strong as DjotStrong
import com.vitorpamplona.djot.Subscript as DjotSubscript
import com.vitorpamplona.djot.Superscript as DjotSuperscript
import com.vitorpamplona.djot.Table as DjotTable
import com.vitorpamplona.djot.ThematicBreak as DjotThematicBreak

/**
 * [Djot](https://djot.net/) into a [Document], with djot-kmp's parser: NIP-54 wiki articles.
 *
 * Everything Djot has keeps its meaning: highlight, insert, super/subscript, spans and divs with
 * their classes and attributes, definition lists, footnotes (gathered at the end, numbered in the
 * order they are first called). Text goes through [NostrInlinePass], so mentions, hashtags, media
 * and a Djot `:alias:` symbol that names a custom emoji come out as in a note.
 *
 * NIP-54's two rules: a link may target a `nostr:` URI (it simply does), and a reference link with
 * no definition is a wikilink, which [resolveWikilink] turns into a destination, or null to keep
 * its words.
 */
class DjotToDocument(
    private val nostr: NostrInlinePass = NostrInlinePass.NONE,
    private val resolveWikilink: (String) -> String? = { null },
) {
    fun parse(djot: String): Document {
        val doc = parse(djot, ParseOptions(warn = NO_WARNINGS))
        return Converter(doc).run()
    }

    private inner class Converter(
        val doc: Doc,
    ) {
        /** Footnote labels, in the order first called: a note's number is its index + 1. */
        val footnoteOrder = mutableListOf<String>()

        fun run(): Document {
            val blocks = blocks(doc.children).toMutableList()
            // Notes may call others, which then join the list: loop until no new one appears.
            val notes = ArrayList<Footnote>()
            var i = 0
            while (i < footnoteOrder.size) {
                val label = footnoteOrder[i]
                val note = doc.footnotes[label]
                notes.add(Footnote(label, i + 1, if (note != null) blocks(note.children) else emptyList()))
                i++
            }
            if (notes.isNotEmpty()) blocks.add(Footnotes(notes))
            return Document(blocks)
        }

        /** Blocks and inlines entered and not yet left: past [Document.MAX_DEPTH], only words. */
        var depth = 0

        fun blocks(nodes: List<AstNode>): List<Block> {
            if (depth >= Document.MAX_DEPTH) return flatText(nodes).let { if (it.isEmpty()) emptyList() else listOf(Paragraph(listOf(Text(it)))) }
            depth++
            try {
                return blockRun(nodes)
            } finally {
                depth--
            }
        }

        /** What [nodes] say, without their markup; djot-kmp walks them with a stack, not recursion. */
        fun flatText(nodes: List<AstNode>): String = WHITESPACE.replace(nodes.joinToString(" ") { getStringContent(it) }, " ").trim()

        private fun blockRun(nodes: List<AstNode>): List<Block> {
            val out = ArrayList<Block>()
            nodes.forEach { node ->
                when (node) {
                    // A section is the heading and what follows it: its blocks, in place.
                    is Section -> out.addAll(blocks(node.children))
                    else -> block(node)?.let { out.add(it) }
                }
            }
            return out
        }

        fun block(node: AstNode): Block? =
            when (node) {
                is Para -> inlines(node.children).takeIf { it.isNotEmpty() }?.let { Paragraph(it) }
                is DjotHeading ->
                    Heading(
                        level = node.level.coerceIn(1, 6),
                        inlines = inlines(node.children),
                        anchor = MarkdownChunks.anchorOf(getStringContent(node)),
                    )
                is DjotThematicBreak -> ThematicBreak
                is DjotDiv -> Div(blocks(node.children), classes(node), attributes(node))
                is BlockQuote -> Quote(blocks(node.children))
                // `{.json}` before a fence is how some authors name its language in Djot.
                is DjotCodeBlock -> CodeBlock(node.text.removeSuffix("\n"), node.lang ?: classes(node).singleOrNull())
                is RawBlock -> null
                is BulletList -> ListBlock(node.children.map { item(it) }, ordered = false, tight = node.tight)
                is OrderedList -> ListBlock(node.children.map { item(it) }, ordered = true, start = (node.start ?: 1L).toInt(), tight = node.tight)
                is TaskList -> ListBlock(node.children.map { ListItem(blocks(it.children), checked = it.checkbox == "checked") }, ordered = false, tight = node.tight)
                is DjotDefinitionList ->
                    DefinitionList(
                        node.children.map { item ->
                            val term = item.children.filterIsInstance<Term>().firstOrNull()
                            val definition = item.children.filterIsInstance<DjotDefinition>().firstOrNull()
                            Definition(term?.let { inlines(it.children) } ?: emptyList(), definition?.let { blocks(it.children) } ?: emptyList())
                        },
                    )
                is DjotTable -> table(node)
                else -> null
            }

        fun item(node: DjotListItem) = ListItem(blocks(node.children))

        fun table(table: DjotTable): Block? {
            val rows = table.children.filterIsInstance<Row>()
            if (rows.isEmpty()) return null
            val headerRow = rows.firstOrNull { it.head }
            val body = rows.filter { it !== headerRow }
            val widest = (headerRow ?: rows.first()).children

            fun cells(row: Row) = row.children.map { TableCell(inlines(it.children)) }
            val alignments = widest.map { alignment(it) }
            val caption =
                table.children
                    .filterIsInstance<Caption>()
                    .firstOrNull()
                    ?.takeIf { it.children.isNotEmpty() }
                    ?.let { inlines(it.children) }
            return Table(headerRow?.let { cells(it) } ?: emptyList(), body.map { cells(it) }, alignments, caption)
        }

        fun alignment(cell: Cell) =
            when (cell.align) {
                "left" -> ColumnAlignment.LEFT
                "right" -> ColumnAlignment.RIGHT
                "center" -> ColumnAlignment.CENTER
                else -> ColumnAlignment.NONE
            }

        // ---- Inlines ----------------------------------------------------------------------------

        /** [inLink] is true inside a link's text: it is a link already, so nothing is detected again. */
        fun inlines(
            nodes: List<DjotInline>,
            inLink: Boolean = false,
        ): List<Inline> {
            if (depth >= Document.MAX_DEPTH) return flatText(nodes).let { if (it.isEmpty()) emptyList() else listOf(Text(it)) }
            depth++
            try {
                return inlineRun(nodes, inLink)
            } finally {
                depth--
            }
        }

        private fun inlineRun(
            nodes: List<DjotInline>,
            inLink: Boolean,
        ): List<Inline> {
            val out = ArrayList<Inline>()
            val pending = StringBuilder()

            fun flush() {
                if (pending.isEmpty()) return
                if (inLink) out.add(Text(pending.toString())) else out.addAll(nostr.inlines(pending.toString()))
                pending.setLength(0)
            }

            fun add(inline: Inline) {
                flush()
                out.add(inline)
            }

            nodes.forEach { node ->
                when (node) {
                    is Str -> pending.append(node.text)
                    is NonBreakingSpace -> pending.append('\u00A0')
                    is SmartPunctuation -> pending.append(smart(node.type, node.text))
                    // `:alias:`: a custom emoji when the event names one, the alias as written otherwise.
                    is Symb -> pending.append(':').append(node.alias).append(':')
                    is DjotSoftBreak -> add(SoftBreak)
                    is DjotHardBreak -> add(HardBreak)
                    is Verbatim -> add(Code(node.text))
                    is RawInline -> {}
                    is InlineMath -> add(Math(node.text))
                    is DisplayMath -> add(Math(node.text, display = true))
                    is Url -> add(if (inLink) Text(node.text) else Link(node.text, listOf(Text(node.text))))
                    is Email -> add(if (inLink) Text(node.text) else Link("mailto:" + node.text, listOf(Text(node.text))))
                    is DjotFootnoteReference -> add(FootnoteReference(node.text, footnoteNumber(node.text)))
                    is Emph -> add(Emphasis(inlines(node.children, inLink)))
                    is DjotStrong -> add(Strong(inlines(unwrapSame(node), inLink)))
                    is Delete -> add(Strikethrough(inlines(node.children, inLink)))
                    is Mark -> add(Highlight(inlines(node.children, inLink)))
                    is DjotInsert -> add(Insert(inlines(node.children, inLink)))
                    is DjotSuperscript -> add(Superscript(inlines(node.children, inLink)))
                    is DjotSubscript -> add(Subscript(inlines(node.children, inLink)))
                    is DjotSpan -> add(Span(inlines(node.children, inLink), classes(node), attributes(node)))
                    is DoubleQuoted -> {
                        pending.append('\u201C')
                        flush()
                        out.addAll(inlines(node.children, inLink))
                        pending.append('\u201D')
                    }
                    is SingleQuoted -> {
                        pending.append('\u2018')
                        flush()
                        out.addAll(inlines(node.children, inLink))
                        pending.append('\u2019')
                    }
                    is DjotLink -> {
                        val target = target(node.destination, node.reference)
                        val label = inlines(node.children, inLink = true)
                        if (target == null || inLink) {
                            // Nothing to link to: the words.
                            flush()
                            out.addAll(label)
                        } else {
                            add(Link(target, label))
                        }
                    }
                    is DjotImage -> {
                        val target = target(node.destination, node.reference)
                        val alt = getStringContent(Para(node.children.toMutableList()))
                        if (target == null) pending.append(alt) else add(Image(target, alt))
                    }
                }
            }
            flush()
            return out.mergeTexts()
        }

        /** Djot reads a markdown habit, `**bold**`, as strong inside strong: draw it once. */
        fun unwrapSame(node: DjotStrong): List<DjotInline> {
            var children: List<DjotInline> = node.children
            while (children.size == 1 && children[0] is DjotStrong) children = (children[0] as DjotStrong).children
            return children
        }

        fun target(
            destination: String?,
            reference: String?,
        ): String? =
            when {
                destination != null -> destination
                reference.isNullOrEmpty() -> null
                else -> (doc.references[reference] ?: doc.autoReferences[reference])?.destination ?: resolveWikilink(reference)
            }

        fun footnoteNumber(label: String): Int {
            val index = footnoteOrder.indexOf(label)
            if (index >= 0) return index + 1
            footnoteOrder.add(label)
            return footnoteOrder.size
        }
    }

    private fun classes(node: AstNode): List<String> =
        node.attributes
            ?.get("class")
            ?.split(' ')
            ?.filter { it.isNotEmpty() } ?: emptyList()

    private fun attributes(node: AstNode): Map<String, String> = node.attributes?.filterKeys { it != "class" } ?: emptyMap()

    private fun smart(
        type: String,
        text: String,
    ): String =
        when (type) {
            "left_single_quote" -> "\u2018"
            "right_single_quote" -> "\u2019"
            "left_double_quote" -> "\u201C"
            "right_double_quote" -> "\u201D"
            "ellipses" -> "\u2026"
            "em_dash" -> "\u2014"
            "en_dash" -> "\u2013"
            else -> text
        }

    companion object {
        private val NO_WARNINGS = WarningHandler { }
        private val WHITESPACE = Regex("[ \\t\\r\\n]+")
    }
}

/** Adjacent text pieces as one: the same words, fewer runs to draw. */
internal fun List<Inline>.mergeTexts(): List<Inline> {
    if (size < 2) return this
    val out = ArrayList<Inline>(size)
    forEach { inline ->
        val last = out.lastOrNull()
        if (inline is Text && last is Text) out[out.size - 1] = Text(last.text + inline.text) else out.add(inline)
    }
    return out
}
