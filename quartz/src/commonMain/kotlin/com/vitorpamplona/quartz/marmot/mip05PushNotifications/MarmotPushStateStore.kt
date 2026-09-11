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

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Durable push state for one group: which token records are active, the
 * high-water stamp of every record key, and which keys carry a tombstone.
 *
 * ## Why this needs a store of its own
 *
 * Because a tombstone that does not survive a restart is not a tombstone. Owner
 * authentication makes a record relay-portable, so any current member can
 * re-emit a revoked-but-still-signed record inside a fresh kind `448` at any
 * later epoch. The per-key stamp is the ONLY thing that recognises it as stale,
 * and it cannot be rebuilt by replaying retained app payloads — the relayed
 * record's carrying epoch is unbounded, while the retained window is not.
 *
 * The state holds no secret: encrypted tokens are already encrypted to a
 * notification server this client cannot read, and everything else is public
 * routing. An implementation MAY still encrypt at rest, but a store that cannot
 * is better than no store.
 */
interface MarmotPushStateStore {
    /** The stored blob for a group, or null when nothing is stored yet. */
    suspend fun load(nostrGroupId: HexKey): String?

    suspend fun save(
        nostrGroupId: HexKey,
        state: String,
    )

    suspend fun clear(nostrGroupId: HexKey)
}

/** Non-durable default. A restart forgets every tombstone, so a stale relay can win once. */
class InMemoryPushStateStore : MarmotPushStateStore {
    private val states = mutableMapOf<HexKey, String>()

    override suspend fun load(nostrGroupId: HexKey): String? = states[nostrGroupId]

    override suspend fun save(
        nostrGroupId: HexKey,
        state: String,
    ) {
        states[nostrGroupId] = state
    }

    override suspend fun clear(nostrGroupId: HexKey) {
        states.remove(nostrGroupId)
    }
}

/**
 * The on-disk shape of a [PushRecordStore].
 *
 * A local format, not a wire format: it is never sent anywhere, so it is free
 * to store the derived stamp beside each key rather than recompute it. That is
 * the point — the stamp of a tombstoned key has no record left to recompute it
 * from.
 */
object PushStateCodec {
    private val parser = Json { ignoreUnknownKeys = true }

    fun encode(store: PushRecordStore): String {
        val stamps = store.snapshotStamps()
        val tombstones = store.snapshotTombstones()
        return buildJsonObject {
            put("group_id", store.groupIdHex)
            put(
                "records",
                buildJsonArray {
                    store.active().forEach { add(recordJson(it)) }
                },
            )
            put(
                "stamps",
                buildJsonArray {
                    stamps.forEach { (key, stamp) ->
                        add(
                            buildJsonObject {
                                putKey(key)
                                put("owner_ts", stamp.ownerTsMillis)
                                put("digest", stamp.digestHex)
                                put("tombstone", key in tombstones)
                            },
                        )
                    }
                },
            )
        }.toString()
    }

    /** Restore [store] from [json]. A blob that cannot be read leaves the store untouched. */
    fun decodeInto(
        store: PushRecordStore,
        json: String,
    ): Boolean {
        val root =
            try {
                parser.parseToJsonElement(json) as? JsonObject
            } catch (_: Exception) {
                null
            } ?: return false

        val records =
            (root["records"] as? JsonArray)
                ?.mapNotNull { it as? JsonObject }
                ?.mapNotNull { readRecord(it) }
                .orEmpty()

        val stamps = mutableMapOf<PushRecordKey, PushRecordStamp>()
        val tombstones = mutableSetOf<PushRecordKey>()
        (root["stamps"] as? JsonArray)?.mapNotNull { it as? JsonObject }?.forEach { obj ->
            val key = readKey(obj) ?: return@forEach
            val ownerTs = obj.long("owner_ts") ?: return@forEach
            val digest = obj.str("digest") ?: return@forEach
            stamps[key] = PushRecordStamp(ownerTs, digest)
            if ((obj["tombstone"] as? JsonPrimitive)?.content == "true") tombstones.add(key)
        }

        store.restore(records, stamps, tombstones)
        return true
    }

    private fun recordJson(entry: PushTokenEntry): JsonObject =
        buildJsonObject {
            putKey(entry.key)
            put("token_fingerprint", entry.tokenFingerprint)
            put("relay_hint", entry.relayHint)
            put("encrypted_token", entry.encryptedTokenBase64)
            put("owner_ts", entry.ownerTsMillis)
            put("owner_sig", PushHex.of(entry.ownerSig))
        }

    private fun readRecord(obj: JsonObject): PushTokenEntry? {
        val key = readKey(obj) ?: return null
        val fingerprint = obj.str("token_fingerprint") ?: return null
        val token =
            PushBase64
                .decodeOrNull(obj.str("encrypted_token") ?: return null)
                ?.takeIf { it.size == PushSignedRecord.ENCRYPTED_TOKEN_BYTES } ?: return null
        val ownerTs = obj.long("owner_ts") ?: return null
        val sigHex = obj.str("owner_sig")?.takeIf { PushHex.isLower(it, 128) } ?: return null
        return PushTokenEntry(
            memberIdHex = key.memberIdHex,
            leafIndex = key.leafIndex,
            platform = key.platform,
            tokenFingerprint = fingerprint,
            serverPubKeyHex = key.serverPubKeyHex,
            relayHint = obj.str("relay_hint").orEmpty(),
            encryptedToken = token,
            ownerTsMillis = ownerTs,
            ownerSig = PushHex.bytes(sigHex),
        )
    }

    private fun readKey(obj: JsonObject): PushRecordKey? {
        val member = obj.str("member_id_hex")?.takeIf { PushHex.isLower(it, 64) } ?: return null
        val server = obj.str("server_pubkey_hex")?.takeIf { PushHex.isLower(it, 64) } ?: return null
        val leaf = obj.long("leaf_index")?.takeIf { it in 0..Int.MAX_VALUE.toLong() }?.toInt() ?: return null
        val platform = obj.str("platform")?.let { PushPlatform.fromWireName(it) } ?: return null
        return PushRecordKey(member, leaf, platform, server)
    }

    private fun kotlinx.serialization.json.JsonObjectBuilder.putKey(key: PushRecordKey) {
        put("member_id_hex", key.memberIdHex)
        put("leaf_index", key.leafIndex)
        put("platform", key.platform.wireName)
        put("server_pubkey_hex", key.serverPubKeyHex)
    }

    private fun JsonObject.str(name: String): String? = (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun JsonObject.long(name: String): Long? = (this[name] as? JsonPrimitive)?.takeIf { !it.isString }?.content?.toLongOrNull()
}
