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

class ExpandableTextCutOffCalculator {
    companion object {
        private const val SHORT_TEXT_LENGTH = 350
        private const val SHORTEN_AFTER_LINES = 10
        private const val TOO_FAR_SEARCH_THE_OTHER_WAY = 450

        fun indexToCutOff(content: String): Int {
            // Cuts the text in the first space or first new line after SHORT_TEXT_LENGTH characters
            val firstSpaceAfterCut =
                content.indexOf(' ', SHORT_TEXT_LENGTH).let { if (it < 0) content.length else it }
            val firstNewLineAfterCut =
                content.indexOf('\n', SHORT_TEXT_LENGTH).let { if (it < 0) content.length else it }

            // Cuts the text if too many new lines have passed.
            val firstLineAfterLineLimits =
                content.nthIndexOf('\n', SHORTEN_AFTER_LINES).let { if (it < 0) content.length else it }

            // gets the minimum of them all.
            val min = minOf(firstSpaceAfterCut, firstNewLineAfterCut, firstLineAfterLineLimits)

            val result =
                if (min > TOO_FAR_SEARCH_THE_OTHER_WAY) {
                    // if it is still too big, finds the first space or new line BEFORE the cut off.
                    val newString = content.take(SHORT_TEXT_LENGTH)

                    val firstSpaceBeforeCut = newString.lastIndexOf(' ')
                    val firstNewLineBeforeCut = newString.lastIndexOf('\n')

                    if (firstSpaceBeforeCut < 0 && firstNewLineBeforeCut < 0) {
                        // No whitespace boundary anywhere in the first
                        // SHORT_TEXT_LENGTH characters either. The
                        // entire content is one indivisible atom — a
                        // long cashuA/cashuB token, a base64 data: URI,
                        // a Lightning invoice, a single huge URL. Cutting
                        // mid-atom corrupts the segment so the inline
                        // renderer can't decode it (the cashuB prefix
                        // survives so it gets matched, but the truncated
                        // body fails to base64-decode and falls back to
                        // rendering the raw text + a useless Show More
                        // button). Render the whole atom.
                        return content.length
                    }

                    maxOf(
                        firstSpaceBeforeCut.coerceAtLeast(0),
                        firstNewLineBeforeCut.coerceAtLeast(0),
                    )
                } else {
                    min
                }

            // Only returns if the difference between short and long posts is more than 100 chars or too many new lines.
            return if (result == firstLineAfterLineLimits || result + 100 < content.length) {
                result
            } else {
                content.length
            }
        }
    }
}

fun String.nthIndexOf(
    ch: Char,
    N: Int,
): Int {
    var occur = N
    var pos = -1

    while (occur > 0) {
        // calling the native function multiple times is faster than looping just once
        pos = indexOf(ch, pos + 1)
        if (pos == -1) return -1
        occur--
    }

    return if (occur == 0) pos else -1
}
