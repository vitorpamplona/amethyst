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

import org.nibor.autolink.LinkExtractor
import org.nibor.autolink.LinkType
import java.util.EnumSet

/**
 * Keeps bare URLs whole through the markdown parser.
 *
 * The parser links a bare URL only after emphasis has been parsed (commonmark's autolink
 * extension runs over the finished text nodes), so the `_`, `*` and `~` in a URL's path are read
 * as markers first: `https://m.media-amazon.com/images/I/71zvSJXs2KL._SL1499_.jpg` became a link
 * to `…/71zvSJXs2KL`, an italic `SL1499` and a stray `.jpg`, and the image never showed.
 *
 * [protect] writes each such URL as a CommonMark autolink, `<url>`, which is parsed before
 * emphasis and renders as the same link (text = destination, so link previews and inline images
 * still apply). URLs without those characters are left as they are, and so is anything where
 * brackets would change the meaning: code, existing links and autolinks, HTML tags and reference
 * definitions.
 */
object MarkdownBareUrls {
    // The finder commonmark's autolink extension uses, so the URLs found here end where its do.
    private val urlFinder = LinkExtractor.builder().linkTypes(EnumSet.of(LinkType.URL)).build()

    fun protect(content: String): String {
        if (!content.contains("://") || content.none { it.isEmphasisMarker() }) return content

        val out = StringBuilder(content.length + 16)
        var changed = false
        // The open fence's marker run (```` ``` ````, `~~~~`), while inside a fenced code block.
        var fence: String? = null

        var start = 0
        while (true) {
            val newline = content.indexOf('\n', start)
            val end = if (newline < 0) content.length else newline
            val line = content.substring(start, end)

            val indent = indentOf(line)
            val body = line.trimStart(' ', '\t')
            val openFence = fence
            if (openFence != null) {
                if (indent <= 3 && body.startsWith(openFence) && body.trimStart(openFence[0]).isBlank()) fence = null
                out.append(line)
            } else if (indent <= 3 && (body.startsWith("```") || body.startsWith("~~~"))) {
                fence = body.takeWhile { it == body[0] }
                out.append(line)
            } else if (indent >= 4 || referenceDefinition.matches(line)) {
                // Possibly an indented code block, where brackets would show; and a reference
                // definition's URL is a destination, not text.
                out.append(line)
            } else {
                val protected = protectLine(line)
                if (protected !== line) changed = true
                out.append(protected)
            }

            if (newline < 0) break
            out.append('\n')
            start = newline + 1
        }

        return if (changed) out.toString() else content
    }

    /** [line] with its bare URLs bracketed, or [line] itself when none needed it. */
    private fun protectLine(line: String): String {
        var out: StringBuilder? = null
        var copied = 0
        val skipped = spansToLeaveAlone(line)

        for (span in urlFinder.extractLinks(line)) {
            val begin = span.beginIndex
            // GFM leaves trailing `*`, `_` and `~` out of a bare URL (they close the emphasis the
            // URL sits in) and so does the trailing punctuation they hid. The finder keeps them.
            var end = span.endIndex
            while (end > begin && line[end - 1] in TRAILING_NOT_URL) end--

            if (end <= begin || skipped.any { begin < it.last + 1 && it.first < end }) continue
            if (!line.hasEmphasisMarker(begin, end) || line.hasCharThatEndsAnAutolink(begin, end)) continue

            val builder = out ?: StringBuilder(line.length + 8).also { out = it }
            builder
                .append(line, copied, begin)
                .append('<')
                .append(line, begin, end)
                .append('>')
            copied = end
        }

        return out?.append(line, copied, line.length)?.toString() ?: line
    }

    /**
     * Where a URL is not bare text: code spans, `<…>` autolinks and HTML tags, and a link's
     * `[text](destination)` or `[text][ref]`. Brackets inside a link's text would nest a link in
     * a link, which CommonMark resolves by dropping the outer one.
     */
    private fun spansToLeaveAlone(line: String): List<IntRange> {
        val spans = ArrayList<IntRange>()
        var i = 0
        while (i < line.length) {
            when (line[i]) {
                '\\' -> {
                    i += 2
                }

                '`' -> {
                    val run = runLength(line, i, '`')
                    val close = closingBacktickRun(line, i + run, run)
                    if (close >= 0) {
                        spans.add(i until close + run)
                        i = close + run
                    } else {
                        i += run
                    }
                }

                '<' -> {
                    val close = line.indexOf('>', i + 1)
                    val next = line.getOrNull(i + 1)
                    if (close > 0 && next != null && (next.isLetter() || next == '/' || next == '!' || next == '?')) {
                        spans.add(i..close)
                        i = close + 1
                    } else {
                        i++
                    }
                }

                '[' -> {
                    val close = matching(line, i, '[', ']')
                    val after = if (close > 0) line.getOrNull(close + 1) else null
                    val end =
                        when (after) {
                            '(' -> matching(line, close + 1, '(', ')')
                            '[' -> matching(line, close + 1, '[', ']')
                            else -> -1
                        }
                    if (end > 0) {
                        spans.add(i..end)
                        i = end + 1
                    } else {
                        i++
                    }
                }

                else -> {
                    i++
                }
            }
        }
        return spans
    }

    private fun runLength(
        line: String,
        from: Int,
        c: Char,
    ): Int {
        var j = from
        while (j < line.length && line[j] == c) j++
        return j - from
    }

    /** Start of the next run of exactly [length] backticks at or after [from], or -1. */
    private fun closingBacktickRun(
        line: String,
        from: Int,
        length: Int,
    ): Int {
        var j = from
        while (j < line.length) {
            if (line[j] == '`') {
                val run = runLength(line, j, '`')
                if (run == length) return j
                j += run
            } else {
                j++
            }
        }
        return -1
    }

    /** Index of the [close] that balances the [open] at [from], skipping escapes, or -1. */
    private fun matching(
        line: String,
        from: Int,
        open: Char,
        close: Char,
    ): Int {
        var depth = 0
        var j = from
        while (j < line.length) {
            when (line[j]) {
                '\\' -> j++
                open -> depth++
                close -> if (--depth == 0) return j
            }
            j++
        }
        return -1
    }

    private fun indentOf(line: String): Int {
        var width = 0
        for (c in line) {
            when (c) {
                ' ' -> width++
                '\t' -> width += 4 - width % 4
                else -> return width
            }
        }
        return width
    }

    private fun Char.isEmphasisMarker() = this == '_' || this == '*' || this == '~'

    private fun String.hasEmphasisMarker(
        from: Int,
        to: Int,
    ): Boolean {
        for (i in from until to) if (this[i].isEmphasisMarker()) return true
        return false
    }

    // An autolink can't hold whitespace, `<` or `>`; a `|` would cut a table cell in two.
    private fun String.hasCharThatEndsAnAutolink(
        from: Int,
        to: Int,
    ): Boolean {
        for (i in from until to) {
            val c = this[i]
            if (c == '<' || c == '>' || c == '|' || c.isWhitespace()) return true
        }
        return false
    }

    private const val TRAILING_NOT_URL = "*_~?!.,:"

    private val referenceDefinition = Regex("^ {0,3}\\[[^\\]]+\\]:.*")
}
