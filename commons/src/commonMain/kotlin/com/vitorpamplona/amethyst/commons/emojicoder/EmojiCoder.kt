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

// KMP-compatible codePoint helpers
private fun codePointToChars(codePoint: Int): CharArray =
    if (codePoint in 0x10000..0x10FFFF) {
        val high = ((codePoint - 0x10000) shr 10) + 0xD800
        val low = ((codePoint - 0x10000) and 0x3FF) + 0xDC00
        charArrayOf(high.toChar(), low.toChar())
    } else {
        charArrayOf(codePoint.toChar())
    }

private fun charCountForCodePoint(codePoint: Int): Int = if (codePoint >= 0x10000) 2 else 1

private fun String.codePointAt(index: Int): Int {
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

        if (!isVariationChar(text.codePointAt(text.length - 2))) {
            return false
        }

        if (text.length > 4 && !isVariationChar(text.codePointAt(text.length - 4))) {
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
            val codePoint = text.codePointAt(i)
            val byte = fromVariationSelector(codePoint)

            if (byte == null && decoded.isNotEmpty()) {
                break
            } else if (byte == null) {
                i += charCountForCodePoint(codePoint)
                continue
            }

            decoded.add(byte)
            i += charCountForCodePoint(codePoint)
        }

        val decodedArray = ByteArray(decoded.size) { decoded[it].toByte() }
        return decodedArray.decodeToString()
    }

    fun cropToFirstMessage(text: String): String {
        val decoded = mutableListOf<Int>()

        var i = 0
        while (i < text.length) {
            val codePoint = text.codePointAt(i)
            val byte = fromVariationSelector(codePoint)

            if (byte == null && decoded.isNotEmpty()) {
                break
            } else if (byte == null) {
                i += charCountForCodePoint(codePoint)
                continue
            }

            decoded.add(byte)
            i += charCountForCodePoint(codePoint)
        }

        return text.substring(0, i)
    }
}
