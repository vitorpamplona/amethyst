/**
 * Copyright (c) 2024 Vitor Pamplona
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

import com.vitorpamplona.quartz.utils.arrayOfNotNull

class SegmentTag(
    val start: String, // HH:MM:SS.sss
    val end: String, // HH:MM:SS.sss
    val title: String,
    val thumbnailUrl: String?,
) {
    fun toTagArray() = assemble(start, end, title, thumbnailUrl)

    companion object {
        const val TAG_NAME = "segment"
        const val TAG_SIZE = 4

        @JvmStatic
        fun parse(tag: Array<String>): SegmentTag? {
            if (tag.size < TAG_SIZE || tag[0] != TAG_NAME) return null
            return SegmentTag(tag[1], tag[2], tag[3], tag.getOrNull(4))
        }

        @JvmStatic
        fun assemble(
            start: String, // HH:MM:SS.sss
            end: String, // HH:MM:SS.sss
            title: String,
            thumbnailUrl: String?,
        ) = arrayOfNotNull(TAG_NAME, start, end, title, thumbnailUrl)
    }
}
