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
package com.vitorpamplona.quartz.nip05.namecoin.wallet

import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip05.namecoin.ElectrumxClient
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.security.SecureRandom

/**
 * Builds Namecoin name operation scripts and values.
 *
 * Namecoin extends Bitcoin with three name opcodes:
 * - **NAME_NEW** (OP_1): Pre-register a name by committing its hash.
 * - **NAME_FIRSTUPDATE** (OP_2): Reveal and register the name (12+ blocks after NAME_NEW).
 * - **NAME_UPDATE** (OP_3): Update the name's value or renew it.
 *
 * Each name operation produces a script output that combines the
 * name opcode + data with a standard P2PKH address script, allowing
 * the name to be "owned" by an address.
 */
object NmcNameScripts {
    // Namecoin-specific opcodes (repurposed from Bitcoin's OP_1/OP_2/OP_3)
    const val OP_NAME_NEW: Byte = 0x51
    const val OP_NAME_FIRSTUPDATE: Byte = 0x52
    const val OP_NAME_UPDATE: Byte = 0x53

    const val OP_2DROP: Byte = 0x6d
    const val OP_DROP: Byte = 0x75

    /** Namecoin consensus: names expire after 36,000 blocks (~250 days).
     *  Delegates to [ElectrumxClient.NAME_EXPIRE_DEPTH]. */
    val NAME_EXPIRE_DEPTH: Int get() = ElectrumxClient.NAME_EXPIRE_DEPTH

    /** Maximum name value size in bytes. */
    const val MAX_VALUE_SIZE = 520

    /** Registration cost in satoshis (0.01 NMC). */
    const val NAME_NEW_COST = 1_000_000L

    private val json =
        Json {
            ignoreUnknownKeys = true
            prettyPrint = false
        }
    private val NAME_REGEX_ID = Regex("^id/[a-z0-9]([a-z0-9 -]*[a-z0-9])?\$")
    private val NAME_REGEX_DOMAIN = Regex("^d/[a-z0-9]([a-z0-9-]*[a-z0-9])?\$")

    // ── Validation ─────────────────────────────────────────────────────

    fun isValidName(name: String): Boolean =
        when {
            name.startsWith("id/") -> NAME_REGEX_ID.matches(name) && name.length <= 255
            name.startsWith("d/") -> NAME_REGEX_DOMAIN.matches(name) && name.substringAfter("d/").length <= 63
            else -> false
        }

    fun isValueWithinLimit(value: String): Boolean = value.toByteArray(Charsets.UTF_8).size <= MAX_VALUE_SIZE

    // ── NAME_NEW ───────────────────────────────────────────────────────

    /**
     * Generate the salt and commitment for a NAME_NEW operation.
     *
     * The commitment hides the name being registered:
     *   commitment = RIPEMD160(SHA256(salt + name_bytes))
     *
     * @return [NameNewData] with salt (save it!) and commitment hash
     */
    fun prepareNameNew(name: String): NameNewData {
        require(isValidName(name)) { "Invalid Namecoin name: $name" }
        val salt = ByteArray(20).also { SecureRandom().nextBytes(it) }
        val nameBytes = name.toByteArray(Charsets.US_ASCII)
        val commitment = NmcKeyManager.hash160(salt + nameBytes)
        return NameNewData(name = name, salt = salt, commitment = commitment)
    }

    /**
     * Build the NAME_NEW output script.
     *
     * Script: OP_NAME_NEW <commitment_hash> OP_2DROP <P2PKH_script>
     *
     * @param commitment 20-byte commitment hash from [prepareNameNew]
     * @param ownerHash160 20-byte hash160 of the owner's public key
     */
    fun buildNameNewScript(
        commitment: ByteArray,
        ownerHash160: ByteArray,
    ): ByteArray {
        require(commitment.size == 20)
        return byteArrayOf(OP_NAME_NEW) +
            NmcTransactionBuilder.pushData(commitment) +
            byteArrayOf(OP_2DROP) +
            NmcTransactionBuilder.buildP2PKHScript(ownerHash160)
    }

    // ── NAME_FIRSTUPDATE ───────────────────────────────────────────────

