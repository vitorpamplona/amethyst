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

import com.vitorpamplona.djot.AstNode
import com.vitorpamplona.djot.BlockQuote
import com.vitorpamplona.djot.BulletList
import com.vitorpamplona.djot.Caption
import com.vitorpamplona.djot.Cell
import com.vitorpamplona.djot.CodeBlock
import com.vitorpamplona.djot.Definition
import com.vitorpamplona.djot.DefinitionList
import com.vitorpamplona.djot.Delete
import com.vitorpamplona.djot.DisplayMath
import com.vitorpamplona.djot.Div
import com.vitorpamplona.djot.Doc
import com.vitorpamplona.djot.DoubleQuoted
import com.vitorpamplona.djot.Email
import com.vitorpamplona.djot.Emph
import com.vitorpamplona.djot.FootnoteReference
import com.vitorpamplona.djot.HardBreak
import com.vitorpamplona.djot.Heading
import com.vitorpamplona.djot.Image
import com.vitorpamplona.djot.Inline
import com.vitorpamplona.djot.InlineMath
import com.vitorpamplona.djot.Insert
import com.vitorpamplona.djot.Link
import com.vitorpamplona.djot.ListItem
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
import com.vitorpamplona.djot.SoftBreak
import com.vitorpamplona.djot.Span
import com.vitorpamplona.djot.Str
import com.vitorpamplona.djot.Strong
import com.vitorpamplona.djot.Subscript
import com.vitorpamplona.djot.Superscript
import com.vitorpamplona.djot.Symb
import com.vitorpamplona.djot.Table
import com.vitorpamplona.djot.TaskList
import com.vitorpamplona.djot.Term
import com.vitorpamplona.djot.ThematicBreak
import com.vitorpamplona.djot.Url
import com.vitorpamplona.djot.Verbatim
import com.vitorpamplona.djot.WarningHandler
import com.vitorpamplona.djot.childNodes
import com.vitorpamplona.djot.getStringContent
import com.vitorpamplona.djot.parse
import com.vitorpamplona.quartz.utils.cache.ConcurrentLruCache

/**
 * Djot (NIP-54 wiki pages) to the markdown the app's markdown renderer draws, the way
 * `AsciiDocToMarkdown` serves NKBIP-01 publications.
 *
 * The Djot is parsed by djot-kmp, which matches the reference parser, so what is a link, a list or
 * emphasis is decided by Djot's rules, not by the markdown that comes out. The markdown then only
 * has to say the same thing: text is escaped so markdown never reads its own syntax into it, and
 * Djot features markdown lacks degrade to their content (superscript, subscript, highlight,
 * insertion, spans and divs keep their text; definition lists become a bold term and its
 * definition; math shows its TeX as code; footnotes become numbered notes after a rule). Raw
 * blocks and inlines are dropped, as the app renders no HTML. Bare URLs in text stay as they are,
 * so the renderer links them as it does in any article.
 *
 * NIP-54 wikilinks: a reference-style link whose reference the document doesn't define goes to
 * [resolveWikilink] with the reference's text; its answer is the destination, or null to keep
 * only the link's text.
 *
 * A temporary bridge: the content-parsing plan (commons/plans/2026-11-25-content-parsing.md)
 * replaces it with a mapping from Djot's tree into the shared document model.
 */
object DjotToMarkdown {
    fun convert(
        djot: String,
        resolveWikilink: (String) -> String?,
    ): String = Converter(parse(djot, ParseOptions(warn = NO_WARNINGS)), resolveWikilink).run()

    /**
     * Whether [content] is AsciiDoc, the format NIP-54 specified before Djot, which older wiki
     * articles still use: its first non-blank line is an AsciiDoc document title (`= Title`). That
     * line means nothing in Djot, which has no `=` headings, so no Djot article starts with one.
     */
    fun isLegacyAsciiDoc(content: String): Boolean {
        val first = content.lineSequence().firstOrNull { it.isNotBlank() } ?: return false
        return first.startsWith("= ") && first.length > 2 && !first[2].isWhitespace()
    }

