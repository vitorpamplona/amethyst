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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.devices

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import com.vitorpamplona.emulator.SkinRotation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeviceScreenMappingTest {
    // A 1080 x 2400 phone shown as a 270 x 600 frame in a 400 x 600 panel: 65 px bars left and right.
    private val box = IntSize(400, 600)
    private val display = 1080 to 2400

    @Test
    fun theCornersMapToTheDisplaysCorners() {
        assertEquals(0 to 0, toDevice(Offset(65f, 0f), box, 270, 600, display))
        assertEquals(1079 to 2399, toDevice(Offset(335f, 600f), box, 270, 600, display))
    }

    @Test
    fun theMiddleIsTheMiddle() = assertEquals(539 to 1199, toDevice(Offset(200f, 300f), box, 270, 600, display))

    @Test
    fun theBarsBesideThePictureAreNotTheScreen() {
        assertNull(toDevice(Offset(20f, 300f), box, 270, 600, display))
        assertNull(toDevice(Offset(390f, 300f), box, 270, 600, display))
    }

    /**
     * What emulator 37 did with a 480 x 800 device, turned each way, shown in an 800 x 480 (or
     * 480 x 800) frame drawn 1:1: the upright point that showed under each frame point.
     */
    private val device = 480 to 800

    private fun on(
        rotation: SkinRotation,
        x: Float,
        y: Float,
    ): Pair<Int, Int>? {
        val turned = rotation == SkinRotation.LANDSCAPE || rotation == SkinRotation.REVERSE_LANDSCAPE
        val (fw, fh) = if (turned) 800 to 480 else 480 to 800
        return toDevice(Offset(x, y), IntSize(fw, fh), fw, fh, device, rotation)
    }

    @Test
    fun aTurnedDeviceTakesItsTouchesUpright() {
        // Pressing upright (100, 100) showed at these frame points.
        assertEquals(100 to 100, on(SkinRotation.PORTRAIT, 100f, 100f)?.near())
        assertEquals(100 to 100, on(SkinRotation.LANDSCAPE, 100f, 380f)?.near())
        assertEquals(100 to 100, on(SkinRotation.REVERSE_LANDSCAPE, 700f, 100f)?.near())
        assertEquals(100 to 100, on(SkinRotation.REVERSE_PORTRAIT, 380f, 700f)?.near())
    }

    /** Within a pixel: the frame's edges map onto the display's last pixel, not one past it. */
    private fun Pair<Int, Int>.near() = ((first + 1) / 5 * 5) to ((second + 1) / 5 * 5)

    @Test
    fun noDisplaySizeYetUsesTheFrame() = assertEquals(269 to 599, toDevice(Offset(335f, 600f), box, 270, 600, null))

    @Test
    fun aNotchSwipesUpWithinThePicture() {
        // The 270 x 600 picture in the 400 x 600 panel: a notch is 72 px of it.
        assertEquals(Offset(200f, 228f), swipeEnd(Offset(200f, 300f), box, 270, 600, 1))
        assertEquals(Offset(200f, 372f), swipeEnd(Offset(200f, 300f), box, 270, 600, -1))
        // Never past the picture's edge.
        assertEquals(Offset(200f, 0f), swipeEnd(Offset(200f, 30f), box, 270, 600, 1))
    }
}
