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
package com.vitorpamplona.fantasyconsole.runtime

/**
 * The built-in font: 3x5 glyphs in a 4x6 cell for ASCII, 7x5 in an 8x6 cell for the glyphs past
 * 127. These glyphs were drawn for this project. Lowercase letters draw as capitals, as on the
 * console, and uppercase share them for now.
 */
object Font {
    const val HEIGHT = 6

    class Glyph(
        /** Advance in pixels, gap included. */
        val advance: Int,
        /** Five rows, bit (width - 1) is the leftmost pixel. */
        val rows: IntArray,
        val width: Int,
    )

    private val narrow = arrayOfNulls<Glyph>(128)
    private val wide = arrayOfNulls<Glyph>(256)

    private val BLANK_WIDE = Glyph(8, IntArray(5), 7)

    /** The glyph for P8SCII [code]; codes below 32 have none. */
    fun glyph(code: Int): Glyph? =
        when {
            code < 32 -> null
            code < 128 -> narrow[code]
            code < 256 -> wide[code] ?: BLANK_WIDE
            else -> null
        }

    init {
        // Each glyph is five octal digits, one per row: 4 = left pixel, 2 = middle, 1 = right.
        val ascii =
            mapOf(
                ' ' to "00000",
                '!' to "22202",
                '"' to "55000",
                '#' to "57575",
                '$' to "36763",
                '%' to "51245",
                '&' to "66757",
                '\'' to "22000",
                '(' to "24442",
                ')' to "21112",
                '*' to "52725",
                '+' to "02720",
                ',' to "00024",
                '-' to "00700",
                '.' to "00002",
                '/' to "11244",
                '0' to "75557",
                '1' to "62227",
                '2' to "71747",
                '3' to "71317",
                '4' to "55711",
                '5' to "74717",
                '6' to "44757",
                '7' to "71111",
                '8' to "75757",
                '9' to "75711",
                ':' to "02020",
                ';' to "02024",
                '<' to "12421",
                '=' to "07070",
                '>' to "42124",
                '?' to "71302",
                '@' to "25543",
                'a' to "75755",
                'b' to "75657",
                'c' to "34443",
                'd' to "65556",
                'e' to "74647",
                'f' to "74644",
                'g' to "34557",
                'h' to "55755",
                'i' to "72227",
                'j' to "72226",
                'k' to "55655",
                'l' to "44447",
                'm' to "77555",
                'n' to "65555",
                'o' to "25552",
                'p' to "75744",
                'q' to "25563",
                'r' to "75655",
                's' to "34716",
                't' to "72222",
                'u' to "55557",
                'v' to "55572",
                'w' to "55577",
                'x' to "55255",
                'y' to "55722",
                'z' to "71247",
                '[' to "64446",
                '\\' to "44211",
                ']' to "31113",
                '^' to "25000",
                '_' to "00007",
                '`' to "42000",
                '{' to "32623",
                '|' to "22222",
                '}' to "62326",
                '~' to "00630",
            )
        for ((c, rows) in ascii) {
            val glyph = Glyph(4, IntArray(5) { rows[it] - '0' }, 3)
            narrow[c.code] = glyph
            if (c in 'a'..'z') narrow[c.code - 32] = glyph
        }
        narrow[127] = Glyph(4, intArrayOf(2, 5, 5, 5, 2), 3)

        // Seven-pixel rows, 64 = leftmost.
        val glyphs =
            mapOf(
                128 to intArrayOf(127, 127, 127, 127, 127), // █
                129 to intArrayOf(85, 42, 85, 42, 85), // ▒
                131 to intArrayOf(8, 8, 62, 28, 8), // ⬇
                132 to intArrayOf(85, 0, 85, 0, 85), // ░
                134 to intArrayOf(28, 62, 62, 62, 28), // ●
                135 to intArrayOf(54, 127, 127, 62, 28), // ♥
                139 to intArrayOf(16, 48, 127, 48, 16), // ⬅
                141 to intArrayOf(14, 10, 8, 56, 56), // ♪
                142 to intArrayOf(62, 99, 99, 99, 62), // 🅾
                143 to intArrayOf(8, 28, 62, 28, 8), // ◆
                144 to intArrayOf(0, 0, 0, 0, 84), // …
                145 to intArrayOf(4, 6, 127, 6, 4), // ➡
                146 to intArrayOf(8, 28, 127, 28, 34), // ★
                148 to intArrayOf(8, 28, 62, 8, 8), // ⬆
                151 to intArrayOf(99, 54, 28, 54, 99), // ❎
            )
        for ((code, rows) in glyphs) wide[code] = Glyph(8, rows, 7)
    }
}
