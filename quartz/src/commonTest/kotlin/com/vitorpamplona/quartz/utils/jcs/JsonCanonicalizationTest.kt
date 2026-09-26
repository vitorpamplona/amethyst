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
package com.vitorpamplona.quartz.utils.jcs

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * RFC 8785 conformance.
 *
 * The number cases are the ones that matter: they are where an implementation
 * that "works" silently produces a different hash from every other one.
 */
class JsonCanonicalizationTest {
    @Test
    fun `sorts object keys by UTF-16 code unit`() {
        val input = linkedMapOf<String, Any?>("b" to 1, "a" to 2, "C" to 3, "ä" to 4)
        // Uppercase sorts before lowercase, and non-ASCII after both.
        assertEquals("""{"C":3,"a":2,"b":1,"ä":4}""", JsonCanonicalization.canonicalize(input))
    }

    @Test
    fun `sorts nested objects too`() {
        val input = mapOf("z" to linkedMapOf("y" to 1, "x" to 2))
        assertEquals("""{"z":{"x":2,"y":1}}""", JsonCanonicalization.canonicalize(input))
    }

    @Test
    fun `preserves array order`() {
        assertEquals("""[3,1,2]""", JsonCanonicalization.canonicalize(listOf(3, 1, 2)))
    }

    @Test
    fun `emits no insignificant whitespace`() {
        val input = mapOf("a" to listOf(1, mapOf("b" to true)), "c" to null)
        assertEquals("""{"a":[1,{"b":true}],"c":null}""", JsonCanonicalization.canonicalize(input))
    }

    @Test
    fun `escapes only what RFC 8785 requires`() {
        val input =
            mapOf(
                "k" to
                    "a\"b\\c\nd\te" + '\u0008' + "f" + '\u000C' + "g\rh" + '\u0001' + "i",
            )
        assertEquals(
            "{\"k\":\"a\\\"b\\\\c\\nd\\te\\bf\\fg\\rh\\u0001i\"}",
            JsonCanonicalization.canonicalize(input),
        )
    }

    @Test
    fun `leaves non-ASCII literal rather than escaping it`() {
        // The canonical form is UTF-8. Escaping to \u would be a different
        // byte sequence and therefore a different hash.
        assertEquals("""{"k":"héllo → 🚀"}""", JsonCanonicalization.canonicalize(mapOf("k" to "héllo → 🚀")))
    }

    // --- numbers: ECMAScript Number::toString ---

    @Test
    fun `renders integral values without a decimal point`() {
        assertEquals("0", JsonCanonicalization.canonicalNumber(0.0))
        assertEquals("1", JsonCanonicalization.canonicalNumber(1.0))
        assertEquals("123", JsonCanonicalization.canonicalNumber(123.0))
        assertEquals("-123", JsonCanonicalization.canonicalNumber(-123.0))
    }

    @Test
    fun `renders negative zero as zero`() {
        // ECMAScript prints both zeroes as "0", so they canonicalize identically.
        assertEquals("0", JsonCanonicalization.canonicalNumber(-0.0))
    }

    @Test
    fun `renders fractions plainly inside the non-exponential range`() {
        assertEquals("1.5", JsonCanonicalization.canonicalNumber(1.5))
        assertEquals("0.5", JsonCanonicalization.canonicalNumber(0.5))
        assertEquals("-0.5", JsonCanonicalization.canonicalNumber(-0.5))
        assertEquals("0.000001", JsonCanonicalization.canonicalNumber(0.000001))
    }

    @Test
    fun `switches to exponential below 1e-6`() {
        // The boundary ECMAScript defines: 1e-6 prints plainly, 1e-7 does not.
        assertEquals("1e-7", JsonCanonicalization.canonicalNumber(1e-7))
        assertEquals("1.5e-7", JsonCanonicalization.canonicalNumber(1.5e-7))
    }

    @Test
    fun `switches to exponential at 1e21 and carries a plus sign`() {
        // JVM toString gives "1.0E21"; ECMAScript and therefore JCS want "1e+21".
        assertEquals("1e+21", JsonCanonicalization.canonicalNumber(1e21))
        assertEquals("1e+30", JsonCanonicalization.canonicalNumber(1e30))
        assertEquals("1.5e+30", JsonCanonicalization.canonicalNumber(1.5e30))
    }

    @Test
    fun `prints 1e20 plainly because it is still inside the range`() {
        assertEquals("100000000000000000000", JsonCanonicalization.canonicalNumber(1e20))
    }

    @Test
    fun `renders the extremes of the double range`() {
        assertEquals("5e-324", JsonCanonicalization.canonicalNumber(Double.MIN_VALUE))
        assertEquals("1.7976931348623157e+308", JsonCanonicalization.canonicalNumber(Double.MAX_VALUE))
    }

    @Test
    fun `renders values that need every significant digit`() {
        assertEquals("0.1", JsonCanonicalization.canonicalNumber(0.1))
        assertEquals("0.30000000000000004", JsonCanonicalization.canonicalNumber(0.1 + 0.2))
        assertEquals("9007199254740991", JsonCanonicalization.canonicalNumber(9007199254740991.0))
    }

    @Test
    fun `integers arrive through the same path as doubles`() {
        assertEquals("""{"a":1,"b":2}""", JsonCanonicalization.canonicalize(mapOf("a" to 1, "b" to 2L)))
    }

    @Test
    fun `rejects values JSON cannot represent`() {
        assertFailsWith<IllegalArgumentException> { JsonCanonicalization.canonicalNumber(Double.NaN) }
        assertFailsWith<IllegalArgumentException> {
            JsonCanonicalization.canonicalNumber(Double.POSITIVE_INFINITY)
        }
    }

    @Test
    fun `rejects a non-string object key`() {
        assertFailsWith<IllegalArgumentException> {
            JsonCanonicalization.canonicalize(mapOf(1 to "a"))
        }
    }

    @Test
    fun `rejects a type it cannot represent`() {
        assertFailsWith<IllegalArgumentException> {
            JsonCanonicalization.canonicalize(mapOf("k" to Any()))
        }
    }

    @Test
    fun `canonicalizes the RFC 8785 number sample`() {
        // The number array from RFC 8785's worked example. Each entry exercises a
        // different branch: full significant digits, the upper exponential
        // boundary, a stripped trailing zero, a small plain fraction, and the
        // lower exponential boundary.
        val input =
            mapOf(
                "numbers" to listOf(333333333.33333329, 1E30, 4.50, 2e-3, 0.000000000000000000000000001),
            )
        assertEquals(
            """{"numbers":[333333333.3333333,1e+30,4.5,0.002,1e-27]}""",
            JsonCanonicalization.canonicalize(input),
        )
    }

    @Test
    fun `escapes a dollar sign and a solidus literally`() {
        // Neither has a short escape in RFC 8785, and the solidus is explicitly
        // NOT escaped even though JSON permits it.
        assertEquals("{\"k\":\"$100/mo\"}", JsonCanonicalization.canonicalize(mapOf("k" to "\u0024100/mo")))
    }
}
