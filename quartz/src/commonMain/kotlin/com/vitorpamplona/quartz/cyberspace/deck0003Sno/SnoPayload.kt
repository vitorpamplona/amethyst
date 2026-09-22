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
package com.vitorpamplona.quartz.cyberspace.deck0003Sno

import androidx.compose.runtime.Immutable

/**
 * One Simple Nostr Object: a small triangle mesh on an integer lattice
 * (DECK-0003 §1). A single connected budget of vertices, colours and faces;
 * not a scene and not a hierarchy.
 *
 * Positions are exact. [positions] holds **total ticks** — whole units times
 * [TICKS_PER_UNIT] plus the sub-unit remainder — as a single integer per axis,
 * which is the same exact value the wire's `vertices`/`ticks` pair carries and
 * is what §1.2 requires welding, deduplication, equality, sorting and hashing
 * to run on. One 120th is not representable in binary floating point, so a
 * reader that compares derived floats has given away the one guarantee the
 * lattice makes. Convert to a float at the rasteriser and nowhere earlier.
 *
 * Colours arrive resolved: [colors] and [faceColors] are opaque ARGB, already
 * looked up through whichever palette [paletteRef] named. A palette reference
 * that has not been fetched resolves against the built-in, so the payload is
 * always drawable; when the referenced event does arrive, re-parse the content
 * with it rather than patching this object.
 */
@Immutable
class SnoPayload(
    /** Format version, 1 or 2 (§2). Z has already been negated for a `v: 1` payload. */
    val version: Int,
    /** A name for humans, truncated to [MAX_NAME] characters. */
    val name: String,
    /** Scale exponent: one model unit is `2^unit` of the application's base unit (§1.6). */
    val unit: Int,
    /**
     * Grid half-width in model units, repaired and grown to contain the data
     * (§1.8). A declared extent is only honoured inside `1..`[MAX_EXTENT]; the
     * grown one has no ceiling, because the geometry is what it is.
     */
    val extent: Int,
    val mode: SnoMode,
    /** Three total-tick coordinates per vertex: X, Y, Z. */
    val positions: IntArray,
    /** One opaque ARGB colour per vertex, parallel to the vertices. */
    val colors: IntArray,
    /** Three vertex indices per face. */
    val faces: IntArray,
    /** One opaque ARGB colour per face, or null when every face interpolates its vertices (§1.4a). */
    val faceColors: IntArray?,
    val paletteRef: SnoPaletteRef,
    /** True when the object stands on the Earth's surface where it is placed (§1.7). */
    val up: Boolean,
    /** With [up], the compass bearing the object's `+Z` faces, 0 to 359. */
    val spin: Int,
) {
    val vertexCount: Int get() = colors.size

    val faceCount: Int get() = faces.size / 3

    /** The total-tick coordinate of [vertex] on [axis] (0 = X, 1 = Y, 2 = Z). */
    fun tickAt(
        vertex: Int,
        axis: Int,
    ): Int = positions[vertex * 3 + axis]

    companion object {
        /** The lattice spacing: a tick is one 120th of a model unit (§1.2). */
        const val TICKS_PER_UNIT = 120

        const val MAX_VERTICES = 512
        const val MAX_FACES = 1024
        const val MIN_EXTENT = 1
        const val MAX_EXTENT = 64
        const val DEFAULT_EXTENT = 8
        const val MAX_UNIT = 84
        const val MAX_NAME = 64

        /**
         * The farthest a vertex may lie from the origin on any axis, in ticks.
         *
         * This is a representation limit and not a rule of the format. §1.8
         * puts the position bound on publishers — "a publisher MUST NOT write a
         * vertex further than `64` model units (`7680` ticks) from the origin
         * on any axis" — and offers a reader two ways to answer one that does:
         * "A reader MAY reject such a payload and MAY instead repair it by
         * growing the extent." Both references repair. `sno-core`'s
         * `fromPayload` never looks at a position's magnitude and lets
         * `neededExtent` grow the extent past its own `MAX_EXTENT`, and
         * `sno-reference.py` does not check either. So Amethyst repairs, and an
         * object that reaches past the grid is drawn rather than refused; what
         * is left here is only the point at which the lattice stops being able
         * to say where a vertex is.
         *
         * That point is `Int.MAX_VALUE` ticks, about 17.9 million model units,
         * because a position is a total tick count in an Int — the exact
         * integer §1.2 requires every weld, comparison and hash to run on. Past
         * it there is no coordinate to repair, only one that would wrap, so
         * that is a rejection rather than a judgement about size.
         */
        const val MAX_TICKS_FROM_ORIGIN = Int.MAX_VALUE.toLong()
    }
}
