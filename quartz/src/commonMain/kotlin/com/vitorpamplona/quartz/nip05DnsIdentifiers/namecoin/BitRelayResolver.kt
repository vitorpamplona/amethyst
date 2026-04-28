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

import androidx.collection.LruCache
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.utils.TimeUtils
import com.vitorpamplona.quartz.utils.UriParser
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * Resolves Nostr relay URLs whose host is a Namecoin `.bit` domain
 * to the underlying real `wss://` (or `ws://`) endpoint.
 *
 * This is a thin policy layer over existing infrastructure:
 *   - [UriParser] parses the URL and gives us scheme + host + path
 *     (uses `java.net.URI` on JVM, platform-native parsers on iOS).
 *   - [NamecoinNameResolver.parseHostFlat] splits a `.bit` host into the
 *     **single-label** registered Namecoin name (`d/<label>`) and the
 *     subdomain path beneath it. The resolver only ever looks up the
 *     registered name; multi-label hosts (`relay.testls.bit`) are
 *     realised through the parent's `map` field per ifa-0001 §"map",
 *     never as a separate `d/relay.testls` query.
 *   - [NamecoinNameResolver.lookupNameDetailed] performs the `name_show`
 *     lookup with the standard timeout + exception translation. Reuses
 *     the same client AppModules already builds for NIP-05.
 *   - [NamecoinNameResolver.walkSubdomain] walks the value JSON's `map`
 *     tree (exact → wildcard → `""` defaults) to find the effective
 *     Domain Name Object for the subdomain.
 *   - [NamecoinNameResolver.parseRelayUrls] / [NamecoinNameResolver.parseTlsaRecords]
 *     extract relay URLs and TLSA records from THAT node only —
 *     ancestors do NOT leak `relay`/`tls` into descendants.
 *
 * All this resolver adds is: in-memory caching with separate positive/
 * negative TTLs, and a small policy that picks the first usable URL
 * while preserving the original `.bit` URL's path when the record lacks one.
 *
 * Record format details and the connection flow are documented in
 * [README.md](./README.md).
 */
