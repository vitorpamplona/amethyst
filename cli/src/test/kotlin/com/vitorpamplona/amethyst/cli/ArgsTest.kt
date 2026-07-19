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
package com.vitorpamplona.amethyst.cli

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ArgsTest {
    @Test
    fun flagForms() {
        val args = Args(arrayOf("--name", "alice", "--about=hi there", "pos1"))
        assertEquals("alice", args.flag("name"))
        assertEquals("hi there", args.flag("about"))
        assertEquals("pos1", args.positional(0, "first"))
    }

    @Test
    fun booleanFlags() {
        val args = Args(arrayOf("--json-ish", "--limit", "5"))
        assertTrue(args.bool("json-ish"))
        assertEquals(5, args.intFlag("limit", 1))
        assertFalse(args.bool("absent"))
    }

    @Test
    fun doubleDashEndsFlagParsing() {
        val args = Args(arrayOf("--relay", "wss://a", "--", "--not-a-flag", "-x"))
        assertEquals("wss://a", args.flag("relay"))
        assertEquals(listOf("--not-a-flag", "-x"), args.positional)
    }

    @Test
    fun nonNumericIntFlagThrows() {
        val args = Args(arrayOf("--limit", "ten"))
        assertFailsWith<IllegalArgumentException> { args.intFlag("limit", 1) }
    }

    @Test
    fun nonNumericLongFlagThrows() {
        val args = Args(arrayOf("--timeout", "soon"))
        assertFailsWith<IllegalArgumentException> { args.longFlag("timeout", 1) }
    }

    @Test
    fun absentNumericFlagsFallBackToDefault() {
        val args = Args(arrayOf())
        assertEquals(7, args.intFlag("limit", 7))
        assertEquals(9L, args.longFlag("timeout", 9))
    }

    @Test
    fun requireFlagThrowsWithFlagNameInMessage() {
        val e = assertFailsWith<IllegalArgumentException> { Args(arrayOf()).requireFlag("server") }
        assertTrue(e.message!!.contains("--server"))
    }

    @Test
    fun missingPositionalThrows() {
        assertFailsWith<IllegalArgumentException> { Args(arrayOf()).positional(0, "text") }
        assertNull(Args(arrayOf()).positionalOrNull(0))
    }

    @Test
    fun rejectUnknownFlagsTypo() {
        val args = Args(arrayOf("--limt", "5"))
        args.intFlag("limit", 1)
        val e = assertFailsWith<IllegalArgumentException> { args.rejectUnknown() }
        assertTrue(e.message!!.contains("--limt"))
    }

    @Test
    fun rejectUnknownPassesWhenAllFlagsRead() {
        val args = Args(arrayOf("--limit", "5", "--force"))
        args.intFlag("limit", 1)
        args.bool("force")
        args.rejectUnknown() // must not throw
    }

    @Test
    fun rejectUnknownHonoursAlsoAllowed() {
        val args = Args(arrayOf("--relay", "wss://a"))
        args.rejectUnknown("relay") // read by a helper later — declared instead
    }

    @Test
    fun rejectUnknownAlwaysAllowsHelp() {
        val args = Args(arrayOf("--help"))
        args.rejectUnknown()
        assertTrue(args.help)
    }

    @Test
    fun conditionalReadStillRegisters() {
        // flag() records the name even when the flag is absent from argv.
        val args = Args(arrayOf("--mint", "https://m"))
        assertNull(args.flag("mints"))
        assertEquals("https://m", args.flag("mint"))
        args.rejectUnknown()
    }
}
