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
package com.vitorpamplona.amethyst.commons.content.markdown

import com.vitorpamplona.amethyst.commons.content.Block
import com.vitorpamplona.amethyst.commons.content.Callout
import com.vitorpamplona.amethyst.commons.content.Code
import com.vitorpamplona.amethyst.commons.content.CodeBlock
import com.vitorpamplona.amethyst.commons.content.ColumnAlignment
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
import com.vitorpamplona.amethyst.commons.content.Link
import com.vitorpamplona.amethyst.commons.content.ListBlock
import com.vitorpamplona.amethyst.commons.content.ListItem
import com.vitorpamplona.amethyst.commons.content.NostrInlinePass
import com.vitorpamplona.amethyst.commons.content.Paragraph
import com.vitorpamplona.amethyst.commons.content.Quote
import com.vitorpamplona.amethyst.commons.content.SoftBreak
import com.vitorpamplona.amethyst.commons.content.Strikethrough
import com.vitorpamplona.amethyst.commons.content.Strong
import com.vitorpamplona.amethyst.commons.content.Table
import com.vitorpamplona.amethyst.commons.content.TableCell
import com.vitorpamplona.amethyst.commons.content.Text
import com.vitorpamplona.amethyst.commons.content.ThematicBreak
import com.vitorpamplona.amethyst.commons.richtext.MarkdownChunks
import org.intellij.markdown.IElementType
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.flavours.gfm.GFMElementTypes
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.flavours.gfm.GFMTokenTypes
import org.intellij.markdown.parser.MarkdownParser

/**
 * GitHub-flavoured Markdown into a [Document], with JetBrains' multiplatform parser.
 *
 * What the Markdown parser leaves as plain text goes through [NostrInlinePass], so mentions,
 * hashtags, custom emoji, bare URLs and media, invoices and math come out as they do in a note.
 * NIP-23's "MUST NOT support adding HTML" holds: tags are not interpreted, their text is kept, and
 * `<br>` is a line break.
 */