class BitRelayResolver(
    private val nameResolver: NamecoinNameResolver,
    private val positiveTtlSecs: Long = 3_600L, // 1 hour
    private val negativeTtlSecs: Long = 60L, // 1 minute
    private val maxEntries: Int = 500,
) {
    private val cache = LruCache<String, CachedRelayResolution>(maxEntries)
    private val mutex = Mutex()

    private val sharedJson =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

    companion object {
        /** Returns true iff [url] is a `ws[s]://` relay URL whose host ends in `.bit`. */
        fun isBitRelay(url: NormalizedRelayUrl): Boolean = isBitRelayUrl(url.url)

        /** Same as [isBitRelay], operating on a raw URL string. */
        fun isBitRelayUrl(rawUrl: String): Boolean {
            val (scheme, host) = parseWsUrl(rawUrl) ?: return false
            return (scheme == "ws" || scheme == "wss") && host.endsWith(".bit")
        }

        /**
         * Returns `(scheme, host)` (both lowercase) for [rawUrl] using the
         * shared Quartz [UriParser] (`java.net.URI` on JVM, platform-native on
         * iOS). Null if the URL has no scheme or host, or can't be parsed.
         */
        internal fun parseWsUrl(rawUrl: String): Pair<String, String>? =
            runCatching {
                val parser = UriParser(rawUrl.trim())
                val scheme = parser.scheme()?.lowercase() ?: return@runCatching null
                val host = parser.host()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return@runCatching null
                scheme to host
            }.getOrNull()
    }

    /** Result of a `.bit` relay resolution attempt. */
    sealed class Resolution {
        /** Host did not need to be resolved; pass through unchanged. */
        data object NotABitHost : Resolution()

        /** Successfully resolved to a real URL. */
        data class Resolved(
            /** The canonical `.bit` URL the user requested. */
            val originalUrl: String,
            /** The real `ws[s]://` URL to actually connect to. */
            val resolvedUrl: String,
            /** All relay URLs found in the record (resolved first, then fallbacks). */
            val candidates: List<String>,
            /**
             * TLSA records pulled from the same Namecoin value JSON, in declaration
             * order. Empty if the record has no `tls` field. Use these to pin the
             * TLS handshake to [resolvedUrl] (RFC 6698 / Namecoin `ifa-0001`).
             */
            val tlsaRecords: List<NamecoinNameResolver.TlsaRecord> = emptyList(),
            /**
             * `ws[s]://...onion[...]` URLs declared at the same Namecoin node,
             * usually under a `tor` or `_tor.txt` field. Empty if the record
             * does not advertise a Tor hidden service. Callers that have Tor
             * routing enabled SHOULD prefer one of these over [resolvedUrl];
             * callers without Tor should ignore them (the underlying
             * `.onion` cannot be reached over clearnet).
             */
            val onionEndpoints: List<String> = emptyList(),
        ) : Resolution()

        /** The `.bit` name does not exist or has no relay record. */
        data class NotFound(
            val host: String,
            val message: String,
        ) : Resolution()

        /** Resolution failed (network, timeout, etc.). */
        data class Error(
            val host: String,
            val message: String,
        ) : Resolution()
    }

    private data class CachedRelayResolution(
        val candidates: List<String>,
        val tlsaRecords: List<NamecoinNameResolver.TlsaRecord>,
        val onionEndpoints: List<String>,
        val message: String?,
        val timestamp: Long = TimeUtils.now(),
    )

    /**
     * Attempt to resolve the `.bit` host inside [url] to a real `ws[s]://` URL.
     * Non-`.bit` URLs return [Resolution.NotABitHost] without I/O.
     */
    suspend fun resolve(url: NormalizedRelayUrl): Resolution = resolveRaw(url.url)

    /** As [resolve], operating on a raw URL string. */
    suspend fun resolveRaw(rawUrl: String): Resolution {
        val (scheme, host) = parseWsUrl(rawUrl) ?: return Resolution.NotABitHost
        if (scheme != "ws" && scheme != "wss") return Resolution.NotABitHost
        if (!host.endsWith(".bit")) return Resolution.NotABitHost

        // Split the host into the registered Namecoin name (single label) and
        // a subdomain path beneath it.  `relay.testls.bit` -> (`d/testls`,
        // ["relay"]); `testls.bit` -> (`d/testls`, []).  We then look up the
        // registered name once and walk the value JSON's `map` tree to find
        // the effective Domain Name Object for this subdomain. This matches
        // the Namecoin `ifa-0001` `map` semantics that browsers / Encaya use
        // for DNS-style subdomain expression and lets a single `d/<root>`
        // record host any number of `<sub>.<root>.bit` relays.
        val parsedHost =
            NamecoinNameResolver.parseHostFlat(host)
                ?: return Resolution.Error(host, "Cannot map `$host` to a Namecoin name")
        val namecoinName = parsedHost.namecoinName
        val subdomainLabels = parsedHost.subdomainLabels

        // Cache lookup
        mutex.withLock {
            cache[host]?.let { entry ->
                val ttl = if (entry.candidates.isNotEmpty()) positiveTtlSecs else negativeTtlSecs
                val age = TimeUtils.now() - entry.timestamp
                if (age <= ttl) {
                    return cachedResolution(rawUrl, host, entry)
                } else {
                    cache.remove(host)
                }
            }
        }

        // Delegate the ElectrumX call (with timeout + exception translation)
        // to NamecoinNameResolver so the NIP-05 path and this path stay in
        // lock-step on lookup semantics.
        val nameResult =
            when (val outcome = nameResolver.lookupNameDetailed(namecoinName)) {
                is NamecoinNameResolver.NameLookupOutcome.Found -> {
                    outcome.result
                }

                is NamecoinNameResolver.NameLookupOutcome.NotFound -> {
                    cachePut(host, emptyList(), emptyList(), emptyList(), "Name not found")
                    return Resolution.NotFound(host, "Namecoin name `${outcome.name}` not found")
                }

                is NamecoinNameResolver.NameLookupOutcome.ServersUnreachable -> {
                    return Resolution.Error(host, "Namecoin servers unreachable: ${outcome.message}")
                }

                NamecoinNameResolver.NameLookupOutcome.Timeout -> {
                    return Resolution.Error(host, "Namecoin lookup timed out")
                }
            }

        // Per ifa-0001 §"import": expand any `import` items on the parent
        // record before walking subdomains. This lets a record point at
        // another name for shared boilerplate (e.g. a stable `nostr.names`
        // block, or a common TLSA pin) and still have all `relay`/`tls`/
        // `_tor` fields read cleanly from the merged view. We use a
        // pre-parsed [JsonObject] here and pass it to the JsonObject
        // overloads of the parsers, so the value JSON is parsed once.
        //
        // If the published value isn't valid JSON (a real failure mode
        // when an operator hand-builds the value and miscounts braces),
        // surface the parser's error message verbatim. Hiding it behind
        // a generic "no relay field" message — which is what happens if
        // the JSON parse exception is swallowed downstream — sends the
        // publisher chasing a missing field that isn't actually the
        // problem.
        val parsedRoot =
            runCatching { sharedJson.parseToJsonElement(nameResult.value).jsonObject }
                .getOrElse { err ->
                    val detail = err.message ?: "unparseable JSON value"
                    return Resolution.Error(
                        host,
                        "Malformed Namecoin record JSON for `$namecoinName`: $detail",
                    )
                }
        val effectiveRoot: JsonObject =
            NamecoinImportResolver.expandImports(parsedRoot) { name ->
                nameResolver.fetchValueForImport(name)
            }

        // Reuse the shared relay-URL parser from NamecoinNameResolver instead
        // of re-implementing the JSON shape walk here. For subdomains we
        // pass the labels so the parser walks `map` to the right node before
        // collecting; passing an empty list yields the original top-level
        // behaviour.
        val candidates = NamecoinNameResolver.parseRelayUrls(effectiveRoot, subdomainLabels)
        // Same JSON, same parser — pull the TLSA list and Tor endpoints out
        // of the value so the caller can pin / route without paying for a
        // second ElectrumX round-trip.
        val tlsaRecords = NamecoinNameResolver.parseTlsaRecords(effectiveRoot, subdomainLabels)
        val onionEndpoints = NamecoinNameResolver.parseTorEndpoints(effectiveRoot, subdomainLabels)
        if (candidates.isEmpty() && onionEndpoints.isEmpty()) {
            val msg =
                if (subdomainLabels.isEmpty()) {
                    "No `relay` or `tor` field in Namecoin record for `$namecoinName`"
                } else {
                    "No `relay` or `tor` field at subdomain `${subdomainLabels.joinToString(".")}` of `$namecoinName`"
                }
            cachePut(host, emptyList(), tlsaRecords, emptyList(), msg)
            return Resolution.NotFound(host, msg)
        }

        cachePut(host, candidates, tlsaRecords, onionEndpoints, null)
        return resolutionFromCandidates(rawUrl, host, candidates, tlsaRecords, onionEndpoints)
    }

    private fun cachedResolution(
        rawUrl: String,
        host: String,
        entry: CachedRelayResolution,
    ): Resolution =
        if (entry.candidates.isNotEmpty() || entry.onionEndpoints.isNotEmpty()) {
            resolutionFromCandidates(rawUrl, host, entry.candidates, entry.tlsaRecords, entry.onionEndpoints)
        } else {
            Resolution.NotFound(host, entry.message ?: "No relay record")
        }

    private fun resolutionFromCandidates(
        rawUrl: String,
        host: String,
        candidates: List<String>,
        tlsaRecords: List<NamecoinNameResolver.TlsaRecord>,
        onionEndpoints: List<String>,
    ): Resolution {
        // Pick a clearnet candidate by default. The rewriter decides per
        // connection whether to swap it for an onion endpoint based on the
        // user's live Tor settings (which the resolver itself doesn't see).
        val first =
            candidates.firstOrNull()
                ?: onionEndpoints.firstOrNull()
                ?: return Resolution.NotFound(host, "No relay record")
        return Resolution.Resolved(
            originalUrl = rawUrl,
            resolvedUrl = mergeOriginalPath(rawUrl, first),
            candidates = candidates,
            tlsaRecords = tlsaRecords,
            onionEndpoints = onionEndpoints,
        )
    }

    /**
     * If the user typed `wss://example.bit/rooms/foo` but the Namecoin record
     * only specifies `wss://relay.example.com/`, preserve the user's path so
     * that NIP-29 / room scoping still works.
     *
     * Rule: if the resolved URL has no path beyond `/`, append the original's path.
     */
    private fun mergeOriginalPath(
        originalUrl: String,
        resolvedUrl: String,
    ): String {
        val resolvedPath = pathOf(resolvedUrl)
        if (resolvedPath.length > 1) return resolvedUrl // already has a meaningful path
        val origPath = pathOf(originalUrl)
        return if (origPath.length > 1) {
            resolvedUrl.trimEnd('/') + origPath
        } else {
            resolvedUrl
        }
    }

    private fun pathOf(rawUrl: String): String = runCatching { UriParser(rawUrl).path() ?: "" }.getOrDefault("")

    private suspend fun cachePut(
        host: String,
        candidates: List<String>,
        tlsaRecords: List<NamecoinNameResolver.TlsaRecord>,
        onionEndpoints: List<String>,
        message: String?,
    ) {
        mutex.withLock {
            cache.put(host, CachedRelayResolution(candidates, tlsaRecords, onionEndpoints, message))
        }
    }

    /**
     * Look up the cached TLSA records for a `.bit` host that has previously
     * been resolved through [resolve]. Returns `null` if the host has not been
     * resolved (or has been evicted), an empty list if it has been resolved
     * but the Namecoin record has no `tls` field.
     *
     * The TLS-pinning code path needs the records keyed by the original
     * `.bit` host. Bypassing the cache here would either issue duplicate
     * ElectrumX lookups or force the rewriter to push the records into the
     * OkHttp factory through a side channel.
     */
    suspend fun cachedTlsaFor(host: String): List<NamecoinNameResolver.TlsaRecord>? {
        val key = host.lowercase()
        return mutex.withLock {
            cache[key]?.tlsaRecords
        }
    }

    /**
     * Look up the cached `.onion` endpoints for a `.bit` host that has
     * previously been resolved through [resolve]. Returns `null` if the
     * host has not been resolved, an empty list if it has been resolved
     * but the Namecoin record advertises no Tor hidden service.
     *
     * Used by the URL rewriter to swap the resolved clearnet handshake
     * for an onion endpoint when the user has Tor routing enabled. Same
     * cache as the relay-URL/TLSA path — no extra ElectrumX call.
     */
    suspend fun cachedOnionEndpointsFor(host: String): List<String>? {
        val key = host.lowercase()
        return mutex.withLock {
            cache[key]?.onionEndpoints
        }
    }

    /** Drop a single cached `.bit` host (e.g. after a connection failure). */
    suspend fun invalidate(host: String) {
        mutex.withLock {
            cache.remove(host.lowercase())
        }
    }

    /** Drop all cached `.bit` resolutions. */
    suspend fun clear() {
        mutex.withLock {
            cache.evictAll()
        }
    }
}
