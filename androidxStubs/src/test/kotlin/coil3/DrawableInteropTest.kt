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
package coil3

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Every avatar and notification icon crosses this bridge as
 * `(image.asDrawable(resources) as? BitmapDrawable)?.bitmap`. Channel order and
 * alpha handling are exactly the sort of thing that produces a blue-tinted or
 * darkened image rather than an error, so the pixels are checked, not just the
 * types.
 */
class DrawableInteropTest {
    private fun bitmapOf(vararg argb: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(argb.size, 1, Bitmap.Config.ARGB_8888)
        argb.forEachIndexed { x, color -> bitmap.setPixel(x, 0, color) }
        return bitmap
    }

    private fun assertClose(
        expected: Int,
        actual: Int,
        message: String,
    ) {
        for (shift in listOf(24, 16, 8, 0)) {
            val e = (expected shr shift) and 0xFF
            val a = (actual shr shift) and 0xFF
            assertTrue(
                abs(e - a) <= 1,
                "$message: channel at $shift was $a, expected $e " +
                    "(${expected.toUInt().toString(16)} vs ${actual.toUInt().toString(16)})",
            )
        }
    }

    @Test
    fun opaqueColorsSurviveTheRoundTrip() {
        val colors = intArrayOf(0xFFFF0000.toInt(), 0xFF00FF00.toInt(), 0xFF0000FF.toInt(), 0xFF123456.toInt())
        val back = bitmapOf(*colors.toTypedArray().toIntArray()).asImage().toAndroidBitmap()

        colors.forEachIndexed { x, expected ->
            assertClose(expected, back.getPixel(x, 0), "pixel $x")
        }
    }

    @Test
    fun redDoesNotComeBackAsBlue() {
        // The single most likely bug here is a BGRA/RGBA mix-up, which is
        // silent and looks like a colour-graded image.
        val back = bitmapOf(0xFFFF0000.toInt()).asImage().toAndroidBitmap().getPixel(0, 0)
        assertTrue((back shr 16) and 0xFF > 200, "red channel lost")
        assertTrue(back and 0xFF < 40, "red came back in the blue channel")
    }

    @Test
    fun partialAlphaIsNotDarkened() {
        // Skia stores premultiplied by default; forgetting to un-multiply
        // darkens every semi-transparent pixel by exactly its alpha.
        val original = 0x80FF8040.toInt()
        val back = bitmapOf(original).asImage().toAndroidBitmap().getPixel(0, 0)
        assertClose(original, back, "semi-transparent pixel")
    }

    @Test
    fun fullyTransparentStaysTransparent() {
        val back = bitmapOf(0x00000000).asImage().toAndroidBitmap().getPixel(0, 0)
        assertEquals(0, (back shr 24) and 0xFF)
    }

    @Test
    fun dimensionsAreCarried() {
        val bitmap = Bitmap.createBitmap(7, 3, Bitmap.Config.ARGB_8888)
        val image = bitmap.asImage()
        assertEquals(7, image.width)
        assertEquals(3, image.height)

        val back = image.toAndroidBitmap()
        assertEquals(7, back.getWidth())
        assertEquals(3, back.getHeight())
    }

    @Test
    fun asDrawableGivesBackAReadableBitmap() {
        // This exact shape is what six call sites write.
        val drawable = bitmapOf(0xFF204060.toInt()).asImage().asDrawable(null)

        assertIs<BitmapDrawable>(drawable)
        val bitmap = drawable.bitmap
        assertClose(0xFF204060.toInt(), bitmap.getPixel(0, 0), "unwrapped pixel")
        assertEquals(1, drawable.intrinsicWidth)
    }
}
