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

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp
import kotlin.math.max

/**
 * The Amethyst gem as one flat colour, for the macOS menu bar, whose icons are monochrome and follow
 * the bar's light or dark appearance (the tray library draws it black or white to match). The same
 * paths as Android's themed launcher icon (`amethyst/src/main/res/drawable/amethyst_monochrome.xml`),
 * cropped to the gem: that drawable pads it into the adaptive icon's safe zone, which would leave a
 * speck in a 22-point menu bar.
 */
object TrayGlyph {
    // The dot above the gem, then the gem; the viewport they were drawn in is 512 x 512.
    private val PATHS =
        listOf(
            "M249.48,212.29a7.57,7.7 90,1 0,15.41 0a7.57,7.7 90,1 0,-15.41 0z",
            "M370.18,326.86C369.91,326.04 307.3,176.97 290.09,135.63c-20.96,0 -65.23,0 -96.46,0 -9.17,22.67 -32.82,80.44 -51.8,126.98 10.4,24.81 19.74,47.1 26.95,64.29l39.28,0a2.68,2.68 135,0 0,2.51 -3.15c-0.34,-7.93 -21.93,-36.76 -15.49,-67.94 1.72,-6.71 3.03,-13.53 4.91,-20.21a235.88,235.88 135,0 1,18.04 -46.7c1.19,-2.27 1.74,-3.29 4.5,-1.57 5.86,3.65 12.41,3.44 18.94,2.97 5.62,-1.22 8.36,-3.92 18.67,-1.18 2.04,0 4.03,0 6.11,0.19 4.95,0.39 9.6,1.57 12.57,6.05 3.25,4.9 3.77,10.44 3.25,16.1 -0.79,8.47 -0.23,16.36 6.94,22.33a77.19,77.19 0,0 0,8.05 5.37c3.56,2.28 7.85,3.27 10.9,6.44 1.8,1.87 2.86,3.85 2.08,6.51 -0.78,2.66 -2.86,3.91 -5.6,4.2 -6.92,0.75 -13.69,-0.66 -20.49,-1.48 -0.89,-0.11 -1.76,-0.17 -2.68,-0.23a15.39,15.39 0,0 1,-5.11 0.17l-1.6,0a35.83,35.83 0,0 0,-7.92 1.89c-6.41,2.34 -12.25,4.91 -19.82,4.78a5.8,5.8 0,0 0,-2.75 1.19c-5.93,5.27 -9.5,11.93 -6.54,21.66 8.13,22.72 29,77.53 37.96,101.18 -0.25,-16.02 -0.32,-44.71 -0.38,-58.64 35.8,0.05 90.7,0.03 95.08,0.03z",
        )

    /** Room left around the gem, as a share of its larger side. */
    private const val MARGIN = 0.06f

    val vector: ImageVector by lazy {
        val nodes = PATHS.map { PathParser().parsePathString(it).toNodes() }
        val bounds =
            nodes
                .map { PathParser().addPathNodes(it).toPath().getBounds() }
                .reduce { a, b -> Rect(minOf(a.left, b.left), minOf(a.top, b.top), maxOf(a.right, b.right), maxOf(a.bottom, b.bottom)) }
        // A square around the gem's bounds, so it keeps its shape and sits in the middle.
        val side = max(bounds.width, bounds.height) * (1 + 2 * MARGIN)
        val left = bounds.center.x - side / 2
        val top = bounds.center.y - side / 2
        ImageVector
            .Builder(name = "AmethystTray", defaultWidth = 22.dp, defaultHeight = 22.dp, viewportWidth = side, viewportHeight = side)
            .addGroup(translationX = -left, translationY = -top)
            .apply { nodes.forEach { addPath(it, fill = SolidColor(Color.Black)) } }
            .clearGroup()
            .build()
    }
}
