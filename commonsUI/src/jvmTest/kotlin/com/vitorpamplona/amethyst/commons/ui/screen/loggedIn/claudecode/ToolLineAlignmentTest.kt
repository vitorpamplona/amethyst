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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.claudecode

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.ui.theme.AmethystPreviewTheme
import org.jetbrains.skia.Bitmap
import kotlin.math.abs
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A tool line's icon and chevron sit level with the label's lowercase letters, where the eye
 * reads the line, not with its capitals. Measured on the drawn ink, at 4x.
 */
class ToolLineAlignmentTest {
    private var scene: ImageComposeScene? = null

    @AfterTest
    fun close() {
        scene?.close()
    }

    @Test
    fun theIconsAreLevelWithTheLabel() {
        val d = 4
        scene =
            ImageComposeScene(width = 200 * d, height = 30 * d, density = Density(d.toFloat())) {
                AmethystPreviewTheme(dark = false) {
                    Box(Modifier.width(200.dp).background(MaterialTheme.colorScheme.background)) {
                        // Only x-height letters, so the label's ink is its lowercase band.
                        ToolLine(MaterialSymbols.Code, "ooo", running = false, error = false, expanded = false, onClick = {})
                    }
                }
            }
        val bitmap = Bitmap.makeFromImage(scene!!.render())

        // Opaque, dark-enough pixels: ink.
        fun ink(
            x: Int,
            y: Int,
        ): Boolean {
            val c = bitmap.getColor(x, y)
            return (c ushr 24) > 200 && ((c shr 8) and 0xff) < 220
        }

        // The ink's middle row in a band of columns, in dp.
        fun middle(columns: IntRange): Float {
            val rows = (0 until bitmap.height).filter { y -> columns.any { x -> ink(x, y) } }
            return (rows.first() + rows.last()) / 2f / d
        }
        val lastInk = (0 until bitmap.width).last { x -> (0 until bitmap.height).any { y -> ink(x, y) } }
        val icon = middle(0 until 16 * d)
        val label = middle(26 * d until 34 * d)
        // The chevron is the rightmost ink: its glyph is under 12 dp wide.
        val chevron = middle(lastInk - 10 * d..lastInk)
        assertTrue(abs(icon - label) < 0.75f, "icon middle $icon dp, label middle $label dp")
        assertTrue(abs(chevron - label) < 0.75f, "chevron middle $chevron dp, label middle $label dp")
    }
}
