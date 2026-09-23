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

/**
 * `CYBERSPACE_V2.md` §7.7 — the hider's clue as to where a bag is.
 *
 * A bag reveals nothing about its own location: its `d` tag is a hash of a
 * hash (§7.2), and §7.7 is blunt about what that leaves. "Without more
 * information, a bag is found only by intentionally deep scanning and/or
 * wandering. With no additional information, any given bag is equally likely to
 * be at any point in the full 2^256 coordinate space: an impossibly hardened
 * secret."
 *
 * A hint is the way out, and it is **a search, not a direction**. The hider
 * publishes an aligned box, as coarse as they like; a seeker derives the region
 * key of every candidate region inside it and asks a relay for each `lookup_id`.
 * What makes that possible from anywhere is the sentence that governs this
 * whole feature:
 *
 * > The seeker's own position never enters this cost, because §7.1 makes
 * > looking and walking equivalent: a region key can be computed for any
 * > coordinate without traveling there.
 *
 * So the hint is also the **difficulty knob**. The price is
 * `2^((Hx - h) + (Hy - h) + (Hz - h))` candidates — [gapBits] — and the hider
 * sets it by how far above the bag's own height they pitch each axis. At the
 * bottom of the range, three heights equal to `h` "name the region itself: the
 * hint is then a destination the seeker can compute or walk to directly, not a
 * search" ([isDestination]). At the top, a sector-only hint on a shallow bag is
 * a gap of 75, which no one will ever sweep — that hint says where to travel,
 * not where to search.
 *
 * A box need not be a cube. Equal heights make one; unequal ones make a slab or
 * a column, and an axis at exactly the bag's height with two coarse ones turns
 * the hunt into two dimensions.
 */
