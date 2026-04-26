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
package com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin

import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

/** Detailed outcome of a Namecoin resolution attempt. */
sealed class NamecoinResolveOutcome {
    data class Success(
        val result: NamecoinNostrResult,
    ) : NamecoinResolveOutcome()

    /** The name does not exist on the Namecoin blockchain. */
    data class NameNotFound(
        val name: String,
    ) : NamecoinResolveOutcome()

    /** The name exists but has no valid "nostr" field in its value. */
    data class NoNostrField(
        val name: String,
    ) : NamecoinResolveOutcome()

    /** All ElectrumX servers were unreachable. */
    data class ServersUnreachable(
        val message: String,
    ) : NamecoinResolveOutcome()

    /** The identifier could not be parsed as a Namecoin name. */
    data class InvalidIdentifier(
        val identifier: String,
    ) : NamecoinResolveOutcome()

    /** Timed out waiting for a response. */
    data object Timeout : NamecoinResolveOutcome()
}

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
    private val electrumxClient: IElectrumXClient,
    private val lookupTimeoutMs: Long = 20_000L,
    private val serverListProvider: () -> List<ElectrumxServer> = { DEFAULT_ELECTRUMX_SERVERS },
) {
    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

    companion object {
        private val HEX_PUBKEY_REGEX = Regex("^[0-9a-fA-F]{64}$")

        private val SHARED_JSON =
            Json {
                ignoreUnknownKeys = true
                isLenient = true
            }

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

        /**
         * Map a user-supplied identifier to the Namecoin key the resolver
         * would query (e.g. `example.bit` → `d/example`, `id/alice` → `id/alice`).
         *
         * Returns null for unparseable inputs. Multi-label `.bit` names are
         * not supported by the Namecoin spec and return null.
         */
        fun toNamecoinName(identifier: String): String? = parseIdentifierFlat(identifier)?.namecoinName

        /**
         * Pure (no-IO) parser shared by the instance lookup path and external
         * callers like the `.bit` relay resolver.
         */
        fun parseIdentifierFlat(raw: String): ParsedIdentifierFlat? {
            val input = raw.trim()

            if (input.startsWith("d/", ignoreCase = true)) {
                return ParsedIdentifierFlat(input.lowercase(), "_", isIdentityNamespace = false)
            }
            if (input.startsWith("id/", ignoreCase = true)) {
                return ParsedIdentifierFlat(input.lowercase(), "_", isIdentityNamespace = true)
            }
            // Lowercase early so `.bit` suffix-strip is safe regardless of original casing.
            val lower = input.lowercase()
            if (lower.contains("@") && lower.endsWith(".bit")) {
                val parts = lower.split("@", limit = 2)
                if (parts.size != 2) return null
                val localPart = parts[0].ifEmpty { "_" }
                val domain = parts[1].removeSuffix(".bit")
                if (domain.isEmpty()) return null
                return ParsedIdentifierFlat("d/$domain", localPart, isIdentityNamespace = false)
            }
            if (lower.endsWith(".bit")) {
                val domain = lower.removeSuffix(".bit")
                if (domain.isEmpty()) return null
                return ParsedIdentifierFlat("d/$domain", "_", isIdentityNamespace = false)
            }
            return null
        }

        /**
         * Parse a Namecoin `d/<name>` value JSON for any Nostr relay URLs.
         *
         * Recognises (in priority order):
         *   1. `relay` (string)
         *   2. `relays` (array of strings)
         *   3. `nostr.relay` (string)
         *   4. `nostr.relays` (array of strings)
         *   5. `nostr.relays[<pubkey>]` (array of strings keyed by pubkey)
         *
         * URLs are de-duplicated, returned in priority order, and only those
         * with a `ws://` or `wss://` scheme are kept.
         */
        fun parseRelayUrls(rawValueJson: String): List<String> =
            try {
                collectRelayUrls(SHARED_JSON.parseToJsonElement(rawValueJson).jsonObject)
            } catch (_: Exception) {
                emptyList()
            }

        private fun collectRelayUrls(obj: JsonObject): List<String> {
            val out = mutableListOf<String>()

            pushWsString(obj["relay"], out)
            pushWsArray(obj["relays"], out)

            val nostr = obj["nostr"] as? JsonObject
            if (nostr != null) {
                pushWsString(nostr["relay"], out)
                pushWsArray(nostr["relays"], out)

                // pubkey-keyed shape: nostr.relays[<pubkey>] = ["wss://..."]
                val pubkeyKeyed = nostr["relays"] as? JsonObject
                if (pubkeyKeyed != null) {
                    for ((_, value) in pubkeyKeyed.entries) pushWsArray(value, out)
                }
            }
            return out.distinct()
        }

        private fun pushWsString(
            value: JsonElement?,
            out: MutableList<String>,
        ) {
            if (value is JsonPrimitive && value.isString) {
                val trimmed = value.content.trim()
                // Reuse the canonical Quartz scheme test instead of a local one.
                if (RelayUrlNormalizer.isRelayUrl(trimmed)) {
                    out += trimmed
                }
            }
        }

        private fun pushWsArray(
            value: JsonElement?,
            out: MutableList<String>,
        ) {
            try {
                value?.jsonArray?.forEach { pushWsString(it, out) }
            } catch (_: Exception) {
                // not an array; ignore
            }
        }
    }

    /** Flat parsed identifier surface used by [toNamecoinName] and the instance parser. */
    data class ParsedIdentifierFlat(
        val namecoinName: String,
        val localPart: String,
        val isIdentityNamespace: Boolean,
    )

    /**
     * Resolve a user-supplied identifier to a Nostr pubkey via Namecoin.
     *
     * @param identifier User input, e.g. "alice@example.bit", "id/alice", "example.bit"
     * @return [NamecoinNostrResult] on success, null if resolution failed.
     *
     * Implemented as a thin wrapper over [resolveDetailed] so the
     * lookup/timeout/exception logic lives in exactly one place.
     */
    suspend fun resolve(identifier: String): NamecoinNostrResult? = (resolveDetailed(identifier) as? NamecoinResolveOutcome.Success)?.result

    /**
     * Resolve with detailed outcome for error reporting in UI flows.
     *
     * The timeout is applied inside [lookupNameDetailed]; no outer
     * `withTimeoutOrNull` wrapper is needed here.
     */
    suspend fun resolveDetailed(identifier: String): NamecoinResolveOutcome {
        val parsed =
            parseIdentifier(identifier)
                ?: return NamecoinResolveOutcome.InvalidIdentifier(identifier)
        return performLookupDetailed(parsed)
    }

    /** Outcome of a low-level `name_show` lookup. */
    sealed class NameLookupOutcome {
        data class Found(
            val result: NameShowResult,
        ) : NameLookupOutcome()

        data class NotFound(
            val name: String,
        ) : NameLookupOutcome()

        data class ServersUnreachable(
            val message: String,
        ) : NameLookupOutcome()

        data object Timeout : NameLookupOutcome()
    }

    /**
     * Run a `name_show` lookup with the standard timeout + exception
     * translation used throughout this package.
     *
     * Both [resolveDetailed] (NIP-05 identity path) and
     * [BitRelayResolver.resolveRaw] (`.bit` relay path) call this helper
     * so the timeout / `NameNotFound` / `NameExpired` / `ServersUnreachable`
     * mapping is implemented exactly once.
     */
    suspend fun lookupNameDetailed(namecoinName: String): NameLookupOutcome {
        val result =
            withTimeoutOrNull(lookupTimeoutMs) {
                runCatching {
                    electrumxClient.nameShowWithFallback(namecoinName, serverListProvider())
                }
            } ?: return NameLookupOutcome.Timeout

        result.exceptionOrNull()?.let { e ->
            return when (e) {
                is NamecoinLookupException.NameNotFound -> {
                    NameLookupOutcome.NotFound(namecoinName)
                }

                is NamecoinLookupException.NameExpired -> {
                    NameLookupOutcome.NotFound(namecoinName)
                }

                is NamecoinLookupException.ServersUnreachable -> {
                    NameLookupOutcome.ServersUnreachable(
                        e.message ?: "All ElectrumX servers unreachable",
                    )
                }

                else -> {
                    NameLookupOutcome.ServersUnreachable(e.message ?: "Lookup failed")
                }
            }
        }

        val nameShow = result.getOrNull() ?: return NameLookupOutcome.NotFound(namecoinName)
        return NameLookupOutcome.Found(nameShow)
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
     *
     * Implementation delegates to the static [parseIdentifierFlat] so external
     * callers (e.g. the `.bit` relay resolver) use the exact same parser.
     */
    private fun parseIdentifier(raw: String): ParsedIdentifier? {
        val flat = parseIdentifierFlat(raw) ?: return null
        return ParsedIdentifier(
            namecoinName = flat.namecoinName,
            localPart = flat.localPart,
            namespace = if (flat.isIdentityNamespace) Namespace.IDENTITY else Namespace.DOMAIN,
        )
    }

    // ── Lookup & Value Parsing ─────────────────────────────────────────

    private suspend fun performLookupDetailed(parsed: ParsedIdentifier): NamecoinResolveOutcome {
        val nameResult =
            when (val outcome = lookupNameDetailed(parsed.namecoinName)) {
                is NameLookupOutcome.Found -> {
                    outcome.result
                }

                is NameLookupOutcome.NotFound -> {
                    return NamecoinResolveOutcome.NameNotFound(outcome.name)
                }

                is NameLookupOutcome.ServersUnreachable -> {
                    return NamecoinResolveOutcome.ServersUnreachable(outcome.message)
                }

                NameLookupOutcome.Timeout -> {
                    return NamecoinResolveOutcome.Timeout
                }
            }

        val valueJson =
            tryParseJson(nameResult.value)
                ?: return NamecoinResolveOutcome.NoNostrField(parsed.namecoinName)

        val nostrResult =
            when (parsed.namespace) {
                Namespace.DOMAIN -> extractFromDomainValue(valueJson, parsed)
                Namespace.IDENTITY -> extractFromIdentityValue(valueJson, parsed)
            }

        return if (nostrResult != null) {
            NamecoinResolveOutcome.Success(nostrResult)
        } else {
            NamecoinResolveOutcome.NoNostrField(parsed.namecoinName)
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

            // Resolve: exact match → "_" root → first entry (root lookups only)
            val resolvedLocalPart: String
            val pubkey: String

            val exactMatch = names[parsed.localPart]
            val rootMatch = names["_"]
            val firstEntry = if (parsed.localPart == "_") names.entries.firstOrNull() else null

            when {
                exactMatch is JsonPrimitive && isValidPubkey(exactMatch.content) -> {
                    resolvedLocalPart = parsed.localPart
                    pubkey = exactMatch.content
                }

                rootMatch is JsonPrimitive && isValidPubkey(rootMatch.content) -> {
                    resolvedLocalPart = "_"
                    pubkey = rootMatch.content
                }

                firstEntry != null &&
                    firstEntry.value is JsonPrimitive &&
                    isValidPubkey((firstEntry.value as JsonPrimitive).content) -> {
                    resolvedLocalPart = firstEntry.key
                    pubkey = (firstEntry.value as JsonPrimitive).content
                }

                else -> {
                    return null
                }
            }

            val relays = extractRelays(nostrField, pubkey)
            return NamecoinNostrResult(
                pubkey = pubkey.lowercase(),
                relays = relays,
                namecoinName = parsed.namecoinName,
                localPart = resolvedLocalPart,
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
