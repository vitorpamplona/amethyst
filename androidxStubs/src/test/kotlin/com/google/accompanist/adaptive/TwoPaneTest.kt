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
package com.google.accompanist.adaptive

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.jetbrains.skia.Bitmap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * TwoPane is a real layout here, not a stub, so it is worth checking it
 * actually splits: an inert version that drew only the first pane would look
 * like a chat list that never opens a conversation, with nothing in the log.
 */
class TwoPaneTest {
    private val red = 0xFFFF0000.toInt()
    private val blue = 0xFF0000FF.toInt()

    private fun render(
        width: Int,
        height: Int,
        content: @Composable () -> Unit,
    ): IntArray {
        val scene = ImageComposeScene(width, height, Density(1f)) { content() }
        try {
            val image = scene.render()
            val bitmap = Bitmap()
            bitmap.allocN32Pixels(width, height)
            check(image.readPixels(bitmap)) { "could not read rendered pixels" }
            return IntArray(width * height) { i -> bitmap.getColor(i % width, i / width) }
        } finally {
            scene.close()
        }
    }

    @Composable
    private fun Panes(
        strategy: TwoPaneStrategy,
        config: FoldAwareConfiguration = FoldAwareConfiguration.AllFolds,
    ) = TwoPane(
        first = { Box(Modifier.fillMaxSize().background(Color.Red)) },
        second = { Box(Modifier.fillMaxSize().background(Color.Blue)) },
        strategy = strategy,
        displayFeatures = emptyList(),
        foldAwareConfiguration = config,
        modifier = Modifier.fillMaxSize(),
    )

    @Test
    fun `a horizontal strategy splits the width at the fraction it was given`() {
        val pixels = render(400, 100) { Panes(HorizontalTwoPaneStrategy(splitFraction = 0.25f)) }
        val row = IntArray(400) { pixels[50 * 400 + it] }

        assertEquals(red, row[0])
        assertEquals(red, row[99])
        assertEquals(blue, row[100])
        assertEquals(blue, row[399])
        assertEquals(100, row.count { it == red })
        assertEquals(300, row.count { it == blue })
    }

    @Test
    fun `a vertical strategy splits the height instead`() {
        val pixels = render(100, 400) { Panes(VerticalTwoPaneStrategy(splitFraction = 0.75f)) }
        val column = IntArray(400) { pixels[it * 100 + 50] }

        assertEquals(red, column[299])
        assertEquals(blue, column[300])
        assertEquals(300, column.count { it == red })
        assertEquals(100, column.count { it == blue })
    }

    @Test
    fun `the gap comes out of the panes, not off the end`() {
        val pixels = render(400, 100) { Panes(HorizontalTwoPaneStrategy(splitFraction = 0.5f, gapWidth = 40.dp)) }
        val row = IntArray(400) { pixels[50 * 400 + it] }

        // 400 - 40 gap = 360 shared, so 180 each, with the gap between them.
        assertEquals(180, row.count { it == red })
        assertEquals(180, row.count { it == blue })
        assertEquals(red, row[179])
        assertTrue(row[200] != red && row[200] != blue, "the gap should show the background through")
        assertEquals(blue, row[220])
        assertEquals(blue, row[399])
    }

    @Test
    fun `both panes are laid out even at an extreme fraction`() {
        // The chat list must not vanish when the window is dragged narrow.
        val pixels = render(400, 100) { Panes(HorizontalTwoPaneStrategy(splitFraction = 0.02f)) }
        val row = IntArray(400) { pixels[50 * 400 + it] }
        assertEquals(8, row.count { it == red })
        assertEquals(392, row.count { it == blue })
    }

    @Test
    fun `fold awareness changes nothing, because a desktop display has no fold`() {
        val all = render(400, 100) { Panes(HorizontalTwoPaneStrategy(0.25f), FoldAwareConfiguration.AllFolds) }
        val vertical = render(400, 100) { Panes(HorizontalTwoPaneStrategy(0.25f), FoldAwareConfiguration.VerticalFoldsOnly) }
        assertTrue(all.contentEquals(vertical))
    }

    @Test
    fun `calculateDisplayFeatures reports none`() {
        // Not a placeholder: a monitor has no hinge, so the honest list is empty
        // and TwoPane's non-folding path is the correct one to take.
        val scene = ImageComposeScene(10, 10, Density(1f)) { assertTrue(calculateDisplayFeatures(null).isEmpty()) }
        try {
            scene.render()
        } finally {
            scene.close()
        }
    }
}
