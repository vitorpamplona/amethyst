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
package com.vitorpamplona.fantasyconsole.cart

/**
 * The console's character set: one byte per character, with control codes below 32 and glyphs
 * from 128. A `.p8` text file writes those bytes as Unicode; this maps them both ways. Code and
 * strings inside the runtime are P8SCII, one [Char] (0-255) per byte.
 */
object P8scii {
    private val TABLE: Array<String> =
        Array(256) { i ->
            when (i) {
                in 32..126 -> i.toChar().toString()
                else -> ""
            }
        }.also { t ->
            t[0] = "\u0000"
            "¹²³⁴⁵⁶⁷⁸".forEachIndexed { i, c -> t[1 + i] = c.toString() }
            t[9] = "\t"
            t[10] = "\n"
            t[11] = "ᵇ"
            t[12] = "ᶜ"
            t[13] = "\r"
            t[14] = "ᵉ"
            t[15] = "ᶠ"
            "▮■□⁙⁘‖◀▶「」¥•、。゛゜".forEachIndexed { i, c -> t[16 + i] = c.toString() }
            t[127] = "○"
            val high =
                listOf(
                    "█",
                    "▒",
                    "🐱",
                    "⬇\uFE0F",
                    "░",
                    "✽",
                    "●",
                    "♥",
                    "☉",
                    "웃",
                    "⌂",
                    "⬅\uFE0F",
                    "😐",
                    "♪",
                    "🅾\uFE0F",
                    "◆",
                    "…",
                    "➡\uFE0F",
                    "★",
                    "⧗",
                    "⬆\uFE0F",
                    "ˇ",
                    "∧",
                    "❎",
                    "▤",
                    "▥",
                )
            high.forEachIndexed { i, s -> t[128 + i] = s }
            val kana =
                "あいうえおかきくけこさしすせそたちつてとなにぬねのはひふへほまみむめもやゆよらりるれろわをんっゃゅょ" +
                    "アイウエオカキクケコサシスセソタチツテトナニヌネノハヒフヘホマミムメモヤユヨラリルレロワヲンッャュョ"
            kana.forEachIndexed { i, c -> t[154 + i] = c.toString() }
            t[254] = "◜"
            t[255] = "◝"
        }

    /** First code point of each glyph → its byte; emoji are matched with or without U+FE0F. */
    private val REVERSE: Map<Int, Int> =
        buildMap {
            for (i in 255 downTo 0) {
                val s = TABLE[i]
                if (s.isNotEmpty()) put(codePointAt(s, 0), i)
            }
            // `.p8` files write the console's second case (A-Z) as italic letters: 𝘢 is A.
            for (k in 0 until 26) put(0x1D622 + k, 'A'.code + k)
        }

    private fun codePointAt(
        s: String,
        index: Int,
    ): Int {
        val c = s[index]
        if (c.isHighSurrogate() && index + 1 < s.length) {
            val low = s[index + 1]
            if (low.isLowSurrogate()) return ((c.code - 0xD800) shl 10) + (low.code - 0xDC00) + 0x10000
        }
        return c.code
    }

    /** P8SCII bytes as the console's Unicode text. */
    fun toUnicode(p8: String): String {
        val sb = StringBuilder(p8.length)
        for (c in p8) sb.append(TABLE[c.code and 0xff].ifEmpty { c.toString() })
        return sb.toString()
    }

    /** Unicode from a `.p8` file to P8SCII; characters outside the table become `?`. */
    fun fromUnicode(text: String): String {
        val sb = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            val cp = codePointAt(text, i)
            i += if (cp >= 0x10000) 2 else 1
            if (cp == 0xFE0F) continue
            val b = REVERSE[cp]
            sb.append(
                when {
                    b != null -> b.toChar()
                    cp < 256 -> cp.toChar()
                    else -> '?'
                },
            )
        }
        return sb.toString()
    }

    /** Bytes as a P8SCII string, one char per byte. */
    fun fromBytes(
        bytes: ByteArray,
        start: Int = 0,
        end: Int = bytes.size,
    ): String {
        val chars = CharArray(end - start) { (bytes[start + it].toInt() and 0xff).toChar() }
        return chars.concatToString()
    }
}