class MarkdownToDocument(
    /**
     * What finds Nostr text in the plain text. Null for text that is not Nostr's (an agent's reply,
     * a README): it stays text, and only its bare URLs become links.
     */
    private val nostr: NostrInlinePass? = NostrInlinePass.NONE,
) {
    fun parse(markdown: String): Document {
        // CommonMark ends a line at \r\n and \r too; this parser only at \n, so a document written
        // with Windows line endings would be one paragraph.
        val normalized = if (markdown.contains('\r')) markdown.replace("\r\n", "\n").replace('\r', '\n') else markdown
        val extracted = MarkdownFootnotes.extract(LooseFences.repair(normalized))
        val src = capQuoteDepth(extracted.source)
        val root = parser.buildMarkdownTreeFromString(src)
        val definitions = linkDefinitions(src, root)
        // Definitions inside a quote or a list item are still in the tree, as paragraphs.
        val nested = nestedFootnotes(src, root)
        val notes = if (nested.notes.isEmpty()) extracted.notes else LinkedHashMap(extracted.notes).apply { nested.notes.forEach { (k, v) -> if (k !in this) this[k] = v } }
        val calls = FootnoteCalls(notes)
        val blocks = Context(src, definitions, calls, nested.nodes).blocks(root.children).toMutableList()

        // Notes may call others, which then join the list: each is parsed once, in call order.
        val drawn = ArrayList<Footnote>()
        var i = 0
        while (i < calls.order.size) {
            val key = calls.order[i]
            val noteSrc = capQuoteDepth(notes[key] ?: "")
            val noteRoot = parser.buildMarkdownTreeFromString(noteSrc)
            drawn.add(Footnote(calls.labels[i], i + 1, Context(noteSrc, definitions, calls, emptySet()).blocks(noteRoot.children)))
            i++
        }
        if (drawn.isNotEmpty()) blocks.add(Footnotes(drawn))
        return Document(blocks)
    }

    /** The notes a document defines, numbered in the order they are first called. */
    private class FootnoteCalls(
        val defined: Map<String, String>,
    ) {
        val order = ArrayList<String>()
        val labels = ArrayList<String>()
        private val numbers = HashMap<String, Int>()

        /** The number of the note [label] calls, or null when no note has that label. */
        fun number(label: String): Int? {
            val key = MarkdownFootnotes.normalize(label)
            numbers[key]?.let { return it }
            if (key !in defined) return null
            order.add(key)
            labels.add(label)
            return order.size.also { numbers[key] = it }
        }
    }

    private inner class Context(
        val src: String,
        val definitions: Map<String, LinkDefinition>,
        val footnotes: FootnoteCalls,
        /** Footnote definitions found in the tree: they draw nothing where they were written. */
        val definitionNodes: Set<ASTNode>,
    ) {
        fun text(node: ASTNode) = src.substring(node.startOffset, node.endOffset)

        /** Blocks and inlines entered and not yet left: past [Document.MAX_DEPTH], only words. */
        var depth = 0

        fun blocks(nodes: List<ASTNode>): List<Block> {
            if (depth >= Document.MAX_DEPTH) return flatParagraph(nodes)
            depth++
            try {
                return nodes.mapNotNull { block(it) }
            } finally {
                depth--
            }
        }

        /** What [nodes] say, as one paragraph of their source with its spaces collapsed. */
        fun flatText(nodes: List<ASTNode>): String = WHITESPACE.replace(nodes.joinToString("") { text(it) }, " ").trim()

        fun flatParagraph(nodes: List<ASTNode>): List<Block> = flatText(nodes).let { if (it.isEmpty()) emptyList() else listOf(Paragraph(listOf(Text(it)))) }

        fun block(node: ASTNode): Block? =
            if (node in definitionNodes) {
                null
            } else {
                blockOf(node)
            }

        private fun blockOf(node: ASTNode): Block? =
            when (val type = node.type) {
                MarkdownElementTypes.PARAGRAPH -> paragraph(node)
                MarkdownElementTypes.ATX_1, MarkdownElementTypes.ATX_2, MarkdownElementTypes.ATX_3,
                MarkdownElementTypes.ATX_4, MarkdownElementTypes.ATX_5, MarkdownElementTypes.ATX_6,
                -> heading(node, atxLevel(type), MarkdownTokenTypes.ATX_CONTENT)
                MarkdownElementTypes.SETEXT_1 -> heading(node, 1, MarkdownTokenTypes.SETEXT_CONTENT)
                MarkdownElementTypes.SETEXT_2 -> heading(node, 2, MarkdownTokenTypes.SETEXT_CONTENT)
                MarkdownElementTypes.UNORDERED_LIST -> list(node, ordered = false)
                MarkdownElementTypes.ORDERED_LIST -> list(node, ordered = true)
                MarkdownElementTypes.BLOCK_QUOTE -> Quote(blocks(node.children))
                GFMElementTypes.ALERT -> alert(node)
                MarkdownElementTypes.CODE_FENCE -> fence(node)
                MarkdownElementTypes.CODE_BLOCK -> indentedCode(node)
                MarkdownTokenTypes.HORIZONTAL_RULE -> ThematicBreak
                GFMElementTypes.TABLE -> table(node)
                MarkdownElementTypes.HTML_BLOCK -> htmlBlock(node)
                // Link definitions draw nothing: their links already point at them.
                else -> null
            }

        fun paragraph(node: ASTNode): Block? {
            val inlines = inlines(node.children).trimSpaces()
            return if (inlines.isEmpty()) null else Paragraph(inlines)
        }

        fun heading(
            node: ASTNode,
            level: Int,
            contentType: IElementType,
        ): Block {
            val content = node.children.firstOrNull { it.type == contentType }
            val inlines = content?.let { inlines(it.children).trimSpaces() } ?: emptyList()
            return Heading(level, inlines, MarkdownChunks.anchorOf(content?.let { text(it).trim() } ?: ""))
        }

        fun list(
            node: ASTNode,
            ordered: Boolean,
        ): Block {
            val itemNodes = node.children.filter { it.type == MarkdownElementTypes.LIST_ITEM }
            // Loose when a blank line separates two items or two blocks of an item.
            val tight = !hasBlankLine(node.children.dropWhile { it.type != MarkdownElementTypes.LIST_ITEM }) && itemNodes.none { hasBlankLine(it.children) }
            val start =
                if (ordered) {
                    itemNodes.firstOrNull()?.children?.firstOrNull { it.type == MarkdownTokenTypes.LIST_NUMBER }?.let { number ->
                        text(number).trim().trimEnd('.', ')').toIntOrNull()
                    } ?: 1
                } else {
                    1
                }
            val items =
                itemNodes.map { item ->
                    val checkBox = item.children.firstOrNull { it.type == GFMTokenTypes.CHECK_BOX }
                    ListItem(
                        blocks = blocks(item.children),
                        checked = checkBox?.let { text(it).contains('x', ignoreCase = true) },
                    )
                }
            return ListBlock(items, ordered, start, tight)
        }

        private fun hasBlankLine(children: List<ASTNode>): Boolean {
            // Two line ends in a row between the first and the last block.
            val last = children.indexOfLast { it.type != MarkdownTokenTypes.EOL }
            for (i in 1 until last) {
                if (children[i].type == MarkdownTokenTypes.EOL && children[i - 1].type == MarkdownTokenTypes.EOL) return true
            }
            return false
        }

        fun alert(node: ASTNode): Block {
            val title = node.children.firstOrNull { it.type == GFMTokenTypes.ALERT_TITLE }?.let { text(it) } ?: ""
            val kind =
                title
                    .removePrefix("[!")
                    .removeSuffix("]")
                    .trim()
                    .lowercase()
                    .ifEmpty { "note" }
            return Callout(kind, blocks(node.children))
        }

        fun fence(node: ASTNode): Block {
            val language = node.children.firstOrNull { it.type == MarkdownTokenTypes.FENCE_LANG }?.let { text(it).trim().split(' ').first() }
            // The code is the content tokens of every line after the opening one, so a fence inside
            // a quote or a list item loses the `> ` and the indentation its container puts before
            // each line. CommonMark also takes the opening fence's own indentation off each line.
            val children = node.children
            val firstEol = children.indexOfFirst { it.type == MarkdownTokenTypes.EOL }
            if (firstEol < 0) return CodeBlock("", language?.ifEmpty { null })
            val fenceIndent = children.firstOrNull { it.type == MarkdownTokenTypes.CODE_FENCE_START }?.let { leadingSpaces(text(it)) } ?: 0
            val code = StringBuilder()
            for (i in firstEol + 1 until children.size) {
                val child = children[i]
                when (child.type) {
                    MarkdownTokenTypes.CODE_FENCE_CONTENT -> code.append(dropSpaces(text(child), fenceIndent))
                    MarkdownTokenTypes.EOL -> code.append('\n')
                    MarkdownTokenTypes.CODE_FENCE_END -> break
                }
            }
            return CodeBlock(code.toString().removeSuffix("\n"), language?.ifEmpty { null })
        }

        fun indentedCode(node: ASTNode): Block {
            val code = StringBuilder()
            node.children.forEach { child ->
                when (child.type) {
                    MarkdownTokenTypes.CODE_LINE -> code.append(dropIndent(text(child)))
                    MarkdownTokenTypes.EOL -> code.append('\n')
                }
            }
            return CodeBlock(code.toString().trimEnd('\n'))
        }

        private fun leadingSpaces(line: String): Int {
            var i = 0
            while (i < line.length && line[i] == ' ') i++
            return i
        }

        private fun dropSpaces(
            line: String,
            count: Int,
        ): String = line.substring(minOf(leadingSpaces(line), count))

        private fun dropIndent(line: String): String {
            var i = 0
            var width = 0
            while (i < line.length && width < 4) {
                when (line[i]) {
                    ' ' -> width++
                    '\t' -> width = 4
                    else -> return line.substring(i)
                }
                i++
            }
            return line.substring(i)
        }

        fun table(node: ASTNode): Block {
            val header = node.children.firstOrNull { it.type == GFMElementTypes.HEADER }
            val separator = node.children.firstOrNull { it.type == GFMTokenTypes.TABLE_SEPARATOR && text(it).contains('-') }
            val rows = node.children.filter { it.type == GFMElementTypes.ROW }

            fun cells(row: ASTNode) = row.children.filter { it.type == GFMTokenTypes.CELL }.map { TableCell(inlines(it.children).trimSpaces()) }

            val headerCells = header?.let { cells(it) } ?: emptyList()
            val alignments =
                separator
                    ?.let { text(it) }
                    ?.trim()
                    ?.removePrefix("|")
                    ?.removeSuffix("|")
                    ?.split('|')
                    ?.map { spec ->
                        val s = spec.trim()
                        when {
                            s.startsWith(':') && s.endsWith(':') -> ColumnAlignment.CENTER
                            s.endsWith(':') -> ColumnAlignment.RIGHT
                            s.startsWith(':') -> ColumnAlignment.LEFT
                            else -> ColumnAlignment.NONE
                        }
                    } ?: emptyList()
            return Table(headerCells, rows.map { cells(it) }, alignments)
        }

        fun htmlBlock(node: ASTNode): Block? {
            // Not interpreted (NIP-23): its text, without the tags, as a paragraph.
            val withoutTags = HtmlText.strip(text(node)).trim()
            if (withoutTags.isEmpty()) return null
            return Paragraph(nostr?.inlines(withoutTags) ?: listOf(Text(withoutTags)))
        }

        // ---- Inlines ----------------------------------------------------------------------------

        /**
         * [inLink] is true inside a link's text: it is already a link, so nothing in it is detected
         * again (a URL, a mention or a hashtag there is text).
         */
        fun inlines(
            nodes: List<ASTNode>,
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
            nodes: List<ASTNode>,
            inLink: Boolean,
        ): List<Inline> {
            val out = ArrayList<Inline>()
            val pending = StringBuilder()

            fun flush() {
                if (pending.isNotEmpty()) {
                    if (inLink || nostr == null) {
                        out.add(Text(WHITESPACE.replace(pending.toString(), " ")))
                    } else {
                        out.addAll(nostr.inlines(pending.toString()))
                    }
                    pending.setLength(0)
                }
            }

            // At the start of a continuation line, its indentation and a quote's `>` are not text.
            var lineStart = false
            // `<me@example.com>` comes as `<`, the address and `>`: the brackets are not text.
            var closingBracket = false
            for (node in nodes) {
                if (lineStart && (node.type == MarkdownTokenTypes.WHITE_SPACE || node.type == MarkdownTokenTypes.BLOCK_QUOTE)) continue
                lineStart = false
                if (closingBracket) {
                    closingBracket = false
                    if (node.type == MarkdownTokenTypes.GT) continue
                }
                when (node.type) {
                    MarkdownTokenTypes.TEXT -> pending.append(decode(text(node)))
                    MarkdownTokenTypes.WHITE_SPACE -> pending.append(' ')
                    MarkdownTokenTypes.EOL -> {
                        flush()
                        out.dropTrailingSpace()
                        out.add(SoftBreak)
                        lineStart = true
                    }
                    MarkdownTokenTypes.HARD_LINE_BREAK -> {
                        flush()
                        out.dropTrailingSpace()
                        out.add(HardBreak)
                        lineStart = true
                    }
                    // `>` markers of the lines a quote's paragraph continues on.
                    MarkdownTokenTypes.BLOCK_QUOTE -> {}
                    // A bare URL: the Nostr pass decides whether it is media, a preview or a link.
                    GFMTokenTypes.GFM_AUTOLINK ->
                        if (nostr == null && !inLink) {
                            flush()
                            out.add(Link(text(node), listOf(Text(text(node)))))
                        } else {
                            pending.append(text(node))
                        }
                    // Math goes back to the text as written, for the same rules a note's math uses.
                    GFMElementTypes.INLINE_MATH, GFMElementTypes.BLOCK_MATH -> pending.append(text(node))
                    MarkdownTokenTypes.HTML_TAG -> {
                        if (HtmlText.isLineBreak(text(node))) {
                            flush()
                            out.add(HardBreak)
                        }
                    }
                    MarkdownElementTypes.EMPH -> {
                        flush()
                        out.add(Emphasis(inlines(node.children.withoutDelimiters(MarkdownTokenTypes.EMPH), inLink)))
                    }
                    MarkdownElementTypes.STRONG -> {
                        flush()
                        out.add(Strong(inlines(node.children.withoutDelimiters(MarkdownTokenTypes.EMPH), inLink)))
                    }
                    GFMElementTypes.STRIKETHROUGH -> {
                        flush()
                        out.add(Strikethrough(inlines(node.children.withoutDelimiters(GFMTokenTypes.TILDE), inLink)))
                    }
                    GFMElementTypes.HIGHLIGHT -> {
                        flush()
                        out.add(Highlight(inlines(node.children.withoutDelimiters(GFMTokenTypes.EQUALS), inLink)))
                    }
                    MarkdownElementTypes.CODE_SPAN -> {
                        flush()
                        out.add(Code(codeSpan(node)))
                    }
                    MarkdownElementTypes.INLINE_LINK -> {
                        flush()
                        out.add(inlineLink(node))
                    }
                    MarkdownElementTypes.FULL_REFERENCE_LINK, MarkdownElementTypes.SHORT_REFERENCE_LINK -> {
                        val calls = footnoteCalls(node)
                        val link = if (calls == null) referenceLink(node) else null
                        if (calls != null) {
                            flush()
                            out.addAll(calls)
                        } else if (link != null) {
                            flush()
                            out.add(link)
                        } else {
                            // An undefined reference is the text as written.
                            pending.append(decode(text(node)))
                        }
                    }
                    MarkdownElementTypes.AUTOLINK, MarkdownTokenTypes.EMAIL_AUTOLINK -> {
                        if (node.type == MarkdownTokenTypes.EMAIL_AUTOLINK && pending.endsWith('<')) {
                            pending.setLength(pending.length - 1)
                            closingBracket = true
                        }
                        flush()
                        val url = text(node).removePrefix("<").removeSuffix(">")
                        val destination = if (url.contains('@') && !url.contains("://") && !url.startsWith("mailto:")) "mailto:$url" else url
                        if (inLink) out.add(Text(url)) else out.add(Link(destination, listOf(Text(url))))
                    }
                    MarkdownElementTypes.IMAGE -> {
                        flush()
                        image(node)?.let { out.add(it) }
                    }
                    else ->
                        if (node.children.isEmpty()) {
                            // Punctuation tokens (`:`, `(`, `!`, ...) are text.
                            pending.append(decode(text(node)))
                        } else {
                            // A node this mapper does not know: its children, so no text is lost.
                            flush()
                            out.addAll(inlines(node.children, inLink))
                        }
                }
            }
            flush()
            return out
        }

        /** A link's destination: `(url)` parses as one, `(<url>)` as an autolink in its place. */
        private fun List<ASTNode>.destinationNode(): ASTNode? = firstOrNull { it.type == MarkdownElementTypes.LINK_DESTINATION || it.type == MarkdownElementTypes.AUTOLINK }

        private fun List<ASTNode>.withoutDelimiters(delimiter: IElementType): List<ASTNode> {
            var from = 0
            var to = size
            while (from < to && this[from].type == delimiter) from++
            while (to > from && this[to - 1].type == delimiter) to--
            return subList(from, to)
        }

        private fun codeSpan(node: ASTNode): String {
            val children = node.children
            var from = 0
            var to = children.size
            while (from < to && children[from].type == MarkdownTokenTypes.BACKTICK) from++
            while (to > from && children[to - 1].type == MarkdownTokenTypes.BACKTICK) to--
            if (from >= to) return ""
            val raw = src.substring(children[from].startOffset, children[to - 1].endOffset).replace('\n', ' ')
            // One space on each side is padding when both are there (CommonMark).
            return if (raw.length >= 2 && raw.startsWith(' ') && raw.endsWith(' ') && raw.isNotBlank()) raw.substring(1, raw.length - 1) else raw
        }

        private fun linkText(node: ASTNode?): List<Inline> {
            if (node == null) return emptyList()
            val inner = node.children.withoutBrackets()
            return inlines(inner, inLink = true)
        }

        private fun List<ASTNode>.withoutBrackets(): List<ASTNode> {
            var from = 0
            var to = size
            if (from < to && this[from].type == MarkdownTokenTypes.LBRACKET) from++
            if (to > from && this[to - 1].type == MarkdownTokenTypes.RBRACKET) to--
            return subList(from, to)
        }

        private fun inlineLink(node: ASTNode): Inline {
            val destination = node.children.destinationNode()?.let { destination(text(it)) } ?: ""
            val title = node.children.firstOrNull { it.type == MarkdownElementTypes.LINK_TITLE }?.let { title(text(it)) }
            val label = linkText(node.children.firstOrNull { it.type == MarkdownElementTypes.LINK_TEXT })
            return Link(destination, label, title)
        }

        /**
         * `[^1]` parses as a reference link, and `[^1][^2]` as one with a text and a label: the
         * calls to notes the document defines, or null when any part is not one.
         */
        private fun footnoteCalls(node: ASTNode): List<Inline>? {
            if (footnotes.defined.isEmpty()) return null
            val parts = node.children.filter { it.type == MarkdownElementTypes.LINK_TEXT || it.type == MarkdownElementTypes.LINK_LABEL }
            if (parts.isEmpty()) return null
            val labels = parts.map { MarkdownFootnotes.referenceLabel(text(it)) ?: return null }
            if (labels.any { MarkdownFootnotes.normalize(it) !in footnotes.defined }) return null
            return labels.map { FootnoteReference(it, footnotes.number(it)!!) }
        }

        private fun referenceLink(node: ASTNode): Inline? {
            val labelNode = node.children.firstOrNull { it.type == MarkdownElementTypes.LINK_LABEL } ?: return null
            val definition = definitions[normalizeLabel(text(labelNode))] ?: return null
            val textNode = node.children.firstOrNull { it.type == MarkdownElementTypes.LINK_TEXT }
            val label = if (textNode != null) linkText(textNode) else linkText(labelNode)
            return Link(definition.destination, label, definition.title)
        }

        private fun image(node: ASTNode): Inline? {
            val link = node.children.firstOrNull { it.type == MarkdownElementTypes.INLINE_LINK || it.type == MarkdownElementTypes.FULL_REFERENCE_LINK || it.type == MarkdownElementTypes.SHORT_REFERENCE_LINK } ?: return null
            val alt =
                (link.children.firstOrNull { it.type == MarkdownElementTypes.LINK_TEXT } ?: link.children.firstOrNull { it.type == MarkdownElementTypes.LINK_LABEL })
                    ?.let { decode(text(it).removePrefix("[").removeSuffix("]")) }
                    ?: ""
            return when (link.type) {
                MarkdownElementTypes.INLINE_LINK -> {
                    val url = link.children.destinationNode()?.let { destination(text(it)) } ?: return null
                    val title = link.children.firstOrNull { it.type == MarkdownElementTypes.LINK_TITLE }?.let { title(text(it)) }
                    Image(url, alt, title)
                }
                else -> {
                    val label = link.children.firstOrNull { it.type == MarkdownElementTypes.LINK_LABEL } ?: return null
                    val definition = definitions[normalizeLabel(text(label))] ?: return null
                    Image(definition.destination, alt, definition.title)
                }
            }
        }
    }

    private class LinkDefinition(
        val destination: String,
        val title: String?,
    )

    /**
     * The parser keeps state per quote level on every line, so 20,000 nested `>` ran it out of
     * memory. No reader follows quotes [Document.MAX_DEPTH] deep: a line's markers past that are
     * dropped before parsing.
     */
    private fun capQuoteDepth(src: String): String {
        if (!src.contains(">>") && !src.contains("> >")) return src
        var changed = false
        val out = StringBuilder(src.length)
        var lineStart = 0
        while (lineStart <= src.length) {
            val lineEnd = src.indexOf('\n', lineStart).let { if (it < 0) src.length else it }
            var i = lineStart
            var markers = 0
            var cut = -1
            while (i < lineEnd) {
                val c = src[i]
                if (c == '>') {
                    markers++
                    if (markers == Document.MAX_DEPTH) cut = i + 1
                } else if (c != ' ' && c != '\t') {
                    break
                }
                i++
            }
            if (markers > Document.MAX_DEPTH && cut >= 0) {
                changed = true
                out.append(src, lineStart, cut).append(' ')
                // The rest of the line after its last marker.
                out.append(src, i, lineEnd)
            } else {
                out.append(src, lineStart, lineEnd)
            }
            if (lineEnd < src.length) out.append('\n')
            lineStart = lineEnd + 1
        }
        return if (changed) out.toString() else src
    }

    private class NestedFootnotes(
        /** Each note's Markdown, by normalized label. The first definition of a label wins. */
        val notes: Map<String, String>,
        /** The paragraphs (and link definitions) that held them. */
        val nodes: Set<ASTNode>,
    )

    /**
     * `[^label]: text` inside a quote or a list item, which [MarkdownFootnotes.extract] leaves to
     * the parser: it comes out as a paragraph, or as a link definition when the text is one word.
     * Only the definition's own paragraph is the note; its quote markers and indentation go.
     */
    private fun nestedFootnotes(
        src: String,
        root: ASTNode,
    ): NestedFootnotes {
        if (!src.contains("[^")) return NestedFootnotes(emptyMap(), emptySet())
        val notes = LinkedHashMap<String, String>()
        val nodes = HashSet<ASTNode>()
        // A stack, not recursion, as in [linkDefinitions]; children in reverse, so the first wins.
        val stack = ArrayList<ASTNode>()
        stack.add(root)
        while (stack.isNotEmpty()) {
            val node = stack.removeAt(stack.size - 1)
            when (node.type) {
                MarkdownElementTypes.PARAGRAPH, MarkdownElementTypes.LINK_DEFINITION -> {
                    val text = src.substring(node.startOffset, node.endOffset)
                    val start = MarkdownFootnotes.definitionStart(text.substringBefore('\n'))
                    if (start != null) {
                        nodes.add(node)
                        val key = MarkdownFootnotes.normalize(start.first)
                        if (key !in notes) {
                            val rest = text.substringAfter('\n', "").split('\n').joinToString("\n") { stripContainer(it) }
                            notes[key] = if (rest.isEmpty()) start.second else start.second + "\n" + rest
                        }
                    }
                }
                MarkdownElementTypes.MARKDOWN_FILE, MarkdownElementTypes.BLOCK_QUOTE, GFMElementTypes.ALERT,
                MarkdownElementTypes.UNORDERED_LIST, MarkdownElementTypes.ORDERED_LIST, MarkdownElementTypes.LIST_ITEM,
                -> for (i in node.children.indices.reversed()) stack.add(node.children[i])
            }
        }
        return NestedFootnotes(notes, nodes)
    }

    /** A continuation line without what its containers put before it: indentation and `>` markers. */
    private fun stripContainer(line: String): String {
        var i = 0
        while (i < line.length && (line[i] == ' ' || line[i] == '\t' || line[i] == '>')) i++
        return line.substring(i)
    }

    /** Every `[label]: destination "title"`, by normalized label. The first definition of a label wins. */
    private fun linkDefinitions(
        src: String,
        root: ASTNode,
    ): Map<String, LinkDefinition> {
        val found = HashMap<String, LinkDefinition>()

        // A stack, not recursion: lists and quotes can nest as deep as a stranger types them.
        // Children go on in reverse, so definitions come off in document order and the first wins.
        val stack = ArrayList<ASTNode>()
        stack.add(root)
        while (stack.isNotEmpty()) {
            val node = stack.removeAt(stack.size - 1)
            if (node.type == MarkdownElementTypes.LINK_DEFINITION) {
                val label = node.children.firstOrNull { it.type == MarkdownElementTypes.LINK_LABEL }
                val destination = node.children.firstOrNull { it.type == MarkdownElementTypes.LINK_DESTINATION }
                if (label != null && destination != null) {
                    val key = normalizeLabel(src.substring(label.startOffset, label.endOffset))
                    if (key !in found) {
                        val title = node.children.firstOrNull { it.type == MarkdownElementTypes.LINK_TITLE }?.let { title(src.substring(it.startOffset, it.endOffset)) }
                        found[key] = LinkDefinition(destination(src.substring(destination.startOffset, destination.endOffset)), title)
                    }
                }
            } else {
                for (i in node.children.indices.reversed()) stack.add(node.children[i])
            }
        }
        return found
    }

    companion object {
        private val parser = MarkdownParser(GFMFlavourDescriptor())
        private val WHITESPACE = Regex("[ \\t\\r\\n]+")

        private fun atxLevel(type: IElementType): Int =
            when (type) {
                MarkdownElementTypes.ATX_1 -> 1
                MarkdownElementTypes.ATX_2 -> 2
                MarkdownElementTypes.ATX_3 -> 3
                MarkdownElementTypes.ATX_4 -> 4
                MarkdownElementTypes.ATX_5 -> 5
                else -> 6
            }

        /** Case- and whitespace-insensitive, as CommonMark matches labels. */
        private fun normalizeLabel(label: String): String =
            label
                .removePrefix("[")
                .removeSuffix("]")
                .trim()
                .lowercase()
                .split(Regex("\\s+"))
                .joinToString(" ")

        private fun destination(raw: String): String = decode(raw.trim().removePrefix("<").removeSuffix(">"))

        private fun title(raw: String): String {
            val t = raw.trim()
            return decode(if (t.length >= 2) t.substring(1, t.length - 1) else t)
        }

        /** Backslash escapes and entities, as CommonMark reads text. */
        fun decode(text: String): String = HtmlText.decodeEntities(unescape(text))

        private fun unescape(text: String): String {
            if (!text.contains('\\')) return text
            val sb = StringBuilder(text.length)
            var i = 0
            while (i < text.length) {
                val c = text[i]
                if (c == '\\' && i + 1 < text.length && text[i + 1].isAsciiPunctuation()) {
                    sb.append(text[i + 1])
                    i += 2
                } else {
                    sb.append(c)
                    i++
                }
            }
            return sb.toString()
        }

        private fun Char.isAsciiPunctuation(): Boolean = this in "!\"#$%&'()*+,-./:;<=>?@[\\]^_`{|}~"
    }
}

/** Drops the space a line ends with before a break. */
private fun MutableList<Inline>.dropTrailingSpace() {
    val last = lastOrNull() as? Text ?: return
    val t = last.text.trimEnd()
    if (t.isEmpty()) removeAt(size - 1) else this[size - 1] = Text(t)
}

/** Trims the spaces markup leaves at the ends of a paragraph, heading or cell. */
internal fun List<Inline>.trimSpaces(): List<Inline> {
    if (isEmpty()) return this
    val out = toMutableList()
    val first = out.first()
    if (first is Text) {
        val t = first.text.trimStart()
        if (t.isEmpty()) out.removeAt(0) else out[0] = Text(t)
    }
    if (out.isNotEmpty()) {
        val last = out.last()
        if (last is Text) {
            val t = last.text.trimEnd()
            if (t.isEmpty()) out.removeAt(out.size - 1) else out[out.size - 1] = Text(t)
        }
    }
    return out
}
