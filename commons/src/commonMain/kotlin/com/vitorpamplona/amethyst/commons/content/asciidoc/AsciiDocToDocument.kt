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
package com.vitorpamplona.amethyst.commons.content.asciidoc

import com.vitorpamplona.amethyst.commons.content.Block
import com.vitorpamplona.amethyst.commons.content.Callout
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
import com.vitorpamplona.amethyst.commons.content.Link
import com.vitorpamplona.amethyst.commons.content.ListBlock
import com.vitorpamplona.amethyst.commons.content.ListItem
import com.vitorpamplona.amethyst.commons.content.NostrInlinePass
import com.vitorpamplona.amethyst.commons.content.Paragraph
import com.vitorpamplona.amethyst.commons.content.Quote
import com.vitorpamplona.amethyst.commons.content.SoftBreak
import com.vitorpamplona.amethyst.commons.content.Strong
import com.vitorpamplona.amethyst.commons.content.Subscript
import com.vitorpamplona.amethyst.commons.content.Superscript
import com.vitorpamplona.amethyst.commons.content.Table
import com.vitorpamplona.amethyst.commons.content.TableCell
import com.vitorpamplona.amethyst.commons.content.Text
import com.vitorpamplona.amethyst.commons.content.ThematicBreak
import com.vitorpamplona.amethyst.commons.richtext.MarkdownChunks

/**
 * AsciiDoc into a [Document]: NKBIP-01 publication sections and the wiki articles written before
 * NIP-54 moved to Djot.
 *
 * It reads the constructs prose uses: section titles, paragraphs (with `+` and `%hardbreaks` line
 * breaks), nested `*` and `.` lists, checklists, `term::` description lists, admonitions (`NOTE:`
 * paragraphs and `[NOTE]` blocks) as callouts, listing, literal and source blocks, quote, sidebar,
 * example and open blocks, `|===` tables, block images, thematic and page breaks; and inline, bold,
 * italic, monospace and highlight (constrained and unconstrained), super- and subscript, `link:`,
 * URL and `mailto:` macros, inline images, `<<xref>>`, `[[wikilinks]]` and footnotes. Comments and
 * attributes are skipped. Anything else stays the text it was: an unread construct degrades to its
 * words, never to mangled output.
 *
 * What is left as plain text goes through [NostrInlinePass], so mentions, hashtags and media come
 * out as in a note. [resolveWikilink] turns a `[[target]]` into a destination, or null to keep its
 * words.
 */
