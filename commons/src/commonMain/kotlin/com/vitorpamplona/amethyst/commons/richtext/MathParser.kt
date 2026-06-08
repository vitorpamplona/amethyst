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
 * Splits a line into the space-delimited words consumed by [RichTextParser],
 * keeping LaTeX math spans (`$...$` inline, `$$...$$` display) whole even though
 * they may contain spaces.
 *
 * This is a drop-in replacement for `line.split(' ')`: for a line with no math
 * it returns exactly the same words (including the empty strings that
 * consecutive/leading/trailing spaces produce, which [RichTextParser] relies on
 * to preserve double-spaces). A math span simply comes back as a single
 * [Token.Math] word instead of being torn apart at its internal spaces.
 *
 * Delimiter rules follow the common "pandoc/remark-math dollar" convention so
 * that ordinary prose with currency (`$5 and $10`) doesn't false-fire:
 *  - An opening `$` must be immediately followed by a non-whitespace char.
 *  - A closing `$` must be immediately preceded by a non-whitespace char and
 *    must not be immediately followed by a digit.
 *  - A `$` escaped with a backslash (`\$`) is a literal dollar, never a delimiter.
 *  - `$$...$$` (display) is matched before `$...$` (inline).
 *
 * Math glued to text without a separating space (e.g. `a$x$b`) stays a single
 * plain word and renders literally — only whitespace-delimited spans become math.
 */
object MathParser {
    sealed interface Token {
        /** A space-delimited word; may be empty for consecutive spaces. */
        data class Word(
            val text: String,
        ) : Token

        /**
         * A math span. [raw] includes the `$` delimiters; [latex] is the inner
         * formula. [trailing] holds any punctuation glued right after the closing
         * `$` (e.g. the `.` in `$x$.`) so it renders next to the equation instead
         * of drifting off behind a space — same idea as [HashTagSegment]'s extras.
         */
        data class Math(
            val raw: String,
            val latex: String,
            val displayMode: Boolean,
            val trailing: String = "",
        ) : Token
    }

    /** Cheap gate: a line can only contain math if it has at least two `$`. */
    fun mightContainMath(line: String): Boolean {
        val first = line.indexOf('$')
        return first >= 0 && line.indexOf('$', first + 1) >= 0
    }

    /**
     * Splits [line] on spaces into [Token.Word]s, with any whitespace-delimited
     * math span surfaced as a [Token.Math].
     */
    fun split(line: String): List<Token> =
        splitKeepingMathWhole(line).map { cell ->
            // A span at the start of the cell becomes math, carrying any trailing
            // punctuation (`$x$.`). Leading-glued math (`a$x$`) stays a plain word.
            val math = matchMathAt(cell, 0)
            if (math != null) math.copy(trailing = cell.substring(math.raw.length)) else Token.Word(cell)
        }

    /**
     * Splits [line] on single spaces the way `line.split(' ')` would, except that
     * spaces *inside* a math span don't act as delimiters.
     */
    private fun splitKeepingMathWhole(line: String): List<String> {
        if (!mightContainMath(line)) return line.split(' ')

        val cells = ArrayList<String>()
        val current = StringBuilder()
        val len = line.length
        var i = 0
        while (i < len) {
            val c = line[i]
            when {
                c == ' ' -> {
                    cells.add(current.toString())
                    current.clear()
                    i++
                }
                c == '$' -> {
                    val span = matchMathAt(line, i)?.raw
                    if (span != null) {
                        current.append(span)
                        i += span.length
                    } else {
                        current.append(c)
                        i++
                    }
                }
                else -> {
                    current.append(c)
                    i++
                }
            }
        }
        cells.add(current.toString())
        return cells
    }

    /** Matches a `$$...$$` or `$...$` span starting at [start], or null. */
    private fun matchMathAt(
        line: String,
        start: Int,
    ): Token.Math? {
        if (start >= line.length || line[start] != '$' || isEscaped(line, start)) return null
        return matchDisplay(line, start) ?: matchInline(line, start)
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
