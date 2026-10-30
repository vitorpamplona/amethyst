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
package com.vitorpamplona.fantasyconsole

import com.vitorpamplona.fantasyconsole.lua.Fix
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FixTest {
    private fun str(f: Int) = Fix.toStr(f)

    @Test
    fun formatsWithFourDecimals() {
        assertEquals("0.3333", str(Fix.div(Fix.ONE, Fix.fromInt(3))))
        assertEquals("0.6667", str(Fix.div(Fix.fromInt(2), Fix.fromInt(3))))
        assertEquals("-1.5", str(-Fix.ONE * 3 / 2))
        assertEquals("32767", str(Fix.fromInt(32767)))
        assertEquals("-32768", str(Int.MIN_VALUE))
        assertEquals("0", str(0))
        assertEquals("0x0001.8000", Fix.toHexStr(Fix.ONE * 3 / 2))
    }

    @Test
    fun parsesLiterals() {
        assertEquals(Fix.ONE * 3 / 2, Fix.parse("1.5"))
        assertEquals(Fix.ONE / 2, Fix.parse(".5"))
        assertEquals(0x1199a, Fix.parse("1.1"))
        assertEquals(0x00108000, Fix.parse("0x10.8"))
        assertEquals(Fix.fromInt(5), Fix.parse("0b101"))
        assertEquals(Fix.ONE / 2 + Fix.ONE, Fix.parse("0b1.1"))
        assertEquals(Fix.fromInt(-1), Fix.parse("65535"))
        assertNull(Fix.parse("2e3"))
        assertEquals(Fix.fromInt(-3), Fix.parse("-3"))
        assertNull(Fix.parse("abc"))
        assertNull(Fix.parse("1.2.3"))
        assertNull(Fix.parse(""))
    }

    @Test
    fun divisionAndModulo() {
        assertEquals(Fix.MAX, Fix.div(Fix.ONE, 0))
        assertEquals(Fix.MIN_PLUS_ONE, Fix.div(-Fix.ONE, 0))
        assertEquals(Int.MIN_VALUE, Fix.div(Int.MIN_VALUE, Fix.ONE))
        assertEquals(Fix.fromInt(2), Fix.mod(Fix.fromInt(-7), Fix.fromInt(3)))
        assertEquals(Fix.fromInt(1), Fix.mod(Fix.fromInt(7), Fix.fromInt(-3)))
        assertEquals(Fix.fromInt(-4), Fix.idiv(Fix.fromInt(-7), Fix.fromInt(2)))
    }

    @Test
    fun flooringAndShifts() {
        assertEquals(Fix.fromInt(-2), Fix.flr(-Fix.ONE * 3 / 2))
        assertEquals(Fix.fromInt(-1), Fix.ceil(-Fix.ONE * 3 / 2))
        assertEquals(Fix.fromInt(4), Fix.shl(Fix.ONE, Fix.fromInt(2)))
        assertEquals(Fix.ONE / 2, Fix.shr(Fix.ONE, Fix.ONE))
        assertEquals(0, Fix.shl(Fix.ONE, Fix.fromInt(40)))
    }
}
