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

/** A per-distance split: [number] is the 1-based split index, [cumulativeSeconds] the elapsed time at its end. */
@Stable
class SplitTag(
    val number: Int,
    val cumulativeSeconds: Long,
) {
    companion object {
        const val TAG_NAME = "split"

        fun isTag(tag: Array<String>) = tag.has(2) && tag[0] == TAG_NAME && tag[1].isNotEmpty() && tag[2].isNotEmpty()

        fun parse(tag: Array<String>): SplitTag? {
            ensure(tag.has(2)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            val number = tag[1].toIntOrNull() ?: return null
            val seconds = DurationTag.parseTime(tag[2]) ?: return null
            return SplitTag(number, seconds)
        }

        fun assemble(
            number: Int,
            cumulativeSeconds: Long,
        ) = arrayOf(TAG_NAME, number.toString(), DurationTag.formatTime(cumulativeSeconds))

        fun assemble(split: SplitTag) = assemble(split.number, split.cumulativeSeconds)

        fun assemble(splits: List<SplitTag>) = splits.map { split -> assemble(split) }
    }
}
