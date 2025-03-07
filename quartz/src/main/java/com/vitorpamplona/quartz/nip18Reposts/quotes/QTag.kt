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
package com.vitorpamplona.quartz.nip18Reposts.quotes

import com.vitorpamplona.quartz.nip01Core.hints.types.AddressHint
import com.vitorpamplona.quartz.nip01Core.hints.types.EventIdHint

interface QTag {
    fun toTagArray(): Array<String>

    companion object {
        const val TAG_NAME = "q"
        const val TAG_SIZE = 2

        @JvmStatic
        fun parse(tag: Array<String>): QTag? {
            if (tag.size < TAG_SIZE || tag[0] != TAG_NAME) return null
            return if (tag[1].length == 64) {
                QEventTag.parse(tag)
            } else {
                QAddressableTag.parse(tag)
            }
        }

        @JvmStatic
        fun parseEventAsHint(tag: Array<String>): EventIdHint? {
            if (tag.size < 3 || tag[0] != TAG_NAME || tag[1].length != 64 || tag[2].isEmpty()) return null
            return EventIdHint(tag[1], tag[2])
        }

        @JvmStatic
        fun parseAddressAsHint(tag: Array<String>): AddressHint? {
            if (tag.size < 3 || tag[0] != TAG_NAME || tag[1].length == 64 || !tag[1].contains(':') || tag[2].isEmpty()) return null
            return AddressHint(tag[1], tag[2])
        }
    }
}
