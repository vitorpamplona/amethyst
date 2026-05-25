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
package com.vitorpamplona.quartz.nip60Cashu.p2pk

import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.utils.RandomInstance
import com.vitorpamplona.quartz.utils.Secp256k1Instance
import com.vitorpamplona.quartz.utils.sha256.sha256
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * NUT-11 Pay-to-Public-Key locked proofs.
 *
 *   secret = ["P2PK", { "nonce":"<hex>", "data":"<x-only pubkey hex>", "tags":[...] }]
 *
 * Witness format:
 *
 *   witness = { "signatures": ["<bip340_sig_hex>", …] }
 *
 * The signature is BIP-340 Schnorr over `sha256(secret_as_utf8_bytes)` using
 * the private key whose x-only pubkey matches the `data` field.
 *
 * For Amethyst's NIP-60 wallet this is used:
 *  - When MINTING locked proofs to send a NIP-61 nutzap (recipient's pubkey
 *    is the `data`, recipient signs the witness on redemption).
 *  - When REDEEMING an inbound nutzap (we sign with our wallet's P2PK
 *    private key before passing the proofs to /v1/swap).
 */
object P2PK {
    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

    /**
     * Build a Cashu P2PK secret JSON string locking the proof to [recipientPubKeyHex].
     *
     * Accepts either 32-byte x-only (64 hex chars — Nostr-style) or 33-byte
     * compressed (66 hex chars with 02/03 prefix — cashu/NIP-61 style). The
     * mint verifies using BIP-340 Schnorr which is x-only, so the parity byte
     * is informational only. A fresh 16-byte nonce is generated.
     */
    fun lockedSecret(recipientPubKeyHex: String): String {
        require(recipientPubKeyHex.length == 64 || recipientPubKeyHex.length == 66) {
            "Recipient pubkey must be 32-byte x-only (64 hex) or 33-byte compressed (66 hex)"
        }
        val nonce = RandomInstance.bytes(16).toHexKey()
        val body =
            buildJsonObject {
                put("nonce", nonce)
                put("data", recipientPubKeyHex)
                put("tags", JsonArray(emptyList()))
            }
        val secret =
            buildJsonArray {
                add(JsonPrimitive("P2PK"))
                add(body)
            }
        return json.encodeToString(JsonElement.serializer(), secret)
    }

    /**
     * Parse a Cashu P2PK secret string. Returns null if the string is not a
     * NUT-11 P2PK secret.
     */
    fun parseSecret(secret: String): ParsedP2pk? =
        try {
            val arr = json.parseToJsonElement(secret) as? JsonArray ?: return null
            if (arr.size < 2) return null
            val kind = (arr[0] as? JsonPrimitive)?.content
            if (kind != "P2PK") return null
            val body = arr[1] as? JsonObject ?: return null
            val data = (body["data"] as? JsonPrimitive)?.content ?: return null
            val nonce = (body["nonce"] as? JsonPrimitive)?.content
            ParsedP2pk(pubKeyHex = data, nonceHex = nonce)
        } catch (_: Exception) {
            null
        }

    /**
     * BIP-340 Schnorr signature over `sha256(secret_bytes)` used as the unlock
     * witness. Returns the witness JSON string ready to drop into a Cashu
     * proof's `witness` field.
     */
    fun signWitness(
        secret: String,
        privKeyHex: String,
    ): String {
        val priv = privKeyHex.hexToByteArray()
        require(priv.size == 32) { "Private key must be 32 bytes" }
        val digest = sha256(secret.encodeToByteArray())
        val sig = Secp256k1Instance.signSchnorr(digest, priv)
        return buildJsonObject {
            put(
                "signatures",
                buildJsonArray { add(JsonPrimitive(sig.toHexKey())) },
            )
        }.toString()
    }

    @Serializable
    data class ParsedP2pk(
        val pubKeyHex: String,
        val nonceHex: String? = null,
    )
}
