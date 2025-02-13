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

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.HexKey
import com.vitorpamplona.quartz.utils.arrayOfNotNull
import com.vitorpamplona.quartz.utils.bytesUsedInMemory
import com.vitorpamplona.quartz.utils.pointerSizeInBytes

@Immutable
data class QEventTag(
    val eventId: HexKey,
) : QTag {
    var relay: String? = null
    var author: HexKey? = null

    constructor(eventId: HexKey, relayHint: String? = null, authorPubKeyHex: HexKey? = null) : this(eventId) {
        this.relay = relayHint
        this.author = authorPubKeyHex
    }

    fun countMemory(): Long =
        3 * pointerSizeInBytes + // 3 fields, 4 bytes each reference (32bit)
            eventId.bytesUsedInMemory() +
            (relay?.bytesUsedInMemory() ?: 0) +
            (author?.bytesUsedInMemory() ?: 0)

    override fun toTagArray() = assemble(eventId, relay, author)

    companion object {
        const val TAG_SIZE = 2

        @JvmStatic
        fun parse(tag: Array<String>): QEventTag? {
            if (tag.size < TAG_SIZE || tag[0] != QTag.TAG_NAME) return null
            return QEventTag(tag[1], tag.getOrNull(2), tag.getOrNull(3))
        }

        @JvmStatic
        fun assemble(
            eventId: HexKey,
            relay: String?,
            author: HexKey?,
        ) = arrayOfNotNull(QTag.TAG_NAME, eventId, relay, author)
    }
}
