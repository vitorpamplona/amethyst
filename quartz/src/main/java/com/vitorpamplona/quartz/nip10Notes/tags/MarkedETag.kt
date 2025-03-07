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
package com.vitorpamplona.quartz.nip10Notes.tags

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.hints.types.EventIdHint
import com.vitorpamplona.quartz.nip01Core.tags.events.GenericETag
import com.vitorpamplona.quartz.nip19Bech32.entities.NEvent
import com.vitorpamplona.quartz.utils.arrayOfNotNull
import com.vitorpamplona.quartz.utils.bytesUsedInMemory
import com.vitorpamplona.quartz.utils.pointerSizeInBytes

@Immutable
data class MarkedETag(
    override val eventId: HexKey,
) : GenericETag {
    override var relay: String? = null
    var marker: String? = null
    override var author: HexKey? = null

    constructor(eventId: HexKey, relayHint: String? = null, marker: String? = null, authorPubKeyHex: HexKey? = null) : this(eventId) {
        this.relay = relayHint
        this.marker = marker
        this.author = authorPubKeyHex
    }

    constructor(eventId: HexKey, relayHint: String? = null, marker: MARKER? = null, authorPubKeyHex: HexKey? = null) : this(eventId) {
        this.relay = relayHint
        this.marker = marker?.code
        this.author = authorPubKeyHex
    }

    fun countMemory(): Long =
        4 * pointerSizeInBytes + // 3 fields, 4 bytes each reference (32bit)
            eventId.bytesUsedInMemory() +
            (relay?.bytesUsedInMemory() ?: 0) +
            (marker?.bytesUsedInMemory() ?: 0) +
            (author?.bytesUsedInMemory() ?: 0)

    fun toNEvent(): String = NEvent.create(eventId, author, null, relay)

    override fun toTagArray() = arrayOfNotNull(TAG_NAME, eventId, relay, marker, author)

    enum class MARKER(
        val code: String,
    ) {
        ROOT("root"),
        REPLY("reply"),
        MENTION("mention"),
        FORK("fork"),
    }

    companion object {
        const val TAG_NAME = "e"
        const val TAG_SIZE = 4

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
                tag[ORDER_RELAY],
                tag[ORDER_MARKER],
                tag.getOrNull(
                    ORDER_PUBKEY,
                ),
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
                    MarkedETag(tag[1], tag.getOrNull(2), null as String?, null)
                } else if (tag.size == 4) {
                    if (tag[3].isEmpty()) {
                        // empty tags ["e", "id", "relay", ""]
                        MarkedETag(tag[1], tag[2], null as String?, null)
                    } else if (tag[3].length == 64) {
                        // updated case with pubkey instead of marker ["e", "id", "relay", "pubkey"]
                        MarkedETag(tag[1], tag[2], null as String?, tag[3])
                    } else if (tag[3] == MARKER.ROOT.code) {
                        // corrent root ["e", "id", "relay", "root"]
                        MarkedETag(tag[1], tag[2], tag[3])
                    } else if (tag[3] == MARKER.REPLY.code) {
                        // correct reply ["e", "id", "relay", "reply"]
                        MarkedETag(tag[1], tag[2], tag[3])
                    } else {
                        // ignore "mention" and "fork" markers
                        null
                    }
                } else {
                    // tag.size >= 5
                    if (tag[3].isEmpty()) {
                        // empty tags ["e", "id", "relay", "", "pubkey"]
                        MarkedETag(tag[1], tag[2], null as String?, tag[4])
                    } else if (tag[3].length == 64) {
                        // updated case with pubkey instead of marker ["e", "id", "relay", "pubkey"]
                        MarkedETag(tag[1], tag[2], null as String?, tag[3])
                    } else if (tag[3] == MARKER.ROOT.code) {
                        // corrent root ["e", "id", "relay", "root"]
                        MarkedETag(tag[1], tag[2], tag[3], tag[4])
                    } else if (tag[3] == MARKER.REPLY.code) {
                        // correct reply ["e", "id", "relay", "reply"]
                        MarkedETag(tag[1], tag[2], tag[3], tag[4])
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
            if (tag.size < 3 || tag[0] != TAG_NAME || tag[1].length != 64 || tag[2].isEmpty()) return null
            return EventIdHint(tag[1], tag[2])
        }

        @JvmStatic
        fun parseRoot(tag: Array<String>): MarkedETag? {
            if (tag.size < TAG_SIZE || tag[0] != TAG_NAME) return null
            if (tag[ORDER_MARKER] != MARKER.ROOT.code) return null
            // ["e", id hex, relay hint, marker, pubkey]
            return MarkedETag(
                tag[ORDER_EVT_ID],
                tag[ORDER_RELAY],
                tag[ORDER_MARKER],
                tag.getOrNull(
                    ORDER_PUBKEY,
                ),
            )
        }

        /**
         * Old positional arguments
         */
        @JvmStatic
        fun parseUnmarkedRoot(tag: Array<String>): MarkedETag? =
            if (tag.size in 2..3 && tag[0] == TAG_NAME) {
                MarkedETag(tag[1], tag.getOrNull(2), MARKER.ROOT)
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
                tag[ORDER_RELAY],
                tag[ORDER_MARKER],
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
                MarkedETag(tag[1], tag.getOrNull(2), MARKER.REPLY)
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
            relay: String?,
            marker: MARKER?,
            author: HexKey?,
        ) = arrayOfNotNull(TAG_NAME, eventId, relay, marker?.code, author)
    }
}
