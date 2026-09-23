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
package com.vitorpamplona.quartz.cyberspace.deck0003Sno

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The built-in palette is normative (DECK-0003 Appendix C) and 256 entries long,
 * generated here from `decks/sno-palette.json` rather than typed. These anchors
 * are the ones the deck itself names, so a bad paste cannot ship silently.
 */
class SnoBuiltInPaletteTest {
    @Test
    fun theresTwoHundredAndFiftySixOfThem() {
        assertEquals(256, SnoBuiltInPalette.COLORS.size)
        assertEquals(256, SnoPalette.BUILT_IN.size)
    }

    @Test
    fun theAnchorsAppendixANamesAreWhereItSaysTheyAre() {
        assertEquals(0xFFFF0000.toInt(), SnoBuiltInPalette.COLORS[238], "238 is pure red")
        assertEquals(0xFF00FF00.toInt(), SnoBuiltInPalette.COLORS[235], "235 is pure green")
        assertEquals(0xFF0000FF.toInt(), SnoBuiltInPalette.COLORS[239], "239 is pure blue")
        assertEquals(0xFFFFFFFF.toInt(), SnoBuiltInPalette.COLORS[225], "225 is white")
        assertEquals(0xFF000000.toInt(), SnoBuiltInPalette.COLORS[224], "224 is black")
    }

    @Test
    fun theRampsAndSteelsAreWhereTheLayoutSaysTheyAre() {
        // 0..191 is 24 hues of 8 steps; the first entry is the darkest of hue 0.
        assertEquals(0xFF003632.toInt(), SnoBuiltInPalette.COLORS[0])
        // 192..223 is 32 steels running black to white.
        assertEquals(0xFF010101.toInt(), SnoBuiltInPalette.COLORS[192])
        assertEquals(0xFFF8F8F8.toInt(), SnoBuiltInPalette.COLORS[223])
        // 224..255 is the signatures; the last is a deep ground.
        assertEquals(0xFF1C1C0F.toInt(), SnoBuiltInPalette.COLORS[255])
    }

    @Test
    fun everyEntryIsOpaque() {
        SnoBuiltInPalette.COLORS.forEach {
            assertEquals(0xFF, (it shr 24) and 0xFF)
        }
    }
}
