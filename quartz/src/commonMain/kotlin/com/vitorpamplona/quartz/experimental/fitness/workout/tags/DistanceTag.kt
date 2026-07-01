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
package com.vitorpamplona.quartz.experimental.fitness.workout.tags

import androidx.compose.runtime.Stable
import com.vitorpamplona.quartz.nip01Core.core.has
import com.vitorpamplona.quartz.utils.ensure

@Stable
class DistanceTag(
    val value: Double,
    val unit: String,
) {
    fun toMeters() =
        when (unit) {
            MILES -> value * METERS_PER_MILE
            METERS -> value
            else -> value * 1000.0
        }

    fun toKilometers() = toMeters() / 1000.0

    companion object {
        const val TAG_NAME = "distance"

        const val KILOMETERS = "km"
        const val MILES = "mi"
        const val METERS = "m"

        const val METERS_PER_MILE = 1609.344

        fun isTag(tag: Array<String>) = tag.has(1) && tag[0] == TAG_NAME && tag[1].isNotEmpty()

        /** Lax by design: a missing or unknown unit defaults to kilometers. */
        fun parse(tag: Array<String>): DistanceTag? {
            ensure(tag.has(1)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            val value = tag[1].toDoubleOrNull() ?: return null
            return DistanceTag(value, tag.getOrNull(2) ?: KILOMETERS)
        }

        fun assemble(
            value: Double,
            unit: String = KILOMETERS,
        ) = arrayOf(TAG_NAME, value.toString(), unit)

        fun assemble(distance: DistanceTag) = assemble(distance.value, distance.unit)
    }
}
