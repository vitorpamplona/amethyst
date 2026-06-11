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
class Elevation(
    val value: Double,
    val unit: String,
) {
    fun toMeters() = if (unit == FEET) value * METERS_PER_FOOT else value

    companion object {
        const val METERS = "m"
        const val FEET = "ft"

        const val METERS_PER_FOOT = 0.3048

        /** Lax by design: a missing or unknown unit defaults to meters. */
        fun parse(
            tag: Array<String>,
            tagName: String,
        ): Elevation? {
            ensure(tag.has(1)) { return null }
            ensure(tag[0] == tagName) { return null }
            val value = tag[1].toDoubleOrNull() ?: return null
            return Elevation(value, tag.getOrNull(2) ?: METERS)
        }
    }
}

class ElevationGainTag {
    companion object {
        const val TAG_NAME = "elevation_gain"

        fun isTag(tag: Array<String>) = tag.has(1) && tag[0] == TAG_NAME && tag[1].isNotEmpty()

        fun parse(tag: Array<String>) = Elevation.parse(tag, TAG_NAME)

        fun assemble(
            value: Double,
            unit: String = Elevation.METERS,
        ) = arrayOf(TAG_NAME, value.toString(), unit)
    }
}

class ElevationLossTag {
    companion object {
        const val TAG_NAME = "elevation_loss"

        fun isTag(tag: Array<String>) = tag.has(1) && tag[0] == TAG_NAME && tag[1].isNotEmpty()

        fun parse(tag: Array<String>) = Elevation.parse(tag, TAG_NAME)

        fun assemble(
            value: Double,
            unit: String = Elevation.METERS,
        ) = arrayOf(TAG_NAME, value.toString(), unit)
    }
}
