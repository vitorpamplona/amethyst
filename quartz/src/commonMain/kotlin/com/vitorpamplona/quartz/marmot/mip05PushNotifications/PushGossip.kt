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
package com.vitorpamplona.quartz.marmot.mip05PushNotifications

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * The `content` JSON of the push gossip app events — kinds `447`, `448` and
 * `449` (`features/push-notifications.md`, "Token gossip event shapes").
 *
 * ## Everything here is advisory
 *
 * Push draws a hard line: a malformed entry, an unverifiable `owner_sig`, a
 * removal that matches nothing, an array that lost an ordering race — all of it
 * is dropped datum by datum, and NONE of it may reject the group message that
 * carried it or touch group state. So the decoders below return the entries
 * they could read and silently discard the rest, rather than throwing.
 *
 * The one array-wide rule is the 32-entry cap: an array longer than that is
 * treated as invalid *in its entirety*, before any signature is verified, so an
 * oversized array cannot make a recipient do unbounded verification work.
 */
object PushGossip {
    /** The only `v` any of these events may carry. A different value is rejected outright. */
    const val VERSION = "marmot-push-v1"

    const val MAX_ENTRIES = 32

    /**
     * How far ahead of local wall clock an `owner_ts` may be: one hour.
     *
     * The bound exists because `owner_ts` is a latest-wins high-water mark. A
     * far-future stamp would otherwise pin a record permanently, and no honest
     * clock skew reaches an hour.
     */
    const val OWNER_TS_MAX_FUTURE_MILLIS = 3_600_000L

    private val parser = Json { ignoreUnknownKeys = true }

    // ---------------------------------------------------------------- encode

    fun encodeTokens(entries: List<PushTokenEntry>): String =
        buildJsonObject {
            put("v", VERSION)
            put(
                "tokens",
                buildJsonArray {
                    entries.forEach { add(encodeToken(it)) }
                },
            )
        }.toString()

    fun encodeRemovals(entries: List<PushRemovalEntry>): String =
        buildJsonObject {
            put("v", VERSION)
            put(
                "removals",
                buildJsonArray {
                    entries.forEach { add(encodeRemoval(it)) }
                },
            )
        }.toString()

    /** A kind `447` with no entries: "share your records with me". */
    fun encodeRequest(): String = encodeTokens(emptyList())

    private fun encodeToken(entry: PushTokenEntry): JsonObject =
        buildJsonObject {
            put("member_id_hex", entry.memberIdHex)
            put("leaf_index", entry.leafIndex)
            put("platform", entry.platform.wireName)
            put("token_fingerprint", entry.tokenFingerprint)
            put("server_pubkey_hex", entry.serverPubKeyHex)
            // An absent hint is omitted rather than written as "", matching what
            // the owner proof signed over.
            if (entry.relayHint.isNotEmpty()) put("relay_hint", entry.relayHint)
            put("encrypted_token", entry.encryptedTokenBase64)
            put("owner_ts", entry.ownerTsMillis)
            put("owner_sig", PushHex.of(entry.ownerSig))
        }

    private fun encodeRemoval(entry: PushRemovalEntry): JsonObject =
        buildJsonObject {
            put("member_id_hex", entry.memberIdHex)
            put("leaf_index", entry.leafIndex)
            put("platform", entry.platform.wireName)
            put("token_fingerprint", entry.tokenFingerprint)
            put("server_pubkey_hex", entry.serverPubKeyHex)
            put("owner_ts", entry.ownerTsMillis)
            put("owner_sig", PushHex.of(entry.ownerSig))
        }

    // ---------------------------------------------------------------- decode

    /**
     * Read a kind `447`/`448` content.
     *
     * An empty list means either "a request" or "nothing survived validation" —
     * deliberately the same outcome, because both change no state.
     */
    fun decodeTokens(content: String): List<PushTokenEntry> = decodeArray(content, "tokens")?.mapNotNull { decodeToken(it) } ?: emptyList()

    fun decodeRemovals(content: String): List<PushRemovalEntry> = decodeArray(content, "removals")?.mapNotNull { decodeRemoval(it) } ?: emptyList()

    /** True when the content is a well-formed `marmot-push-v1` object at all. */
    fun isSupportedVersion(content: String): Boolean = root(content) != null

