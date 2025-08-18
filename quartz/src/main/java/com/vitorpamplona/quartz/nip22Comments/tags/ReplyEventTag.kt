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
package com.vitorpamplona.quartz.nip22Comments.tags

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.Tag
import com.vitorpamplona.quartz.nip01Core.core.has
import com.vitorpamplona.quartz.nip01Core.hints.types.EventIdHint
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.tags.events.EventReference
import com.vitorpamplona.quartz.utils.Hex
import com.vitorpamplona.quartz.utils.TagParsingUtils
import com.vitorpamplona.quartz.utils.arrayOfNotNull
import com.vitorpamplona.quartz.utils.ensure

@Immutable
class ReplyEventTag(
    val ref: EventReference,
) {
    constructor(eventId: String, relayHint: NormalizedRelayUrl?, pubkey: String?) :
        this(EventReference(eventId, pubkey, relayHint))

    fun toTagArray() = assemble(ref)

    companion object {
        const val TAG_NAME = "e"

        @JvmStatic
        fun match(tag: Tag) = TagParsingUtils.matchesTag(tag, TAG_NAME)

        @JvmStatic
        fun isTagged(
            tag: Array<String>,
            eventId: String,
        ) = TagParsingUtils.isTaggedWith(tag, TAG_NAME, eventId)

        @JvmStatic
        fun isIn(
            tag: Array<String>,
            eventIds: Set<String>,
        ) = TagParsingUtils.isTaggedWithAny(tag, TAG_NAME, eventIds)

        @JvmStatic
        fun parse(tag: Array<String>): ReplyEventTag? {
            if (!TagParsingUtils.validateHexKeyTag(tag, TAG_NAME)) return null

            val relayHint = TagParsingUtils.parseRelayHint(tag, 2)
            return ReplyEventTag(tag[1], relayHint, tag.getOrNull(3))
        }

        @JvmStatic
        fun parseKey(tag: Array<String>): String? {
            if (!TagParsingUtils.validateHexKeyTag(tag, TAG_NAME)) return null
            return tag[1]
        }

        @JvmStatic
        fun parseValidKey(tag: Array<String>): String? {
            if (!TagParsingUtils.validateHexKeyTag(tag, TAG_NAME)) return null
            if (!Hex.isHex(tag[1])) return null
            return tag[1]
        }

        @JvmStatic
        fun parseAsHint(tag: Array<String>): EventIdHint? {
            ensure(tag.has(2)) { return null }
            if (!TagParsingUtils.validateHexKeyTag(tag, TAG_NAME)) return null
            ensure(tag[2].isNotEmpty()) { return null }

            val relayHint = TagParsingUtils.parseRelayHint(tag, 2)
            ensure(relayHint != null) { return null }

            return EventIdHint(tag[1], relayHint)
        }

        @JvmStatic
        fun assemble(
            eventId: HexKey,
            relay: NormalizedRelayUrl?,
            pubkey: String?,
        ) = arrayOfNotNull(TAG_NAME, eventId, relay?.url, pubkey)

        @JvmStatic
        fun assemble(ref: EventReference) = assemble(ref.eventId, ref.relayHint, ref.author)
    }
}
