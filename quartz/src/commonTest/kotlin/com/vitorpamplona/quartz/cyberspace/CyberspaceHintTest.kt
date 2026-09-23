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
 * §7.7's hinted box, against the three golden vectors the section publishes.
 *
 * Those vectors are produced by `hint-reference.py` and they pin more than a
 * parser: each gives a point, a bag height and three hint heights, and states
 * the coordinate the aligned base must come out as and the sector tags the bag
 * must then carry. So building a hint from the point reproduces the published
 * tag byte for byte, or the alignment is wrong.
 */
class CyberspaceHintTest {
    /** §9.8's london vector, which the first two hint vectors are taken from. */
    private val london = "c492492492492492492492edf5bee7267451c787d95ba4d7840c76d1e33c9940"

    /** §7.7: "The ideaspace point is x = 2^84 + 12345, y = 3 * 2^80 + 777, z = 2^85 - 1 - 4242 on plane 1." */
    private val ideaspace = "a4b64924924924924924924924924924924924924924924924924d84b60d9c8f"

    private fun hintOf(
        coordinate: String,
        x: Int,
        y: Int,
        z: Int,
    ): CyberspaceHint {
        val point = CyberspaceCoordinate.decode(coordinate)
        assertNotNull(point)
        return CyberspaceHint(
            CyberspacePoint(point.x.alignedBase(x), point.y.alignedBase(y), point.z.alignedBase(z), point.plane),
            x,
            y,
            z,
        )
    }

    @Test
    fun londonAtElevenReproducesItsPublishedTag() {
        // `london_h5_box11`: bag at height 5, heights 11/11/11, 2^18 candidates.
        val hint = hintOf(london, 11, 11, 11)
        assertEquals(
            "c492492492492492492492edf5bee7267451c787d95ba4d7840c76d000000000",
            CyberspaceCoordinate.encode(hint.base),
        )
        assertEquals(18, hint.gapBits(5))
        assertEquals(1L shl 18, hint.candidates(5))
        assertFalse(hint.isDestination(5))

        assertEquals(
            listOf(
                listOf("X", "18014398541305938"),
                listOf("Y", "18014398549232983"),
                listOf("Z", "18014398509410999"),
                listOf("S", "18014398541305938-18014398549232983-18014398509410999"),
            ),
            hint.sectorTags().map { it.toList() },
        )
    }

    @Test
    fun londonWithAnExactAxisReproducesItsPublishedTag() {
        // `london_h5_x_exact`: heights 5/14/14. §7.7 calls this "a
        // two-dimensional hunt: X is exact, so the seeker sweeps a 2^9 by 2^9
        // slab of height-5 regions" — still 2^18, reached a different way.
        val hint = hintOf(london, 5, 14, 14)
        assertEquals(
            "c492492492492492492492edf5bee7267451c787d95ba4d7840c749041240000",
            CyberspaceCoordinate.encode(hint.base),
        )
        assertEquals(18, hint.gapBits(5))
        // Every axis is at or under the sector shift, so all four tags, and the
        // same ones as the cube: a sector is decided by bits above 30, which
        // neither height touches.
        assertEquals(4, hint.sectorTags().size)
        assertEquals("18014398541305938-18014398549232983-18014398509410999", hint.sectorTags()[3][1])
    }

    @Test
    fun anOpenAxisReproducesItsTagAndDropsItsSector() {
        // `ideaspace_h8_y_open`: bag at height 8, heights 12/40/12, 2^40
        // candidates — "far beyond any search; that hint tells the seeker where
        // to travel". Hy is above the sector shift, so no `Y` and no `S`.
        val hint = hintOf(ideaspace, 12, 40, 12)
        assertEquals(
            "a4b64924924924924924924924924924924924924924924924924d8000000001",
            CyberspaceCoordinate.encode(hint.base),
        )
        assertEquals(40, hint.gapBits(8))
        assertEquals(1L shl 40, hint.candidates(8))

        assertEquals(
            listOf(listOf("X", "18014398509481984"), listOf("Z", "36028797018963967")),
            hint.sectorTags().map { it.toList() },
        )
    }

    @Test
    fun aTagRoundTripsThroughTheReader() {
        for (hint in listOf(hintOf(london, 11, 11, 11), hintOf(london, 5, 14, 14), hintOf(ideaspace, 12, 40, 12))) {
            assertEquals(hint, CyberspaceHint.read(hint.toTag()))
            assertEquals(hint, CyberspaceHint.read(arrayOf(hint.toTag()), bagHeight = 5))
        }
    }

    @Test
    fun aHintAtTheBagsOwnHeightIsADestinationRatherThanASearch() {
        // §7.7: "Three heights equal to `h` name the region itself: the hint is
        // then a destination the seeker can compute or walk to directly, not a
        // search."
        val hint = hintOf(london, 5, 5, 5)
        assertTrue(hint.isDestination(5))
        assertEquals(0, hint.gapBits(5))
        assertEquals(1L, hint.candidates(5))
        assertEquals(3L, hint.axisTrees(5), "one tree per axis and no more")
    }

