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
package com.vitorpamplona.quartz.nip01Core.tags.events

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.hints.types.EventIdHint
import com.vitorpamplona.quartz.nip19Bech32.entities.NEvent
import com.vitorpamplona.quartz.utils.arrayOfNotNull
import com.vitorpamplona.quartz.utils.bytesUsedInMemory
import com.vitorpamplona.quartz.utils.pointerSizeInBytes

@Immutable
data class ETag(
    override val eventId: HexKey,
) : GenericETag {
    override var relay: String? = null
    override var author: HexKey? = null

    constructor(eventId: HexKey, relayHint: String? = null, authorPubKeyHex: HexKey? = null) : this(eventId) {
        this.relay = relayHint
        this.author = authorPubKeyHex
    }

    fun countMemory(): Long =
        3 * pointerSizeInBytes + // 3 fields, 4 bytes each reference (32bit)
            eventId.bytesUsedInMemory() +
            (relay?.bytesUsedInMemory() ?: 0) +
            (author?.bytesUsedInMemory() ?: 0)

    fun toNEvent(): String = NEvent.create(eventId, author, null, relay)

    override fun toTagArray() = toNamedTagArray(TAG_NAME)

    fun toQTagArray() = toNamedTagArray("q")

    fun toNamedTagArray(key: String) = arrayOfNotNull(key, eventId, relay, author)

    companion object {
        const val TAG_NAME = "e"
        const val TAG_SIZE = 2

        @JvmStatic
        fun isTagged(tag: Array<String>) = tag.size >= TAG_SIZE && tag[0] == TAG_NAME && tag[1].length == 64

        @JvmStatic
        fun isTagged(
            tag: Array<String>,
            eventId: HexKey,
        ) = tag.size >= TAG_SIZE && tag[0] == TAG_NAME && tag[1] == eventId

        @JvmStatic
        fun parse(tag: Array<String>): ETag? {
            if (tag.size < TAG_SIZE || tag[0] != TAG_NAME || tag[1].length != 64) return null
            return ETag(tag[1], tag.getOrNull(2), tag.getOrNull(3))
        }

        @JvmStatic
        fun parseId(tag: Array<String>): String? {
            if (tag.size < TAG_SIZE || tag[0] != TAG_NAME || tag[1].length != 64) return null
            return tag[1]
        }

        @JvmStatic
        fun parseAsHint(tag: Array<String>): EventIdHint? {
            if (tag.size < 3 || tag[0] != TAG_NAME || tag[1].length != 64 || tag[2].isEmpty()) return null
            return EventIdHint(tag[1], tag[2])
        }

        @JvmStatic
        fun assemble(
            eventId: HexKey,
            relay: String?,
            author: HexKey?,
        ) = arrayOfNotNull(TAG_NAME, eventId, relay, author)
    }
}
