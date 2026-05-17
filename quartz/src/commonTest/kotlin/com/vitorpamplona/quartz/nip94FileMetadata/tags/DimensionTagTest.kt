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
package com.vitorpamplona.quartz.nip94FileMetadata.tags

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class DimensionTagTest {
    @Test
    fun parsesIntegerDimensions() {
        val tag = DimensionTag.parse("317x498")
        assertNotNull(tag)
        assertEquals(317, tag.width)
        assertEquals(498, tag.height)
    }

    @Test
    fun parsesFloatDimensionsFromPrimal() {
        // Regression: kind:1 notes from Primal-style clients ship floating-point dims in
        // their imeta tag (e.g. "dim 317.0x498.0"). Before this was tolerated the value
        // parsed to null, the GIF/image container lost its aspectRatio modifier, and the
        // post body collapsed to zero height until Coil delivered the bitmap.
        val tag = DimensionTag.parse("317.0x498.0")
        assertNotNull(tag)
        assertEquals(317, tag.width)
        assertEquals(498, tag.height)
    }

    @Test
    fun truncatesNonIntegerFloats() {
        val tag = DimensionTag.parse("317.9x498.4")
        assertNotNull(tag)
        assertEquals(317, tag.width)
        assertEquals(498, tag.height)
    }

    @Test
    fun rejectsZeroByZero() {
        assertNull(DimensionTag.parse("0x0"))
    }

    @Test
    fun rejectsMalformed() {
        assertNull(DimensionTag.parse("not-a-dim"))
        assertNull(DimensionTag.parse("317"))
        assertNull(DimensionTag.parse("317x"))
    }

    @Test
    fun aspectRatioMatchesPrimalGif() {
        val tag = DimensionTag.parse("317.0x498.0")
        assertNotNull(tag)
        assertEquals(317f / 498f, tag.aspectRatio())
    }
}
