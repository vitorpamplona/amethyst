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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.feed

/** A message of up to this many emoji renders as jumbo emoji without a bubble. */
const val MAX_JUMBO_EMOJI = 3

/**
 * Number of emoji in [content] when the message is emoji-only (ignoring
 * whitespace) and short enough to render jumbo, or 0 when it contains any
 * non-emoji text or more than [MAX_JUMBO_EMOJI] emoji.
 *
 * Modifier code points (variation selectors, ZWJ, skin tones, keycaps) don't
 * count as separate emoji, so a ZWJ family or a flag counts what it draws as.
 */
fun jumboEmojiCount(content: String): Int {
    var count = 0
    var previousWasZwj = false
    var pendingRegionalIndicator = false

    var i = 0
    while (i < content.length) {
        val cp = content.codePointAt(i)
        i += Character.charCount(cp)

        when {
            Character.isWhitespace(cp) -> {
                previousWasZwj = false
            }

            isEmojiModifier(cp) -> {
                previousWasZwj = cp == ZWJ
            }

            isRegionalIndicator(cp) -> {
                // Two regional indicators pair into one flag.
                if (pendingRegionalIndicator) {
                    pendingRegionalIndicator = false
                } else {
                    pendingRegionalIndicator = true
                    count++
                }
                previousWasZwj = false
            }

            isEmojiBase(cp) -> {
                if (!previousWasZwj) count++
                previousWasZwj = false
            }

            else -> return 0
        }

        if (count > MAX_JUMBO_EMOJI) return 0
    }

    return count
}

private const val ZWJ = 0x200D

private fun isEmojiModifier(cp: Int): Boolean =
    cp == ZWJ ||
        cp == 0xFE0E || // variation selector 15 (text style)
        cp == 0xFE0F || // variation selector 16 (emoji style)
        cp == 0x20E3 || // combining enclosing keycap
        cp in 0x1F3FB..0x1F3FF // skin tones

private fun isRegionalIndicator(cp: Int): Boolean = cp in 0x1F1E6..0x1F1FF

private fun isEmojiBase(cp: Int): Boolean =
    cp in 0x1F000..0x1FAFF || // pictographs, emoticons, transport, supplemental, extended-A
        cp in 0x2600..0x27BF || // misc symbols + dingbats (hearts, stars, hands)
        cp in 0x2B00..0x2BFF || // arrows-C block (star, heavy circles)
        cp == 0x203C || // double exclamation
        cp == 0x2049 || // exclamation question
        cp == 0x00A9 || // copyright (with VS16)
        cp == 0x00AE || // registered (with VS16)
        cp in 0x2934..0x2935 || // arrow emoji
        cp in 0x3297..0x3299 || // circled ideographs
        cp == 0x3030 || // wavy dash
        cp == 0x303D // part alternation mark
