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
package com.vitorpamplona.quartz.experimental.ratings.tags

import com.vitorpamplona.quartz.nip01Core.core.Tag
import com.vitorpamplona.quartz.nip01Core.core.has
import com.vitorpamplona.quartz.utils.ensure
import kotlin.math.roundToInt

/**
 * The `rating` tag of a kind-34259 entity rating: the score normalized to 0..1.
 *
 * The spec (`XYZ.md` in abh3po/nostr-polls) words the range as "less than 1 and
 * greater than 0", but the clients that actually publish this kind emit `1.000`
 * for a full score, so [parse] accepts the closed interval and leaves the
 * out-of-range decision to the caller.
 */
class RatingTag {
    companion object {
        const val TAG_NAME = "rating"

        fun match(tag: Tag) = tag.has(1) && tag[0] == TAG_NAME && tag[1].isNotEmpty()

        /** The raw tag value as a number, whatever scale it is on. Null when absent or unparseable. */
        fun parse(tag: Tag): Double? {
            ensure(tag.has(1)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            ensure(tag[1].isNotEmpty()) { return null }
            return tag[1].toDoubleOrNull()
        }

        fun assemble(fraction: Double) = arrayOf(TAG_NAME, format3(fraction))

        /**
         * Formats a 0..1 fraction with three decimals ("1.000", "0.800"), matching what the
         * publishing clients emit. Hand-rolled because commonMain has no `String.format`.
         */
        fun format3(fraction: Double): String {
            val scaled = (fraction.coerceIn(0.0, 1.0) * 1000).roundToInt()
            return "${scaled / 1000}.${(scaled % 1000).toString().padStart(3, '0')}"
        }
    }
}
