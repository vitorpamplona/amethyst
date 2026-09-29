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
package com.vitorpamplona.quartz.nip71Video.views.tags

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.has
import com.vitorpamplona.quartz.utils.ensure

/**
 * Elapsed playback, in whole seconds, not positions in the video: a 6-second video looped twice
 * is `0..12`. divine-mobile always writes a start of 0.
 */
@Immutable
data class ViewedRange(
    val start: Long,
    val end: Long,
) {
    init {
        // The same rule ViewedTag.parse applies: an inverted range would read as negative watch
        // time, so it is refused on the way out rather than signed and then ignored on the way in.
        require(start in 0..end) { "Invalid viewed range: $start..$end" }
    }

    val seconds get() = end - start
}

class ViewedTag {
    companion object {
        const val TAG_NAME = "viewed"

        fun isTag(tag: Array<String>) = tag.has(2) && tag[0] == TAG_NAME && tag[1].isNotEmpty() && tag[2].isNotEmpty()

        fun parse(tag: Array<String>): ViewedRange? {
            ensure(tag.has(2)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            val start = tag[1].toLongOrNull() ?: return null
            val end = tag[2].toLongOrNull() ?: return null
            // An inverted range would read as negative watch time; the publisher drops those too.
            ensure(start in 0..end) { return null }
            return ViewedRange(start, end)
        }

        fun assemble(range: ViewedRange) = arrayOf(TAG_NAME, range.start.toString(), range.end.toString())
    }
}
