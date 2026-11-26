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

import com.halilibo.richtext.markdown.node.AstBlockQuote
import com.halilibo.richtext.markdown.node.AstCode
import com.halilibo.richtext.markdown.node.AstEmphasis
import com.halilibo.richtext.markdown.node.AstFencedCodeBlock
import com.halilibo.richtext.markdown.node.AstHeading
import com.halilibo.richtext.markdown.node.AstImage
import com.halilibo.richtext.markdown.node.AstIndentedCodeBlock
import com.halilibo.richtext.markdown.node.AstLink
import com.halilibo.richtext.markdown.node.AstListItem
import com.halilibo.richtext.markdown.node.AstNode
import com.halilibo.richtext.markdown.node.AstOrderedList
import com.halilibo.richtext.markdown.node.AstParagraph
import com.halilibo.richtext.markdown.node.AstStrikethrough
import com.halilibo.richtext.markdown.node.AstStrongEmphasis
import com.halilibo.richtext.markdown.node.AstTableRoot
import com.halilibo.richtext.markdown.node.AstText
import com.halilibo.richtext.markdown.node.AstThematicBreak
import com.halilibo.richtext.markdown.node.AstUnorderedList
import com.vitorpamplona.amethyst.commons.content.Block
import com.vitorpamplona.amethyst.commons.content.BlockContainer
import com.vitorpamplona.amethyst.commons.content.Code
import com.vitorpamplona.amethyst.commons.content.CodeBlock
import com.vitorpamplona.amethyst.commons.content.Emphasis
import com.vitorpamplona.amethyst.commons.content.Heading
import com.vitorpamplona.amethyst.commons.content.Image
import com.vitorpamplona.amethyst.commons.content.Inline
import com.vitorpamplona.amethyst.commons.content.InlineContainer
import com.vitorpamplona.amethyst.commons.content.Link
import com.vitorpamplona.amethyst.commons.content.ListBlock
import com.vitorpamplona.amethyst.commons.content.NostrInline
import com.vitorpamplona.amethyst.commons.content.Paragraph
import com.vitorpamplona.amethyst.commons.content.Quote
import com.vitorpamplona.amethyst.commons.content.Strikethrough
import com.vitorpamplona.amethyst.commons.content.Strong
import com.vitorpamplona.amethyst.commons.content.Table
import com.vitorpamplona.amethyst.commons.content.Text
import com.vitorpamplona.amethyst.commons.content.ThematicBreak
import com.vitorpamplona.amethyst.commons.content.markdown.MarkdownToDocument
import com.vitorpamplona.amethyst.commons.richtext.ImageSegment
import com.vitorpamplona.amethyst.commons.richtext.VideoSegment
import java.io.File
import java.util.Base64
import kotlin.test.Test

/**
 * The parity harness of `commons/plans/2026-11-25-content-parsing.md`: real articles parsed by the
 * old path (commonmark through the compose-richtext fork) and by the document model, compared by
 * what each finds. Opt-in: `PARITY_IN` names a file of `id<TAB>base64(content)` lines (articles fetched
 * with `amy fetch --kind 30023 --json`), `PARITY_OUT` where the report goes.
 */
class MarkdownParityTest {
    @Test
    fun compareWithTheOldParser() {
        val input = System.getenv("PARITY_IN") ?: return
        val output = File(System.getenv("PARITY_OUT") ?: "parity-report.txt")
        val report = StringBuilder()
        val totalsOld = sortedMapOf<String, Int>()
        val totalsNew = sortedMapOf<String, Int>()
        var same = 0
        var count = 0

        File(input).forEachLine { line ->
            val tab = line.indexOf('\t')
            if (tab < 0) return@forEachLine
            val id = line.substring(0, tab)
            val content = Base64.getDecoder().decode(line.substring(tab + 1)).decodeToString()
            count++
            val old = countOld(CachedMarkdownAst.parse(content))
            val new = countNew(MarkdownToDocument().parse(content).blocks)
            old.forEach { (k, v) -> totalsOld[k] = (totalsOld[k] ?: 0) + v }
            new.forEach { (k, v) -> totalsNew[k] = (totalsNew[k] ?: 0) + v }
            if (old == new) {
                same++
            } else {
                val keys = (old.keys + new.keys).toSortedSet()
                report.append(id).append('\n')
                keys.filter { old[it] != new[it] }.forEach {
                    report
                        .append("  ")
                        .append(it)
                        .append(": old ")
                        .append(old[it] ?: 0)
                        .append(", new ")
                        .append(new[it] ?: 0)
                        .append('\n')
                }
            }
        }

        val head = StringBuilder()
        head
            .append("articles: ")
            .append(count)
            .append(", same structure: ")
            .append(same)
            .append('\n')
        (totalsOld.keys + totalsNew.keys).toSortedSet().forEach {
            head
                .append(it)
                .append(": old ")
                .append(totalsOld[it] ?: 0)
                .append(", new ")
                .append(totalsNew[it] ?: 0)
                .append('\n')
        }
        output.writeText(head.toString() + "\n" + report)
    }

