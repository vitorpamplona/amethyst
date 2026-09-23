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
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * §7.7's sweep: the thing a seeker actually does with a hint.
 *
 * The test that matters is the round trip — hide a region, hint a box around it,
 * sweep the box, and find the region's own `lookup_id` among the candidates.
 * Everything else about this feature is preparation for that one answer.
 */
class RegionSweepTest {
    private val london = CyberspaceCoordinate.decode("c492492492492492492492edf5bee7267451c787d95ba4d7840c76d1e33c9940")!!

    private fun boxAround(
        point: CyberspacePoint,
        height: Int,
    ) = CyberspaceHint(
        CyberspacePoint(point.x.alignedBase(height), point.y.alignedBase(height), point.z.alignedBase(height), point.plane),
        height,
        height,
        height,
    )

    @Test
    fun aSweepFindsTheRegionItsBoxWasDrawnAround() {
        // A bag hidden at height 4, hinted with a box one height up: 2^3 = 8
        // candidates, one of which is the region itself.
        val bagHeight = 4
        val hint = boxAround(london, 5)
        assertEquals(3, hint.gapBits(bagHeight))

        val wanted = RegionKey.at(london, bagHeight).lookupId
        val swept = RegionSweep.of(hint, bagHeight).toList()

        assertEquals(8, swept.size, "2^((5-4) * 3) candidates")
        assertTrue(swept.any { it.lookupId == wanted }, "the region the box was drawn around is in the box")
        assertEquals(swept.size, swept.map { it.lookupId }.toSet().size, "every candidate is its own region")
        assertTrue(swept.all { it.height == bagHeight })
    }

    @Test
    fun aDestinationHintSweepsExactlyTheRegionItNames() {
        // §7.7: "Three heights equal to `h` name the region itself: the hint is
        // then a destination the seeker can compute or walk to directly, not a
        // search."
        val bagHeight = 6
        val hint = boxAround(london, bagHeight)
        assertTrue(hint.isDestination(bagHeight))

        val swept = RegionSweep.of(hint, bagHeight).toList()
        assertEquals(1, swept.size)
        assertEquals(RegionKey.at(london, bagHeight).lookupId, swept[0].lookupId)
    }

    @Test
    fun anUnevenBoxSweepsTheProductOfItsAxes() {
        // A slab: X pinned exactly, two coarse axes. §7.7 calls this "a
        // two-dimensional hunt".
        val bagHeight = 3
        val hint =
            CyberspaceHint(
                CyberspacePoint(london.x.alignedBase(3), london.y.alignedBase(5), london.z.alignedBase(5), london.plane),
                3,
                5,
                5,
            )
        assertEquals(4, hint.gapBits(bagHeight))

        val swept = RegionSweep.of(hint, bagHeight).toList()
        assertEquals(16, swept.size, "1 * 4 * 4")
        assertTrue(swept.any { it.lookupId == RegionKey.at(london, bagHeight).lookupId })
    }

    @Test
    fun nothingHappensUntilSomethingPulls() {
        // The budget is the only defence against a hint a stranger chose, so a
        // sweep has to be cold: building the sequence must not build a tree.
        val hint = boxAround(london, 12)
        val sequence = RegionSweep.of(hint, 4)
        // 2^24 candidates. Constructing this is free; taking one is not, and
        // taking all of them is what the caller's budget is for.
        assertEquals(24, hint.gapBits(4))
        assertNotNull(sequence)
    }

    @Test
    fun aBoxSmallerThanTheRegionIsRefused() {
        // §7.7: "a box smaller than the region could not contain it".
        assertFailsWith<IllegalArgumentException> { RegionSweep.of(boxAround(london, 4), 8).first() }
    }

    @Test
    fun anAxisPastWhatFitsInMemoryIsRefusedRatherThanAttempted() {
        // Not a judgement about difficulty — the hider sets that — but about
        // the roots of one axis being held while the other two are walked.
        val hint = boxAround(london, RegionSweep.MAX_AXIS_STEPS + 5)
        assertFailsWith<IllegalArgumentException> { RegionSweep.of(hint, 0).first() }
    }

    @Test
    fun steppingAcrossTheAxisSplitStaysOnTheLattice() {
        // An axis is two Longs, so a box whose candidates cross the 64-bit
        // boundary is where a carry would go missing — and a wrong base is a
        // key that opens nothing, silently.
        val low = CyberspaceAxis(0L, -1L - 3L) // four steps below 2^64
        val point = CyberspacePoint(low, low, low, CyberspacePlane.DATASPACE)
        val hint = CyberspaceHint(CyberspacePoint(low.alignedBase(2), low.alignedBase(2), low.alignedBase(2), point.plane), 2, 2, 2)

        val swept = RegionSweep.of(hint, 0).toList()
        assertEquals(64, swept.size, "4 * 4 * 4")
        assertEquals(swept.size, swept.map { it.lookupId }.toSet().size, "no two candidates collided across the split")
    }
}
