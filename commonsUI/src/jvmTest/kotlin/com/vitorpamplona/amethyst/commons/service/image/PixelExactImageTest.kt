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
package com.vitorpamplona.amethyst.commons.service.image

import com.vitorpamplona.amethyst.commons.ui.render.SoftwareDrawing
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.Color
import org.jetbrains.skia.Image
import org.jetbrains.skia.SamplingMode
import org.jetbrains.skia.Surface
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PixelExactImageTest {
    @BeforeTest
    fun drawInSoftware() {
        SoftwareDrawing.active = true
        ExactCopies.clear()
    }

    @AfterTest
    fun reset() {
        SoftwareDrawing.active = false
        ExactCopies.clear()
    }

    private fun solid(
        width: Int,
        height: Int,
        color: Int,
    ): PixelExactImage {
        val bitmap = Bitmap()
        bitmap.allocN32Pixels(width, height, true)
        bitmap.erase(color)
        bitmap.setImmutable()
        return PixelExactImage(bitmap)
    }

    /** The drawn area, as the columns and rows that came out [color] on a white canvas. */
    private fun drawnArea(
        draw: (Canvas) -> Unit,
        color: Int,
    ): List<Int> {
        val surface = Surface.makeRasterN32Premul(200, 200)
        surface.canvas.clear(Color.WHITE)
        draw(surface.canvas)
        val out = Bitmap()
        out.allocN32Pixels(200, 200, true)
        surface.readPixels(out, 0, 0)
        val xs = (0 until 200).filter { x -> (0 until 200).any { y -> out.getColor(x, y) == color } }
        val ys = (0 until 200).filter { y -> (0 until 200).any { x -> out.getColor(x, y) == color } }
        // Every pixel inside the box is the color: no blended edge from a fractional position.
        for (x in xs) for (y in ys) assertEquals(color, out.getColor(x, y), "pixel $x,$y")
        return listOf(xs.first(), ys.first(), xs.size, ys.size)
    }

    @Test
    fun scaledDrawLandsOnWholePixelsAtItsOnScreenSize() {
        val image = solid(100, 80, Color.RED)
        val area =
            drawnArea({ canvas ->
                canvas.translate(10.4f, 5.6f)
                canvas.scale(0.5f, 0.5f)
                image.draw(canvas)
            }, Color.RED)
        assertEquals(listOf(10, 6, 50, 40), area)
    }

    @Test
    fun unscaledDrawAtAFractionalOffsetLandsOnWholePixels() {
        val image = solid(30, 20, Color.BLUE)
        val area =
            drawnArea({ canvas ->
                canvas.translate(3.7f, 8.2f)
                image.draw(canvas)
            }, Color.BLUE)
        assertEquals(listOf(4, 8, 30, 20), area)
    }

    @Test
    fun upscaledDrawFillsItsOnScreenSize() {
        val image = solid(10, 10, Color.GREEN)
        val area =
            drawnArea({ canvas ->
                canvas.scale(4f, 3f)
                image.draw(canvas)
            }, Color.GREEN)
        assertEquals(listOf(0, 0, 40, 30), area)
    }

    @Test
    fun onAGpuItDrawsScaledExactlyAsAPlainBitmap() {
        SoftwareDrawing.active = false
        val image = solid(100, 80, Color.RED)
        val expected =
            render {
                it.translate(10.4f, 5.6f)
                it.scale(0.5f, 0.5f)
                it.drawImageRect(Image.makeFromBitmap(image.bitmap), 0f, 0f, 100f, 80f, 0f, 0f, 100f, 80f, SamplingMode.LINEAR, null, true)
            }
        val actual =
            render {
                it.translate(10.4f, 5.6f)
                it.scale(0.5f, 0.5f)
                image.draw(it)
            }
        assertTrue(expected.contentEquals(actual))
        assertEquals(0, ExactCopies.size)
    }

    @Test
    fun smallTargetsAreNotCopied() {
        val image = solid(100, 100, Color.RED)
        render {
            it.scale(0.5f, 0.5f)
            image.draw(it)
        }
        assertEquals(0, ExactCopies.size)
    }

    @Test
    fun imagesWrappingTheSameBitmapShareTheirCopy() {
        val first = solid(400, 400, Color.RED)
        val second = PixelExactImage(first.bitmap)
        render {
            it.scale(0.5f, 0.5f)
            first.draw(it)
        }
        render {
            it.scale(0.5f, 0.5f)
            second.draw(it)
        }
        assertEquals(1, ExactCopies.size)
        // The copy and the decoded picture it keeps alive.
        assertEquals(200L * 200 * 4 + 400L * 400 * 4, ExactCopies.heldBytes)
    }

    @Test
    fun theBudgetEvictsTheOldestCopies() {
        // Each entry holds a 900x900 copy and its 1000x1000 source: ~7 MB, so ~9 fit in 64 MB.
        repeat(20) {
            val image = solid(1000, 1000, Color.BLUE)
            render { c ->
                c.scale(0.9f, 0.9f)
                image.draw(c)
            }
        }
        assertTrue(ExactCopies.heldBytes <= 64L * 1024 * 1024, "held ${ExactCopies.heldBytes}")
        assertTrue(ExactCopies.size in 5..10, "kept ${ExactCopies.size}")
    }

    @Test
    fun preparedCopiesAreReadyBeforeTheFirstDraw() {
        val image = solid(400, 300, Color.RED)
        assertFalse(image.isReady(200, 150))
        runBlocking { image.prepare(200, 150) }
        assertTrue(image.isReady(200, 150))
        assertEquals(1, ExactCopies.size)
        // The draw finds it: no second copy.
        render {
            it.scale(0.5f, 0.5f)
            image.draw(it)
        }
        assertEquals(1, ExactCopies.size)
    }

    @Test
    fun aHugeSourceDrawsScaledWithoutACopy() {
        // 2200x2000 is 17.6 MB: past what a copy's source may keep alive.
        val image = solid(2200, 2000, Color.BLUE)
        render {
            it.scale(0.25f, 0.25f)
            image.draw(it)
        }
        assertEquals(0, ExactCopies.size)
        assertTrue(image.isReady(550, 500))
    }

    private fun render(draw: (Canvas) -> Unit): ByteArray {
        val surface = Surface.makeRasterN32Premul(600, 600)
        surface.canvas.clear(Color.WHITE)
        draw(surface.canvas)
        val out = Bitmap()
        out.allocN32Pixels(600, 600, true)
        surface.readPixels(out, 0, 0)
        return out.readPixels()!!
    }
}