    /** The article's text with all markup removed, its blocks joined by spaces, for previews. */
    fun plainText(djot: String): String {
        val parts = mutableListOf<String>()
        // Each block that holds text adds its words; containers are walked with a stack, not
        // recursion, since a hostile article may nest deeply.
        val stack = ArrayDeque<AstNode>()
        stack.addLast(parse(djot, ParseOptions(warn = NO_WARNINGS)))
        while (stack.isNotEmpty()) {
            when (val node = stack.removeLast()) {
                is Para, is Heading, is Term, is Caption, is Cell -> parts += getStringContent(node)
                is CodeBlock -> parts += node.text
                else -> node.childNodes.asReversed().forEach { stack.addLast(it) }
            }
        }
        return parts.joinToString(" ") { it.trim() }.trim()
    }

    private val NO_WARNINGS = WarningHandler { }

    private class Converter(
        val doc: Doc,
        val resolveWikilink: (String) -> String?,
    ) {
        /** Footnote labels, in the order first referenced: a note's number is its index + 1. */
        val footnoteOrder = mutableListOf<String>()

        fun run(): String {
            val body = blocks(doc.children, tight = false)
            val notes = footnotes()
            return (if (notes.isEmpty()) body else "$body\n\n***\n\n$notes").trimEnd() + "\n"
        }

        // Footnotes may reference others, which then join the list: loop until no new one appears.
        fun footnotes(): String {
            val out = StringBuilder()
            var i = 0
            while (i < footnoteOrder.size) {
                val note = doc.footnotes[footnoteOrder[i]]
                val content = if (note != null) blocks(note.children, tight = true) else ""
                if (out.isNotEmpty()) out.append('\n')
                out.append(listItem("${i + 1}. ", content))
                i++
            }
            return out.toString()
        }

        fun blocks(
            nodes: List<AstNode>,
            tight: Boolean,
        ): String =
            nodes
                .mapNotNull { block(it).ifEmpty { null } }
                .joinToString(if (tight) "\n" else "\n\n")

        fun block(node: AstNode): String =
            when (node) {
                is Para -> lineStarts(inlines(node.children))
                is Heading -> "#".repeat(node.level.coerceIn(1, 6)) + " " + inlines(node.children).replace('\n', ' ')
                is ThematicBreak -> "***"
                is Section -> blocks(node.children, tight = false)
                is Div -> blocks(node.children, tight = false)
                is BlockQuote -> blocks(node.children, tight = false).lines().joinToString("\n") { if (it.isEmpty()) ">" else "> $it" }
                // `{.json}` before a fence is how some authors name its language in Djot.
                is CodeBlock ->
                    codeBlock(
                        node.text,
                        node.lang ?: node.attributes
                            ?.get("class")
                            ?.split(' ')
                            ?.singleOrNull(),
                    )
                is RawBlock -> ""
                is BulletList -> list(node.children, node.tight) { _ -> "- " }
                is OrderedList -> list(node.children, node.tight) { i -> "${(node.start ?: 1L) + i}. " }
                is TaskList ->
                    node.children
                        .map { item -> listItem(if (item.checkbox == "checked") "- [x] " else "- [ ] ", blocks(item.children, node.tight)) }
                        .joinToString(if (node.tight) "\n" else "\n\n")
                is DefinitionList ->
                    node.children.joinToString("\n\n") { item ->
                        item.children.joinToString("\n\n") { part ->
                            when (part) {
                                is Term -> "**" + inlines(part.children).replace('\n', ' ') + "**"
                                is Definition -> blocks(part.children, tight = false)
                            }
                        }
                    }
                is Table -> table(node)
                else -> ""
            }

        fun list(
            items: List<ListItem>,
            tight: Boolean,
            marker: (Int) -> String,
        ): String =
            items
                .mapIndexed { i, item -> listItem(marker(i), blocks(item.children, tight)) }
                .joinToString(if (tight) "\n" else "\n\n")

        /** [content] after [marker], its later lines indented to line up under the first. */
        fun listItem(
            marker: String,
            content: String,
        ): String {
            val indent = " ".repeat(marker.length)
            return marker + content.lines().mapIndexed { i, line -> if (i == 0 || line.isEmpty()) line else indent + line }.joinToString("\n")
        }

        fun codeBlock(
            text: String,
            lang: String?,
        ): String {
            val fence = "`".repeat(maxOf(3, longestRun(text, '`') + 1))
            val body = if (text.endsWith("\n")) text else text + "\n"
            return fence + (lang ?: "") + "\n" + body + fence
        }

        fun table(table: Table): String {
            val rows = table.children.filterIsInstance<Row>()
            if (rows.isEmpty()) return ""

            fun row(r: Row) = "|" + r.children.joinToString("") { " " + inlines(it.children).replace('\n', ' ') + " |" }
            val header = rows.first()
            val align =
                "|" +
                    header.children.joinToString("") {
                        when (it.align) {
                            "left" -> " :-- |"
                            "right" -> " --: |"
                            "center" -> " :-: |"
                            else -> " --- |"
                        }
                    }
            val out = StringBuilder(row(header)).append('\n').append(align)
            rows.drop(1).forEach { out.append('\n').append(row(it)) }
            val caption = table.children.filterIsInstance<Caption>().firstOrNull()
            if (caption != null && caption.children.isNotEmpty()) out.append("\n\n*").append(inlines(caption.children).trim()).append('*')
            return out.toString()
        }

        fun inlines(nodes: List<Inline>): String {
            val out = StringBuilder()
            nodes.forEach { inline(it, out) }
            return out.toString()
        }

        fun inline(
            node: Inline,
            out: StringBuilder,
        ) {
            when (node) {
                is Str -> out.append(escape(node.text))
                is SoftBreak -> out.append(' ')
                is HardBreak -> out.append("\\\n")
                is NonBreakingSpace -> out.append('\u00A0')
                is Symb -> out.append(':').append(node.alias).append(':')
                is Verbatim -> out.append(codeSpan(node.text))
                is RawInline -> {}
                is InlineMath -> out.append(codeSpan(node.text))
                is DisplayMath -> out.append(codeSpan(node.text))
                is Url -> out.append('<').append(node.text).append('>')
                is Email -> out.append('<').append(node.text).append('>')
                is FootnoteReference -> out.append("\\[").append(footnoteNumber(node.text)).append("\\]")
                is SmartPunctuation -> out.append(smart(node.type, node.text))
                is Emph -> delimited("*", node.children, out)
                is Strong -> delimited("**", node.children, out)
                is Delete -> delimited("~~", node.children, out)
                is Link -> link(node.children, node.destination, node.reference, image = false, out)
                is Image -> link(node.children, node.destination, node.reference, image = true, out)
                is Span -> node.children.forEach { inline(it, out) }
                is Mark -> node.children.forEach { inline(it, out) }
                is Insert -> node.children.forEach { inline(it, out) }
                is Superscript -> node.children.forEach { inline(it, out) }
                is Subscript -> node.children.forEach { inline(it, out) }
                is DoubleQuoted -> {
                    out.append('\u201C')
                    node.children.forEach { inline(it, out) }
                    out.append('\u201D')
                }
                is SingleQuoted -> {
                    out.append('\u2018')
                    node.children.forEach { inline(it, out) }
                    out.append('\u2019')
                }
            }
        }

        /**
         * Markdown emphasis can't open after or close before a space, so spaces move outside. A
         * node whose only child is the same kind is written once: Djot reads a markdown habit like
         * `**bold**` as strong inside strong, and `****bold****` would not be bold in markdown.
         */
        fun delimited(
            marker: String,
            children: List<Inline>,
            out: StringBuilder,
        ) {
            var content = children
            while (content.size == 1 && sameKind(marker, content[0])) content = childrenOf(content[0])
            val inner = inlines(content)
            val trimmed = inner.trim(' ')
            if (trimmed.isEmpty()) {
                out.append(inner)
                return
            }
            out.append(inner.substring(0, inner.indexOf(trimmed)))
            out.append(marker).append(trimmed).append(marker)
            out.append(inner.substring(inner.indexOf(trimmed) + trimmed.length))
        }

        fun sameKind(
            marker: String,
            node: Inline,
        ) = (marker == "*" && node is Emph) || (marker == "**" && node is Strong) || (marker == "~~" && node is Delete)

        fun childrenOf(node: Inline): List<Inline> =
            when (node) {
                is Emph -> node.children
                is Strong -> node.children
                is Delete -> node.children
                else -> emptyList()
            }

        fun link(
            children: List<Inline>,
            destination: String?,
            reference: String?,
            image: Boolean,
            out: StringBuilder,
        ) {
            val target =
                when {
                    destination != null -> destination
                    reference.isNullOrEmpty() -> null
                    else -> (doc.references[reference] ?: doc.autoReferences[reference])?.destination ?: resolveWikilink(reference)
                }
            val text = if (image) escape(getStringContent(Para(children.toMutableList()))) else inlines(children)
            if (target == null) {
                // Nothing to link to: keep the words.
                out.append(text)
                return
            }
            if (image) out.append('!')
            out
                .append('[')
                .append(text)
                .append("](")
                .append(destinationOf(target))
                .append(')')
        }

        fun footnoteNumber(label: String): Int {
            val index = footnoteOrder.indexOf(label)
            if (index >= 0) return index + 1
            footnoteOrder.add(label)
            return footnoteOrder.size
        }
    }

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
            else -> escape(text)
        }

    // Every character markdown could read as syntax in running text. Bare URLs are copied as they
    // are, so the renderer links them whole.
    private const val SPECIAL = "\\`*_[]<>|~&#!"

    private fun escape(text: String): String {
        val out = StringBuilder(text.length + 8)
        var i = 0
        while (i < text.length) {
            val urlEnd = bareUrlEnd(text, i)
            if (urlEnd > i) {
                out.append(text, i, urlEnd)
                i = urlEnd
                continue
            }
            val c = text[i]
            if (c in SPECIAL) out.append('\\')
            out.append(c)
            i++
        }
        return out.toString()
    }

    /** The end of a bare `http(s)://` URL starting at [start], or [start] if none starts there. */
    private fun bareUrlEnd(
        text: String,
        start: Int,
    ): Int {
        if (start > 0 && text[start - 1].isLetterOrDigit()) return start
        val scheme =
            when {
                text.startsWith("https://", start, ignoreCase = true) -> 8
                text.startsWith("http://", start, ignoreCase = true) -> 7
                else -> return start
            }
        var end = start + scheme
        while (end < text.length && !text[end].isWhitespace() && text[end] != '<' && text[end] != '>') end++
        return if (end > start + scheme) end else start
    }

    /** A paragraph line starting with block syntax (`#`, `>`, `-`, `+`, `=`, `1.`) is escaped. */
    private fun lineStarts(text: String): String =
        text.lines().joinToString("\n") { line ->
            when {
                line.isEmpty() -> line
                line[0] in "#>-+=" -> "\\" + line
                else -> {
                    var d = 0
                    while (d < line.length && line[d].isDigit()) d++
                    if (d in 1..9 && d < line.length && (line[d] == '.' || line[d] == ')')) {
                        line.substring(0, d) + "\\" + line.substring(d)
                    } else {
                        line
                    }
                }
            }
        }

    private fun codeSpan(text: String): String {
        val flat = text.replace('\n', ' ')
        val fence = "`".repeat(longestRun(flat, '`') + 1)
        val pad = if (flat.startsWith("`") || flat.endsWith("`") || flat.isBlank()) " " else ""
        return fence + pad + flat + pad + fence
    }

    // A destination markdown would end early or misread goes in angle brackets.
    private fun destinationOf(target: String): String =
        if (target.any { it == ' ' || it == '(' || it == ')' || it == '<' || it == '>' }) {
            "<" + target.replace("<", "%3C").replace(">", "%3E") + ">"
        } else {
            target
        }

    private fun longestRun(
        text: String,
        c: Char,
    ): Int {
        var best = 0
        var run = 0
        for (ch in text) {
            run = if (ch == c) run + 1 else 0
            if (run > best) best = run
        }
        return best
    }
}

/**
 * The shared cache in front of [DjotToMarkdown], as [CachedAsciiDocToMarkdown] is for AsciiDoc:
 * the conversion runs on the composition thread, and `remember` is dropped when a card scrolls out
 * of a lazy list. Keyed by event id: an edited article is a new event, so a new entry.
 */
object CachedDjotToMarkdown {
    private val cache = ConcurrentLruCache<String, String>(60)

    fun convert(
        eventId: String,
        djot: String,
        resolveWikilink: (String) -> String?,
    ): String = cache.get(eventId) ?: DjotToMarkdown.convert(djot, resolveWikilink).also { cache.put(eventId, it) }

    fun trimToSize(maxItems: Int) = cache.trimToSize(maxItems)
}
