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
package com.vitorpamplona.quartz.nip10Notes.tags

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.has
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip01Core.hints.types.EventIdHint
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.tags.events.GenericETag
import com.vitorpamplona.quartz.nip19Bech32.entities.NEvent
import com.vitorpamplona.quartz.utils.arrayOfNotNull
import com.vitorpamplona.quartz.utils.ensure

@Immutable
data class MarkedETag(
    override val eventId: HexKey,
) : GenericETag {
    override var relay: NormalizedRelayUrl? = null
    var marker: MARKER? = null
    override var author: HexKey? = null

    constructor(eventId: HexKey, relayHint: NormalizedRelayUrl? = null, marker: MARKER? = null, authorPubKeyHex: HexKey? = null) : this(eventId) {
        this.relay = relayHint
        this.marker = marker
        this.author = authorPubKeyHex
    }

    fun toNEvent(): String = NEvent.create(eventId, author, null, relay)

    override fun toTagArray() = assemble(eventId, relay, marker, author)

    enum class MARKER(
        val code: String,
    ) {
        ROOT("root"),
        REPLY("reply"),
        MENTION("mention"),
        FORK("fork"),
        ;

        companion object {
            fun parse(code: String): MARKER? =
                when (code) {
                    ROOT.code -> ROOT
                    REPLY.code -> REPLY
                    MENTION.code -> MENTION
                    FORK.code -> FORK
                    else -> null
                }
        }
    }

    companion object {
        const val TAG_NAME = "e"
        private const val TAG_SIZE = 4

        const val ORDER_NAME = 0
        const val ORDER_EVT_ID = 1
        const val ORDER_RELAY = 2
        const val ORDER_MARKER = 3
        const val ORDER_PUBKEY = 4

        fun isTagged(
            tag: Array<String>,
            key: HexKey,
        ) = tag.size >= 2 && tag[0] == TAG_NAME && tag[1] == key

        fun parse(tag: Array<String>): MarkedETag? {
            ensure(tag.has(2)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            ensure(tag[1].length == 64) { return null }
            ensure(tag[2].isNotEmpty()) { return null }

            return MarkedETag(
                eventId = tag[1],
                relayHint = pickRelayHint(tag),
                marker = pickMarker(tag),
                authorPubKeyHex = pickAuthor(tag),
            )
        }

        fun parseId(tag: Array<String>): HexKey? {
            ensure(tag.has(1)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            ensure(tag[1].length == 64) { return null }

            return tag[1]
        }

        // simple case   ["e", "id", "relay"]
        // empty tags    ["e", "id", "relay", ""]
        // current root  ["e", "id", "relay", "marker"]
        // current root  ["e", "id", "relay", "marker", "pubkey"]
        // empty tags    ["e", "id", "relay", "", "pubkey"]
        // pubkey marker ["e", "id", "relay", "pubkey"]
        // pubkey marker ["e", "id", "relay", "pubkey", "marker"]
        // pubkey marker ["e", "id", "pubkey"] // incorrect
        // current root  ["e", "id", "marker"] // incorrect

        private fun pickRelayHint(tag: Array<String>): NormalizedRelayUrl? {
            if (tag.has(2) && tag[2].length > 7 && RelayUrlNormalizer.isRelayUrl(tag[2])) return RelayUrlNormalizer.normalizeOrNull(tag[2])
            if (tag.has(3) && tag[3].length > 7 && RelayUrlNormalizer.isRelayUrl(tag[3])) return RelayUrlNormalizer.normalizeOrNull(tag[3])
            if (tag.has(4) && tag[4].length > 7 && RelayUrlNormalizer.isRelayUrl(tag[4])) return RelayUrlNormalizer.normalizeOrNull(tag[4])
            return null
        }

        private fun pickAuthor(tag: Array<String>): HexKey? {
            if (tag.has(3) && tag[3].length == 64) return tag[3]
            if (tag.has(4) && tag[4].length == 64) return tag[4]
            if (tag.has(2) && tag[2].length == 64) return tag[2]
            return null
        }

        private fun pickMarker(tag: Array<String>): MARKER? {
            if (tag.has(3)) MARKER.parse(tag[3])?.let { return it }
            if (tag.has(4)) MARKER.parse(tag[4])?.let { return it }
            if (tag.has(2)) MARKER.parse(tag[2])?.let { return it }
            return null
        }

        fun parseAllThreadTags(tag: Array<String>): MarkedETag? =
            if (tag.size >= 2 && tag[0] == TAG_NAME) {
                MarkedETag(
                    eventId = tag[1],
                    relayHint = pickRelayHint(tag),
                    marker = pickMarker(tag),
                    authorPubKeyHex = pickAuthor(tag),
                )
            } else {
                null
            }

        fun parseOnlyPositionalThreadTagsIds(tag: Array<String>): HexKey? =
            if (tag.size >= 2 && tag[0] == TAG_NAME) {
                if (tag.size <= 3) {
                    // simple case ["e", "id"]
                    // simple case ["e", "id", "relay"]
                    tag[1]
                } else if (tag.size == 4) {
                    if (tag[3].isEmpty() || tag[3].length == 64) {
                        // empty tags ["e", "id", "relay", ""]
                        tag[1]
                    } else {
                        // ignore all markers
                        null
                    }
                } else {
                    // tag.size >= 5
                    if (tag[3].isEmpty() || tag[3].length == 64) {
                        // empty tags ["e", "id", "relay", "", "pubkey"]
                        // updated case with pubkey instead of marker ["e", "id", "relay", "pubkey"]
                        tag[1]
                    } else {
                        // ignore all markers
                        null
                    }
                }
            } else {
                null
            }

        fun parseAsHint(tag: Array<String>): EventIdHint? {
            ensure(tag.has(2)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            ensure(tag[1].length == 64) { return null }

            val hint = pickRelayHint(tag)
            ensure(hint != null) { return null }

            return EventIdHint(tag[1], hint)
        }

        fun parseRoot(tag: Array<String>): MarkedETag? {
            ensure(tag.has(3)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            ensure(tag[1].length == 64) { return null }

            val marker = pickMarker(tag)
            ensure(marker == MARKER.ROOT) { return null }

            return MarkedETag(
                eventId = tag[1],
                relayHint = pickRelayHint(tag),
                marker = marker,
                authorPubKeyHex = pickAuthor(tag),
            )
        }

        /**
         * Old positional arguments
         */

        fun parseUnmarkedRoot(tag: Array<String>): MarkedETag? =
            if (tag.size in 2..3 && tag[0] == TAG_NAME) {
                MarkedETag(tag[1], pickRelayHint(tag), MARKER.ROOT)
            } else {
                null
            }

        fun parseReply(tag: Array<String>): MarkedETag? {
            ensure(tag.has(3)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            ensure(tag[1].length == 64) { return null }

            val marker = pickMarker(tag)
            ensure(marker == MARKER.REPLY) { return null }

            return MarkedETag(
                eventId = tag[1],
                relayHint = pickRelayHint(tag),
                marker = marker,
                authorPubKeyHex = pickAuthor(tag),
            )
        }

        /**
         * Old positional arguments
         */

        fun parseUnmarkedReply(tag: Array<String>): MarkedETag? =
            if (tag.size in 2..3 && tag[0] == TAG_NAME) {
                MarkedETag(tag[1], pickRelayHint(tag), MARKER.REPLY)
            } else {
                null
            }

        fun parseRootId(tag: Array<String>): HexKey? {
            ensure(tag.has(3)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            ensure(tag[1].length == 64) { return null }

            val marker = pickMarker(tag)
            ensure(marker == MARKER.ROOT) { return null }

            return tag[1]
        }

        fun assemble(
            eventId: HexKey,
            relay: NormalizedRelayUrl?,
            marker: String?,
            author: HexKey?,
        ) = arrayOfNotNull(TAG_NAME, eventId, relay?.url, marker, author)

        fun assemble(
            eventId: HexKey,
            relay: NormalizedRelayUrl?,
            marker: MARKER?,
            author: HexKey?,
        ) = assemble(eventId, relay, marker?.code, author)
    }
}

fun <T : Event> EventHintBundle<T>.toMarkedETag(marker: MarkedETag.MARKER) = MarkedETag(event.id, relay, marker, event.pubKey)
