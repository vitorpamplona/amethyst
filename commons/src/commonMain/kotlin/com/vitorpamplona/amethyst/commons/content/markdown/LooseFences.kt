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
 * Notes often write a code block with its fences on the code's own first and last lines:
 *
 * ```
 * ```[18:41:43] It's so dark
 * [18:42:55] and the cat```
 * ```
 *
 * CommonMark reads the opening line's text as the block's language and `cat```` as code, so the
 * block runs to the end of the note and takes everything after it, mentions included. This puts
 * each such fence on a line of its own before parsing:
 *
 * - An opening fence whose text does not start with a language (`kotlin`, `c++`, `objective-c`)
 *   moves that text to the code's first line, so it is shown rather than dropped as a language.
 * - When a line of the code ends with the fence before any line closes it, that line closes the
 *   block: the fence moves to a line of its own.
 *
 * Only fences at the top level are repaired; one inside a quote or a list is left to the parser.
 */
internal object LooseFences {
    fun repair(src: String): String {
        if (!src.contains("```") && !src.contains("~~~")) return src
        val lines = src.split('\n')
        val out = ArrayList<String>(lines.size + 4)
        var changed = false
        var i = 0
        while (i < lines.size) {
            val open = opening(lines[i])
            if (open == null) {
                out.add(lines[i])
                i++
                continue
            }
            val (indent, fence, info) = open
            // The first line after the opening one that closes it, properly or on a line of code.
            var close = -1
            var loose = false
            for (j in i + 1 until lines.size) {
                if (closes(lines[j], fence)) {
                    close = j
                    break
                }
                if (endsWithFence(lines[j], fence)) {
                    close = j
                    loose = true
                    break
                }
            }
            val textOnOpening = info.isNotEmpty() && !startsWithLanguage(info)
            if (close < 0 || (!loose && !textOnOpening)) {
                // No change: copy the block as written, through its closing fence.
                val end = if (close < 0) lines.size - 1 else close
                for (k in i..end) out.add(lines[k])
                i = end + 1
                continue
            }
            changed = true
            if (textOnOpening) {
                out.add(" ".repeat(indent) + fence)
                out.add(" ".repeat(indent) + info)
            } else {
                out.add(lines[i])
            }
            for (k in i + 1 until close) out.add(lines[k])
            if (loose) {
                val last = lines[close].trimEnd()
                val code = last.trimEnd(fence[0])
                out.add(code)
                out.add(" ".repeat(indent) + fence)
            } else {
                out.add(lines[close])
            }
            i = close + 1
        }
        return if (changed) out.joinToString("\n") else src
    }

    private data class Opening(
        val indent: Int,
        val fence: String,
        val info: String,
    )

    /** The fence [line] opens, when it opens one at the top level. */
    private fun opening(line: String): Opening? {
        val indent = leadingSpaces(line)
        if (indent > 3 || indent >= line.length) return null
        val c = line[indent]
        if (c != '`' && c != '~') return null
        var end = indent
        while (end < line.length && line[end] == c) end++
        if (end - indent < 3) return null
        val info = line.substring(end).trim()
        // A backtick in a backtick fence's text makes the line a code span instead.
        if (c == '`' && info.contains('`')) return null
        return Opening(indent, line.substring(indent, end), info)
    }

    /** Whether [line] is a fence that closes [fence]: as long or longer, and nothing after it. */
    private fun closes(
        line: String,
        fence: String,
    ): Boolean {
        val indent = leadingSpaces(line)
        if (indent > 3) return false
        val body = line.substring(indent).trimEnd()
        return body.length >= fence.length && body.all { it == fence[0] }
    }

    /** Whether [line] is code that ends with a fence closing [fence]. */
    private fun endsWithFence(
        line: String,
        fence: String,
    ): Boolean {
        val body = line.trimEnd()
        val code = body.trimEnd(fence[0])
        return code.isNotBlank() && body.length - code.length >= fence.length && opening(line) == null
    }

    /** Whether [info]'s first word reads as a language name rather than as text. */
    private fun startsWithLanguage(info: String): Boolean {
        val word = info.substringBefore(' ').substringBefore('\t')
        return word.isNotEmpty() && word[0].isLetter() && word.all { it.isLetterOrDigit() || it in "+-#._" }
    }

    private fun leadingSpaces(line: String): Int {
        var i = 0
        while (i < line.length && line[i] == ' ') i++
        return i
    }
}
