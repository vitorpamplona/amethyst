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

    @Test
    fun aTurnedDeviceSwapsTheDisplay() {
        // Landscape: a 400 x 180 frame centred in the panel, 210 px above and below.
        assertEquals(2399 to 1079, toDevice(Offset(400f, 390f), box, 400, 180, display))
    }

    @Test
    fun noDisplaySizeYetUsesTheFrame() = assertEquals(269 to 599, toDevice(Offset(335f, 600f), box, 270, 600, null))
}
