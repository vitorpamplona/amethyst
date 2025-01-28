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
package com.vitorpamplona.quartz.nip48ProxyTags

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.utils.bytesUsedInMemory
import com.vitorpamplona.quartz.utils.pointerSizeInBytes

@Immutable
data class ProxyTag(
    val id: String,
    val protocol: String,
) {
    fun countMemory(): Long = 2 * pointerSizeInBytes + id.bytesUsedInMemory() + protocol.bytesUsedInMemory()

    fun toTagArray() = assemble(id, protocol)

    companion object {
        const val TAG_NAME = "proxy"

        @JvmStatic
        fun parse(tags: Array<String>): ProxyTag {
            require(tags[0] == TAG_NAME)
            return ProxyTag(tags[1], tags[2])
        }

        @JvmStatic
        fun assemble(
            id: String,
            protocol: String,
        ) = arrayOf(TAG_NAME, id, protocol)
    }

    enum class Protocol(
        val code: String,
    ) {
        ACT_PUB("activitypub"),
        AT("atproto"),
        RSS("rss"),
        WEB("web"),
    }
}
