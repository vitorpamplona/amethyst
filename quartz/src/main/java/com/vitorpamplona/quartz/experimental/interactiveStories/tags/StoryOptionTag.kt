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
package com.vitorpamplona.quartz.experimental.interactiveStories.tags

import com.vitorpamplona.quartz.nip01Core.tags.addressables.ATag
import com.vitorpamplona.quartz.utils.arrayOfNotNull

class StoryOptionTag(
    val option: String,
    val address: ATag,
) {
    fun toTagArray() = assemble(option, address)

    companion object {
        const val TAG_NAME = "option"
        const val TAG_SIZE = 3

        @JvmStatic
        fun parse(tag: Array<String>): StoryOptionTag? {
            if (tag.size < TAG_SIZE || tag[0] != TAG_NAME) return null

            return ATag.parse(tag[2], tag.getOrNull(3))?.let { StoryOptionTag(tag[1], it) }
        }

        @JvmStatic
        fun assemble(
            title: String,
            destination: ATag,
        ) = arrayOfNotNull(TAG_NAME, title, destination.toTag(), destination.relay)
    }
}
