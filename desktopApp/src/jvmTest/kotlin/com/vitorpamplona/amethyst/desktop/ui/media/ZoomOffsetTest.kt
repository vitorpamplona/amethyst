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
package com.vitorpamplona.amethyst.desktop.ui.media

import kotlin.test.Test
import kotlin.test.assertEquals

class ZoomOffsetTest {
    /** Where a point [fromCenter] from the image's center is drawn, as the graphics layer draws it. */
    private fun drawnAt(
        fromCenter: Float,
        scale: Float,
        offset: Float,
    ) = fromCenter * scale + offset

    /** The image point under a cursor [cursor] from the box's center. */
    private fun pointUnder(
        cursor: Float,
        scale: Float,
        offset: Float,
    ) = (cursor - offset) / scale

    @Test
    fun thePointUnderTheCursorStaysUnderIt() {
        var scale = 1f
        var offset = 0f
        // Scroll in three steps with the cursor 300 px right of the center, then two more at -120 px.
        for ((cursor, factor) in listOf(300f to 1.1f, 300f to 1.1f, 300f to 1.1f, -120f to 1.1f, -120f to 0.9f)) {
            val point = pointUnder(cursor, scale, offset)
            val newScale = scale * factor
            offset = offsetKeepingPoint(cursor, offset, scale, newScale)
            scale = newScale
            assertEquals(cursor, drawnAt(point, scale, offset), 0.001f)
        }
    }

    @Test
    fun zoomingAtTheCenterKeepsTheImageCentered() {
        assertEquals(0f, offsetKeepingPoint(0f, 0f, 1f, 2f))
    }
}
