/**
 * Copyright (c) 2023 Vitor Pamplona
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
package com.vitorpamplona.amethyst.commons

class ExpandableTextParser {
    companion object {
        const val SHORT_TEXT_LENGTH = 350
        const val SHORTEN_AFTER_LINES = 10
    }

    fun computeWhereToCutIfPostIsTooLong(content: String): Int {
        // Cuts the text in the first space or new line after SHORT_TEXT_LENGTH characters
        val firstSpaceAfterCut =
            content.indexOf(' ', SHORT_TEXT_LENGTH).let { if (it < 0) content.length else it }
        val firstNewLineAfterCut =
            content.indexOf('\n', SHORT_TEXT_LENGTH).let { if (it < 0) content.length else it }

        // or after SHORTEN_AFTER_LINES lines
        val numberOfLines = content.count { it == '\n' }

        var charactersInLines = minOf(firstSpaceAfterCut, firstNewLineAfterCut)

        if (numberOfLines > SHORTEN_AFTER_LINES) {
            val shortContent = content.lines().take(SHORTEN_AFTER_LINES)
            charactersInLines = 0
            for (line in shortContent) {
                // +1 because new line character is omitted from .lines
                charactersInLines += (line.length + 1)
            }
        }

        return minOf(firstSpaceAfterCut, firstNewLineAfterCut, charactersInLines)
    }
}
