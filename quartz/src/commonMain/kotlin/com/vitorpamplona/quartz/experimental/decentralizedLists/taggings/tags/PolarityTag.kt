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
package com.vitorpamplona.quartz.experimental.decentralizedLists.taggings.tags

import com.vitorpamplona.quartz.nip01Core.core.has
import com.vitorpamplona.quartz.utils.ensure

/** How a v1 reader counts a tagging. */
enum class Polarity {
    /** `≥ 0.5`, or no `polarity` tag at all. */
    APPLIED,

    /** `≤ -0.5`. */
    DISPUTED,

    /**
     * Strictly between the two, or unreadable. Reserved for a future graded-valence design and
     * not counted either way in v1.
     */
    UNCOUNTED,
}

/**
 * Tags & Taggings: `["polarity", "1" | "-1"]` — apply or dispute. An absent tag means apply.
 * It is multi-letter, so relays cannot filter on it; excluding disputes is a read-time step.
 */
class PolarityTag {
    companion object {
        const val TAG_NAME = "polarity"
        const val APPLY = "1"
        const val DISPUTE = "-1"

        fun isTag(tag: Array<String>) = tag.has(1) && tag[0] == TAG_NAME

        /** The raw value, or null when the tag is not a polarity tag or does not hold a number. */
        fun parseValue(tag: Array<String>): Double? {
            ensure(tag.has(1)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            return tag[1].toDoubleOrNull()?.takeIf { it.isFinite() }
        }

        fun bucket(value: Double) =
            when {
                value >= 0.5 -> Polarity.APPLIED
                value <= -0.5 -> Polarity.DISPUTED
                else -> Polarity.UNCOUNTED
            }

        fun assemble(apply: Boolean) = arrayOf(TAG_NAME, if (apply) APPLY else DISPUTE)
    }
}
