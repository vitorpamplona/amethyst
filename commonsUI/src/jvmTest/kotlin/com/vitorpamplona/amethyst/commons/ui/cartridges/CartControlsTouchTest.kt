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
package com.vitorpamplona.amethyst.commons.ui.cartridges

import androidx.compose.foundation.layout.size
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.cartridges.CartPlayerViewModel.Buttons
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The on-screen controls read a finger that slides without lifting: across the pad's arms and
 * diagonals, off its edge, and from O to X through the strip that presses both. Density 1, so
 * dp == px.
 */
class CartControlsTouchTest {
    private lateinit var scene: ImageComposeScene
    private val seen = ArrayList<Int>()
    private var timeMs = 0L

    @AfterTest
    fun close() {
        if (::scene.isInitialized) scene.close()
    }

    private fun frame() {
        timeMs += 16
        scene.render(timeMs * 1_000_000)
    }

    private fun touch(
        type: PointerEventType,
        x: Float,
        y: Float,
    ) {
        scene.sendPointerEvent(type, Offset(x, y), timeMillis = timeMs, type = PointerType.Touch)
        frame()
    }

    /** One finger down at the first point, sliding through the rest, then lifted. */
    private fun slide(vararg points: Pair<Float, Float>) {
        touch(PointerEventType.Press, points[0].first, points[0].second)
        for ((x, y) in points.drop(1)) touch(PointerEventType.Move, x, y)
        touch(PointerEventType.Release, points.last().first, points.last().second)
    }

    @Test
    fun aThumbSlidesAcrossThePadWithoutLifting() {
        scene = ImageComposeScene(width = 300, height = 300, density = Density(1f)) { DirectionPad(onChange = { seen.add(it) }, modifier = Modifier.size(180.dp)) }
        frame()
        // Left arm, the up-left corner, the up arm, then past the pad's top edge.
        slide(15f to 90f, 15f to 15f, 90f to 15f, 90f to -60f)
        assertEquals(listOf(Buttons.LEFT, Buttons.LEFT or Buttons.UP, Buttons.UP, 0), seen)
    }

    @Test
    fun aThumbRollsFromOToXThroughBoth() {
        scene = ImageComposeScene(width = 300, height = 300, density = Density(1f)) { ActionButtons(size = 200.dp, onChange = { seen.add(it) }) }
        frame()
        // O sits low left, X high right; halfway between them presses both.
        slide(45f to 155f, 100f to 100f, 155f to 45f)
        assertEquals(listOf(Buttons.O, Buttons.O or Buttons.X, Buttons.X, 0), seen)
    }
}
