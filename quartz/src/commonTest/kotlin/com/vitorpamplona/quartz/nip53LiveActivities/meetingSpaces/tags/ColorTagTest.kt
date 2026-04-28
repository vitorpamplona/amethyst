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
package com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.tags

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ColorTagTest {
    @Test
    fun parsesValidHexAndTarget() {
        val parsed = ColorTag.parse(arrayOf("c", "#FF8800", "background"))
        assertEquals("FF8800", parsed?.hex)
        assertEquals(ColorTag.Target.BACKGROUND, parsed?.target)
    }

    @Test
    fun parsesWithoutHashPrefix() {
        val parsed = ColorTag.parse(arrayOf("c", "abcdef", "primary"))
        assertEquals("ABCDEF", parsed?.hex)
        assertEquals(ColorTag.Target.PRIMARY, parsed?.target)
    }

    @Test
    fun rejectsShortHex() {
        // `#abc` shorthand is rejected — keeps the parser strict so
        // a typo'd room event can't crash the renderer.
        assertNull(ColorTag.parse(arrayOf("c", "#abc", "text")))
    }

    @Test
    fun rejectsNamedColors() {
        assertNull(ColorTag.parse(arrayOf("c", "red", "text")))
    }

    @Test
    fun rejectsUnknownTarget() {
        assertNull(ColorTag.parse(arrayOf("c", "#abcdef", "accent")))
    }

    @Test
    fun rejectsMissingTarget() {
        assertNull(ColorTag.parse(arrayOf("c", "#abcdef")))
    }

    @Test
    fun assembleProducesUppercaseNoHash() {
        val tag = ColorTag.assemble("#abcdef", ColorTag.Target.TEXT)
        assertEquals(arrayOf("c", "ABCDEF", "text").toList(), tag.toList())
    }

    @Test
    fun roundTripPreservesEverything() {
        val tag = ColorTag.assemble("FF00FF", ColorTag.Target.PRIMARY)
        val parsed = ColorTag.parse(tag)
        assertEquals(ColorTag(hex = "FF00FF", target = ColorTag.Target.PRIMARY), parsed)
    }
}
