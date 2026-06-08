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

import com.vitorpamplona.amethyst.commons.richtext.MathParser.Token
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MathParserTest {
    // Kotlin uses `$` for string templates; using a constant keeps the test
    // strings readable instead of peppering them with `\$`.
    private val d = "$"
    private val bs = "\\"

    private fun math(tokens: List<Token>) = tokens.filterIsInstance<Token.Math>()

    @Test
    fun simpleInlineMath() {
        val tokens = MathParser.split("before ${d}x_1$d after")
        assertEquals(
            listOf(
                Token.Text("before "),
                Token.Math("${d}x_1$d", "x_1", false),
                Token.Text(" after"),
            ),
            tokens,
        )
    }

    @Test
    fun displayMath() {
        val tokens = MathParser.split("eq $d${d}E = mc^2$d$d end")
        val m = math(tokens).single()
        assertEquals("E = mc^2", m.latex)
        assertTrue(m.displayMode)
        assertEquals("$d${d}E = mc^2$d$d", m.raw)
    }

    @Test
    fun mathWithInternalSpacesStaysWhole() {
        val tokens = MathParser.split("Vectors ${d}A_1, ${bs}ldots, A_n$d are independent")
        val m = math(tokens).single()
        assertEquals("A_1, ${bs}ldots, A_n", m.latex)
        assertFalse(m.displayMode)
    }

    @Test
    fun theLinearIndependencePost() {
        val content =
            "Vectors ${d}A_1, ${bs}ldots, A_n$d are independent if the only choice of scalars for which " +
                "${d}x_1 A_1 + ${bs}cdots + x_n A_n = 0$d is the trivial one ${d}x_1 = ${bs}cdots = x_n = 0$d."
        val m = math(MathParser.split(content))
        assertEquals(3, m.size)
        assertEquals("A_1, ${bs}ldots, A_n", m[0].latex)
        assertEquals("x_1 A_1 + ${bs}cdots + x_n A_n = 0", m[1].latex)
        assertEquals("x_1 = ${bs}cdots = x_n = 0", m[2].latex)
    }

    @Test
    fun currencyIsNotMath() {
        // Opening `$` of `$5` is followed by a digit (fine), but the closing `$`
        // of `$10` is preceded by a space, so no valid span is formed.
        val tokens = MathParser.split("It costs ${d}5 and ${d}10 total")
        assertTrue(math(tokens).isEmpty())
        assertEquals("It costs ${d}5 and ${d}10 total", (tokens.single() as Token.Text).text)
    }

    @Test
    fun openingDollarFollowedBySpaceIsNotMath() {
        assertTrue(math(MathParser.split("a $d x + y $d b")).isEmpty())
    }

    @Test
    fun escapedDollarIsLiteral() {
        assertTrue(math(MathParser.split("price is $bs${d}5 to $bs${d}9")).isEmpty())
    }

    @Test
    fun escapedDollarInsideMathDoesNotClose() {
        val m = math(MathParser.split("cost ${d}a + $bs$d + b$d ok")).single()
        assertEquals("a + $bs$d + b", m.latex)
    }

    @Test
    fun emptyMathIsIgnored() {
        assertTrue(math(MathParser.split("empty $d$d here")).isEmpty())
    }

    @Test
    fun noDollarsShortCircuits() {
        assertFalse(MathParser.mightContainMath("just regular text"))
        val tokens = MathParser.split("just regular text")
        assertEquals(listOf(Token.Text("just regular text")), tokens)
    }

    @Test
    fun adjacentInlineSpans() {
        val m = math(MathParser.split("${d}a$d ${d}b$d"))
        assertEquals(2, m.size)
        assertEquals("a", m[0].latex)
        assertEquals("b", m[1].latex)
    }
}