    /**
     * Build the NAME_FIRSTUPDATE output script.
     *
     * Script: OP_NAME_FIRSTUPDATE <name> <salt> <value> OP_2DROP OP_2DROP <P2PKH_script>
     *
     * Must be broadcast ≥12 blocks after the NAME_NEW transaction.
     *
     * @param name The name being registered (e.g. "d/example")
     * @param salt The salt from [prepareNameNew] (must match the NAME_NEW commitment)
     * @param value The JSON value to set for the name
     * @param ownerHash160 20-byte hash160 of the owner's public key
     */
    fun buildNameFirstUpdateScript(
        name: String,
        salt: ByteArray,
        value: String,
        ownerHash160: ByteArray,
    ): ByteArray {
        require(isValueWithinLimit(value)) { "Value exceeds $MAX_VALUE_SIZE bytes" }
        return byteArrayOf(OP_NAME_FIRSTUPDATE) +
            NmcTransactionBuilder.pushData(name.toByteArray(Charsets.US_ASCII)) +
            NmcTransactionBuilder.pushData(salt) +
            NmcTransactionBuilder.pushData(value.toByteArray(Charsets.UTF_8)) +
            byteArrayOf(OP_2DROP, OP_2DROP) +
            NmcTransactionBuilder.buildP2PKHScript(ownerHash160)
    }

    // ── NAME_UPDATE ────────────────────────────────────────────────────

    /**
     * Build the NAME_UPDATE output script.
     *
     * Script: OP_NAME_UPDATE <name> <value> OP_2DROP OP_DROP <P2PKH_script>
     *
     * @param name The name being updated
     * @param value The new JSON value
     * @param ownerHash160 20-byte hash160 of the owner's public key
     */
    fun buildNameUpdateScript(
        name: String,
        value: String,
        ownerHash160: ByteArray,
    ): ByteArray {
        require(isValueWithinLimit(value)) { "Value exceeds $MAX_VALUE_SIZE bytes" }
        return byteArrayOf(OP_NAME_UPDATE) +
            NmcTransactionBuilder.pushData(name.toByteArray(Charsets.US_ASCII)) +
            NmcTransactionBuilder.pushData(value.toByteArray(Charsets.UTF_8)) +
            byteArrayOf(OP_2DROP, OP_DROP) +
            NmcTransactionBuilder.buildP2PKHScript(ownerHash160)
    }

    // ── Value JSON Builders ────────────────────────────────────────────

    /**
     * Build a d/ domain value with NIP-05-compatible Nostr data.
     */
    fun buildDomainValue(
        pubkeyHex: String,
        relays: List<String> = emptyList(),
        existingValue: String? = null,
    ): String {
        val base = parseOrEmpty(existingValue)
        val nostrObj =
            buildJsonObject {
                put("names", buildJsonObject { put("_", pubkeyHex.lowercase()) })
                if (relays.isNotEmpty()) {
                    put(
                        "relays",
                        buildJsonObject {
                            put(pubkeyHex.lowercase(), buildJsonArray { relays.forEach { add(JsonPrimitive(it)) } })
                        },
                    )
                }
            }
        base["nostr"] = nostrObj
        return json.encodeToString(JsonObject.serializer(), JsonObject(base))
    }

    /**
     * Build an id/ identity value with a Nostr pubkey.
     */
    fun buildIdentityValue(
        pubkeyHex: String,
        displayName: String? = null,
        relays: List<String> = emptyList(),
        existingValue: String? = null,
    ): String {
        val base = parseOrEmpty(existingValue)
        base["nostr"] =
            buildJsonObject {
                put("pubkey", pubkeyHex.lowercase())
                if (relays.isNotEmpty()) {
                    put("relays", buildJsonArray { relays.forEach { add(JsonPrimitive(it)) } })
                }
            }
        if (displayName != null && !base.containsKey("name")) {
            base["name"] = JsonPrimitive(displayName)
        }
        return json.encodeToString(JsonObject.serializer(), JsonObject(base))
    }

    /**
     * Update only the Nostr field in an existing value, preserving all other fields.
     */
    fun updateNostrInValue(
        existingValue: String,
        pubkeyHex: String,
        relays: List<String> = emptyList(),
    ): String {
        val parsed = parseOrEmpty(existingValue)
        return if (parsed.containsKey("names")) {
            buildDomainValue(pubkeyHex, relays, existingValue)
        } else {
            buildIdentityValue(pubkeyHex, relays = relays, existingValue = existingValue)
        }
    }

    private fun parseOrEmpty(value: String?): MutableMap<String, kotlinx.serialization.json.JsonElement> {
        if (value == null) return mutableMapOf()
        return try {
            json.parseToJsonElement(value).jsonObject.toMutableMap()
        } catch (_: Exception) {
            mutableMapOf()
        }
    }
}

/**
 * Data produced by [NmcNameScripts.prepareNameNew].
 * The [salt] MUST be saved — it is required for NAME_FIRSTUPDATE.
 */
data class NameNewData(
    val name: String,
    val salt: ByteArray,
    val commitment: ByteArray,
) {
    val saltHex: String get() = salt.toHexKey()
    val commitmentHex: String get() = commitment.toHexKey()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is NameNewData) return false
        return name == other.name && salt.contentEquals(other.salt)
    }

    override fun hashCode() = 31 * name.hashCode() + salt.contentHashCode()
}
