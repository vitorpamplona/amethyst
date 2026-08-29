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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.embed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The IME envelopes come off a web page, so every accessor has to survive a missing key, a null, and a
 * value of the wrong type without throwing — the keyboard must not die on a malformed message. These pin
 * that contract, which used to come from Android's bundled `org.json` and now comes from
 * kotlinx.serialization.
 */
class ImeJsonTest {
    @Test
    fun missingKeysFallBack() {
        val o = parseImeEnvelope("{}")!!
        assertEquals(0.0, o.optDouble("nope"), 0.0)
        assertEquals(7.5, o.optDouble("nope", 7.5), 0.0)
        assertEquals(0, o.optInt("nope"))
        assertEquals(3, o.optInt("nope", 3))
        assertFalse(o.optBoolean("nope"))
        assertTrue(o.optBoolean("nope", true))
        assertEquals("", o.optString("nope"))
        assertEquals("x", o.optString("nope", "x"))
        assertNull(o.optObject("nope"))
        assertFalse(o.has("nope"))
    }

    @Test
    fun wrongTypesFallBackInsteadOfThrowing() {
        val o = parseImeEnvelope("""{"n":"abc","b":"abc","o":[1,2],"s":{"a":1}}""")!!
        assertEquals(0.0, o.optDouble("n"), 0.0)
        assertEquals(0, o.optInt("n"))
        assertFalse(o.optBoolean("b"))
        assertNull(o.optObject("o"))
        // A nested object read as a string falls back rather than stringifying.
        assertEquals("fb", o.optString("s", "fb"))
    }

    @Test
    fun numericStringsAreAccepted() {
        val o = parseImeEnvelope("""{"d":"12.5","i":"42","b":"true"}""")!!
        assertEquals(12.5, o.optDouble("d"), 0.0)
        assertEquals(42, o.optInt("i"))
        assertTrue(o.optBoolean("b"))
    }

    @Test
    fun jsonNullIsPresentButStillFallsBack() {
        val o = parseImeEnvelope("""{"cx":null}""")!!
        // `has` mirrors org.json: the key is there, even holding null...
        assertTrue(o.has("cx"))
        // ...but reading it yields the fallback, never a throw.
        assertEquals(0.0, o.optDouble("cx"), 0.0)
        assertEquals("", o.optString("cx"))
    }

    @Test
    fun unknownKeysAndNonObjectsDoNotBlowUp() {
        assertNull(parseImeEnvelope("not json at all"))
        assertNull(parseImeEnvelope("[1,2,3]"))
        assertNull(parseImeEnvelope(""))
        // Unknown keys are simply carried along.
        assertEquals("ime.blur", parseImeEnvelope("""{"type":"ime.blur","future":1}""")!!.optString("type"))
    }
}
