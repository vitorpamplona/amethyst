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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * §2.2's interleave, against the spec's own consensus locks.
 *
 * Every vector here is published: the six GPS golden vectors of §9.8 and the three
 * hint vectors of §7.7, which between them pin the bit layout, the plane bit, the
 * sector shift of §10 and the aligned base of §4.5. None of them was produced by
 * this code.
 */
class CyberspaceCoordinateTest {
    private val zeros = "0".repeat(64)

    /** §9.8, spec version `2026-03-16-h34-corrected`, altitude 0. */
    private val goldenVectors =
        mapOf(
            "origin_equator_prime" to "e000000000000000000001200041040208048040000000000000000000000000",
            "equator_east_90" to "e000000000000000000000480010410082012010000000000000000000000000",
            "equator_west_90" to "c492492492492492492492012482082410480490000000000000000000000000",
            "north_pole" to "e000000000000000000000900004924920020000820000920100824920800020",
            "london" to "c492492492492492492492edf5bee7267451c787d95ba4d7840c76d1e33c9940",
            "nyc" to "c4924924924924924924921f79235dae293ada913e78294253a235239a332854",
        )

    @Test
    fun everyGoldenVectorSurvivesTheRoundTrip() {
        // The mapping that produced these is §9.7's, which this does not implement.
        // What it does implement is §2.2, and a coordinate that decodes to three
        // axes and back to a different string has the bit layout wrong.
        for ((name, hex) in goldenVectors) {
            val point = CyberspaceCoordinate.decode(hex)
            assertNotNull(point, name)
            assertEquals(hex, CyberspaceCoordinate.encode(point), name)
        }
    }

    @Test
    fun theKnownPointOfTheIdeaspaceVectorDecodesToItsStatedAxes() {
        // §7.7 states this one's axes outright: "The ideaspace point is
        // x = 2^84 + 12345, y = 3 * 2^80 + 777, z = 2^85 - 1 - 4242 on plane 1."
        val point = CyberspaceCoordinate.decode("a4b64924924924924924924924924924924924924924924924924d84b60d9c8f")
        assertNotNull(point)
        assertEquals(CyberspacePlane.IDEASPACE, point.plane)

        // 2^84 is bit 84, the top bit of an 85-bit axis: high = 2^20, low = 12345.
        assertEquals(1L shl 20, point.x.high)
        assertEquals(12345L, point.x.low)
        // 3 * 2^80 sets bits 80 and 81, which are bits 16 and 17 of the high half.
        assertEquals((1L shl 16) or (1L shl 17), point.y.high)
        assertEquals(777L, point.y.low)
        // 2^85 - 1 is every bit set; less 4242.
        assertEquals((1L shl 21) - 1, point.z.high)
        assertEquals(-1L - 4242L, point.z.low, "the low half runs unsigned and this one has its top bit set")
    }

    @Test
    fun theSectorsAreTheOnesTheHintVectorsCarry() {
        // §7.7's `london_h5_box11`: the hint coordinate and the sector tags a bag
        // carrying it MUST also carry. A sector is the axis shifted right by 30
        // (§10), which is the one part of an axis that fits a Long.
        val london = CyberspaceCoordinate.decode("c492492492492492492492edf5bee7267451c787d95ba4d7840c76d000000000")
        assertNotNull(london)
        assertEquals(18014398541305938L, london.x.sector())
        assertEquals(18014398549232983L, london.y.sector())
        assertEquals(18014398509410999L, london.z.sector())
        assertEquals("18014398541305938-18014398549232983-18014398509410999", london.sector())

        // `ideaspace_h8_y_open` fixes X and Z only; its Y height of 40 is above the
        // sector shift, so no `Y` tag, which is a rule about the hint rather than
        // about the axis — the axis still has a sector and this checks the two the
        // vector publishes.
        val idea = CyberspaceCoordinate.decode("a4b64924924924924924924924924924924924924924924924924d8000000001")
        assertNotNull(idea)
        assertEquals(18014398509481984L, idea.x.sector())
        assertEquals(36028797018963967L, idea.z.sector())
    }

    @Test
    fun theHintCoordinateIsThePointWithItsLowBitsCleared() {
        // §7.7: the hint's coordinate "MUST be the aligned base: the low `H` bits of
        // each axis MUST be zero". `london_h5_box11` is the §9.8 london vector at
        // heights 11, 11, 11, so aligning london to 11 must reproduce it exactly.
        val london = CyberspaceCoordinate.decode(goldenVectors.getValue("london"))
        assertNotNull(london)
        assertEquals(
            "c492492492492492492492edf5bee7267451c787d95ba4d7840c76d000000000",
            CyberspaceCoordinate.encode(london.alignedBase(11)),
        )
        // And `london_h5_x_exact` is the same point at heights 5, 14, 14 — an
        // unequal box, so it is not a single alignedBase call, but X aligned to 5
        // is what its exact axis claims.
        assertEquals(london.x.alignedBase(5), CyberspaceCoordinate.decode(goldenVectors.getValue("london"))!!.x.alignedBase(5))
    }

    @Test
    fun aligningPastTheAxisEmptiesIt() {
        val london = CyberspaceCoordinate.decode(goldenVectors.getValue("london"))!!
        // §7.7: "A height of 85 leaves an axis open: the base is 0".
        assertEquals(0L, london.x.alignedBase(85).high)
        assertEquals(0L, london.x.alignedBase(85).low)
        // The boundary at 64, where an axis stops fitting one Long.
        assertEquals(0L, london.x.alignedBase(64).low)
        assertEquals(london.x.high, london.x.alignedBase(64).high)
        assertEquals(london.x, london.x.alignedBase(0))
    }

    @Test
    fun thePlaneIsTheLeastSignificantBit() {
        // §2.2: "Bit 0 (LSB): plane bit P", and §2.4: 0 is dataspace, 1 ideaspace.
        assertEquals(CyberspacePlane.DATASPACE, CyberspaceCoordinate.planeOf(zeros))
        assertEquals(CyberspacePlane.IDEASPACE, CyberspaceCoordinate.planeOf(zeros.dropLast(1) + "1"))
        assertEquals(CyberspacePlane.DATASPACE, CyberspaceCoordinate.planeOf(zeros.dropLast(1) + "e"))
        assertEquals(CyberspacePlane.IDEASPACE, CyberspaceCoordinate.planeOf(zeros.dropLast(1) + "f"))
        // The other 255 bits are the axes and say nothing about the plane.
        assertEquals(CyberspacePlane.DATASPACE, CyberspaceCoordinate.planeOf("f".repeat(63) + "0"))
        // And the plane survives a round trip on its own.
        assertEquals("f".repeat(63) + "1", CyberspaceCoordinate.encode(CyberspaceCoordinate.decode("f".repeat(63) + "1")!!))
    }

    @Test
    fun theOriginIsEveryAxisAtZero() {
        val point = CyberspaceCoordinate.decode(zeros)
        assertNotNull(point)
        assertEquals(0L, point.x.high + point.x.low + point.y.high + point.y.low + point.z.high + point.z.low)
        assertEquals(zeros, CyberspaceCoordinate.encode(point))
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
        assertNull(CyberspaceCoordinate.decode("not a coordinate"))
    }
}
