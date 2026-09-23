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

import com.vitorpamplona.quartz.utils.bigint.UBigInt

/**
 * Sweeping the box a hint names (`CYBERSPACE_V2.md` §7.7).
 *
 * > A seeker who trusts a hint sweeps the box: for every candidate region of
 * > height `h` inside it, derive the region key (§7.2), compute its
 * > `lookup_id`, and check the relay for a bag with that `d` tag (one batched
 * > query can carry many lookup ids).
 *
 * **The axes are swept independently up to the combine**, which is the whole
 * reason this is affordable. §4.7 builds `region_n` from three per-axis Cantor
 * roots, so a box needs one tree per distinct base *per axis* —
 * `2^(Hx-h) + 2^(Hy-h) + 2^(Hz-h)` of them — and then one pairing per candidate,
 * rather than a whole three-tree key each time. For an even box that is a cube
 * root of the candidates in tree work.
 *
 * **It is a cold [Sequence] on purpose.** The caller holds the budget, because
 * the budget is the only defence: a hint is a stranger's choice of difficulty,
 * and a bag can carry a gap of 30 precisely to burn a day of a reader's battery.
 * Nothing here starts work until something pulls, and a caller that stops
 * pulling has stopped paying. Price the hint with [CyberspaceHint.gapBits]
 * *before* the first pull.
 *
 * Nothing here talks to a relay. What comes out is `lookup_id`s and the keys
 * that made them; asking for the bags, in batches, is the caller's job.
 */
object RegionSweep {
    /**
     * Every candidate region in [hint]'s box, as the key material that finds it.
     *
     * Ordered so that the axis roots are built once each and reused across the
     * combines they take part in: X outermost, then Y, then Z. A caller pulling
     * the first item therefore pays for `2^(Hx-h) + 2^(Hy-h) + 2^(Hz-h)` trees
     * and one combine, and each item after that is one combine and two hashes.
     *
     * @param bagHeight the `h` of §8.6 — the height of the region the bag is
     *   keyed to, and the size of the candidates this enumerates.
     */
    fun of(
        hint: CyberspaceHint,
        bagHeight: Int,
        maxComputeHeight: Int = CantorTree.DEFAULT_MAX_COMPUTE_HEIGHT,
    ): Sequence<RegionKeyMaterial> {
        require(bagHeight >= 0) { "height must be >= 0" }
        require(bagHeight <= hint.heightX && bagHeight <= hint.heightY && bagHeight <= hint.heightZ) {
            "a box smaller than the region cannot contain it (§7.7)"
        }

        return sequence {
            val xs = axisRoots(hint.base.x, hint.heightX, bagHeight, maxComputeHeight)
            val ys = axisRoots(hint.base.y, hint.heightY, bagHeight, maxComputeHeight)
            val zs = axisRoots(hint.base.z, hint.heightZ, bagHeight, maxComputeHeight)

            for (x in xs) {
                for (y in ys) {
                    val xy = CantorTree.cantorPair(x, y)
                    for (z in zs) {
                        yield(RegionKey.derive(CantorTree.cantorPair(xy, z), bagHeight))
                    }
                }
            }
        }
    }

    /**
     * The Cantor root of every aligned subtree of [bagHeight] inside this axis's
     * span of the box.
     *
     * The bases step by `2^bagHeight` from the box's own base, which is where
     * §7.7's "both the region and the box are aligned" pays off: the candidates
     * on an axis are exactly the `2^(H - h)` aligned positions in it, with
     * nothing to search between them.
     */
    private fun axisRoots(
        base: CyberspaceAxis,
        boxHeight: Int,
        bagHeight: Int,
        maxComputeHeight: Int,
    ): List<UBigInt> {
        val steps = boxHeight - bagHeight
        require(steps <= MAX_AXIS_STEPS) { "an axis of 2^$steps candidates is past anything a client sweeps" }

        val count = 1L shl steps
        val out = ArrayList<UBigInt>(count.toInt())
        var position = base
        for (i in 0 until count) {
            out.add(CantorTree.subtreeRoot(position.toUBigInt(), bagHeight, maxComputeHeight))
            position = position.plusShifted(bagHeight)
        }
        return out
    }

    /**
     * How far a single axis may be swept before this refuses.
     *
     * Not a judgement about difficulty — §7.7 lets a hider set that — but about
     * memory: the roots of one axis are held while the other two are walked, and
     * a root at height 12 is forty kilobytes. A million of them is not a slow
     * search, it is a dead process, and the caller's own budget should have
     * stopped long before here.
     */
    const val MAX_AXIS_STEPS = 20
}

/** This axis with `2^height` added: the next aligned position along it. */
private fun CyberspaceAxis.plusShifted(height: Int): CyberspaceAxis {
    if (height >= Long.SIZE_BITS) {
        return CyberspaceAxis(high + (1L shl (height - Long.SIZE_BITS)), low)
    }
    val step = 1L shl height
    val sum = low + step
    // Unsigned overflow: the sum wrapped past 2^64, so carry into the high half.
    val carried = (low.toULong() > sum.toULong())
    return CyberspaceAxis(if (carried) high + 1 else high, sum)
}
