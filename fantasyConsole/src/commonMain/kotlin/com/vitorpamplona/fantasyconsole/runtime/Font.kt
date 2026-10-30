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
 * The built-in font: 3x5 glyphs in a 4x6 cell below 128, 7x5 in an 8x6 cell from 128. These
 * glyphs were drawn for this project. Lowercase letters draw as capitals, as on the console, and
 * uppercase letters draw as a smaller, lower set of capitals (the console's second case). The
 * kana (154-253) have no drawings yet and print as blank 8-pixel cells.
 */
object Font {
    const val HEIGHT = 6
    const val WIDTH = 4
    const val WIDE_WIDTH = 8

    class Glyph(
        /** Five rows, bit (width - 1) is the leftmost pixel. */
        val rows: IntArray,
        val width: Int,
    )

    private val glyphs = arrayOfNulls<Glyph>(256)

    private val BLANK_WIDE = Glyph(IntArray(5), 7)

    /** The glyph for P8SCII [code]; null for the control codes 0-15. */
    fun glyph(code: Int): Glyph? =
        when {
            code < 16 || code > 255 -> null
            code >= 128 -> glyphs[code] ?: BLANK_WIDE
            else -> glyphs[code]
        }

    private fun narrow(digits: String) = Glyph(IntArray(5) { digits[it] - '0' }, 3)

    init {
        // Each narrow glyph is five octal digits, one per row: 4 = left pixel, 2 = middle, 1 = right.
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
                // The console's lowercase: full-height capitals.
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
                // The console's uppercase: small capitals, four rows sitting on the baseline.
                'A' to "02575",
                'B' to "06756",
                'C' to "03443",
                'D' to "06556",
                'E' to "07647",
                'F' to "07644",
                'G' to "03453",
                'H' to "05755",
                'I' to "07227",
                'J' to "01152",
                'K' to "05655",
                'L' to "04447",
                'M' to "07755",
                'N' to "06555",
                'O' to "02552",
                'P' to "06564",
                'Q' to "02563",
                'R' to "06565",
                'S' to "03616",
                'T' to "07222",
                'U' to "05557",
                'V' to "05552",
                'W' to "05577",
                'X' to "05225",
                'Y' to "05522",
                'Z' to "07147",
                // 127 and the symbols at 16-31: ▮■□⁙⁘‖◀▶「」¥•、。゛゜
                127.toChar() to "25552",
                16.toChar() to "66666",
                17.toChar() to "07770",
                18.toChar() to "07570",
                19.toChar() to "50205",
                20.toChar() to "05050",
                21.toChar() to "55555",
                22.toChar() to "13731",
                23.toChar() to "46764",
                24.toChar() to "74400",
                25.toChar() to "00117",
                26.toChar() to "52722",
                27.toChar() to "00200",
                28.toChar() to "00042",
                29.toChar() to "00252",
                30.toChar() to "55000",
                31.toChar() to "25200",
            )
        for ((c, rows) in ascii) glyphs[c.code] = narrow(rows)

        // Seven-pixel rows, 64 = leftmost.
        val wide =
            mapOf(
                128 to intArrayOf(127, 127, 127, 127, 127), // █
                129 to intArrayOf(85, 42, 85, 42, 85), // ▒
                130 to intArrayOf(34, 62, 42, 62, 28), // 🐱
                131 to intArrayOf(8, 8, 62, 28, 8), // ⬇
                132 to intArrayOf(85, 0, 85, 0, 85), // ░
                133 to intArrayOf(8, 107, 28, 107, 8), // ✽
                134 to intArrayOf(28, 62, 62, 62, 28), // ●
                135 to intArrayOf(54, 127, 127, 62, 28), // ♥
                136 to intArrayOf(28, 34, 42, 34, 28), // ☉
                137 to intArrayOf(28, 28, 62, 8, 20), // 웃
                138 to intArrayOf(8, 28, 62, 54, 62), // ⌂
                139 to intArrayOf(16, 48, 127, 48, 16), // ⬅
                140 to intArrayOf(62, 107, 127, 99, 62), // 😐
                141 to intArrayOf(14, 10, 8, 56, 56), // ♪
                142 to intArrayOf(62, 99, 99, 99, 62), // 🅾
                143 to intArrayOf(8, 28, 62, 28, 8), // ◆
                144 to intArrayOf(0, 0, 0, 0, 84), // …
                145 to intArrayOf(4, 6, 127, 6, 4), // ➡
                146 to intArrayOf(8, 28, 127, 28, 34), // ★
                147 to intArrayOf(127, 62, 28, 62, 127), // ⧗
                148 to intArrayOf(8, 28, 62, 8, 8), // ⬆
                149 to intArrayOf(0, 34, 20, 8, 0), // ˇ
                150 to intArrayOf(0, 8, 20, 34, 0), // ∧
                151 to intArrayOf(99, 54, 28, 54, 99), // ❎
                152 to intArrayOf(127, 0, 127, 0, 127), // ▤
                153 to intArrayOf(85, 85, 85, 85, 85), // ▥
                254 to intArrayOf(28, 32, 64, 64, 64), // ◜
                255 to intArrayOf(28, 2, 1, 1, 1), // ◝
            )
        for ((code, rows) in wide) glyphs[code] = Glyph(rows, 7)
    }
}