    @Test
    fun aSweepCostsTreesPerAxisAndCombinesPerCandidate() {
        // The decomposition of §4.7: the axes are independent up to the combine,
        // so an even box needs a cube root of its candidates in trees.
        val hint = hintOf(london, 11, 11, 11)
        assertEquals(1L shl 18, hint.candidates(5))
        assertEquals(3L * (1L shl 6), hint.axisTrees(5), "2^6 bases on each of three axes")
    }

    @Test
    fun aSweepBeyondCountingSaysSoRatherThanWrapping() {
        // A sector-only hint on a shallow bag is a gap of 75, "about 2^75
        // candidates, which no one will sweep". Nothing holds that, and a
        // silently wrapped count would be quoted to a reader as a small number.
        val hint = hintOf(london, 30, 30, 30)
        assertEquals(75, hint.gapBits(5))
        assertNull(hint.candidates(5))

        // The trees, though, are still countable, and that asymmetry is the
        // point of reporting them apart: 25 bits on each axis is a hundred
        // million Cantor trees against 2^75 combines. Both say the sweep is
        // hopeless; only one of them can say it with a number.
        assertEquals(3L * (1L shl 25), hint.axisTrees(5))

        // Past 40 bits on a single axis even the trees stop fitting anything
        // worth quoting, and that is where null starts.
        assertNull(hintOf(london, 50, 30, 30).axisTrees(5))
    }

    @Test
    fun containmentIsTwoChecksPerAxis() {
        val box = hintOf(london, 11, 11, 11)
        val point = CyberspaceCoordinate.decode(london)!!

        // The region the hint was built around, at the bag's height.
        assertTrue(box.contains(point.alignedBase(5), 5))
        // A region one step outside the box on X.
        val outside = CyberspacePoint(CyberspaceAxis(point.x.high, point.x.low + (1L shl 11)), point.y, point.z, point.plane)
        assertFalse(box.contains(outside.alignedBase(5), 5))
        // §7.7: the claim is about a plane as well as a place.
        val elsewhere = CyberspacePoint(point.x, point.y, point.z, CyberspacePlane.IDEASPACE)
        assertFalse(box.contains(elsewhere.alignedBase(5), 5))
        // "A box smaller than the region could not contain it."
        assertFalse(box.contains(point.alignedBase(12), 12))
    }

    @Test
    fun aBrokenHintIsAbsentRatherThanFatal() {
        // §7.7: "A `hint` tag that breaks any rule above MUST be treated as
        // absent... A bad hint never invalidates the bag."
        val good = hintOf(london, 11, 11, 11).toTag()

        assertNull(CyberspaceHint.read(arrayOf("hint", good[1], "11", "11")), "wrong arity")
        assertNull(CyberspaceHint.read(arrayOf("hint", "nonsense", "11", "11", "11")), "bad hex")
        assertNull(CyberspaceHint.read(arrayOf("hint", good[1].uppercase(), "11", "11", "11")), "not lowercase")
        assertNull(CyberspaceHint.read(arrayOf("hint", good[1], "86", "11", "11")), "height past the axis")
        assertNull(CyberspaceHint.read(arrayOf("hint", good[1], "-1", "11", "11")), "signed")
        assertNull(CyberspaceHint.read(arrayOf("hint", good[1], "011", "11", "11")), "leading zero")
        assertNull(CyberspaceHint.read(arrayOf("hint", good[1], "", "11", "11")), "empty")
        // The base is not aligned to the heights it claims.
        assertNull(CyberspaceHint.read(arrayOf("hint", london, "11", "11", "11")), "unaligned base")
        // "each hint height MUST therefore be at least `h`".
        assertNull(CyberspaceHint.read(arrayOf(good), bagHeight = 12), "box smaller than the region")
        assertNotNull(CyberspaceHint.read(arrayOf(good), bagHeight = 11), "exactly the region is allowed")

        // "A bag MUST carry at most one `hint` tag."
        assertNull(CyberspaceHint.read(arrayOf(good, good)), "two hints")
        assertNull(CyberspaceHint.read(arrayOf(arrayOf("d", "abc"))), "no hint at all")
    }

    @Test
    fun anAxisLeftFullyOpenHasABaseOfZero() {
        // §7.7: "A height of 85 leaves an axis open: the base is 0, the box
        // spans the whole axis, and the hint says nothing about that coordinate."
        val hint = hintOf(london, 85, 11, 11)
        assertEquals(0L, hint.base.x.high)
        assertEquals(0L, hint.base.x.low)
        assertEquals(hint, CyberspaceHint.read(hint.toTag()))
        // And an open axis is far past the sector shift, so it carries no tag.
        assertEquals(listOf("Y", "Z"), hint.sectorTags().map { it[0] })
    }
}
