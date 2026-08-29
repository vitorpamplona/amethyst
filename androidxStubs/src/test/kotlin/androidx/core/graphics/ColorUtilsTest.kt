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
package androidx.core.graphics

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The app nudges a note's accent colour's lightness through these, so an
 * approximate implementation shows up as unreadable text rather than a crash.
 * Values are checked against the platform's documented behaviour.
 */
class ColorUtilsTest {
    private fun hsl(color: Int) = FloatArray(3).also { ColorUtils.colorToHSL(color, it) }

    @Test
    fun primariesMapToTheirKnownHues() {
        assertEquals(0f, hsl(0xFFFF0000.toInt())[0], 0.01f)
        assertEquals(120f, hsl(0xFF00FF00.toInt())[0], 0.01f)
        assertEquals(240f, hsl(0xFF0000FF.toInt())[0], 0.01f)
    }

    @Test
    fun greysHaveNoSaturationAndTheirOwnLightness() {
        val black = hsl(0xFF000000.toInt())
        val white = hsl(0xFFFFFFFF.toInt())
        val mid = hsl(0xFF808080.toInt())

        assertEquals(0f, black[1], 0.001f)
        assertEquals(0f, black[2], 0.001f)
        assertEquals(0f, white[1], 0.001f)
        assertEquals(1f, white[2], 0.001f)
        assertEquals(0f, mid[1], 0.001f)
        assertEquals(0.502f, mid[2], 0.01f)
    }

    @Test
    fun everyColorSurvivesTheRoundTrip() {
        val colors =
            listOf(
                0xFFFF0000,
                0xFF00FF00,
                0xFF0000FF,
                0xFFFFFF00,
                0xFF00FFFF,
                0xFFFF00FF,
                0xFF123456,
                0xFF7F3FBF,
                0xFF010203,
                0xFFFEFDFC,
            ).map { it.toInt() }

        for (color in colors) {
            val back = ColorUtils.HSLToColor(hsl(color))
            for (shift in listOf(16, 8, 0)) {
                val expected = (color shr shift) and 0xFF
                val actual = (back shr shift) and 0xFF
                assertTrue(
                    abs(expected - actual) <= 1,
                    "channel at $shift drifted for ${color.toUInt().toString(16)}: $expected vs $actual",
                )
            }
        }
    }

    @Test
    fun raisingLightnessProducesALighterColorNotTheSameOne() {
        val original = 0xFF3050A0.toInt()
        val values = hsl(original)
        values[2] = (values[2] + 0.25f).coerceAtMost(1f)
        val lighter = ColorUtils.HSLToColor(values)

        assertTrue(
            ColorUtils.calculateLuminance(lighter) > ColorUtils.calculateLuminance(original),
            "a stub that returned its input would pass nothing here",
        )
    }

    @Test
    fun blendRunsFromOneEndToTheOther() {
        val a = 0xFF000000.toInt()
        val b = 0xFFFFFFFF.toInt()
        assertEquals(a, ColorUtils.blendARGB(a, b, 0f))
        assertEquals(b, ColorUtils.blendARGB(a, b, 1f))
        assertEquals(0x80, ColorUtils.blendARGB(a, b, 0.5f) and 0xFF)
    }

    @Test
    fun compositingOverAnOpaqueBackgroundKeepsItOpaque() {
        val over = ColorUtils.compositeColors(0x80FF0000.toInt(), 0xFF000000.toInt())
        assertEquals(0xFF, (over shr 24) and 0xFF)
        // Half-transparent red over black is a darker red, not black and not red.
        assertTrue(((over shr 16) and 0xFF) in 100..160)
    }

    @Test
    fun luminanceOrdersBlackBelowGreyBelowWhite() {
        val black = ColorUtils.calculateLuminance(0xFF000000.toInt())
        val grey = ColorUtils.calculateLuminance(0xFF808080.toInt())
        val white = ColorUtils.calculateLuminance(0xFFFFFFFF.toInt())
        assertEquals(0.0, black, 0.0001)
        assertEquals(1.0, white, 0.0001)
        assertTrue(black < grey && grey < white)
    }

    @Test
    fun setAlphaTouchesOnlyTheAlpha() {
        val result = ColorUtils.setAlphaComponent(0xFF123456.toInt(), 0x40)
        assertEquals(0x40123456, result)
    }
}
