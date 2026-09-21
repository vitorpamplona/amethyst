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

import kotlin.math.ceil
import kotlin.math.ln
import kotlin.math.max

/**
 * The proof of work an avatar owes for its size and its detail
 * (`CYBERSPACE_V2.md` §8.10, normative).
 *
 * ```python
 * AVATAR_FLOOR_BITS  = 16   # every avatar
 * AVATAR_SIZE_BITS   = 2    # per doubling of reach
 * AVATAR_DETAIL_BITS = 3    # per doubling of detail beyond the free thirty-two
 * AVATAR_DETAIL_FREE = 32
 *
 * reach  = max(1.0, max(abs(v + t / 120) for every vertex coordinate) * 2 ** payload.unit)
 * detail = max(AVATAR_DETAIL_FREE, len(payload.vertices) + len(payload.faces))
 * return ceil(AVATAR_FLOOR_BITS + AVATAR_SIZE_BITS * log2(reach) + AVATAR_DETAIL_BITS * log2(detail / AVATAR_DETAIL_FREE))
 * ```
 *
 * Reach is the term that matters to other people, since a large avatar is the
 * one that gets in everyone's way: two bits per doubling makes a shape twice as
 * far across cost four times the work, so a modest shape of a few gibsons costs
 * minutes on a phone while a sector-sized one is out of reach of any hash power.
 * Reach is measured from the build origin rather than the shape's own centre,
 * so a shape is priced as its builder placed it.
 */
object SnoAvatarWork {
    const val FLOOR_BITS = 16
    const val SIZE_BITS = 2
    const val DETAIL_BITS = 3
    const val DETAIL_FREE = 32

    /** The leading zero bits [payload] must have been mined to, as an avatar. */
    fun required(payload: SnoPayload): Int {
        var farthestTicks = 0
        for (tick in payload.positions) {
            val magnitude = if (tick < 0) -tick else tick
            if (magnitude > farthestTicks) farthestTicks = magnitude
        }

        // In the application's base unit at true scale: one model unit is 2^unit
        // of it, and a tick is one 120th of a model unit. Never below one, which
        // is what `max(1.0, ...)` does before the logarithm.
        val reach = max(1.0, (farthestTicks.toDouble() / SnoPayload.TICKS_PER_UNIT) * pow2(payload.unit))
        val detail = max(DETAIL_FREE, payload.vertexCount + payload.faceCount)

        val bits = FLOOR_BITS + SIZE_BITS * log2(reach) + DETAIL_BITS * log2(detail.toDouble() / DETAIL_FREE)
        return ceil(bits).toInt()
    }

    /** `2^exponent` for the 0..84 range `unit` allows, beyond what an Int holds. */
    private fun pow2(exponent: Int): Double {
        var value = 1.0
        repeat(exponent) { value *= 2.0 }
        return value
    }

    private fun log2(value: Double): Double = ln(value) / LN_2

    private val LN_2 = ln(2.0)
}
