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
package com.vitorpamplona.quartz.nip05.namecoin

import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

/**
 * Result of resolving a Namecoin name to Nostr identity data.
 */
data class NamecoinNostrResult(
    /** Hex-encoded 32-byte Schnorr public key */
    val pubkey: String,
    /** Optional relay URLs where this user can be found */
    val relays: List<String> = emptyList(),
    /** The Namecoin name that was resolved (e.g. "d/example") */
    val namecoinName: String,
    /** The local-part that was matched (e.g. "alice" or "_") */
    val localPart: String = "_",
)

/**
 * Resolves Namecoin names to Nostr public keys.
 *
 * This is the primary entry point for Namecoin→Nostr resolution.
 * It is designed to be used alongside Amethyst's existing NIP-05
 * verifier: if an identifier ends with `.bit`, it should be routed
 * here instead of to the HTTP-based NIP-05 path.
 */
class NamecoinNameResolver(
    private val electrumxClient: ElectrumxClient = ElectrumxClient(),
    private val lookupTimeoutMs: Long = 20_000L,
) {
    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

    companion object {
        private val HEX_PUBKEY_REGEX = Regex("^[0-9a-fA-F]{64}$")

        /**
         * Check whether an identifier should be routed to Namecoin
         * resolution rather than standard NIP-05.
         */
        fun isNamecoinIdentifier(identifier: String): Boolean {
            val normalized = identifier.trim().lowercase()
            return normalized.endsWith(".bit") ||
                normalized.startsWith("d/") ||
                normalized.startsWith("id/")
        }
    }

    /**
     * Resolve a user-supplied identifier to a Nostr pubkey via Namecoin.
     *
     * @param identifier User input, e.g. "alice@example.bit", "id/alice", "example.bit"
     * @return [NamecoinNostrResult] on success, null if resolution failed
     */
    suspend fun resolve(identifier: String): NamecoinNostrResult? {
        val parsed = parseIdentifier(identifier) ?: return null
        return withTimeoutOrNull(lookupTimeoutMs) {
            performLookup(parsed)
        }
    }

    // ── Identifier Parsing ─────────────────────────────────────────────

    /**
     * Parsed representation of a Namecoin lookup request.
     */
    private data class ParsedIdentifier(
        /** The Namecoin name to query, e.g. "d/example" or "id/alice" */
        val namecoinName: String,
        /** The local-part to look up within the name's value.
         *  For d/ names: the user part (or "_" for root).
         *  For id/ names: always "_". */
        val localPart: String,
        /** Which namespace: DOMAIN or IDENTITY */
        val namespace: Namespace,
    )

    private enum class Namespace { DOMAIN, IDENTITY }

    /**
     * Parse a user-supplied string into a structured lookup request.
     *
     * Accepted formats:
     *   "alice@example.bit"  → d/example, localPart=alice
     *   "_@example.bit"      → d/example, localPart=_
     *   "example.bit"        → d/example, localPart=_
     *   "d/example"          → d/example, localPart=_
     *   "id/alice"           → id/alice,  localPart=_
     */
    private fun parseIdentifier(raw: String): ParsedIdentifier? {
        val input = raw.trim()

        // Direct namespace references
        if (input.startsWith("d/", ignoreCase = true)) {
            return ParsedIdentifier(
                namecoinName = input.lowercase(),
                localPart = "_",
                namespace = Namespace.DOMAIN,
            )
        }
        if (input.startsWith("id/", ignoreCase = true)) {
            return ParsedIdentifier(
                namecoinName = input.lowercase(),
                localPart = "_",
                namespace = Namespace.IDENTITY,
            )
        }

        // NIP-05 style: user@domain.bit
        if (input.contains("@") && input.endsWith(".bit", ignoreCase = true)) {
            val parts = input.split("@", limit = 2)
            if (parts.size != 2) return null
            val localPart = parts[0].lowercase().ifEmpty { "_" }
            val domain = parts[1].removeSuffix(".bit").lowercase()
            if (domain.isEmpty()) return null
            return ParsedIdentifier(
                namecoinName = "d/$domain",
                localPart = localPart,
                namespace = Namespace.DOMAIN,
            )
        }

        // Bare domain: example.bit
        if (input.endsWith(".bit", ignoreCase = true)) {
            val domain = input.removeSuffix(".bit").lowercase()
            if (domain.isEmpty()) return null
            return ParsedIdentifier(
                namecoinName = "d/$domain",
                localPart = "_",
                namespace = Namespace.DOMAIN,
            )
        }

        return null
    }

    // ── Lookup & Value Parsing ─────────────────────────────────────────

    private suspend fun performLookup(parsed: ParsedIdentifier): NamecoinNostrResult? {
        val nameResult = electrumxClient.nameShowWithFallback(parsed.namecoinName) ?: return null
        val valueJson = tryParseJson(nameResult.value) ?: return null

        return when (parsed.namespace) {
            Namespace.DOMAIN -> extractFromDomainValue(valueJson, parsed)
            Namespace.IDENTITY -> extractFromIdentityValue(valueJson, parsed)
        }
    }

    /**
     * Extract Nostr data from a `d/` domain value.
     *
     * Supports:
     *   { "nostr": "hex-pubkey" }                           → simple form
     *   { "nostr": { "names": { "alice": "hex" }, ... } }   → extended NIP-05-like form
     */
    private fun extractFromDomainValue(
        value: JsonObject,
        parsed: ParsedIdentifier,
    ): NamecoinNostrResult? {
        val nostrField = value["nostr"] ?: return null

        // Simple form: "nostr": "hex-pubkey"
        if (nostrField is JsonPrimitive && nostrField.isString) {
            val pubkey = nostrField.content
            if (parsed.localPart == "_" && isValidPubkey(pubkey)) {
                return NamecoinNostrResult(
                    pubkey = pubkey.lowercase(),
                    namecoinName = parsed.namecoinName,
                    localPart = "_",
                )
            }
            // Simple form only supports root — if a non-root local-part
            // was requested, we can't resolve it.
            if (parsed.localPart != "_") return null
        }

        // Extended form: "nostr": { "names": {...}, "relays": {...} }
        if (nostrField is JsonObject) {
            val names = nostrField["names"]?.jsonObject ?: return null
            val pubkeyElem = names[parsed.localPart] ?: names["_"] // fall back to root
            val pubkey = (pubkeyElem as? JsonPrimitive)?.content ?: return null
            if (!isValidPubkey(pubkey)) return null

            val relays = extractRelays(nostrField, pubkey)
            return NamecoinNostrResult(
                pubkey = pubkey.lowercase(),
                relays = relays,
                namecoinName = parsed.namecoinName,
                localPart = parsed.localPart,
            )
        }

        return null
    }

    /**
     * Extract Nostr data from an `id/` identity value.
     *
     * The id/ namespace stores general identity data.  We look for:
     *   { "nostr": "hex-pubkey" }
     *   { "nostr": { "pubkey": "hex", "relays": [...] } }
     */
    private fun extractFromIdentityValue(
        value: JsonObject,
        parsed: ParsedIdentifier,
    ): NamecoinNostrResult? {
        val nostrField = value["nostr"] ?: return null

        // Simple: "nostr": "hex-pubkey"
        if (nostrField is JsonPrimitive && nostrField.isString) {
            val pubkey = nostrField.content
            if (isValidPubkey(pubkey)) {
                return NamecoinNostrResult(
                    pubkey = pubkey.lowercase(),
                    namecoinName = parsed.namecoinName,
                )
            }
        }

        // Object form: "nostr": { "pubkey": "hex", "relays": [...] }
        if (nostrField is JsonObject) {
            // Try "pubkey" field
            val pubkey = (nostrField["pubkey"] as? JsonPrimitive)?.content
            if (pubkey != null && isValidPubkey(pubkey)) {
                val relays =
                    try {
                        nostrField["relays"]?.jsonArray?.mapNotNull {
                            (it as? JsonPrimitive)?.content
                        } ?: emptyList()
                    } catch (_: Exception) {
                        emptyList()
                    }

                return NamecoinNostrResult(
                    pubkey = pubkey.lowercase(),
                    relays = relays,
                    namecoinName = parsed.namecoinName,
                )
            }

            // Also try NIP-05-like "names" structure for id/ names
            val names = nostrField["names"]?.jsonObject
            if (names != null) {
                val rootPubkey = (names["_"] as? JsonPrimitive)?.content
                if (rootPubkey != null && isValidPubkey(rootPubkey)) {
                    val relays = extractRelays(nostrField, rootPubkey)
                    return NamecoinNostrResult(
                        pubkey = rootPubkey.lowercase(),
                        relays = relays,
                        namecoinName = parsed.namecoinName,
                    )
                }
            }
        }

        return null
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    private fun extractRelays(
        nostrObj: JsonObject,
        pubkey: String,
    ): List<String> {
        return try {
            val relaysMap = nostrObj["relays"]?.jsonObject ?: return emptyList()
            val relayArray =
                relaysMap[pubkey.lowercase()]?.jsonArray
                    ?: relaysMap[pubkey]?.jsonArray
                    ?: return emptyList()
            relayArray.mapNotNull { (it as? JsonPrimitive)?.content }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun tryParseJson(raw: String): JsonObject? =
        try {
            json.parseToJsonElement(raw).jsonObject
        } catch (_: Exception) {
            null
        }

    private fun isValidPubkey(s: String): Boolean = HEX_PUBKEY_REGEX.matches(s)
}
