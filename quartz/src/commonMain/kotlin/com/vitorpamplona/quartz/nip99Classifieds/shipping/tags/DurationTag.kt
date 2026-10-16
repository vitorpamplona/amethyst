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
package com.vitorpamplona.quartz.nip99Classifieds.shipping.tags

import androidx.compose.runtime.Stable
import com.vitorpamplona.quartz.nip01Core.core.has
import com.vitorpamplona.quartz.utils.ensure

/** `["duration", <min>, <max>, <unit>]`: the delivery window, unit H (hours), D (days) or W (weeks). */
@Stable
data class DurationTag(
    val min: String,
    val max: String,
    val unit: String,
) {
    fun toTagArray() = assemble(min, max, unit)

    companion object {
        const val TAG_NAME = "duration"

        fun isTag(tag: Array<String>) = tag.has(3) && tag[0] == TAG_NAME

        fun parse(tag: Array<String>): DurationTag? {
            ensure(tag.has(3)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            return DurationTag(tag[1], tag[2], tag[3])
        }

        fun assemble(
            min: String,
            max: String,
            unit: String,
        ) = arrayOf(TAG_NAME, min, max, unit)
    }
}
