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
package com.vitorpamplona.quartz.cordn.spec02Envelopes

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.crypto.EventHasher
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put

/**
 * The Nostr-shaped application payload a cordn message carries, from
 * `spec/02.md`.
 *
 * A NIP-01 event with no `sig`. The signature is not missing by oversight —
 * §9 removes it deliberately, because MLS has already authenticated the sender
 * and a second signature would mean a second signer round trip (possibly to a
 * remote signer) for a proof nobody needs. Reusing the event shape buys the
 * `kind`/`tags` vocabulary: kind 9 chat, 1111 threaded replies, 7 reactions.
 *
 * ## `pubkey` is a claim until you check it
 *
 * The envelope is not self-authenticating. `spec/02.md` §5 requires the
 * receiver to reject any envelope whose `pubkey` differs from the MLS
 * authenticated sender of the enclosing application message — which is why
 * [decode] demands that identity rather than offering it as an option. A
 * decoder that skipped it would let any member of a group post as any other,
 * with no signature anywhere to contradict them.
 */
data class CordnEnvelope(
    val id: HexKey,
    val pubKey: HexKey,
    val createdAt: Long,
    val kind: Int,
    val tags: Array<Array<String>>,
    val content: String,
) {
    /** The id these fields actually hash to, per NIP-01. */
    fun computedId(): HexKey = EventHasher.hashId(pubKey, createdAt, kind, tags, content)

    fun toJson(): String = Json.encodeToString(JsonObject.serializer(), toJsonObject())

    fun toJsonObject(): JsonObject =
        buildJsonObject {
            put(ID, id)
            put(PUBKEY, pubKey)
            put(CREATED_AT, createdAt)
            put(KIND, kind)
            put(
                TAGS,
                buildJsonArray {
                    tags.forEach { tag -> add(buildJsonArray { tag.forEach { add(JsonPrimitive(it)) } }) }
                },
            )
            put(CONTENT, content)
        }

    /** The UTF-8 bytes that go inside the MLS application message (`spec/02.md` §3). */
    fun encode(): ByteArray = toJson().encodeToByteArray()

    // Array fields, so the generated equals would compare references.
    override fun equals(other: Any?): Boolean =
        this === other ||
            (
                other is CordnEnvelope &&
                    id == other.id &&
                    pubKey == other.pubKey &&
                    createdAt == other.createdAt &&
                    kind == other.kind &&
                    content == other.content &&
                    tags.size == other.tags.size &&
                    tags.indices.all { tags[it].contentEquals(other.tags[it]) }
            )

    override fun hashCode(): Int = id.hashCode()

    companion object {
        private const val ID = "id"
        private const val PUBKEY = "pubkey"
        private const val CREATED_AT = "created_at"
        private const val KIND = "kind"
        private const val TAGS = "tags"
        private const val CONTENT = "content"
        private const val SIG = "sig"

        /** Builds an envelope, deriving its [id] from the rest (`spec/02.md` §4). */
        fun build(
            pubKey: HexKey,
            createdAt: Long,
            kind: Int,
            tags: Array<Array<String>> = emptyArray(),
            content: String,
        ) = CordnEnvelope(EventHasher.hashId(pubKey, createdAt, kind, tags, content), pubKey, createdAt, kind, tags, content)

        /**
         * Decodes an MLS application payload into an envelope.
         *
         * @param senderIdentity the account hex the MLS sender's credential
         *   binds, from [com.vitorpamplona.quartz.cordn.groups.CordnCredential]. Not
         *   optional: see the class KDoc.
         */
        fun decode(
            payload: ByteArray,
            senderIdentity: HexKey,
        ): CordnEnvelope {
            val json =
                try {
                    Json.parseToJsonElement(payload.decodeToString(throwOnInvalidSequence = true)) as? JsonObject
                        ?: throw IllegalArgumentException("cordn envelope must be a JSON object")
                } catch (e: CharacterCodingException) {
                    throw IllegalArgumentException("cordn envelope is not valid UTF-8", e)
                }

            require(SIG !in json) { "cordn envelope must not carry a `sig` (spec/02.md §2)" }

            val envelope =
                CordnEnvelope(
                    id = json.str(ID),
                    pubKey = json.str(PUBKEY),
                    createdAt =
                        json[CREATED_AT]?.jsonPrimitive?.long
                            ?: throw IllegalArgumentException("cordn envelope is missing `$CREATED_AT`"),
                    kind =
                        json[KIND]?.jsonPrimitive?.content?.toIntOrNull()
                            ?: throw IllegalArgumentException("cordn envelope is missing `$KIND`"),
                    tags = json.tagArray(),
                    content = json.str(CONTENT),
                )

            // Both checks are MUSTs, and each covers a different lie: the id
            // check catches a rewritten body, the pubkey check catches a member
            // posting under someone else's name.
            require(envelope.id == envelope.computedId()) {
                "cordn envelope id ${envelope.id} does not match its contents (${envelope.computedId()})"
            }
            require(envelope.pubKey == senderIdentity) {
                "cordn envelope claims pubkey ${envelope.pubKey} but the MLS sender is $senderIdentity"
            }
            return envelope
        }

        private fun JsonObject.str(key: String): String = this[key]?.jsonPrimitive?.content ?: throw IllegalArgumentException("cordn envelope is missing `$key`")

        private fun JsonObject.tagArray(): Array<Array<String>> {
            val tags = this[TAGS] as? JsonArray ?: throw IllegalArgumentException("cordn envelope is missing `$TAGS`")
            return Array(tags.size) { i ->
                val tag = tags[i] as? JsonArray ?: throw IllegalArgumentException("cordn envelope tag $i is not an array")
                Array(tag.size) { j -> tag[j].jsonPrimitive.content }
            }
        }
    }
}
