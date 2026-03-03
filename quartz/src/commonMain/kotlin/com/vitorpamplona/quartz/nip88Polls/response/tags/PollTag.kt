/*
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
package com.vitorpamplona.quartz.nip88Polls.response.tags

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.has
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip01Core.hints.types.EventIdHint
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.tags.events.GenericETag
import com.vitorpamplona.quartz.nip19Bech32.entities.NEvent
import com.vitorpamplona.quartz.nip88Polls.poll.PollEvent
import com.vitorpamplona.quartz.utils.arrayOfNotNull
import com.vitorpamplona.quartz.utils.ensure

@Immutable
data class PollTag(
    override val eventId: HexKey,
) : GenericETag {
    override var relay: NormalizedRelayUrl? = null
    override var author: HexKey? = null

    constructor(eventId: HexKey, relayHint: NormalizedRelayUrl? = null, authorPubKeyHex: HexKey? = null) : this(eventId) {
        this.relay = relayHint
        this.author = authorPubKeyHex
    }

    fun toNEvent(): String = NEvent.create(eventId, author, null, relay)

    override fun toTagArray() = assemble(eventId, relay, author)

    companion object {
        const val TAG_NAME = "e"

        fun isTagged(tag: Array<String>) = tag.has(1) && tag[0] == TAG_NAME && tag[1].length == 64

        fun isTagged(
            tag: Array<String>,
            eventId: HexKey,
        ) = tag.has(1) && tag[0] == TAG_NAME && tag[1] == eventId

        fun isTagged(
            tag: Array<String>,
            eventIds: Set<HexKey>,
        ) = tag.has(1) && tag[0] == TAG_NAME && tag[1] in eventIds

        fun parse(tag: Array<String>): PollTag? {
            ensure(tag.has(1)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            ensure(tag[1].length == 64) { return null }

            val hint = tag.getOrNull(2)?.let { RelayUrlNormalizer.normalizeOrNull(it) }

            return PollTag(tag[1], hint, tag.getOrNull(3))
        }

        fun parseId(tag: Array<String>): String? {
            ensure(tag.has(1)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            ensure(tag[1].length == 64) { return null }
            return tag[1]
        }

        fun parseAsHint(tag: Array<String>): EventIdHint? {
            ensure(tag.has(2)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            ensure(tag[1].length == 64) { return null }
            ensure(tag[2].isNotEmpty()) { return null }

            val hint = RelayUrlNormalizer.normalizeOrNull(tag[2])

            ensure(hint != null) { return null }

            return EventIdHint(tag[1], hint)
        }

        fun assemble(
            eventId: HexKey,
            relay: NormalizedRelayUrl?,
            author: HexKey?,
        ) = arrayOfNotNull(TAG_NAME, eventId, relay?.url, author)

        fun assemble(eventHint: EventHintBundle<PollEvent>) = assemble(eventHint.event.id, eventHint.relay, eventHint.event.pubKey)
    }
}
