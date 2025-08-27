/**
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
package com.vitorpamplona.quartz.nip71Video.tags

import com.vitorpamplona.quartz.nip01Core.core.has
import com.vitorpamplona.quartz.utils.arrayOfNotNull
import com.vitorpamplona.quartz.utils.ensure

class SegmentTag(
    // HH:MM:SS.sss
    val start: String,
    // HH:MM:SS.sss
    val end: String,
    val title: String,
    val thumbnailUrl: String?,
) {
    fun toTagArray() = assemble(start, end, title, thumbnailUrl)

    companion object {
        const val TAG_NAME = "segment"

        @JvmStatic
        fun parse(tag: Array<String>): SegmentTag? {
            ensure(tag.has(3)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            ensure(tag[1].isNotEmpty()) { return null }
            ensure(tag[2].isNotEmpty()) { return null }
            ensure(tag[3].isNotEmpty()) { return null }
            return SegmentTag(tag[1], tag[2], tag[3], tag.getOrNull(4))
        }

        @JvmStatic
        fun assemble(
            // HH:MM:SS.sss
            start: String,
            // HH:MM:SS.sss
            end: String,
            title: String,
            thumbnailUrl: String?,
        ) = arrayOfNotNull(TAG_NAME, start, end, title, thumbnailUrl)
    }
}
