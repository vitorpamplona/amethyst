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
package com.vitorpamplona.quartz.buzz.rsReadState

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * The decrypted NIP-RS (Buzz cross-device read-state, `docs/nips/NIP-RS.md`) blob: a per-client
 * map of context id -> read-frontier unix timestamp (seconds).
 *
 * Field names are exact wire names ([clientId] is `client_id`). Per NIP-RS a client MUST ignore
 * blobs with an unknown [v]; [contexts] values are "all messages in this context at or before
 * this time are read." This is the plaintext that gets NIP-44 self-encrypted into an
 * [com.vitorpamplona.quartz.nip78AppData.AppSpecificDataEvent] `content` — see [ReadState].
 *
 * [contexts] is the **wire** map. It mixes two kinds of entry:
 *  - frontier entries, keyed by a context id escaped with [ReadStateKeys.escape] (an id that
 *    starts with the reserved `ov_` / `esc:` gets one `esc:` prepended) — read them with [frontiers];
 *  - the manual-unread override layer's `ov_s:` / `ov_c:` / `ov_b:` counters — read them with
 *    [overrides], build them with [build].
 *
 * [decodeFromJson] applies NIP-RS "Content Validation": the blob is rejected as a whole only for a
 * structural fault (not an object, missing/non-integer `v`, missing `client_id` or `contexts`, more
 * than [MAX_CONTEXT_ENTRIES] entries); a bad individual entry (a value outside `0..2^32-1`, a key
 * over [MAX_KEY_BYTES] bytes) is dropped and the rest is kept, and override counters are validated
 * as a complete per-context group before anything else touches them.
 */