    private fun MutableMap<String, Int>.inc(key: String) {
        this[key] = (this[key] ?: 0) + 1
    }

    private fun AstNode.children(): Sequence<AstNode> = generateSequence(links.firstChild) { it.links.next }

    private fun textOf(node: AstNode): String =
        buildString {
            fun walk(n: AstNode) {
                (n.type as? AstText)?.let { append(it.literal) }
                n.children().forEach { walk(it) }
            }
            walk(node)
        }

    private fun countOld(root: AstNode): Map<String, Int> {
        val m = sortedMapOf<String, Int>()

        fun walk(n: AstNode) {
            when (val t = n.type) {
                is AstHeading -> m.inc("heading")
                AstParagraph -> m.inc("paragraph")
                is AstUnorderedList -> m.inc("bullet list")
                is AstOrderedList -> m.inc("ordered list")
                AstListItem -> m.inc("list item")
                AstBlockQuote -> m.inc("quote")
                is AstFencedCodeBlock, is AstIndentedCodeBlock -> m.inc("code block")
                AstTableRoot -> m.inc("table")
                AstThematicBreak -> m.inc("rule")
                is AstImage -> m.inc("image")
                is AstLink -> if (textOf(n) != t.destination) m.inc("link")
                is AstEmphasis -> m.inc("emphasis")
                is AstStrongEmphasis -> m.inc("strong")
                is AstStrikethrough -> m.inc("strikethrough")
                is AstCode -> m.inc("code span")
                else -> {}
            }
            n.children().forEach { walk(it) }
        }
        walk(root)
        return m
    }

    private fun countNew(blocks: List<Block>): Map<String, Int> {
        val m = sortedMapOf<String, Int>()

        fun inlines(list: List<Inline>) {
            list.forEach { i ->
                when (i) {
                    is Image -> m.inc("image")
                    // A bare image or video URL: the old path made it an image too.
                    is NostrInline -> if (i.segment is ImageSegment || i.segment is VideoSegment) m.inc("image")
                    is Link -> if ((i.children.singleOrNull() as? Text)?.text != i.destination) m.inc("link")
                    is Emphasis -> m.inc("emphasis")
                    is Strong -> m.inc("strong")
                    is Strikethrough -> m.inc("strikethrough")
                    is Code -> m.inc("code span")
                    else -> {}
                }
                if (i is InlineContainer) inlines(i.children)
            }
        }

        fun walk(b: Block) {
            when (b) {
                is Heading -> {
                    m.inc("heading")
                    inlines(b.inlines)
                }
                is Paragraph -> {
                    m.inc("paragraph")
                    inlines(b.inlines)
                }
                is ListBlock -> {
                    m.inc(if (b.ordered) "ordered list" else "bullet list")
                    b.items.forEach { item ->
                        m.inc("list item")
                        item.blocks.forEach { walk(it) }
                    }
                }
                is Quote -> {
                    m.inc("quote")
                    b.blocks.forEach { walk(it) }
                }
                is CodeBlock -> m.inc("code block")
                is Table -> {
                    m.inc("table")
                    b.header.forEach { inlines(it.inlines) }
                    b.rows.forEach { row -> row.forEach { inlines(it.inlines) } }
                }
                is ThematicBreak -> m.inc("rule")
                is BlockContainer -> b.blocks.forEach { walk(it) }
                else -> {}
            }
        }
        blocks.forEach { walk(it) }
        return m
    }
}
