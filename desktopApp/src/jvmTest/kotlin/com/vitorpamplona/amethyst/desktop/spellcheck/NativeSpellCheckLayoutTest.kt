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
package com.vitorpamplona.amethyst.desktop.spellcheck

import com.sun.jna.Structure
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

/**
 * The native checkers only run on macOS and Windows, but the memory their structs put on the wire
 * can be checked anywhere: a wrong layout would only show up there as a crash or a silent failure.
 */
class NativeSpellCheckLayoutTest {
    @Test
    fun guidIsLaidOutLikeWindowsExpects() {
        // CLSID_SpellCheckerFactory {7AB36653-1796-484B-BDFA-E74F1DB7C1DC}: Data1..3 little-endian, Data4 as written.
        val guid = WindowsSpellChecker.Guid.of("7ab36653-1796-484b-bdfa-e74f1db7c1dc")
        guid.write()

        assertEquals(16, guid.size())
        assertContentEquals(
            byteArrayOf(
                0x53,
                0x66,
                0xB3.toByte(),
                0x7A,
                0x96.toByte(),
                0x17,
                0x4B,
                0x48,
                0xBD.toByte(),
                0xFA.toByte(),
                0xE7.toByte(),
                0x4F,
                0x1D,
                0xB7.toByte(),
                0xC1.toByte(),
                0xDC.toByte(),
            ),
            guid.pointer.getByteArray(0, 16),
        )
    }

    @Test
    fun nsRangeIsTwoWordsAndJnaCanMakeOne() {
        // JNA builds the returned struct through the no-argument constructor.
        val range = Structure.newInstance(MacSpellChecker.NSRange.ByValue::class.java)
        assertEquals(16, range.size())

        val written = MacSpellChecker.NSRange.ByValue(3, 7)
        written.write()
        assertEquals(3L, written.pointer.getLong(0))
        assertEquals(7L, written.pointer.getLong(8))
    }

    @Test
    fun latinScriptCheck() {
        assertEquals(true, isLatinScript("naïve"))
        assertEquals(true, isLatinScript("Việt"))
        assertEquals(false, isLatinScript("Ελλάδα"))
    }
}
