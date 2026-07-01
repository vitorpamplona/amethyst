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

class SetsTag {
    companion object {
        const val TAG_NAME = "sets"

        fun isTag(tag: Array<String>) = tag.has(1) && tag[0] == TAG_NAME && tag[1].isNotEmpty()

        fun parse(tag: Array<String>): Int? {
            ensure(tag.has(1)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            return tag[1].toIntOrNull()
        }

        fun assemble(sets: Int) = arrayOf(TAG_NAME, sets.toString())
    }
}

class RepsTag {
    companion object {
        const val TAG_NAME = "reps"

        fun isTag(tag: Array<String>) = tag.has(1) && tag[0] == TAG_NAME && tag[1].isNotEmpty()

        fun parse(tag: Array<String>): Int? {
            ensure(tag.has(1)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            return tag[1].toIntOrNull()
        }

        fun assemble(reps: Int) = arrayOf(TAG_NAME, reps.toString())
    }
}

@Stable
class WeightTag(
    val value: Double,
    val unit: String,
) {
    fun toKilograms() = if (unit == POUNDS) value * KILOGRAMS_PER_POUND else value

    companion object {
        const val TAG_NAME = "weight"

        const val POUNDS = "lbs"
        const val KILOGRAMS = "kg"

        const val KILOGRAMS_PER_POUND = 0.45359237

        fun isTag(tag: Array<String>) = tag.has(1) && tag[0] == TAG_NAME && tag[1].isNotEmpty()

        /** Lax by design: a missing or unknown unit defaults to pounds (RUNSTR's default). */
        fun parse(tag: Array<String>): WeightTag? {
            ensure(tag.has(1)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            val value = tag[1].toDoubleOrNull() ?: return null
            return WeightTag(value, tag.getOrNull(2) ?: POUNDS)
        }

        fun assemble(
            value: Double,
            unit: String = POUNDS,
        ) = arrayOf(TAG_NAME, value.toString(), unit)
    }
}
