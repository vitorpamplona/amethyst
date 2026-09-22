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
package com.vitorpamplona.quartz.cyberspace

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** §2.2's plane bit, and what counts as a coordinate at all. */
class CyberspaceCoordinateTest {
    private val zeros = "0".repeat(64)

    @Test
    fun thePlaneIsTheLeastSignificantBit() {
        // §2.2: "Bit 0 (LSB): plane bit P", and §2.4: 0 is dataspace, 1 is
        // ideaspace. The LSB of a hex string is the low bit of its last digit,
        // so every odd digit is ideaspace and every even one dataspace.
        assertEquals(CyberspacePlane.DATASPACE, CyberspaceCoordinate.planeOf(zeros))
        assertEquals(CyberspacePlane.IDEASPACE, CyberspaceCoordinate.planeOf(zeros.dropLast(1) + "1"))
        assertEquals(CyberspacePlane.DATASPACE, CyberspaceCoordinate.planeOf(zeros.dropLast(1) + "e"))
        assertEquals(CyberspacePlane.IDEASPACE, CyberspaceCoordinate.planeOf(zeros.dropLast(1) + "f"))
        // The other 255 bits are the axes and say nothing about the plane.
        assertEquals(CyberspacePlane.DATASPACE, CyberspaceCoordinate.planeOf("f".repeat(63) + "0"))
    }

    @Test
    fun anythingThatIsNotThirtyTwoBytesOfLowercaseHexIsNotACoordinate() {
        assertTrue(CyberspaceCoordinate.isWellFormed(zeros))
        assertFalse(CyberspaceCoordinate.isWellFormed(""))
        assertFalse(CyberspaceCoordinate.isWellFormed(zeros.dropLast(1)))
        assertFalse(CyberspaceCoordinate.isWellFormed(zeros + "0"))
        // §7.6 and §8 both say lowercase; an uppercase one is somebody else's
        // convention and is not silently accepted.
        assertFalse(CyberspaceCoordinate.isWellFormed(zeros.dropLast(1) + "A"))
        assertFalse(CyberspaceCoordinate.isWellFormed(zeros.dropLast(1) + "g"))
        assertNull(CyberspaceCoordinate.planeOf("not a coordinate"))
    }
}
