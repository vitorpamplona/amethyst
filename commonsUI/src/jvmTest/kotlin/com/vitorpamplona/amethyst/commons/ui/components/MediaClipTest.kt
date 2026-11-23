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
package com.vitorpamplona.amethyst.commons.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.ui.render.SoftwareDrawing
import org.jetbrains.skia.Bitmap
import kotlin.math.abs
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue

class MediaClipTest {
    private val shape = RoundedCornerShape(12.dp)

    @AfterTest
    fun reset() {
        SoftwareDrawing.active = false
    }

    /** A 1-pixel checkerboard in a bordered box, clipped by [clip], on black: every pixel of it counts. */
    private fun render(clip: Modifier): IntArray {
        val scene =
            ImageComposeScene(200, 160, Density(1f)) {
                Box(
                    Modifier
                        .padding(10.dp)
                        .size(170.dp, 130.dp)
                        .then(clip)
                        .border(1.dp, Color.Gray, shape),
                ) {
                    Canvas(Modifier.size(170.dp, 130.dp)) {
                        for (y in 0 until 130) {
                            for (x in 0 until 170) {
                                if ((x + y) % 2 == 0) drawRect(Color(0xFFE0A030), Offset(x.toFloat(), y.toFloat()), Size(1f, 1f))
                            }
                        }
                    }
                }
            }
        val out = Bitmap.makeFromImage(scene.render())
        scene.close()
        return IntArray(200 * 160) { out.getColor(it % 200, it / 200) }
    }

    private fun assertMatches(
        expected: IntArray,
        actual: IntArray,
        maxDifferingPixels: Int,
    ) {
        var differing = 0
        var maxDiff = 0
        for (i in expected.indices) {
            if (expected[i] == actual[i]) continue
            differing++
            for (shift in 0 until 32 step 8) {
                maxDiff = maxOf(maxDiff, abs(((expected[i] shr shift) and 255) - ((actual[i] shr shift) and 255)))
            }
        }
        assertTrue(differing <= maxDifferingPixels && maxDiff <= 32, "$differing pixels differ, by up to $maxDiff")
    }

    @Test
    fun inSoftwareItClipsAsTheRoundedClipDoes() {
        val expected = render(Modifier.clip(shape))
        SoftwareDrawing.active = true
        // A few pixels where the corner squares meet the straight edges.
        assertMatches(expected, render(Modifier.mediaClip(shape)), maxDifferingPixels = 16)
    }

    @Test
    fun onAGpuItIsTheRoundedClip() {
        SoftwareDrawing.active = false
        assertMatches(render(Modifier.clip(shape)), render(Modifier.mediaClip(shape)), maxDifferingPixels = 0)
    }
}
