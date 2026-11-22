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
package com.vitorpamplona.amethyst.desktop.platform

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.unit.Density
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TrayGlyphTest {
    @Test
    fun theGemFillsASquareViewport() {
        val vector = TrayGlyph.vector
        assertEquals(vector.viewportWidth, vector.viewportHeight)
        // Cropped to the gem: far tighter than the drawable's padded 512 x 512.
        assertTrue(vector.viewportWidth in 100f..300f, "viewport ${vector.viewportWidth}")
    }

    @Test
    fun itRendersAsAMenuBarIcon() {
        // As the tray library draws it for a light menu bar: black, at macOS's 44 px (22 pt @2x).
        val scene =
            ImageComposeScene(44, 44, Density(1f)) {
                Image(TrayGlyph.vector, null, Modifier.fillMaxSize(), colorFilter = ColorFilter.tint(Color.Black))
            }
        val image = scene.render()
        scene.close()
        val pixels = image.peekPixels()!!
        var inked = 0
        for (y in 0 until 44) for (x in 0 until 44) if (pixels.getColor(x, y) ushr 24 != 0) inked++
        // Neither empty nor a filled square.
        assertTrue(inked in 200..1500, "inked pixels: $inked")
        File("build/tray-glyph.png").writeBytes(image.encodeToData(EncodedImageFormat.PNG)!!.bytes)
    }
}
