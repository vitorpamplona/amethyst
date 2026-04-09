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
package com.vitorpamplona.amethyst.commons.emojicoder

/**
 * Returns the number of UTF-16 chars needed to represent the given code point.
 */
private fun codePointCharCount(codePoint: Int): Int = if (codePoint >= 0x10000) 2 else 1

/**
 * Converts a code point to one or two UTF-16 chars (surrogate pair for supplementary).
 */
private fun codePointToChars(codePoint: Int): CharArray =
    if (codePoint >= 0x10000) {
        val offset = codePoint - 0x10000
        charArrayOf(
            (0xD800 + (offset shr 10)).toChar(),
            (0xDC00 + (offset and 0x3FF)).toChar(),
        )
    } else {
        charArrayOf(codePoint.toChar())
    }

/**
 * Returns the Unicode code point at the given char index, handling surrogate pairs.
 */
private fun String.codePointAtIndex(index: Int): Int {
    val high = this[index]
    if (high.isHighSurrogate() && index + 1 < length) {
        val low = this[index + 1]
        if (low.isLowSurrogate()) {
            return 0x10000 + ((high.code - 0xD800) shl 10) + (low.code - 0xDC00)
        }
    }
    return high.code
}

object EmojiCoder {
    // Variation selectors block https://unicode.org/charts/nameslist/n_FE00.html
    // VS1..=VS16
    const val VARIATION_SELECTOR_START = 0xfe00
    const val VARIATION_SELECTOR_END = 0xfe0f

    // Variation selectors supplement https://unicode.org/charts/nameslist/n_E0100.html
    // VS17..=VS256
    const val VARIATION_SELECTOR_SUPPLEMENT_START = 0xe0100
    const val VARIATION_SELECTOR_SUPPLEMENT_END = 0xe01ef

    val toVariationArray =
        Array<CharArray>(256) {
            // converts to UTF-16. Always char[2] back
            when (it) {
                in 0..15 -> codePointToChars(VARIATION_SELECTOR_START + it)
                in 16..255 -> codePointToChars(VARIATION_SELECTOR_SUPPLEMENT_START + it - 16)
                else -> throw RuntimeException("This should never happen")
            }
        }

    fun fromVariationSelector(codePoint: Int): Int? =
        when (codePoint) {
            in VARIATION_SELECTOR_START..VARIATION_SELECTOR_END -> codePoint - VARIATION_SELECTOR_START
            in VARIATION_SELECTOR_SUPPLEMENT_START..VARIATION_SELECTOR_SUPPLEMENT_END -> codePoint - VARIATION_SELECTOR_SUPPLEMENT_START + 16
            else -> null
        }

    fun isVariationChar(charCode: Int) =
        charCode in VARIATION_SELECTOR_START..VARIATION_SELECTOR_END ||
            charCode in VARIATION_SELECTOR_SUPPLEMENT_START..VARIATION_SELECTOR_SUPPLEMENT_END

    fun isCoded(text: String): Boolean {
        if (text.length <= 3) return false

        if (!isVariationChar(text.codePointAtIndex(text.length - 2))) {
            return false
        }

        if (text.length > 4 && !isVariationChar(text.codePointAtIndex(text.length - 4))) {
            return false
        }

        return true
    }

    fun encode(
        emoji: String,
        text: String,
    ): String {
        val input = text.encodeToByteArray()
        val out = CharArray(input.size * 2)
        var outIdx = 0
        for (i in input.indices) {
            val chars = toVariationArray[input[i].toInt() and 0xFF]
            out[outIdx++] = chars[0]
            out[outIdx++] = chars[1]
        }
        return emoji + out.concatToString()
    }

    fun decode(text: String): String {
        val decoded = mutableListOf<Int>()

        var i = 0
        while (i < text.length) {
            val codePoint = text.codePointAtIndex(i)
            val byte = fromVariationSelector(codePoint)

            if (byte == null && decoded.isNotEmpty()) {
                break
            } else if (byte == null) {
                i += codePointCharCount(codePoint) // Advance index by correct number of chars
                continue
            }

            decoded.add(byte)
            i += codePointCharCount(codePoint) // Advance index by correct number of chars
        }

        val decodedArray = ByteArray(decoded.size) { decoded[it].toByte() }
        return decodedArray.decodeToString()
    }

    fun cropToFirstMessage(text: String): String {
        val decoded = mutableListOf<Int>()

        var i = 0
        while (i < text.length) {
            val codePoint = text.codePointAtIndex(i)
            val byte = fromVariationSelector(codePoint)

            if (byte == null && decoded.isNotEmpty()) {
                break
            } else if (byte == null) {
                i += codePointCharCount(codePoint) // Advance index by correct number of chars
                continue
            }

            decoded.add(byte)
            i += codePointCharCount(codePoint) // Advance index by correct number of chars
        }

        return text.substring(0, i)
    }
}
