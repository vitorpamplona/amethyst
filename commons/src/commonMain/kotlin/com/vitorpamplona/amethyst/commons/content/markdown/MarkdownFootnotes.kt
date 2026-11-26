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

/**
 * GitHub's footnotes, which JetBrains' parser does not know: `[^label]` calls a note, and a
 * `[^label]: text` line defines it, with its following lines indented by four spaces (or lazily
 * continuing its last paragraph) as more of the note.
 *
 * [extract] takes the definitions out of the source before it is parsed, so they do not show as
 * paragraphs where they were written, and keeps each one's text to be parsed on its own.
 */
internal object MarkdownFootnotes {
    class Extracted(
        /** The source without its definitions; each one left a blank line in its place. */
        val source: String,
        /** Each note's Markdown, by [normalize]d label. The first definition of a label wins. */
        val notes: Map<String, String>,
    )

    private val NO_NOTES = emptyMap<String, String>()

    fun extract(src: String): Extracted {
        // Nearly every document has no footnote: one scan says so.
        if (!src.contains("[^")) return Extracted(src, NO_NOTES)

        val notes = LinkedHashMap<String, String>()
        val out = StringBuilder(src.length)
        var changed = false
        var fence: String? = null

        val lines = src.split('\n')
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            if (fence != null) {
                if (closesFence(line, fence)) fence = null
                out.appendLine(line, i, lines.size)
                i++
                continue
            }
            val opening = fenceOf(line)
            if (opening != null) {
                fence = opening
                out.appendLine(line, i, lines.size)
                i++
                continue
            }

            val start = definitionStart(line)
            if (start == null) {
                out.appendLine(line, i, lines.size)
                i++
                continue
            }

            changed = true
            val (label, firstLine) = start
            val body = StringBuilder(firstLine)
            var lastBlank = firstLine.isBlank()
            var j = i + 1
            while (j < lines.size) {
                val next = lines[j]
                when {
                    next.isBlank() -> {
                        // A blank line belongs to the note only when an indented line follows it.
                        var k = j
                        while (k < lines.size && lines[k].isBlank()) k++
                        if (k < lines.size && isIndented(lines[k])) {
                            repeat(k - j) { body.append('\n') }
                            j = k
                            lastBlank = true
                        } else {
                            break
                        }
                    }
                    isIndented(next) -> {
                        body.append('\n').append(dedent(next))
                        lastBlank = false
                        j++
                    }
                    // A lazy line goes on the note's last paragraph, unless it starts a block.
                    !lastBlank && !startsBlock(next) -> {
                        body.append('\n').append(next)
                        j++
                    }
                    else -> break
                }
            }
            val key = normalize(label)
            if (key !in notes) notes[key] = body.toString()
            // A blank line where the definition was, so the text around it does not join.
            if (j < lines.size) out.append('\n')
            i = j
        }
        return if (changed) Extracted(out.toString(), notes) else Extracted(src, NO_NOTES)
    }

    /** `[^label]` as a call: the label, or null when [text] is not one. */
    fun referenceLabel(text: String): String? {
        if (text.length < 4 || !text.startsWith("[^") || !text.endsWith("]")) return null
        val label = text.substring(2, text.length - 1)
        return if (isLabel(label)) label else null
    }

    /** Case-insensitive, as GitHub matches labels. */
    fun normalize(label: String): String = label.lowercase()

    private fun isLabel(label: String): Boolean = label.isNotEmpty() && label.none { it == ']' || it == '[' || it.isWhitespace() }

    /** `[^label]: text` with at most three spaces before it: the label and the text. */
    private fun definitionStart(line: String): Pair<String, String>? {
        var i = 0
        while (i < line.length && i < 3 && line[i] == ' ') i++
        if (!line.startsWith("[^", i)) return null
        val close = line.indexOf("]:", i + 2)
        if (close < 0) return null
        val label = line.substring(i + 2, close)
        if (!isLabel(label)) return null
        return label to line.substring(close + 2).trimStart(' ', '\t')
    }

    private fun isIndented(line: String): Boolean = line.startsWith("    ") || line.startsWith("\t")

    private fun dedent(line: String): String =
        when {
            line.startsWith("\t") -> line.substring(1)
            line.startsWith("    ") -> line.substring(4)
            else -> line
        }

    /** Whether [line] opens a block, so it cannot continue a note's paragraph lazily. */
    private fun startsBlock(line: String): Boolean {
        val t = line.trimStart(' ')
        if (line.length - t.length > 3) return false
        return definitionStart(line) != null ||
            t.startsWith('#') ||
            t.startsWith('>') ||
            t.startsWith("```") ||
            t.startsWith("~~~") ||
            t.startsWith("- ") ||
            t.startsWith("* ") ||
            t.startsWith("+ ") ||
            t.startsWith('|')
    }

    /** The fence a line opens (its run of ``` or ~~~), or null. */
    private fun fenceOf(line: String): String? {
        var i = 0
        while (i < line.length && i < 3 && line[i] == ' ') i++
        if (i >= line.length) return null
        val c = line[i]
        if (c != '`' && c != '~') return null
        var n = i
        while (n < line.length && line[n] == c) n++
        if (n - i < 3) return null
        // A backtick fence's info string has no backtick.
        if (c == '`' && line.indexOf('`', n) >= 0) return null
        return line.substring(i, n)
    }

    private fun closesFence(
        line: String,
        fence: String,
    ): Boolean {
        val t = line.trimStart(' ')
        if (line.length - t.length > 3) return false
        var n = 0
        while (n < t.length && t[n] == fence[0]) n++
        return n >= fence.length && t.substring(n).isBlank()
    }

    private fun StringBuilder.appendLine(
        line: String,
        index: Int,
        count: Int,
    ) {
        append(line)
        if (index < count - 1) append('\n')
    }
}