@Serializable
data class ReadStateContent(
    val v: Int = CURRENT_VERSION,
    @SerialName("client_id") val clientId: String,
    val contexts: Map<String, Long> = emptyMap(),
) {
    fun encodeToJson(): String = JSON.encodeToString(this)

    /** NIP-RS validity: the schema version is the one this client understands and `client_id` is 1–64 chars. */
    fun isSupported(): Boolean = v == CURRENT_VERSION && clientId.length in 1..64

    /**
     * The read frontiers keyed by **raw** context id: escaped wire keys are unescaped, and the
     * reserved `ov_` override entries are left out.
     */
    fun frontiers(): Map<String, Long> {
        val result = LinkedHashMap<String, Long>()
        contexts.forEach { (key, value) ->
            if (!ReadStateKeys.isReservedWireKey(key)) result[ReadStateKeys.unescape(key)] = value
        }
        return result
    }

    /**
     * The manual-unread override registers keyed by raw context id. Every group that survived
     * [decodeFromJson] is complete — a live `ov_s`/`ov_c`/`ov_b` triple or a lone `ov_c` tombstone
     * floor — and every group has an `ov_c`, so that key anchors the lookup.
     */
    fun overrides(): Map<String, OverrideRegister> {
        val result = LinkedHashMap<String, OverrideRegister>()
        contexts.forEach { (key, clear) ->
            if (key.startsWith(ReadStateKeys.OV_CLEAR)) {
                val ctx = key.substring(ReadStateKeys.OV_CLEAR.length)
                val set = contexts[ReadStateKeys.OV_SET + ctx] ?: 0L
                val baseline = contexts[ReadStateKeys.OV_BASELINE + ctx] ?: 0L
                result[ctx] = OverrideRegister(set, clear, baseline)
            }
        }
        return result
    }

    companion object {
        const val CURRENT_VERSION = 1

        /** A blob with more context entries than this is rejected outright. */
        const val MAX_CONTEXT_ENTRIES = 10_000

        /** A context key longer than this many UTF-8 bytes is dropped (for an `ov_` key: its whole group). */
        const val MAX_KEY_BYTES = 256

        /** The largest timestamp / counter value: `2^32 - 1`. */
        const val MAX_VALUE = 4_294_967_295L

        val JSON =
            Json {
                ignoreUnknownKeys = true
                explicitNulls = false
                encodeDefaults = true
            }

        /**
         * Parses and validates a decrypted NIP-RS blob. Throws [IllegalArgumentException] (or a
         * serialization exception for malformed JSON) when the whole blob must be discarded. A blob
         * with an unknown [v] parses with empty [contexts] and fails [isSupported] — NIP-RS says to
         * ignore it, and its `contexts` may not even follow this schema.
         */
        fun decodeFromJson(json: String): ReadStateContent {
            val root = JSON.parseToJsonElement(json) as? JsonObject ?: throw IllegalArgumentException("NIP-RS blob is not a JSON object")

            val v = (root["v"] as? JsonPrimitive)?.takeIf { !it.isString }?.content?.toIntOrNull()
            requireNotNull(v) { "NIP-RS blob has a missing or non-integer v" }

            val clientId = (root["client_id"] as? JsonPrimitive)?.takeIf { it.isString }?.content
            requireNotNull(clientId) { "NIP-RS blob has no client_id string" }

            if (v != CURRENT_VERSION) return ReadStateContent(v, clientId, emptyMap())

            val contexts = root["contexts"] as? JsonObject ?: throw IllegalArgumentException("NIP-RS blob has no contexts object")
            require(contexts.size <= MAX_CONTEXT_ENTRIES) { "NIP-RS blob has more than $MAX_CONTEXT_ENTRIES context entries" }

            return ReadStateContent(v, clientId, sanitizeContexts(contexts))
        }

        /**
         * Builds a blob from raw context ids: [frontiers] are written under their escaped keys, and
         * each of [overrides] is canonicalized against its context's frontier first (NIP-RS
         * "Mandatory Canonical Publication") — a live register as its three keys, a dead one as a
         * lone `ov_c` tombstone floor, a virgin one not at all.
         */
        fun build(
            clientId: String,
            frontiers: Map<String, Long>,
            overrides: Map<String, OverrideRegister> = emptyMap(),
        ): ReadStateContent {
            val contexts = LinkedHashMap<String, Long>()
            frontiers.forEach { (ctx, ts) -> contexts[ReadStateKeys.escape(ctx)] = ts }
            overrides.forEach { (ctx, register) ->
                register.canonicalize(frontiers[ctx] ?: 0L)?.let { canonical ->
                    if (canonical.isTombstone()) {
                        contexts[ReadStateKeys.OV_CLEAR + ctx] = canonical.clear
                    } else {
                        contexts[ReadStateKeys.OV_SET + ctx] = canonical.set
                        contexts[ReadStateKeys.OV_CLEAR + ctx] = canonical.clear
                        contexts[ReadStateKeys.OV_BASELINE + ctx] = canonical.baseline
                    }
                }
            }
            return ReadStateContent(CURRENT_VERSION, clientId, contexts)
        }

        private fun sanitizeContexts(contexts: JsonObject): Map<String, Long> {
            val result = LinkedHashMap<String, Long>()
            // Override counters are validated as a group BEFORE any per-entry rule: NIP-RS forbids
            // dropping one bad sibling and keeping the rest (a partial group rebuilds a wrong register).
            val groups = LinkedHashMap<String, MutableMap<String, JsonElement>>()

            contexts.forEach { (key, value) ->
                val prefix = ReadStateKeys.overridePrefixOf(key)
                if (prefix != null) {
                    groups.getOrPut(key.substring(prefix.length)) { LinkedHashMap() }[prefix] = value
                } else {
                    val ts = value.asUInt32()
                    if (ts != null && key.encodeToByteArray().size <= MAX_KEY_BYTES) result[key] = ts
                }
            }

            groups.forEach { (ctx, members) ->
                val isLive = members.size == 3
                val isTombstone = members.size == 1 && ReadStateKeys.OV_CLEAR in members
                if (!isLive && !isTombstone) return@forEach

                val values = LinkedHashMap<String, Long>()
                members.forEach { (prefix, value) ->
                    val key = prefix + ctx
                    val counter = value.asUInt32() ?: return@forEach
                    if (key.encodeToByteArray().size > MAX_KEY_BYTES) return@forEach
                    values[key] = counter
                }
                // One invalid sibling rejects the whole group (its frontier entry is kept).
                if (values.size == members.size) result.putAll(values)
            }
            return result
        }

        /** An integer JSON number in `0..2^32-1`, or null (a string, a fraction, out of range, null…). */
        private fun JsonElement.asUInt32(): Long? {
            val primitive = this as? JsonPrimitive ?: return null
            if (primitive.isString) return null
            val value = primitive.content.toLongOrNull() ?: return null
            return value.takeIf { it in 0..MAX_VALUE }
        }
    }
}
