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

/**
 * Extracts LaTeX math spans delimited by `$...$` (inline) and `$$...$$` (display)
 * from a single line of text.
 *
 * Math spans can contain spaces, so this runs *before* the regular whitespace
 * word-splitter in [RichTextParser]: it tokenizes a line into atomic math spans
 * (kept whole) interleaved with the surrounding plain text (which the caller
 * then splits on spaces as usual).
 *
 * Delimiter rules follow the common "pandoc/remark-math dollar" convention so
 * that ordinary prose with currency (`$5 and $10`) doesn't false-fire:
 *  - An opening `$` must be immediately followed by a non-whitespace char.
 *  - A closing `$` must be immediately preceded by a non-whitespace char and
 *    must not be immediately followed by a digit.
 *  - A `$` escaped with a backslash (`\$`) is a literal dollar, never a delimiter.
 *  - `$$...$$` (display) is matched before `$...$` (inline).
 */
object MathParser {
    sealed interface Token {
        /** Plain text run; the caller splits this on spaces. */
        data class Text(
            val text: String,
        ) : Token

        /** A math span. [raw] includes the `$` delimiters; [latex] is the inner formula. */
        data class Math(
            val raw: String,
            val latex: String,
            val displayMode: Boolean,
        ) : Token
    }

    /** Cheap gate: a line can only contain math if it has at least two `$`. */
    fun mightContainMath(line: String): Boolean {
        val first = line.indexOf('$')
        return first >= 0 && line.indexOf('$', first + 1) >= 0
    }

    /**
     * Splits [line] into alternating [Token.Text] and [Token.Math] tokens.
     * When no valid math span is found the whole line comes back as a single
     * [Token.Text].
     */
    fun split(line: String): List<Token> {
        if (!mightContainMath(line)) return listOf(Token.Text(line))

        val tokens = ArrayList<Token>()
        val text = StringBuilder()
        val len = line.length
        var i = 0

        fun flushText() {
            if (text.isNotEmpty()) {
                tokens.add(Token.Text(text.toString()))
                text.clear()
            }
        }

        while (i < len) {
            if (line[i] == '$' && !isEscaped(line, i)) {
                val match = matchDisplay(line, i) ?: matchInline(line, i)
                if (match != null) {
                    flushText()
                    tokens.add(match)
                    i = match.raw.length + i
                    continue
                }
            }
            text.append(line[i])
            i++
        }
        flushText()

        return tokens
    }

    /** A `$` is escaped when preceded by an odd number of backslashes. */
    private fun isEscaped(
        line: String,
        index: Int,
    ): Boolean {
        var backslashes = 0
        var j = index - 1
        while (j >= 0 && line[j] == '\\') {
            backslashes++
            j--
        }
        return backslashes % 2 == 1
    }

    /** Matches `$$...$$` starting at [start] (which points at the first `$`). */
    private fun matchDisplay(
        line: String,
        start: Int,
    ): Token.Math? {
        if (start + 1 >= line.length || line[start + 1] != '$') return null
        val contentStart = start + 2
        var j = contentStart
        while (j + 1 < line.length) {
            if (line[j] == '$' && line[j + 1] == '$' && !isEscaped(line, j)) {
                val latex = line.substring(contentStart, j)
                if (latex.isBlank()) return null
                return Token.Math(line.substring(start, j + 2), latex.trim(), displayMode = true)
            }
            j++
        }
        return null
    }

    /** Matches `$...$` starting at [start] (which points at the opening `$`). */
    private fun matchInline(
        line: String,
        start: Int,
    ): Token.Math? {
        val contentStart = start + 1
        // Opening `$` must be followed by a non-whitespace character.
        if (contentStart >= line.length || line[contentStart].isWhitespace()) return null

        var j = contentStart
        while (j < line.length) {
            if (line[j] == '$' && !isEscaped(line, j)) {
                // Closing `$` must be preceded by a non-whitespace char and
                // not be immediately followed by a digit (avoids `$5 ... $10`).
                val prev = line[j - 1]
                val nextIsDigit = j + 1 < line.length && line[j + 1].isDigit()
                if (!prev.isWhitespace() && !nextIsDigit) {
                    val latex = line.substring(contentStart, j)
                    if (latex.isBlank()) return null
                    return Token.Math(line.substring(start, j + 1), latex, displayMode = false)
                }
                // This `$` can't close — and since it isn't escaped it also
                // can't appear inside inline math, so stop scanning.
                return null
            }
            j++
        }
        return null
    }
}
