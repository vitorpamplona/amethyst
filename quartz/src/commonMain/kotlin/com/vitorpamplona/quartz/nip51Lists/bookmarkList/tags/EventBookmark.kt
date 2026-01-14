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
package com.vitorpamplona.quartz.nip51Lists.bookmarkList.tags

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.has
import com.vitorpamplona.quartz.nip01Core.hints.types.EventIdHint
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip19Bech32.entities.NEvent
import com.vitorpamplona.quartz.utils.arrayOfNotNull
import com.vitorpamplona.quartz.utils.ensure

@Immutable
class EventBookmark(
    val eventId: HexKey,
    val relay: NormalizedRelayUrl? = null,
    val author: HexKey? = null,
) : BookmarkIdTag {
    fun toNEvent(): String = NEvent.create(eventId, author, null, relay)

    override fun toTagArray() = assemble(eventId, relay, author)

    override fun toTagIdOnly() = assemble(eventId, null, null)

    companion object {
        const val TAG_NAME = "e"

        fun isTagged(tag: Array<String>) = tag.has(1) && tag[0] == TAG_NAME && tag[1].length == 64

        fun isTagged(
            tag: Array<String>,
            eventId: HexKey,
        ) = tag.has(1) && tag[0] == TAG_NAME && tag[1] == eventId

        fun parse(tag: Array<String>): EventBookmark? {
            ensure(tag.has(1)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            ensure(tag[1].length == 64) { return null }

            return EventBookmark(tag[1], pickRelayHint(tag), pickAuthor(tag))
        }

        fun parseId(tag: Array<String>): String? {
            ensure(tag.has(1)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            ensure(tag[1].length == 64) { return null }
            return tag[1]
        }

        private fun pickRelayHint(tag: Array<String>): NormalizedRelayUrl? {
            if (tag.has(2) && tag[2].length > 7 && RelayUrlNormalizer.isRelayUrl(tag[2])) return RelayUrlNormalizer.normalizeOrNull(tag[2])
            if (tag.has(3) && tag[3].length > 7 && RelayUrlNormalizer.isRelayUrl(tag[3])) return RelayUrlNormalizer.normalizeOrNull(tag[3])
            if (tag.has(4) && tag[4].length > 7 && RelayUrlNormalizer.isRelayUrl(tag[4])) return RelayUrlNormalizer.normalizeOrNull(tag[4])
            return null
        }

        private fun pickAuthor(tag: Array<String>): HexKey? {
            if (tag.has(2) && tag[2].length == 64) return tag[2]
            if (tag.has(3) && tag[3].length == 64) return tag[3]
            if (tag.has(4) && tag[4].length == 64) return tag[4]
            return null
        }

        fun parseAsHint(tag: Array<String>): EventIdHint? {
            ensure(tag.has(2)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            ensure(tag[1].length == 64) { return null }
            ensure(tag[2].isNotEmpty()) { return null }

            val hint = pickRelayHint(tag)

            ensure(hint != null) { return null }

            return EventIdHint(tag[1], hint)
        }

        fun assemble(
            eventId: HexKey,
            relay: NormalizedRelayUrl?,
            author: HexKey?,
        ) = arrayOfNotNull(TAG_NAME, eventId, relay?.url, author)
    }
}