class AsciiDocToDocument(
    private val nostr: NostrInlinePass = NostrInlinePass.NONE,
    private val resolveWikilink: (String) -> String? = { null },
) {
    fun parse(asciidoc: String): Document {
        val src = if (asciidoc.contains('\r')) asciidoc.replace("\r\n", "\n").replace('\r', '\n') else asciidoc
        val parser = Parser(src.split('\n'))
        val blocks = parser.blocks(0, parser.lines.size).toMutableList()
        if (parser.footnotes.isNotEmpty()) blocks.add(Footnotes(parser.footnotes))
        return Document(blocks)
    }

    /** What a `[...]` line or a `.Title` line says about the block below it. */
    private class Pending(
        var style: String? = null,
        var language: String? = null,
        var hardbreaks: Boolean = false,
        var header: Boolean = false,
        var title: String? = null,
    ) {
        fun clear() {
            style = null
            language = null
            hardbreaks = false
            header = false
            title = null
        }
    }

    private inner class Parser(
        val lines: List<String>,
    ) {
        val footnotes = ArrayList<Footnote>()
        var documentHardbreaks = false

        /** The blocks in lines [from] until [to]. */
        fun blocks(
            from: Int,
            to: Int,
        ): List<Block> {
            val out = ArrayList<Block>()
            val pending = Pending()
            var i = from
            while (i < to) {
                val line = lines[i]
                val trimmed = line.trim()

                if (trimmed.isEmpty()) {
                    i++
                    continue
                }

                // Comment blocks and line comments.
                if (trimmed == "////") {
                    i = closing(i, to, "////") + 1
                    continue
                }
                if (trimmed.startsWith("//")) {
                    i++
                    continue
                }

                // Document attributes: metadata, except :hardbreaks:.
                ATTRIBUTE_ENTRY.matchEntire(trimmed)?.let { m ->
                    if (m.groupValues[1].startsWith("hardbreaks")) documentHardbreaks = true
                    i++
                    continue
                }

                // Block attributes and titles describe the next block.
                BLOCK_ATTRIBUTES.matchEntire(trimmed)?.let { m ->
                    readAttributes(m.groupValues[1], pending)
                    i++
                    continue
                }
                if (trimmed.length > 1 && trimmed[0] == '.' && trimmed[1] != '.' && !trimmed[1].isWhitespace()) {
                    pending.title = trimmed.substring(1)
                    i++
                    continue
                }

                HEADING.matchEntire(trimmed)?.let { m ->
                    val text = m.groupValues[2].trim()
                    out.add(Heading(m.groupValues[1].length.coerceIn(1, 6), inlines(text), MarkdownChunks.anchorOf(text)))
                    pending.clear()
                    i++
                    continue
                }

                if (trimmed == "'''" || trimmed == "<<<" || trimmed == "---" || trimmed == "***") {
                    out.add(ThematicBreak)
                    pending.clear()
                    i++
                    continue
                }

                BLOCK_IMAGE.matchEntire(trimmed)?.let { m ->
                    val alt = positional(m.groupValues[2])?.substringBefore(',')?.trim() ?: pending.title ?: ""
                    out.add(Paragraph(listOf(Image(m.groupValues[1], alt))))
                    pending.clear()
                    i++
                    continue
                }

                // Delimited blocks.
                delimiter(trimmed)?.let { delimiter ->
                    val end = closing(i, to, delimiter)
                    out.addAll(delimited(delimiter, i + 1, end, pending))
                    pending.clear()
                    i = end + 1
                    continue
                }

                // Lists.
                if (listMarker(trimmed) != null) {
                    var end = i
                    while (end < to && isListLine(end, to)) end++
                    out.addAll(list(i, end))
                    pending.clear()
                    i = end
                    continue
                }
                DESCRIPTION.matchEntire(trimmed)?.let {
                    var end = i
                    while (end < to && lines[end].isNotBlank() && (DESCRIPTION.matchEntire(lines[end].trim()) != null || end > i)) end++
                    out.add(descriptions(i, end))
                    pending.clear()
                    i = end
                    continue
                }

                // A paragraph: lines until a blank one or the start of a block.
                var end = i + 1
                while (end < to && lines[end].isNotBlank() && !startsBlock(lines[end].trim())) end++
                val hardbreaks = pending.hardbreaks || documentHardbreaks
                val text = lines.subList(i, end)
                ADMONITION.matchEntire(text.first().trim())?.let { m ->
                    val first = listOf(m.groupValues[2]) + text.drop(1)
                    out.add(Callout(m.groupValues[1].lowercase(), listOf(Paragraph(paragraph(first, hardbreaks)))))
                } ?: run {
                    val style = pending.style
                    when {
                        style != null && style in ADMONITIONS -> out.add(Callout(style.lowercase(), listOf(Paragraph(paragraph(text, hardbreaks)))))
                        style == "quote" || style == "verse" -> out.add(Quote(listOf(Paragraph(paragraph(text, hardbreaks || style == "verse")))))
                        style == "source" || style == "listing" || style == "literal" -> out.add(CodeBlock(text.joinToString("\n"), pending.language))
                        // A literal paragraph: indented lines are shown as written.
                        text.first().startsWith(' ') || text.first().startsWith('\t') -> out.add(CodeBlock(dedent(text), null))
                        else -> {
                            pending.title?.let { out.add(Paragraph(listOf(Strong(inlines(it))))) }
                            out.add(Paragraph(paragraph(text, hardbreaks)))
                        }
                    }
                }
                pending.clear()
                i = end
            }
            return out
        }

        fun startsBlock(trimmed: String): Boolean =
            HEADING.matches(trimmed) ||
                delimiter(trimmed) != null ||
                BLOCK_ATTRIBUTES.matches(trimmed) ||
                BLOCK_IMAGE.matches(trimmed) ||
                (listMarker(trimmed) != null) ||
                trimmed == "'''" ||
                trimmed == "<<<"

        fun closing(
            open: Int,
            to: Int,
            delimiter: String,
        ): Int {
            for (j in open + 1 until to) if (lines[j].trim() == delimiter) return j
            // An unclosed block runs to the end.
            return to
        }

        fun delimited(
            delimiter: String,
            from: Int,
            to: Int,
            pending: Pending,
        ): List<Block> {
            val content = lines.subList(from, to.coerceAtMost(lines.size))
            val style = pending.style
            return when (delimiter[0]) {
                '-' ->
                    if (delimiter == "--") {
                        // An open block takes the style above it.
                        styled(style, blocks(from, to))
                    } else {
                        listOf(CodeBlock(content.joinToString("\n"), pending.language))
                    }
                '.' -> listOf(CodeBlock(content.joinToString("\n")))
                '+' -> listOf(CodeBlock(content.joinToString("\n")))
                '_' ->
                    if (style == "verse") {
                        listOf(Quote(listOf(Paragraph(paragraph(content, hardbreaks = true)))))
                    } else {
                        listOf(Quote(blocks(from, to)))
                    }
                '=' -> listOf(styled(style, blocks(from, to)).let { if (it.size == 1) it[0] else Div(it, listOf("example")) })
                '*' -> listOf(Div(blocks(from, to), listOf("sidebar")))
                '|' -> listOfNotNull(table(content, pending))
                else -> blocks(from, to)
            }
        }

        fun styled(
            style: String?,
            blocks: List<Block>,
        ): List<Block> =
            when {
                style != null && style in ADMONITIONS -> listOf(Callout(style.lowercase(), blocks))
                style == "quote" -> listOf(Quote(blocks))
                else -> blocks
            }

        fun table(
            content: List<String>,
            pending: Pending,
        ): Block? {
            // Rows: cells start at `|`; a row ends at a blank line, or when it has as many cells as
            // the first row.
            val rows = ArrayList<List<String>>()
            var current = ArrayList<String>()
            var columns = 0
            // Asciidoctor's implicit header: a first line of cells with a blank line under it.
            var firstRowBlankAfter = content.size > 1 && content[0].isNotBlank() && content[1].isBlank()
            content.forEachIndexed { index, line ->
                if (line.isBlank()) {
                    if (current.isNotEmpty()) {
                        if (rows.isEmpty()) firstRowBlankAfter = true
                        rows.add(current)
                        if (columns == 0) columns = current.size
                        current = ArrayList()
                    }
                    return@forEachIndexed
                }
                val cells =
                    line
                        .trim()
                        .split('|')
                        .drop(1)
                        .map { it.trim() }
                if (cells.isEmpty()) return@forEachIndexed
                current.addAll(cells)
                if (columns == 0 && rows.isEmpty() && index == 0) {
                    // The first line's cells fix the column count.
                    columns = cells.size
                }
                if (columns > 0 && current.size >= columns) {
                    rows.add(current)
                    current = ArrayList()
                }
            }
            if (current.isNotEmpty()) rows.add(current)
            if (rows.isEmpty()) return null
            // A header: asked for, or (as Asciidoctor reads it) a first line of cells followed by a blank one.
            val hasHeader = pending.header || firstRowBlankAfter
            val header = if (hasHeader) rows.first().map { TableCell(inlines(it)) } else emptyList()
            val body = (if (hasHeader) rows.drop(1) else rows).map { row -> row.map { TableCell(inlines(it)) } }
            val width = maxOf(header.size, body.maxOfOrNull { it.size } ?: 0)
            val caption = pending.title?.let { inlines(it) }
            return Table(header, body, List(width) { ColumnAlignment.NONE }, caption)
        }

        fun isListLine(
            index: Int,
            to: Int,
        ): Boolean {
            val line = lines[index]
            if (line.isBlank()) {
                // A blank line between two items of the same list does not end it.
                val next = (index + 1 until to).firstOrNull { lines[it].isNotBlank() } ?: return false
                return listMarker(lines[next].trim()) != null
            }
            val trimmed = line.trim()
            return listMarker(trimmed) != null || trimmed == "+" || !startsBlock(trimmed)
        }

        /** The item's marker: `*`..`*****` and `-` bullets, `.`..`.....` numbers. */
        fun listMarker(trimmed: String): String? = LIST_ITEM.matchEntire(trimmed)?.groupValues?.get(1)

        /** The lists in lines [from] until [to]: one, or several when the marker kind changes. */
        fun list(
            from: Int,
            to: Int,
        ): List<Block> {
            class Item(
                val marker: String,
                val lines: MutableList<String>,
            )
            val items = ArrayList<Item>()
            for (j in from until to) {
                val trimmed = lines[j].trim()
                val m = LIST_ITEM.matchEntire(trimmed)
                if (m != null) {
                    items.add(Item(m.groupValues[1], mutableListOf(m.groupValues[2])))
                } else if (items.isNotEmpty() && trimmed.isNotEmpty()) {
                    // A `+` joins the next block to the item; continuation lines join its text.
                    items.last().lines.add(if (trimmed == "+") "" else trimmed)
                }
            }
            val pairs = items.map { it.marker to it.lines }
            val lists = ArrayList<Block>()
            var next = 0
            while (next < pairs.size) {
                val (list, end) = nest(pairs, next)
                lists.add(list)
                // A marker no list here could take (a deeper one first) would not move: take it alone.
                next = if (end > next) end else next + 1
            }
            return lists
        }

        /** Builds a list from items whose markers say their depth, from [start]; returns it and the next index. */
        fun nest(
            items: List<Pair<String, List<String>>>,
            start: Int,
        ): Pair<ListBlock, Int> {
            val marker = items[start].first
            val ordered = marker.startsWith('.')
            val out = ArrayList<ListItem>()
            var i = start
            while (i < items.size) {
                val (itemMarker, itemLines) = items[i]
                if (itemMarker != marker) {
                    // A deeper marker opens a nested list in the previous item; any other ends this one.
                    if (out.isNotEmpty() && depth(itemMarker) > depth(marker)) {
                        val (nested, next) = nest(items, i)
                        val last = out.removeAt(out.size - 1)
                        out.add(ListItem(last.blocks + nested, last.checked))
                        i = next
                        continue
                    }
                    break
                }
                val first = itemLines.first()
                val check = CHECKBOX.matchEntire(first)
                val text = if (check != null) listOf(check.groupValues[2]) + itemLines.drop(1) else itemLines
                // Lines after a `+` are a block of their own.
                val paragraphs = text.splitAt { it.isEmpty() }.filter { it.isNotEmpty() }.map { Paragraph(paragraph(it, false)) }
                out.add(ListItem(paragraphs, check?.let { it.groupValues[1].isNotBlank() }))
                i++
            }
            return ListBlock(out, ordered = ordered) to i
        }

        fun depth(marker: String) = if (marker == "-") 1 else marker.length

        fun descriptions(
            from: Int,
            to: Int,
        ): Block {
            val items = ArrayList<Definition>()
            var term: String? = null
            val body = ArrayList<String>()

            fun close() {
                term?.let { items.add(Definition(inlines(it), if (body.isEmpty()) emptyList() else listOf(Paragraph(paragraph(body.toList(), false))))) }
                body.clear()
            }
            for (j in from until to) {
                val trimmed = lines[j].trim()
                val m = DESCRIPTION.matchEntire(trimmed)
                if (m != null) {
                    close()
                    term = m.groupValues[1]
                    m.groupValues[2]
                        .trim()
                        .takeIf { it.isNotEmpty() }
                        ?.let { body.add(it) }
                } else if (trimmed.isNotEmpty() && trimmed != "+") {
                    body.add(trimmed)
                }
            }
            close()
            return DefinitionList(items)
        }

        fun dedent(text: List<String>): String {
            val indent = text.filter { it.isNotBlank() }.minOfOrNull { line -> line.takeWhile { it == ' ' || it == '\t' }.length } ?: 0
            return text.joinToString("\n") { if (it.length >= indent) it.substring(indent) else it.trimStart() }
        }

        /** A paragraph's lines: joined as one, or kept as lines with [hardbreaks] or a trailing ` +`. */
        fun paragraph(
            text: List<String>,
            hardbreaks: Boolean,
        ): List<Inline> {
            val out = ArrayList<Inline>()
            text.forEachIndexed { index, raw ->
                val line = raw.trim()
                val explicitBreak = line.endsWith(" +") || line == "+"
                val content = if (explicitBreak) line.removeSuffix("+").trimEnd() else line
                out.addAll(inlines(content))
                if (index < text.size - 1) out.add(if (hardbreaks || explicitBreak) HardBreak else SoftBreak)
            }
            return out
        }

        fun readAttributes(
            raw: String,
            pending: Pending,
        ) {
            val parts = raw.split(',').map { it.trim() }
            val first = parts.firstOrNull() ?: return
            val style =
                first
                    .substringBefore('%')
                    .substringBefore('#')
                    .substringBefore('.')
                    .trim()
            if (style.isNotEmpty() && !style.contains('=')) pending.style = if (style.uppercase() in ADMONITIONS) style.uppercase() else style.lowercase()
            if (pending.style == "source") pending.language = parts.getOrNull(1)?.takeIf { it.isNotEmpty() && !it.contains('=') }
            if (raw.contains("%hardbreaks") || raw.contains("hardbreaks-option")) pending.hardbreaks = true
            if (raw.contains("%header") || raw.contains("options=\"header\"") || raw.contains("options=header")) pending.header = true
        }

        // ---- Inlines ----------------------------------------------------------------------------

        fun inlines(
            text: String,
            inLink: Boolean = false,
        ): List<Inline> = InlineParser(text, inLink).parse()

        private inner class InlineParser(
            val text: String,
            val inLink: Boolean,
        ) {
            val out = ArrayList<Inline>()
            val pending = StringBuilder()

            fun flush() {
                if (pending.isEmpty()) return
                val words = typography(pending.toString())
                if (inLink) out.add(Text(words)) else out.addAll(nostr.inlines(words))
                pending.setLength(0)
            }

            fun add(inline: Inline) {
                flush()
                out.add(inline)
            }

            fun parse(): List<Inline> {
                var i = 0
                while (i < text.length) {
                    val next = step(i)
                    if (next > i) {
                        i = next
                    } else {
                        pending.append(text[i])
                        i++
                    }
                }
                flush()
                return out
            }

            /** Reads a construct at [i]; returns where it ends, or [i] when none starts there. */
            fun step(i: Int): Int {
                val c = text[i]
                // A backslash keeps the next mark literal.
                if (c == '\\' && i + 1 < text.length && text[i + 1] in MARKS) {
                    pending.append(text[i + 1])
                    return i + 2
                }
                if (!inLink) {
                    macro(i)?.let { return it }
                }
                return when (c) {
                    '*' -> formatted(i, '*') { Strong(it) }
                    '_' -> formatted(i, '_') { Emphasis(it) }
                    '#' -> formatted(i, '#') { Highlight(it) }
                    '`' -> code(i)
                    '^' -> simple(i, '^') { Superscript(it) }
                    '~' -> simple(i, '~') { Subscript(it) }
                    '+' -> passthrough(i)
                    else -> i
                }
            }

            /** Links, images, cross references, wikilinks and footnotes. */
            fun macro(i: Int): Int? {
                val rest = text.substring(i)
                // Only at a word's start: `name_link:` is a word, not a macro.
                val atWordStart = i == 0 || !text[i - 1].isLetterOrDigit()
                if (atWordStart) {
                    INLINE_IMAGE.matchAt(text, i)?.let { m ->
                        add(Image(m.groupValues[1], positional(m.groupValues[2])?.substringBefore(',')?.trim() ?: ""))
                        return m.range.last + 1
                    }
                    LINK_MACRO.matchAt(text, i)?.let { m ->
                        val url = m.groupValues[1]
                        val label = positional(m.groupValues[2])?.removeSuffix("^")?.ifBlank { null }
                        add(Link(url, label?.let { inlines(it, inLink = true) } ?: listOf(Text(url))))
                        return m.range.last + 1
                    }
                    URL_WITH_LABEL.matchAt(text, i)?.let { m ->
                        val url = m.groupValues[1]
                        val label = positional(m.groupValues[2])?.removeSuffix("^")?.ifBlank { null }
                        add(Link(url, label?.let { inlines(it, inLink = true) } ?: listOf(Text(url))))
                        return m.range.last + 1
                    }
                    MAILTO.matchAt(text, i)?.let { m ->
                        val address = m.groupValues[1]
                        add(Link("mailto:$address", inlines(positional(m.groupValues[2])?.ifBlank { null } ?: address, inLink = true)))
                        return m.range.last + 1
                    }
                }
                if (rest.startsWith("footnote:[")) {
                    val close = matchingBracket(i + "footnote:".length) ?: return null
                    val note = text.substring(i + "footnote:[".length, close)
                    val number = footnotes.size + 1
                    footnotes.add(Footnote(number.toString(), number, listOf(Paragraph(inlines(note)))))
                    // `claim footnote:[...]`: the number sits against the word it marks.
                    while (pending.isNotEmpty() && pending.last() == ' ') pending.setLength(pending.length - 1)
                    add(FootnoteReference(number.toString(), number))
                    return close + 1
                }
                if (rest.startsWith("kbd:[")) {
                    val close = matchingBracket(i + "kbd:".length) ?: return null
                    add(Code(text.substring(i + "kbd:[".length, close)))
                    return close + 1
                }
                if (rest.startsWith("pass:[")) {
                    val close = matchingBracket(i + "pass:".length) ?: return null
                    pending.append(text, i + "pass:[".length, close)
                    return close + 1
                }
                if (rest.startsWith("<<")) {
                    val close = text.indexOf(">>", i + 2)
                    if (close < 0) return null
                    val inner = text.substring(i + 2, close)
                    val id = inner.substringBefore(',').trim()
                    val label = inner.substringAfter(',', "").trim().ifEmpty { id }
                    add(Link("#" + id, inlines(label, inLink = true)))
                    return close + 2
                }
                if (rest.startsWith("[[")) {
                    val close = text.indexOf("]]", i + 2)
                    if (close < 0) return null
                    val inner = text.substring(i + 2, close)
                    if (inner.isBlank() || inner.contains('[')) return null
                    val target = inner.substringBefore('|').trim()
                    val label = inner.substringAfter('|', target).trim().ifBlank { target }
                    val uri = resolveWikilink(target)
                    if (uri != null) add(Link(uri, listOf(Text(label)))) else pending.append(label)
                    return close + 2
                }
                return null
            }

            fun matchingBracket(open: Int): Int? {
                var depth = 0
                for (j in open until text.length) {
                    when (text[j]) {
                        '[' -> depth++
                        ']' -> {
                            depth--
                            if (depth == 0) return j
                        }
                    }
                }
                return null
            }

            /**
             * `**x**` anywhere, or `*x*` at word boundaries (AsciiDoc's constrained form): its content
             * parsed again, so marks nest.
             */
            fun formatted(
                i: Int,
                mark: Char,
                wrap: (List<Inline>) -> Inline,
            ): Int {
                val double = i + 1 < text.length && text[i + 1] == mark
                if (double) {
                    val close = text.indexOf("$mark$mark", i + 2)
                    if (close > i + 2) {
                        add(wrap(inlines(text.substring(i + 2, close), inLink)))
                        return close + 2
                    }
                }
                if (!opensConstrained(i)) return i
                var j = i + 1
                while (j < text.length) {
                    if (text[j] == mark && closesConstrained(j)) {
                        if (j == i + 1) return i
                        add(wrap(inlines(text.substring(i + 1, j), inLink)))
                        return j + 1
                    }
                    j++
                }
                return i
            }

            fun opensConstrained(i: Int): Boolean {
                val before = if (i == 0) ' ' else text[i - 1]
                val after = if (i + 1 < text.length) text[i + 1] else ' '
                return !before.isLetterOrDigit() && before != text[i] && !after.isWhitespace()
            }

            fun closesConstrained(j: Int): Boolean {
                val before = text[j - 1]
                val after = if (j + 1 < text.length) text[j + 1] else ' '
                return !before.isWhitespace() && !after.isLetterOrDigit() && after != text[j]
            }

            /** Monospace: `x` or ``x``, as written. */
            fun code(i: Int): Int {
                val double = i + 1 < text.length && text[i + 1] == '`'
                val open = if (double) "``" else "`"
                val close = text.indexOf(open, i + open.length)
                if (close < 0 || close == i + open.length) return i
                if (!double && !opensConstrained(i)) return i
                add(Code(text.substring(i + open.length, close)))
                return close + open.length
            }

            /** `^sup^` and `~sub~`: no spaces inside. */
            fun simple(
                i: Int,
                mark: Char,
                wrap: (List<Inline>) -> Inline,
            ): Int {
                val close = text.indexOf(mark, i + 1)
                if (close <= i + 1) return i
                val inner = text.substring(i + 1, close)
                if (inner.any { it.isWhitespace() }) return i
                add(wrap(inlines(inner, inLink)))
                return close + 1
            }

            /** `+text+` and `+++text+++`: the text as written, nothing read in it. */
            fun passthrough(i: Int): Int {
                val triple = text.startsWith("+++", i)
                val mark = if (triple) "+++" else "+"
                if (!triple && !opensConstrained(i)) return i
                val close = text.indexOf(mark, i + mark.length)
                if (close < 0 || close == i + mark.length) return i
                pending.append(text, i + mark.length, close)
                return close + mark.length
            }
        }
    }

    companion object {
        private val HEADING = Regex("""^(={1,6})\s+(\S.*)$""")
        private val ATTRIBUTE_ENTRY = Regex("""^:([A-Za-z0-9_][A-Za-z0-9_-]*!?):.*$""")
        private val BLOCK_ATTRIBUTES = Regex("""^\[([^\[\]]*)]$""")
        private val BLOCK_IMAGE = Regex("""^image::(\S+?)\[(.*)]$""")
        private val LIST_ITEM = Regex("""^(\*{1,5}|\.{1,5}|-)\s+(.*)$""")
        private val CHECKBOX = Regex("""^\[([ xX*])]\s+(.*)$""")
        private val DESCRIPTION = Regex("""^(.+?)(?::{2,4}|;;)(?:\s+(.*))?$""")
        private val ADMONITION = Regex("""^(NOTE|TIP|IMPORTANT|WARNING|CAUTION):\s+(.*)$""")
        private val ADMONITIONS = setOf("NOTE", "TIP", "IMPORTANT", "WARNING", "CAUTION")
        private val INLINE_IMAGE = Regex("""image:([^\s\[]+)\[([^\]]*)]""")
        private val LINK_MACRO = Regex("""link:([^\s\[]+)\[([^\]]*)]""")
        private val URL_WITH_LABEL = Regex("""((?:https?|ftp|irc)://[^\s\[\]]+)\[([^\]]*)]""")
        private val MAILTO = Regex("""mailto:([^\s\[]+)\[([^\]]*)]""")
        private const val MARKS = "*_`#^~+[<\\"

        private val DELIMITERS = setOf("----", "....", "++++", "____", "====", "****", "|===", "--")

        /** A delimited block's fence: four or more of the same character (or `--` and `|===`). */
        fun delimiter(trimmed: String): String? {
            if (trimmed in DELIMITERS) return trimmed
            if (trimmed.length > 4 && trimmed.all { it == trimmed[0] } && trimmed[0] in "-._=*+") return trimmed
            return null
        }

        /**
         * A macro's text: its first positional attribute. `[Rupelian,role=orange]` is the text
         * `Rupelian` and an attribute, not a label with a comma in it.
         */
        fun positional(raw: String): String? {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) return null
            if (trimmed.startsWith('"')) return trimmed.removePrefix("\"").substringBefore('"')
            if (!trimmed.contains('=')) return trimmed
            return trimmed
                .split(',')
                .filterNot { it.contains('=') }
                .joinToString(",")
                .trim()
                .ifEmpty { null }
        }

        /** Asciidoctor's replacements: (C), (R), (TM), -- and ... */
        fun typography(text: String): String {
            if (!text.contains('(') && !text.contains("--") && !text.contains("...")) return text
            return text
                .replace("(C)", "\u00A9")
                .replace("(R)", "\u00AE")
                .replace("(TM)", "\u2122")
                .replace(" -- ", " \u2014 ")
                .replace("...", "\u2026")
        }
    }
}

/** Splits [this] into runs at the items [at] matches, which are dropped. */
private inline fun <T> List<T>.splitAt(at: (T) -> Boolean): List<List<T>> {
    val out = ArrayList<List<T>>()
    var current = ArrayList<T>()
    forEach {
        if (at(it)) {
            out.add(current)
            current = ArrayList()
        } else {
            current.add(it)
        }
    }
    out.add(current)
    return out
}