    private fun root(content: String): JsonObject? {
        val obj =
            try {
                parser.parseToJsonElement(content) as? JsonObject
            } catch (_: Exception) {
                null
            } ?: return null
        val version = (obj["v"] as? JsonPrimitive)?.takeIf { it.isString }?.content
        return if (version == VERSION) obj else null
    }

    private fun decodeArray(
        content: String,
        member: String,
    ): List<JsonObject>? {
        val obj = root(content) ?: return null
        // A missing member reads as an empty array; a present non-array member
        // is invalid and contributes nothing. Both are "no entries".
        val array = obj[member] ?: return emptyList()
        val entries = array as? JsonArray ?: return null
        if (entries.size > MAX_ENTRIES) return null
        return entries.mapNotNull { it as? JsonObject }
    }

    private fun decodeToken(obj: JsonObject): PushTokenEntry? {
        val common = decodeCommon(obj) ?: return null
        val encryptedToken =
            PushBase64
                .decodeOrNull(obj.stringOrNull("encrypted_token") ?: return null)
                ?.takeIf { it.size == PushSignedRecord.ENCRYPTED_TOKEN_BYTES } ?: return null
        return PushTokenEntry(
            memberIdHex = common.memberIdHex,
            leafIndex = common.leafIndex,
            platform = common.platform,
            tokenFingerprint = common.tokenFingerprint,
            serverPubKeyHex = common.serverPubKeyHex,
            relayHint = PushSignedRecord.normalizeRelayHint(obj.stringOrNull("relay_hint")),
            encryptedToken = encryptedToken,
            ownerTsMillis = common.ownerTsMillis,
            ownerSig = common.ownerSig,
        )
    }

    private fun decodeRemoval(obj: JsonObject): PushRemovalEntry? {
        val common = decodeCommon(obj) ?: return null
        return PushRemovalEntry(
            memberIdHex = common.memberIdHex,
            leafIndex = common.leafIndex,
            platform = common.platform,
            tokenFingerprint = common.tokenFingerprint,
            serverPubKeyHex = common.serverPubKeyHex,
            ownerTsMillis = common.ownerTsMillis,
            ownerSig = common.ownerSig,
        )
    }

    private class Common(
        val memberIdHex: String,
        val leafIndex: Int,
        val platform: PushPlatform,
        val tokenFingerprint: String,
        val serverPubKeyHex: String,
        val ownerTsMillis: Long,
        val ownerSig: ByteArray,
    )

    /** The six members a token entry and a removal entry encode identically. */
    private fun decodeCommon(obj: JsonObject): Common? {
        val memberIdHex = obj.stringOrNull("member_id_hex")?.takeIf { PushHex.isLower(it, 64) } ?: return null
        val serverPubKeyHex = obj.stringOrNull("server_pubkey_hex")?.takeIf { PushHex.isLower(it, 64) } ?: return null
        val leafIndex = obj.longOrNull("leaf_index")?.takeIf { it in 0..Int.MAX_VALUE.toLong() }?.toInt() ?: return null
        val platform = obj.stringOrNull("platform")?.let { PushPlatform.fromWireName(it) } ?: return null
        val fingerprint = obj.stringOrNull("token_fingerprint") ?: return null
        if (PushSignedRecord.fingerprintBytes(fingerprint) == null) return null
        val ownerTs = obj.longOrNull("owner_ts")?.takeIf { it >= 0 } ?: return null
        val ownerSigHex = obj.stringOrNull("owner_sig")?.takeIf { PushHex.isLower(it, 128) } ?: return null
        return Common(memberIdHex, leafIndex, platform, fingerprint, serverPubKeyHex, ownerTs, PushHex.bytes(ownerSigHex))
    }

    private fun JsonObject.stringOrNull(name: String): String? = (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content

    /**
     * A JSON *number*, not a numeric string. `"leaf_index": "3"` is a different
     * document from `"leaf_index": 3`, and only the latter is what the spec
     * shows — accepting both would let two senders produce entries that agree
     * on meaning and disagree on the digest.
     */
    private fun JsonObject.longOrNull(name: String): Long? {
        val primitive = this[name] as? JsonPrimitive ?: return null
        if (primitive.isString) return null
        return primitive.jsonPrimitive.content.toLongOrNull()
    }
}
