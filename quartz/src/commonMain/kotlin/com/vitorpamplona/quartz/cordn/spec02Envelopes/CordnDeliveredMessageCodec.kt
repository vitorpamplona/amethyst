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

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put

/**
 * The on-disk form of one delivered message.
 *
 * ## Why the plaintext is what gets stored
 *
 * A cordn payload is sealed twice — the MLS application message, then
 * [com.vitorpamplona.quartz.cordn.spec03Payloads.SealedPayload] under an
 * exporter key — and both keys are derived per epoch. Once the group ratchets
 * forward, the ciphertext the coordinator still holds is unreadable to us, and
 * the cursor has in any case advanced past it. Ingestion is the only moment the
 * message is in the clear, so this is not a cache in front of a source of
 * truth: it is the only copy.
 *
 * ## Why the cursor rides along
 *
 * A room orders on the cursor and the unread divider compares against it, and
 * it is not derivable from the envelope — the envelope's `created_at` is the
 * sender's clock, which is a claim, while the cursor is the coordinator's
 * sequence. Storing the envelope alone would lose the ordering the room is
 * built on.
 */
object CordnDeliveredMessageCodec {
    const val VERSION = 1

    private const val V = "v"
    private const val CURSOR = "c"
    private const val ENVELOPE = "e"

    fun encode(message: CordnDeliveredMessage): String =
        Json.encodeToString(
            JsonObject.serializer(),
            buildJsonObject {
                put(V, VERSION)
                put(CURSOR, message.cursor)
                put(ENVELOPE, message.envelope.toJsonObject())
            },
        )

    /**
     * Reads one entry back.
     *
     * Throws on anything malformed rather than returning null, so a caller
     * reading a log decides for itself whether one bad entry drops the line or
     * the conversation. [decodeOrNull] is the "drop the line" form.
     */
    fun decode(entry: String): CordnDeliveredMessage {
        val json =
            Json.parseToJsonElement(entry) as? JsonObject
                ?: throw IllegalArgumentException("a stored cordn message must be a JSON object")

        val version =
            json[V]?.jsonPrimitive?.content?.toIntOrNull()
                ?: throw IllegalArgumentException("a stored cordn message is missing `$V`")
        require(version == VERSION) { "unknown stored cordn message version: $version" }

        val cursor =
            json[CURSOR]?.jsonPrimitive?.long
                ?: throw IllegalArgumentException("a stored cordn message is missing `$CURSOR`")

        val envelope =
            json[ENVELOPE]?.jsonObject
                ?: throw IllegalArgumentException("a stored cordn message is missing `$ENVELOPE`")

        return CordnDeliveredMessage(CordnEnvelope.fromJsonObject(envelope), cursor)
    }

    /**
     * [decode], or null when the entry cannot be read.
     *
     * Loading a room uses this: one corrupted entry — a half-written segment, a
     * format from a version that did not ship — should cost that message, not
     * every message behind it in the log.
     */
    fun decodeOrNull(entry: String): CordnDeliveredMessage? =
        try {
            decode(entry)
        } catch (e: Exception) {
            null
        }
}
