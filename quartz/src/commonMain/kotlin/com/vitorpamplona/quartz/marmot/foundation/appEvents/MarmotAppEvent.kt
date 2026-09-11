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
package com.vitorpamplona.quartz.marmot.foundation.appEvents

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.crypto.EventHasher

/**
 * A Marmot app event — the plaintext inside an MLS application message
 * (`foundation/application-messages.md`).
 *
 * It has the Nostr event shape **minus `sig`**, and the missing signature is
 * the point rather than an omission. MLS already authenticates the sender as a
 * group member and `pubkey` names the Marmot account that wrote it, so a
 * signature would add nothing — while making every plaintext a valid standalone
 * relay event, so one leaked message could be republished publicly as a signed
 * statement by its author. A client MUST NOT sign one.
 *
 * The `id` is still the ordinary NIP-01 event id over
 * `[0, pubkey, created_at, kind, tags, content]`, and decoders MUST reject a
 * payload whose id does not match — which means every implementation has to
 * produce byte-identical canonical JSON. That is why the id goes through
 * [EventHasher] rather than being hashed over hand-built text here.
 */
class MarmotAppEvent(
    val id: HexKey,
    val pubKey: HexKey,
    val createdAt: Long,
    val kind: Int,
    val tags: TagArray,
    val content: String,
) {
    /** Whether [id] matches the canonical id of the other members. */
    fun hasValidId(): Boolean = EventHasher.hashIdCheck(id, pubKey, createdAt, kind, tags, content)

    /**
     * The canonical wire form: one UTF-8 JSON object with exactly the six
     * members, in this order, and no others.
     */
    fun toJson(): String {
        val sb = StringBuilder(128 + content.length)
        sb.append("{\"id\":\"").append(id)
        sb.append("\",\"pubkey\":\"").append(pubKey)
        sb.append("\",\"created_at\":").append(createdAt)
        sb.append(",\"kind\":").append(kind)
        sb.append(",\"tags\":")
        appendTags(sb, tags)
        sb.append(",\"content\":")
        appendJsonString(sb, content)
        sb.append('}')
        return sb.toString()
    }

    fun encodeToPayload(): ByteArray = toJson().encodeToByteArray()

    companion object {
        /** Marmot's default ordinary chat kind. */
        const val KIND_CHAT = 9

        /** In-place replacement of a prior message's text. */
        const val KIND_EDIT = 1009

        /** A durable group system row, synthesized from canonical state. */
        const val KIND_SYSTEM = 1210

        private val ALLOWED_MEMBERS = setOf("id", "pubkey", "created_at", "kind", "tags", "content")

        val EMPTY_TAGS: TagArray = emptyArray()

        /**
         * Drop a signed Nostr event down to its Marmot app-event shape.
         *
         * The id survives unchanged: NIP-01 hashes
         * `[0, pubkey, created_at, kind, tags, content]`, which never covered
         * the signature. That is what lets a client switch to the canonical
         * shape without renumbering its own history.
         */
        fun fromEvent(event: Event) =
            MarmotAppEvent(
                id = event.id,
                pubKey = event.pubKey,
                createdAt = event.createdAt,
                kind = event.kind,
                tags = event.tags,
                content = event.content,
            )

        /** Build one, computing the canonical id from the other members. */
        fun build(
            pubKey: HexKey,
            kind: Int,
            content: String,
            createdAt: Long,
            tags: TagArray = EMPTY_TAGS,
        ) = MarmotAppEvent(
            id = EventHasher.hashId(pubKey, createdAt, kind, tags, content),
            pubKey = pubKey,
            createdAt = createdAt,
            kind = kind,
            tags = tags,
            content = content,
        )

        /**
         * Decode a Marmot app payload, strictly.
         *
         * Every rejection here is required by the spec, and each closes a
         * different hole:
         *
         * - a `sig` member, because a signed inner event is republishable as a
         *   public statement by its author;
         * - an unknown top-level member, because two implementations that
         *   disagree about what to ignore disagree about the id preimage;
         * - a duplicate key, because "last one wins" and "first one wins" are
         *   both defensible and yield different events from identical bytes;
         * - an id that does not match, because the id is what edits, history
         *   and deduplication all reference.
         *
         * @throws IllegalArgumentException naming the reason.
         */
        fun decode(json: String): MarmotAppEvent {
            // Shape before content. MLS authenticates that a group MEMBER sent
            // these bytes, never that they are well-intentioned, and a deeply
            // nested or enormous payload costs a parser far more than it costs
            // the sender. The pre-scan is linear and runs before any JSON
            // library sees the string.
            require(MarmotJson.withinResourceBounds(json)) {
                "Marmot app payload exceeds the parse bounds"
            }
            val obj = MarmotJson.parseObject(json)

            require(!obj.containsKey("sig")) {
                "Marmot app payload MUST NOT carry a Nostr signature"
            }
            val unknown = obj.keys - ALLOWED_MEMBERS
            require(unknown.isEmpty()) {
                "Marmot app payload has unknown member(s): ${unknown.sorted()}"
            }
            require(obj.duplicateKeys.isEmpty()) {
                "Marmot app payload has duplicate key(s): ${obj.duplicateKeys.sorted()}"
            }
            val missing = ALLOWED_MEMBERS - obj.keys
            require(missing.isEmpty()) {
                "Marmot app payload is missing member(s): ${missing.sorted()}"
            }

            val event =
                MarmotAppEvent(
                    id = obj.string("id"),
                    pubKey = obj.string("pubkey"),
                    createdAt = obj.long("created_at"),
                    kind = obj.int("kind"),
                    tags = obj.tags("tags"),
                    content = obj.string("content"),
                )
            require(event.hasValidId()) {
                "Marmot app payload id does not match its canonical serialization"
            }
            return event
        }

        /** [decode], returning null instead of throwing. */
        fun decodeOrNull(json: String): MarmotAppEvent? =
            try {
                decode(json)
            } catch (_: Exception) {
                null
            }

        private fun appendTags(
            sb: StringBuilder,
            tags: TagArray,
        ) {
            sb.append('[')
            for (i in tags.indices) {
                if (i > 0) sb.append(',')
                sb.append('[')
                val tag = tags[i]
                for (j in tag.indices) {
                    if (j > 0) sb.append(',')
                    appendJsonString(sb, tag[j])
                }
                sb.append(']')
            }
            sb.append(']')
        }

        /**
         * NIP-01 string escaping.
         *
         * The escape set is exactly the one NIP-01 pins, because this text also
         * feeds the id preimage: escaping one more character than a peer does
         * changes the hash, and the payload is then rejected as having a bad id.
         */
        private fun appendJsonString(
            sb: StringBuilder,
            value: String,
        ) {
            sb.append('"')
            for (ch in value) {
                when (ch) {
                    '"' -> sb.append("\\\"")
                    '\\' -> sb.append("\\\\")
                    '\n' -> sb.append("\\n")
                    '\r' -> sb.append("\\r")
                    '\t' -> sb.append("\\t")
                    '\u0008' -> sb.append("\\b")
                    '\u000C' -> sb.append("\\f")
                    else ->
                        if (ch < ' ') {
                            sb.append("\\u")
                            sb.append(ch.code.toString(16).padStart(4, '0'))
                        } else {
                            sb.append(ch)
                        }
                }
            }
            sb.append('"')
        }
    }
}
