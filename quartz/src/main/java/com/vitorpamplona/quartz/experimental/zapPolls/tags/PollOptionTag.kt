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
package com.vitorpamplona.quartz.experimental.zapPolls.tags

class PollOptionTag(
    val index: Int,
    val descriptor: String,
) {
    fun toTagArray() = assemble(index, descriptor)

    companion object {
        const val TAG_NAME = "poll_option"
        const val TAG_SIZE = 3

        @JvmStatic
        fun parse(tag: Array<String>): PollOptionTag? {
            if (tag.size < TAG_SIZE || tag[0] != TAG_NAME) return null

            val index = tag[1].toIntOrNull() ?: return null

            return PollOptionTag(index, tag[2])
        }

        @JvmStatic
        fun assemble(
            index: Int,
            descriptor: String,
        ) = arrayOf(TAG_NAME, index.toString(), descriptor)
    }
}
