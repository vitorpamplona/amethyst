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

import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
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

    /**
     * The name exists but its value is not parseable as a Namecoin Domain
     * Name Object (i.e. valid JSON of the right shape). Distinct from
     * [NoNostrField], which means valid JSON but no `nostr` data.
     *
     * `error` is the underlying parser message (kotlinx.serialization);
     * useful for surfacing back to the publisher of the broken record
     * (e.g. "Unfinished JSON term at EOF at line 1, column 474").
     */
    data class MalformedRecord(
        val name: String,
        val error: String,
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

    /**
     * Resolve with detailed outcome for error reporting in UI flows.
     */
    suspend fun resolveDetailed(identifier: String): NamecoinResolveOutcome {
        val parsed =
            parseIdentifier(identifier)
                ?: return NamecoinResolveOutcome.InvalidIdentifier(identifier)
        val result =
            withTimeoutOrNull(lookupTimeoutMs) { performLookupDetailed(parsed) }
        return result ?: NamecoinResolveOutcome.Timeout
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
        val nameResult = electrumxClient.nameShowWithFallback(parsed.namecoinName, serverListProvider()) ?: return null
        val valueJson = tryParseJson(nameResult.value) ?: return null
        val merged = expandImportsIfPresent(valueJson)

        return when (parsed.namespace) {
            Namespace.DOMAIN -> extractFromDomainValue(merged, parsed)
            Namespace.IDENTITY -> extractFromIdentityValue(merged, parsed)
        }
    }

    private suspend fun performLookupDetailed(parsed: ParsedIdentifier): NamecoinResolveOutcome {
        val nameResult: NameShowResult
        try {
            nameResult =
                electrumxClient.nameShowWithFallback(parsed.namecoinName, serverListProvider())
                    ?: return NamecoinResolveOutcome.NameNotFound(parsed.namecoinName)
        } catch (e: NamecoinLookupException.NameNotFound) {
            return NamecoinResolveOutcome.NameNotFound(parsed.namecoinName)
        } catch (e: NamecoinLookupException.NameExpired) {
            return NamecoinResolveOutcome.NameNotFound(parsed.namecoinName)
        } catch (e: NamecoinLookupException.ServersUnreachable) {
            return NamecoinResolveOutcome.ServersUnreachable(
                e.message ?: "All ElectrumX servers unreachable",
            )
        }

        // Distinguish "valid JSON, no `nostr` field" from "value is
        // unparseable JSON". Both used to collapse into NoNostrField, which
        // sent operators chasing a missing field that wasn't actually the
        // problem (the Namecoin record itself was malformed). Surfacing
        // the parser's column number lets a publisher see WHERE the value
        // is bad without spelunking through the whole serialised form.
        val valueJson =
            parseValueOrError(nameResult.value)
                .fold(
                    onSuccess = { it },
                    onFailure = { err ->
                        return NamecoinResolveOutcome.MalformedRecord(
                            name = parsed.namecoinName,
                            error = err.message ?: "unparseable JSON value",
                        )
                    },
                )
        val merged = expandImportsIfPresent(valueJson)

        val nostrResult =
            when (parsed.namespace) {
                Namespace.DOMAIN -> extractFromDomainValue(merged, parsed)
                Namespace.IDENTITY -> extractFromIdentityValue(merged, parsed)
            }

        return if (nostrResult != null) {
            NamecoinResolveOutcome.Success(nostrResult)
        } else {
            NamecoinResolveOutcome.NoNostrField(parsed.namecoinName)
        }
    }

    /**
     * Expand any ifa-0001 `import` items in [root] into a single merged
     * object, fetching imported names through this resolver's ElectrumX
     * client. Records without an `import` key are returned unchanged with
     * zero extra I/O.
     *
     * Failures (name not found, malformed JSON, network errors) are
     * absorbed: the corresponding import contributes nothing and the
     * importing record's own items still apply. This keeps resolution
     * best-effort, in line with the rest of the namecoin path.
     */
    private suspend fun expandImportsIfPresent(root: JsonObject): JsonObject {
        if (!root.containsKey("import")) return root
        return NamecoinImportResolver.expandImports(root) { name ->
            try {
                electrumxClient.nameShowWithFallback(name, serverListProvider())?.value
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: NamecoinLookupException) {
                // Best-effort: missing/expired/unreachable → contribute nothing.
                null
            }
        }
    }

    /**
     * Extract Nostr data from a `d/` domain value.
     *
     * Supports:
     *   { "nostr": "hex-pubkey" }                           → simple-string form
     *   { "nostr": { "names": { "alice": "hex" }, ... } }   → extended NIP-05-like form
     *   { "nostr": { "pubkey": "hex", "relays": [...] } }   → single-identity form
     *
     * The single-identity form is the same shape used by `id/` records and is
     * the natural way to express "this one name = this one pubkey". It only
     * resolves the root local-part (`_`) — there are no sub-identities in
     * this shape. If `nostr.names` is also present, it wins for any
     * sub-identity; root-lookups fall back to the bare `pubkey` when
     * `names["_"]` is missing.
     */
    private fun extractFromDomainValue(
        value: JsonObject,
        parsed: ParsedIdentifier,
    ): NamecoinNostrResult? {
        val nostrField = value["nostr"] ?: return null

        // Simple-string form: "nostr": "hex-pubkey"
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

        if (nostrField is JsonObject) {
            // Extended NIP-05-like form first: "nostr": { "names": {...}, "relays": {...} }
            val names = nostrField["names"]?.jsonObject

            if (names != null) {
                val exactMatch = names[parsed.localPart]
                val rootMatch = names["_"]
                val firstEntry = if (parsed.localPart == "_") names.entries.firstOrNull() else null

                if (exactMatch is JsonPrimitive && isValidPubkey(exactMatch.content)) {
                    return NamecoinNostrResult(
                        pubkey = exactMatch.content.lowercase(),
                        relays = extractRelays(nostrField, exactMatch.content),
                        namecoinName = parsed.namecoinName,
                        localPart = parsed.localPart,
                    )
                }
                if (rootMatch is JsonPrimitive && isValidPubkey(rootMatch.content)) {
                    return NamecoinNostrResult(
                        pubkey = rootMatch.content.lowercase(),
                        relays = extractRelays(nostrField, rootMatch.content),
                        namecoinName = parsed.namecoinName,
                        localPart = "_",
                    )
                }
                if (firstEntry != null &&
                    firstEntry.value is JsonPrimitive &&
                    isValidPubkey((firstEntry.value as JsonPrimitive).content)
                ) {
                    val pk = (firstEntry.value as JsonPrimitive).content
                    return NamecoinNostrResult(
                        pubkey = pk.lowercase(),
                        relays = extractRelays(nostrField, pk),
                        namecoinName = parsed.namecoinName,
                        localPart = firstEntry.key,
                    )
                }
                // names was present but didn't yield a match. Fall through to
                // the single-identity check below — only meaningful for root
                // lookups (non-root requests against a names-only record
                // correctly stop here).
                if (parsed.localPart != "_") return null
            }

            // Single-identity form: "nostr": { "pubkey": "hex", "relays": [...] }
            // Only resolves the root local-part; non-root requests against a
            // single-identity record fall through to null so we don't hand
            // alice@example.bit the root operator's identity.
            if (parsed.localPart == "_") {
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
                        localPart = "_",
                    )
                }
            }
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

    /**
     * Parse [raw] into a [JsonObject], surfacing the underlying parser
     * error message instead of swallowing it. The result is used by
     * [performLookupDetailed] to distinguish [NamecoinResolveOutcome.MalformedRecord]
     * (the value itself is broken) from [NamecoinResolveOutcome.NoNostrField]
     * (valid JSON, just missing the `nostr` block).
     *
     * Returns [Result.failure] with the parser's diagnostic message; the
     * caller is expected to forward that text into a UI / log surface so
     * the publisher of the broken record can fix it.
     */
    private fun parseValueOrError(raw: String): Result<JsonObject> =
        runCatching {
            val element = json.parseToJsonElement(raw)
            element as? JsonObject
                ?: throw IllegalArgumentException(
                    "top-level value is ${element::class.simpleName}, expected JSON object",
                )
        }

    private fun isValidPubkey(s: String): Boolean = HEX_PUBKEY_REGEX.matches(s)
}
