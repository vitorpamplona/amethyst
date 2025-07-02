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
package com.vitorpamplona.quartz.nip10Notes.tags

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.has
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
            fun parse(code: String): MARKER? {
                return when (code) {
                    ROOT.code -> ROOT
                    REPLY.code -> REPLY
                    MENTION.code -> MENTION
                    FORK.code -> FORK
                    else -> null
                }
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

        @JvmStatic
        fun parse(tag: Array<String>): MarkedETag? {
            if (tag.size < TAG_SIZE || tag[0] != TAG_NAME) return null

            return MarkedETag(
                tag[ORDER_EVT_ID],
                RelayUrlNormalizer.normalizeOrNull(tag[ORDER_RELAY]),
                MARKER.parse(tag[ORDER_MARKER]),
                tag.getOrNull(ORDER_PUBKEY),
            )
        }

        @JvmStatic
        fun parseId(tag: Array<String>): HexKey? {
            if (tag.size < TAG_SIZE || tag[0] != TAG_NAME) return null

            return tag[ORDER_EVT_ID]
        }

        @JvmStatic
        fun parseAllThreadTags(tag: Array<String>): MarkedETag? =
            if (tag.size >= 2 && tag[0] == TAG_NAME) {
                if (tag.size <= 3) {
                    // simple case ["e", "id", "relay"]
                    MarkedETag(tag[1], tag.getOrNull(2)?.let { RelayUrlNormalizer.normalizeOrNull(it) }, null, null)
                } else if (tag.size == 4) {
                    val relayHint = RelayUrlNormalizer.normalizeOrNull(tag[2])
                    if (tag[3].isEmpty()) {
                        // empty tags ["e", "id", "relay", ""]
                        MarkedETag(tag[1], relayHint, null, null)
                    } else if (tag[3].length == 64) {
                        // updated case with pubkey instead of marker ["e", "id", "relay", "pubkey"]
                        MarkedETag(tag[1], relayHint, null, tag[3])
                    } else if (tag[3] == MARKER.ROOT.code) {
                        // corrent root ["e", "id", "relay", "root"]
                        MarkedETag(tag[1], relayHint, MARKER.ROOT)
                    } else if (tag[3] == MARKER.REPLY.code) {
                        // correct reply ["e", "id", "relay", "reply"]
                        MarkedETag(tag[1], relayHint, MARKER.REPLY)
                    } else {
                        // ignore "mention" and "fork" markers
                        null
                    }
                } else {
                    val relayHint = RelayUrlNormalizer.normalizeOrNull(tag[2])
                    // tag.size >= 5
                    if (tag[3].isEmpty()) {
                        // empty tags ["e", "id", "relay", "", "pubkey"]
                        MarkedETag(tag[1], relayHint, null, tag[4])
                    } else if (tag[3].length == 64) {
                        // updated case with pubkey instead of marker ["e", "id", "relay", "pubkey"]
                        MarkedETag(tag[1], relayHint, null, tag[3])
                    } else if (tag[3] == MARKER.ROOT.code) {
                        // corrent root ["e", "id", "relay", "root"]
                        MarkedETag(tag[1], relayHint, MARKER.ROOT, tag[4])
                    } else if (tag[3] == MARKER.REPLY.code) {
                        // correct reply ["e", "id", "relay", "reply"]
                        MarkedETag(tag[1], relayHint, MARKER.REPLY, tag[4])
                    } else {
                        // ignore "mention" and "fork" markers
                        null
                    }
                }
            } else {
                null
            }

        @JvmStatic
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

        @JvmStatic
        fun parseAsHint(tag: Array<String>): EventIdHint? {
            ensure(tag.has(2)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            ensure(tag[1].length == 64) { return null }
            ensure(tag[2].isNotEmpty()) { return null }

            val hint = RelayUrlNormalizer.normalizeOrNull(tag[2])
            ensure(hint != null) { return null }

            return EventIdHint(tag[1], hint)
        }

        @JvmStatic
        fun parseRoot(tag: Array<String>): MarkedETag? {
            if (tag.size < TAG_SIZE || tag[0] != TAG_NAME) return null
            if (tag[ORDER_MARKER] != MARKER.ROOT.code) return null

            // ["e", id hex, relay hint, marker, pubkey]
            return MarkedETag(
                eventId = tag[ORDER_EVT_ID],
                relayHint = RelayUrlNormalizer.normalizeOrNull(tag[ORDER_RELAY]),
                marker = MARKER.ROOT,
                authorPubKeyHex = tag.getOrNull(ORDER_PUBKEY),
            )
        }

        /**
         * Old positional arguments
         */
        @JvmStatic
        fun parseUnmarkedRoot(tag: Array<String>): MarkedETag? =
            if (tag.size in 2..3 && tag[0] == TAG_NAME) {
                MarkedETag(tag[1], tag.getOrNull(2)?.let { RelayUrlNormalizer.normalizeOrNull(it) }, MARKER.ROOT)
            } else {
                null
            }

        @JvmStatic
        fun parseReply(tag: Array<String>): MarkedETag? {
            if (tag.size < TAG_SIZE || tag[0] != TAG_NAME) return null
            if (tag[ORDER_MARKER] != MARKER.REPLY.code) return null
            // ["e", id hex, relay hint, marker, pubkey]
            return MarkedETag(
                tag[ORDER_EVT_ID],
                RelayUrlNormalizer.normalizeOrNull(tag[ORDER_RELAY]),
                MARKER.REPLY,
                tag.getOrNull(
                    ORDER_PUBKEY,
                ),
            )
        }

        /**
         * Old positional arguments
         */
        @JvmStatic
        fun parseUnmarkedReply(tag: Array<String>): MarkedETag? =
            if (tag.size in 2..3 && tag[0] == TAG_NAME) {
                MarkedETag(tag[1], tag.getOrNull(2)?.let { RelayUrlNormalizer.normalizeOrNull(it) }, MARKER.REPLY)
            } else {
                null
            }

        @JvmStatic
        fun parseRootId(tag: Array<String>): HexKey? {
            if (tag.size < TAG_SIZE || tag[0] != TAG_NAME) return null
            if (tag[ORDER_MARKER] != MARKER.ROOT.code) return null
            // ["e", id hex, relay hint, marker, pubkey]
            return tag[ORDER_EVT_ID]
        }

        @JvmStatic
        fun assemble(
            eventId: HexKey,
            relay: NormalizedRelayUrl?,
            marker: String?,
            author: HexKey?,
        ) = arrayOfNotNull(TAG_NAME, eventId, relay?.url, marker, author)

        @JvmStatic
        fun assemble(
            eventId: HexKey,
            relay: NormalizedRelayUrl?,
            marker: MARKER?,
            author: HexKey?,
        ) = assemble(eventId, relay, marker?.code, author)
    }
}