class CyberspaceHint(
    /** The box's aligned base, and the plane it lies in. */
    val base: CyberspacePoint,
    val heightX: Int,
    val heightY: Int,
    val heightZ: Int,
) {
    /**
     * The exponent of the candidate count: how many regions of the bag's height
     * fit in this box, as a power of two.
     *
     * An exponent rather than a number because it runs to 255 — three axes left
     * fully open — and nothing holds `2^255`. §7.7's own table is read in these
     * terms: 12 is seconds, 24 is hours, 30 or more is "days to never".
     */
    fun gapBits(bagHeight: Int): Int = (heightX - bagHeight) + (heightY - bagHeight) + (heightZ - bagHeight)

    /** The candidate count itself, when it fits, and null when the sweep is beyond counting. */
    fun candidates(bagHeight: Int): Long? {
        val bits = gapBits(bagHeight)
        return if (bits in 0..62) 1L shl bits else null
    }

    /**
     * How many distinct Cantor trees a sweep of this box has to build.
     *
     * Not the candidate count: §4.7 combines three independent per-axis roots,
     * so a box needs one tree per distinct base *per axis* and then one pairing
     * per candidate. That is `2^(Hx-h) + 2^(Hy-h) + 2^(Hz-h)` trees against
     * `2^gap` combines — for an even box, a cube root of the candidates. The
     * trees are the cheap half above about height 8, where a combine costs
     * roughly ten times a root, so a budget that prices a sweep by its trees
     * will under-quote badly.
     */
    fun axisTrees(bagHeight: Int): Long? {
        var total = 0L
        for (height in intArrayOf(heightX, heightY, heightZ)) {
            val bits = height - bagHeight
            if (bits < 0 || bits > 40) return null
            total += 1L shl bits
        }
        return total
    }

    /** §7.7: three heights equal to the bag's name the region itself, so there is nothing to search. */
    fun isDestination(bagHeight: Int): Boolean = gapBits(bagHeight) == 0

    /**
     * Whether a region of [bagHeight] based at [regionBase] lies inside this box.
     *
     * §7.7 makes it two checks per axis, "because both the region and the box
     * are aligned": the hint height is at least the bag's, and the two bases
     * agree once both are shifted right by the hint height.
     */
    fun contains(
        regionBase: CyberspacePoint,
        bagHeight: Int,
    ): Boolean {
        if (regionBase.plane != base.plane) return false
        if (bagHeight > heightX || bagHeight > heightY || bagHeight > heightZ) return false
        return regionBase.x.alignedBase(heightX) == base.x.alignedBase(heightX) &&
            regionBase.y.alignedBase(heightY) == base.y.alignedBase(heightY) &&
            regionBase.z.alignedBase(heightZ) == base.z.alignedBase(heightZ)
    }

    /**
     * The sector tags §10 requires a bag carrying this hint to publish.
     *
     * "It MUST carry the sector tag of each axis whose hint height is at most
     * 30, computed from the box's base, and `S` when all three are fixed." An
     * axis coarser than a sector has no determined sector and gets no tag —
     * which is why the third golden vector, with `Hy = 40`, carries `X` and `Z`
     * and neither `Y` nor `S`.
     */
    fun sectorTags(): List<Array<String>> {
        val out = mutableListOf<Array<String>>()
        if (heightX <= CyberspaceCoordinate.SECTOR_SHIFT) out.add(arrayOf("X", base.x.sector().toString()))
        if (heightY <= CyberspaceCoordinate.SECTOR_SHIFT) out.add(arrayOf("Y", base.y.sector().toString()))
        if (heightZ <= CyberspaceCoordinate.SECTOR_SHIFT) out.add(arrayOf("Z", base.z.sector().toString()))
        if (out.size == 3) out.add(arrayOf("S", base.sector()))
        return out
    }

    /** The `hint` tag this box writes as. */
    fun toTag(): Array<String> = arrayOf(TAG, CyberspaceCoordinate.encode(base), heightX.toString(), heightY.toString(), heightZ.toString())

    override fun equals(other: Any?): Boolean =
        other is CyberspaceHint &&
            other.base == base &&
            other.heightX == heightX &&
            other.heightY == heightY &&
            other.heightZ == heightZ

    override fun hashCode(): Int = ((base.hashCode() * 31 + heightX) * 31 + heightY) * 31 + heightZ

    companion object {
        const val TAG = "hint"

        /** A hint height names an axis, so it runs to the axis's own width (§7.7). */
        const val MAX_HEIGHT = CyberspaceCoordinate.AXIS_BITS

        /**
         * The hint a bag carries, or null when it carries none — **or carries a
         * broken one**.
         *
         * §7.7 is explicit that those are the same answer: "A `hint` tag that
         * breaks any rule above MUST be treated as absent, meaning the bag is
         * read as if it carried no hint... A bad hint never invalidates the bag,
         * because hints are advisory metadata about where to look; whether a bag
         * is valid is decided by §7.2 and §7.6 alone."
         *
         * So every check below returns null rather than throwing, and a caller
         * that gets null sweeps nothing and still reads the bag.
         *
         * @param bagHeight the `h` tag of §8.6, when the bag carries one. Its
         *   only job here is the last rule: "When the bag carries an `h` tag,
         *   each hint height MUST therefore be at least `h`; a box smaller than
         *   the region could not contain it."
         */
        fun read(
            tags: Array<Array<String>>,
            bagHeight: Int? = null,
        ): CyberspaceHint? {
            val hints = tags.filter { it.isNotEmpty() && it[0] == TAG }
            // "A bag MUST carry at most one `hint` tag." Two is a bag whose
            // author cannot be read literally, and §7.7's own remedy for a rule
            // broken is to read the bag as if it had no hint at all.
            if (hints.size != 1) return null
            return read(hints[0], bagHeight)
        }

        /** One `hint` tag, or null when it breaks any rule of §7.7. */
        fun read(
            tag: Array<String>,
            bagHeight: Int? = null,
        ): CyberspaceHint? {
            if (tag.size != 5 || tag[0] != TAG) return null

            val heightX = canonicalHeight(tag[2]) ?: return null
            val heightY = canonicalHeight(tag[3]) ?: return null
            val heightZ = canonicalHeight(tag[4]) ?: return null

            if (bagHeight != null && (heightX < bagHeight || heightY < bagHeight || heightZ < bagHeight)) return null

            val point = CyberspaceCoordinate.decode(tag[1]) ?: return null
            // "It MUST be the aligned base: the low `H` bits of each axis MUST
            // be zero, and an axis with `H = 85` MUST be `0`." Requiring the
            // base is what lets two hiders who hint the same box publish the
            // same tag, so readers can compare hints by equality.
            if (point.x.alignedBase(heightX) != point.x) return null
            if (point.y.alignedBase(heightY) != point.y) return null
            if (point.z.alignedBase(heightZ) != point.z) return null

            return CyberspaceHint(point, heightX, heightY, heightZ)
        }

        /**
         * A height as §10 writes one: base ten, no sign, no leading zeros
         * except `"0"` itself, and inside `[0, 85]`.
         *
         * The canonical form matters for the same reason the aligned base does:
         * `"05"` and `"5"` are the same number and two different tags, and a
         * reader that accepts both lets one box have two spellings.
         */
        private fun canonicalHeight(text: String): Int? {
            if (text.isEmpty()) return null
            if (text.length > 1 && text[0] == '0') return null
            for (c in text) if (c !in '0'..'9') return null
            val value = text.toIntOrNull() ?: return null
            return if (value in 0..MAX_HEIGHT) value else null
        }
    }
}
