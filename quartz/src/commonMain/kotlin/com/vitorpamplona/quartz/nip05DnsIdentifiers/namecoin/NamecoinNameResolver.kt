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
import kotlinx.serialization.json.intOrNull
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
         *
         * Multi-label `.bit` inputs (e.g. `alice@relay.testls.bit`) are split
         * into the **single-label** registered Namecoin name (`d/testls`) and
         * a subdomain path (`["relay"]`) per ifa-0001. The instance lookup
         * walks the parent's `map` tree to find the effective Domain Name
         * Object before reading `nostr.names.<localPart>` from it; it does
         * NOT issue a separate `d/<sub>.<parent>` query, because that form
         * is non-spec and would never resolve.
         *
         * For `d/<x>` and `id/<x>` literal inputs, no splitting is performed
         * — those forms target the literal name as written.
         */
        fun parseIdentifierFlat(raw: String): ParsedIdentifierFlat? {
            val input = raw.trim()

            if (input.startsWith("d/", ignoreCase = true)) {
                return ParsedIdentifierFlat(
                    namecoinName = input.lowercase(),
                    localPart = "_",
                    subdomainLabels = emptyList(),
                    isIdentityNamespace = false,
                )
            }
            if (input.startsWith("id/", ignoreCase = true)) {
                return ParsedIdentifierFlat(
                    namecoinName = input.lowercase(),
                    localPart = "_",
                    subdomainLabels = emptyList(),
                    isIdentityNamespace = true,
                )
            }
            // Lowercase early so `.bit` suffix-strip is safe regardless of original casing.
            val lower = input.lowercase()
            val (localPart, hostPart) =
                if (lower.contains("@") && lower.endsWith(".bit")) {
                    val parts = lower.split("@", limit = 2)
                    if (parts.size != 2) return null
                    val lp = parts[0].ifEmpty { "_" }
                    lp to parts[1]
                } else if (lower.endsWith(".bit")) {
                    "_" to lower
                } else {
                    return null
                }
            // Reuse parseHostFlat so multi-label .bit hosts split exactly the
            // same way they do on the relay path: registered single-label
            // parent + subdomain labels.
            val host = parseHostFlat(hostPart) ?: return null
            return ParsedIdentifierFlat(
                namecoinName = host.namecoinName,
                localPart = localPart,
                subdomainLabels = host.subdomainLabels,
                isIdentityNamespace = false,
            )
        }

        /**
         * Split a `.bit` host into the registered Namecoin name and the
         * subdomain path beneath it.
         *
         * The Namecoin `d/` namespace is single-label per ifa-0001
         * (`d/example`, not `d/foo.example`). Multi-label `.bit` hosts are
         * realised through the `map` field of the parent name's value.
         *
         * Examples:
         *   - `testls.bit`             → `("d/testls", [])`
         *   - `relay.testls.bit`       → `("d/testls", ["relay"])`
         *   - `a.b.c.testls.bit`       → `("d/testls", ["a", "b", "c"])`
         *   - `"".bit`, `.bit`, `bit`  → `null`
         *
         * The returned subdomain list is in DNS order: index 0 is the
         * label closest to the leaf (most-specific). The companion walker
         * [walkSubdomain] consumes labels in this order.
         *
         * For non-`.bit` hosts and the bare `.bit` TLD this returns `null`
         * — callers should treat the input as opaque in that case.
         */
        fun parseHostFlat(host: String): ParsedHostFlat? {
            val lower = host.trim().lowercase().trimEnd('.')
            if (!lower.endsWith(".bit")) return null
            val withoutTld = lower.removeSuffix(".bit")
            if (withoutTld.isEmpty()) return null
            val labels = withoutTld.split('.').filter { it.isNotEmpty() }
            if (labels.isEmpty()) return null
            // Last DNS label is the registered Namecoin name; everything
            // before it is a subdomain path (DNS order, most-specific first).
            val registered = labels.last()
            val subdomain = labels.dropLast(1)
            return ParsedHostFlat(
                namecoinName = "d/$registered",
                subdomainLabels = subdomain,
            )
        }

        /**
         * Walk a Namecoin domain object's [`map`][ifa-0001] tree to find the
         * effective Domain Name Object for [subdomainLabels].
         *
         * Lookup at each level, in order:
         *   1. Exact label match: `map[label]`.
         *   2. Wildcard match:    `map["*"]`.
         *   3. No match → return `null`.
         *
         * A `""` (empty-string) key at any level acts as a fallback whose
         * items merge into the parent; we apply that rule before recursing
         * deeper, so the returned object has the merged view at that level.
         *
         * The walk also accepts the string-shorthand form `"map": { "sub": "1.2.3.4" }`
         * by promoting the string to `{ "ip": [<string>] }` per ifa-0001.
         *
         * Pass an empty list to get the top-level object back unchanged.
         *
         * Note: returns the JsonObject AT the requested subdomain. It does
         * NOT inherit `tls` / `relay` / etc. from ancestors — inheritance is
         * not part of the Namecoin spec for these item types and would let
         * a parent name silently authorise a subdomain it didn't create. The
         * `""` empty-key default handling is the only spec-defined merging.
         */
        fun walkSubdomain(
            rootObj: JsonObject,
            subdomainLabels: List<String>,
        ): JsonObject? {
            var current: JsonObject = mergeEmptyKeyDefaults(rootObj)
            // Walk from the parent down to the leaf (reverse of DNS order).
            for (label in subdomainLabels.asReversed()) {
                val map = current["map"] as? JsonObject ?: return null
                val rawChild =
                    map[label]
                        ?: map["*"]
                        ?: return null
                val childObj = promoteShorthand(rawChild) ?: return null
                current = mergeEmptyKeyDefaults(childObj)
            }
            return current
        }

        /**
         * Implements the ifa-0001 "\"\"\" empty-key default rule:
         *   any item present at the top of [obj] takes precedence over the
         *   same item under `obj.map[""]`. Everything else under
         *   `obj.map[""]` is exposed as if it had been declared at [obj]'s
         *   top level.
         */
        private fun mergeEmptyKeyDefaults(obj: JsonObject): JsonObject {
            val map = obj["map"] as? JsonObject ?: return obj
            val defaults = map[""] as? JsonObject ?: return obj
            // Spec: only items NOT already present at the parent take effect.
            val merged = obj.toMutableMap()
            for ((k, v) in defaults) {
                if (!merged.containsKey(k)) merged[k] = v
            }
            return JsonObject(merged)
        }

        /**
         * Per ifa-0001 "map": a string value inside `map` is shorthand for
         * `{ "ip": [<string>] }`. Returns the canonical object form, or
         * `null` if [el] is neither a string nor an object.
         */
        private fun promoteShorthand(el: JsonElement): JsonObject? =
            when (el) {
                is JsonObject -> {
                    el
                }

                is JsonPrimitive -> {
                    if (el.isString) {
                        JsonObject(
                            mapOf(
                                "ip" to
                                    kotlinx.serialization.json.JsonArray(
                                        listOf(JsonPrimitive(el.content)),
                                    ),
                            ),
                        )
                    } else {
                        null
                    }
                }

                else -> {
                    null
                }
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
         *
         * Pass [subdomainLabels] (DNS order, most-specific first) to look up
         * a subdomain instead of the top-level object. For example, to read
         * the relay record at `relay.testls.bit` from `d/testls`, pass
         * `["relay"]`. The walk follows the Namecoin `ifa-0001` `map` rules:
         * exact label → wildcard `*` → default `""`. If no subdomain node
         * exists, returns an empty list (the parent's relay records do NOT
         * leak into subdomains; they apply only to the parent host itself).
         */
        fun parseRelayUrls(
            rawValueJson: String,
            subdomainLabels: List<String> = emptyList(),
        ): List<String> =
            try {
                val root = SHARED_JSON.parseToJsonElement(rawValueJson).jsonObject
                parseRelayUrls(root, subdomainLabels)
            } catch (_: Exception) {
                emptyList()
            }

        /**
         * As [parseRelayUrls] but accepts a pre-parsed root [JsonObject].
         * Use this when the caller has already expanded `import` items via
         * [NamecoinImportResolver] and does not want to re-parse / re-walk
         * the (possibly already merged) value.
         */
        fun parseRelayUrls(
            rootObject: JsonObject,
            subdomainLabels: List<String> = emptyList(),
        ): List<String> {
            val target = walkSubdomain(rootObject, subdomainLabels) ?: return emptyList()
            return collectRelayUrls(target)
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

        /**
         * Parse a Namecoin `d/<name>` value JSON for `tls` (TLSA) records, as
         * defined by [namecoin/proposals ifa-0001] (mirrors RFC 6698 TLSA RRs).
         *
         * Each record is a 4-element array `[usage, selector, matchingType, base64]`:
         *   - usage:        RFC 6698 §2.1.1 (0=PKIX-TA, 1=PKIX-EE, 2=DANE-TA, 3=DANE-EE)
         *   - selector:     RFC 6698 §2.1.2 (0=full cert, 1=SubjectPublicKeyInfo)
         *   - matchingType: RFC 6698 §2.1.3 (0=exact, 1=SHA-256, 2=SHA-512)
         *   - base64:       RFC 6698 §2.1.4 association data (Namecoin uses base64,
         *                   not the hex form used in DNS textual TLSA RRs).
         *
         * The Namecoin spec also accepts `tls` nested under per-port subdomains
         * (e.g. `map._443._tcp.tls`). [subdomainLabels] is consulted in DNS
         * order (most-specific first); the walk follows `ifa-0001` `map` rules:
         * exact label → wildcard `*` → default `""`. Pass an empty list (the
         * default) to read the top-level `tls` array — the original behaviour.
         *
         * Records with malformed shape are skipped.
         *
         * @return list of [TlsaRecord] in declaration order, empty if `tls` is
         *         absent or contains no valid records.
         */
        fun parseTlsaRecords(
            rawValueJson: String,
            subdomainLabels: List<String> = emptyList(),
        ): List<TlsaRecord> =
            try {
                val root = SHARED_JSON.parseToJsonElement(rawValueJson).jsonObject
                parseTlsaRecords(root, subdomainLabels)
            } catch (_: Exception) {
                emptyList()
            }

        /**
         * As [parseTlsaRecords] but accepts a pre-parsed root [JsonObject].
         * Use this when the caller has already expanded `import` items via
         * [NamecoinImportResolver].
         */
        fun parseTlsaRecords(
            rootObject: JsonObject,
            subdomainLabels: List<String> = emptyList(),
        ): List<TlsaRecord> {
            val target = walkSubdomain(rootObject, subdomainLabels) ?: return emptyList()
            return collectTlsaRecords(target)
        }

        private fun collectTlsaRecords(obj: JsonObject): List<TlsaRecord> {
            val tlsArray = obj["tls"]?.let { runCatching { it.jsonArray }.getOrNull() } ?: return emptyList()
            return tlsArray.mapNotNull { entry ->
                val arr = runCatching { entry.jsonArray }.getOrNull() ?: return@mapNotNull null
                if (arr.size < 4) return@mapNotNull null
                val usage = (arr[0] as? JsonPrimitive)?.intOrNull ?: return@mapNotNull null
                val selector = (arr[1] as? JsonPrimitive)?.intOrNull ?: return@mapNotNull null
                val matchType = (arr[2] as? JsonPrimitive)?.intOrNull ?: return@mapNotNull null
                val data = (arr[3] as? JsonPrimitive)?.content?.trim() ?: return@mapNotNull null
                if (usage !in 0..255 || selector !in 0..255 || matchType !in 0..255) return@mapNotNull null
                if (data.isEmpty()) return@mapNotNull null
                TlsaRecord(
                    usage = TlsaUsage.fromCode(usage),
                    selector = TlsaSelector.fromCode(selector),
                    matchingType = TlsaMatchingType.fromCode(matchType),
                    associationDataBase64 = data,
                )
            }
        }

        /**
         * Parse a Namecoin `d/<name>` value JSON for Tor `.onion` aliases
         * advertised at the (optionally walked) subdomain.
         *
         * Two shapes are accepted, both observed in the wild:
         *   1. `"tor": "<onion>"` or `"tor": ["<onion-1>", "<onion-2>"]`.
         *   2. `"_tor": { "txt": "<onion>" }` (the live `d/testls` shape,
         *      mirroring the `_tor` synthetic-subdomain DNS convention).
         *
         * Bare hostnames are promoted to `ws://<hostname>/`. Pre-formed
         * `ws[s]://...onion[...]` URLs pass through. Anything else is
         * dropped.
         *
         * Pass [subdomainLabels] to walk the parent's `map` tree before
         * collecting, the same convention as [parseRelayUrls].
         */
        fun parseTorEndpoints(
            rawValueJson: String,
            subdomainLabels: List<String> = emptyList(),
        ): List<String> =
            try {
                val root = SHARED_JSON.parseToJsonElement(rawValueJson).jsonObject
                parseTorEndpoints(root, subdomainLabels)
            } catch (_: Exception) {
                emptyList()
            }

        /**
         * As [parseTorEndpoints] but accepts a pre-parsed root [JsonObject].
         * Use this when the caller has already expanded `import` items via
         * [NamecoinImportResolver].
         */
        fun parseTorEndpoints(
            rootObject: JsonObject,
            subdomainLabels: List<String> = emptyList(),
        ): List<String> {
            val target = walkSubdomain(rootObject, subdomainLabels) ?: return emptyList()
            return collectTorEndpoints(target)
        }

        private fun collectTorEndpoints(obj: JsonObject): List<String> {
            val out = mutableListOf<String>()
            pushOnionField(obj["tor"], out)
            val torSub = obj["_tor"] as? JsonObject
            if (torSub != null) {
                pushOnionField(torSub["txt"], out)
                pushOnionField(torSub["tor"], out)
            }
            return out.distinct()
        }

        private fun pushOnionField(
            value: JsonElement?,
            out: MutableList<String>,
        ) {
            if (value == null) return
            if (value is JsonPrimitive && value.isString) {
                normalizeOnionUrl(value.content)?.let { out += it }
                return
            }
            // Otherwise treat it as an array of strings; non-arrays fall through.
            runCatching {
                value.jsonArray.forEach { entry ->
                    if (entry is JsonPrimitive && entry.isString) {
                        normalizeOnionUrl(entry.content)?.let { out += it }
                    }
                }
            }
        }

        /**
         * Promote a bare `.onion` hostname to a `ws://...onion/` URL, or
         * pass through a `ws[s]://...onion[...]` URL as-is. Returns null
         * for inputs that do not look like a Tor hidden service.
         */
        private fun normalizeOnionUrl(raw: String): String? {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) return null
            if (RelayUrlNormalizer.isRelayUrl(trimmed)) {
                return if (trimmed.contains(".onion")) trimmed else null
            }
            val bareHost =
                trimmed
                    .removePrefix("http://")
                    .removePrefix("https://")
                    .substringBefore('/')
                    .trimEnd('.')
                    .lowercase()
            if (!bareHost.endsWith(".onion")) return null
            val label = bareHost.removeSuffix(".onion")
            if (label.isEmpty() || label.contains('.')) return null
            return "ws://$bareHost/"
        }
    }

    /** RFC 6698 TLSA Certificate Usage Field. */
    enum class TlsaUsage(
        val code: Int,
    ) {
        PKIX_TA(0),
        PKIX_EE(1),
        DANE_TA(2),
        DANE_EE(3),
        UNKNOWN(-1),
        ;

        companion object {
            fun fromCode(code: Int): TlsaUsage = entries.firstOrNull { it.code == code } ?: UNKNOWN
        }
    }

    /** RFC 6698 TLSA Selector Field. */
    enum class TlsaSelector(
        val code: Int,
    ) {
        FULL_CERT(0),
        SUBJECT_PUBLIC_KEY_INFO(1),
        UNKNOWN(-1),
        ;

        companion object {
            fun fromCode(code: Int): TlsaSelector = entries.firstOrNull { it.code == code } ?: UNKNOWN
        }
    }

    /** RFC 6698 TLSA Matching Type Field. */
    enum class TlsaMatchingType(
        val code: Int,
    ) {
        EXACT(0),
        SHA_256(1),
        SHA_512(2),
        UNKNOWN(-1),
        ;

        companion object {
            fun fromCode(code: Int): TlsaMatchingType = entries.firstOrNull { it.code == code } ?: UNKNOWN
        }
    }

    /**
     * One TLSA record from a Namecoin `d/<name>` value, per
     * [namecoin/proposals ifa-0001](https://github.com/namecoin/proposals/blob/master/ifa-0001.md)
     * (semantically equivalent to RFC 6698).
     */
    data class TlsaRecord(
        val usage: TlsaUsage,
        val selector: TlsaSelector,
        val matchingType: TlsaMatchingType,
        /** Base64-encoded association data, per Namecoin spec (NOT hex like DNS textual). */
        val associationDataBase64: String,
    )

    /**
     * Flat parsed identifier surface used by [toNamecoinName] and the
     * instance parser. [namecoinName] is the **single-label** registered
     * name to query; [subdomainLabels] is the path beneath it in DNS order
     * (most-specific first, same convention as [parseHostFlat]). Walk the
     * parent's `map` tree with [walkSubdomain] to find the effective
     * Domain Name Object before reading `nostr.names.<localPart>` from it.
     */
    data class ParsedIdentifierFlat(
        val namecoinName: String,
        val localPart: String,
        val subdomainLabels: List<String> = emptyList(),
        val isIdentityNamespace: Boolean,
    )

    /**
     * Output of [parseHostFlat]: the registered Namecoin name (single label,
     * `d/<label>`) and any subdomain labels beneath it in DNS order
     * (most-specific first).
     */
    data class ParsedHostFlat(
        val namecoinName: String,
        val subdomainLabels: List<String>,
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
     * Fetcher that satisfies the [NamecoinImportResolver.NameValueFetcher]
     * contract by delegating to [lookupNameDetailed]. Failures (not found,
     * unreachable, timeout) are absorbed and reported as `null`, so a
     * broken import does not poison the whole resolution path.
     *
     * Exposed as a method (rather than building a one-off lambda at every
     * call site) so the same fetcher implementation is used by both the
     * relay flow and the NIP-05 flow, which keeps import behaviour
     * identical between them and makes it trivial to stub for tests.
     */
    suspend fun fetchValueForImport(namecoinName: String): String? =
        when (val outcome = lookupNameDetailed(namecoinName)) {
            is NameLookupOutcome.Found -> outcome.result.value

            is NameLookupOutcome.NotFound,
            is NameLookupOutcome.ServersUnreachable,
            NameLookupOutcome.Timeout,
            -> null
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
        /**
         * Subdomain labels beneath [namecoinName], DNS order (most-specific
         * first). Empty for bare-host inputs like `alice@example.bit`.
         * Non-empty for inputs like `alice@relay.testls.bit` (`["relay"]`)
         * — the resolver walks `map` to that node before reading `nostr`.
         */
        val subdomainLabels: List<String>,
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
            subdomainLabels = flat.subdomainLabels,
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

        // Distinguish "valid JSON, no `nostr` field" from "value is
        // unparseable JSON". Both used to collapse into NoNostrField, which
        // sent operators chasing a missing field that wasn't actually the
        // problem (the Namecoin record itself was malformed). Surfacing
        // the parser's column number lets a publisher see WHERE the value
        // is bad without spelunking through the whole serialised form.
        val parsedRoot =
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

        // Per ifa-0001 §"import": expand any `import` items on the parent
        // record before consulting the value. Imports happen at the value
        // level, so subdomain walking sees the fully merged view. Failures
        // inside an import (name not found, unreachable, malformed JSON)
        // are absorbed by [fetchValueForImport] and treated as the empty
        // object, so a transient ElectrumX hiccup on an imported name
        // doesn't break unrelated identity resolution.
        val valueJson =
            NamecoinImportResolver.expandImports(parsedRoot) { name ->
                fetchValueForImport(name)
            }

        // For multi-label `.bit` inputs (e.g. alice@relay.testls.bit) we
        // never query a separate `d/relay.testls`. Instead we walk the
        // parent record's `map` tree to find the effective Domain Name
        // Object for the subdomain, and read `nostr` from THAT node only.
        // No inheritance: a parent's `nostr.names.<localPart>` does NOT
        // silently authorise the same localPart on every subdomain.
        // The empty-labels case returns the root object unchanged, so the
        // bare-host code path is unaffected.
        val effectiveValue =
            walkSubdomain(valueJson, parsed.subdomainLabels)
                ?: return NamecoinResolveOutcome.NoNostrField(parsed.namecoinName)

        val nostrResult =
            when (parsed.namespace) {
                Namespace.DOMAIN -> extractFromDomainValue(effectiveValue, parsed)
                Namespace.IDENTITY -> extractFromIdentityValue(effectiveValue, parsed)
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
