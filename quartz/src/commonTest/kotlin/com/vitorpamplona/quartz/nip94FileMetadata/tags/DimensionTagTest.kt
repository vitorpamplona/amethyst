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
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

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

    /**
     * [rejectsZeroByZero] passes by comparing the string, so the float path of the Primal
     * tolerance above reaches the same 0x0 pixel counts by truncation and is kept.
     */
    @Test
    fun subPixelFloatsTruncateToNoPixels() {
        val tag = DimensionTag.parse("0.4x0.4")
        assertNotNull(tag)
        assertEquals(0, tag.width)
        assertEquals(0, tag.height)
        assertFalse(tag.hasSize())
    }

    /**
     * ...but the shape the author declared survives that truncation. A square is a square whether
     * it was declared as "0.4x0.4" or "400x400"; only the pixel counts are unusable.
     */
    @Test
    fun subPixelFloatsKeepTheDeclaredShape() {
        assertEquals(1f, DimensionTag.parse("0.4x0.4")?.aspectRatioOrNull())
        assertEquals(0.75f, DimensionTag.parse("0.75x1")?.aspectRatioOrNull())
        assertEquals(2f, DimensionTag.parse("0.5x0.25")?.aspectRatioOrNull())
    }

    /**
     * The declared ratio wins over the truncated one wherever they differ, being the more faithful
     * of the two. They differ only for fractional dims — for whole numbers it is the same number.
     */
    @Test
    fun declaredRatioBeatsTheTruncatedOne() {
        val tag = DimensionTag.parse("317.9x498.4")
        assertNotNull(tag)
        assertEquals((317.9 / 498.4).toFloat(), tag.aspectRatioOrNull())
        assertEquals(317f / 498f, tag.aspectRatio())
    }

    @Test
    fun wholeNumberDimsAreUnaffected() {
        val tag = DimensionTag.parse("1920x1080")
        assertNotNull(tag)
        assertEquals(tag.aspectRatio(), tag.aspectRatioOrNull())
    }

    /**
     * The declared ratio is derived from the tag text, so it is not serialized — a tag rebuilt
     * from its width and height falls back to the pixel counts rather than carrying a stale one.
     */
    @Test
    fun aDirectlyConstructedTagFallsBackToPixelCounts() {
        assertEquals(1920f / 1080f, DimensionTag(1920, 1080).aspectRatioOrNull())
        assertNull(DimensionTag(0, 0).aspectRatioOrNull())
    }

    /**
     * Why [DimensionTag.aspectRatioOrNull] exists: `Modifier.aspectRatio` throws on both of these,
     * so a tag that reaches a layout through the raw accessor crashes the composition around it.
     */
    @Test
    fun rawAspectRatioOfAZeroSizedTagIsNotLayoutSafe() {
        assertTrue(DimensionTag(0, 0).aspectRatio().isNaN())
        assertEquals(0f, DimensionTag(0, 100).aspectRatio())
    }

    /**
     * A fractional dim keeps its shape (see [subPixelFloatsKeepTheDeclaredShape]); one that
     * declares no shape at all still has to come back null, since `Modifier.aspectRatio` throws on
     * everything this would otherwise produce.
     */
    @Test
    fun aspectRatioOrNullRefusesEveryShapeALayoutCannotUse() {
        assertNull(DimensionTag.parse("0x5")?.aspectRatioOrNull())
        assertNull(DimensionTag.parse("-3x4")?.aspectRatioOrNull())
        assertNull(DimensionTag(0, 0).aspectRatioOrNull())
        assertNull(DimensionTag(100, 0).aspectRatioOrNull())
        assertNull(DimensionTag(0, 100).aspectRatioOrNull())
        assertNull(DimensionTag(-1, 10).aspectRatioOrNull())
    }

    @Test
    fun aspectRatioOrNullKeepsUsableSizes() {
        assertEquals(317f / 498f, DimensionTag(317, 498).aspectRatioOrNull())
    }

    /**
     * `"NaN"` and `"Infinity"` are legal input to Kotlin's `String.toDouble()` and a relay can
     * carry either, so both reach the ratio maths. The `<= 0.0` rejection cannot stop `NaN` —
     * every comparison against it is false — which is why the finite check is the one holding the
     * line. Whatever comes out, it is never a shape a layout would throw on.
     */
    @Test
    fun nonNumericDoublesNeverProduceAnUnusableShape() {
        assertNull(DimensionTag.parse("NaNxNaN")?.aspectRatioOrNull())
        assertNull(DimensionTag.parse("0.4xNaN")?.aspectRatioOrNull())

        // Infinity saturates to Int.MAX_VALUE on both axes: garbage in, square out, never a throw.
        assertEquals(1f, DimensionTag.parse("InfinityxInfinity")?.aspectRatioOrNull())
    }
}
