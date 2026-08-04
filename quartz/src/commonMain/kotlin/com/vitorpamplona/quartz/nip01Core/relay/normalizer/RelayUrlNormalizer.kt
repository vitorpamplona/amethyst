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
package com.vitorpamplona.quartz.nip01Core.relay.normalizer

import androidx.collection.LruCache
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.Rfc3986
import kotlinx.coroutines.CancellationException
import kotlin.contracts.ExperimentalContracts

sealed interface NormalizationResult {
    class Success(
        val url: NormalizedRelayUrl,
    ) : NormalizationResult

    object Error : NormalizationResult
}

val normalizedUrls = LruCache<String, NormalizationResult>(5000)

class RelayUrlNormalizer {
    companion object {
        fun isLocalHost(url: String) =
            url.contains("127.0.0.1") ||
                url.contains("localhost") ||
                url.contains("//umbrel:") ||
                url.contains("192.168.") ||
                url.contains(".local:") ||
                url.contains(".local/")

        fun isOnion(url: String) = url.endsWith(".onion") || url.contains(".onion/")

        fun isRelaySchemePrefix(url: String) = url.length > 6 && url[0] == 'w' && url[1] == 's'

        fun isRelaySchemePrefixSecure(url: String) = url[2] == 's' && url[3] == ':' && url[4] == '/' && url[5] == '/' && url[6] != '/'

        fun isRelaySchemePrefixInsecure(url: String) = url[2] == ':' && url[3] == '/' && url[4] == '/' && url[5] != '/'

        fun isHttpPrefix(url: String) = url.length > 8 && url[0] == 'h' && url[1] == 't' && url[2] == 't' && url[3] == 'p'

        fun isHttpSSuffix(url: String) = url[4] == 's' && url[5] == ':' && url[6] == '/' && url[7] == '/'

        fun isHttpSuffix(url: String) = url[4] == ':' && url[5] == '/' && url[6] == '/'

        fun isRelayUrl(url: String): Boolean {
            if (url.length < 3) return false

            val trimmed =
                if (url[0].isWhitespace() || url[url.length - 1].isWhitespace()) {
                    url.trim()
                } else {
                    url
                }

            // fast
            if (isRelaySchemePrefix(trimmed)) {
                if (isRelaySchemePrefixSecure(trimmed)) {
                    return true
                } else if (isRelaySchemePrefixInsecure(trimmed)) {
                    return true
                }
            }

            return false
        }

        private fun norm(url: String) = NormalizedRelayUrl(Rfc3986.normalize(url))

        private fun isInvisible(c: Char) = c == '\u200B' || c == '\u200C' || c == '\u200D' || c == '\u2060' || c == '\uFEFF'

        /**
         * Scans the authority (host[:port]) that starts at [start] and ends at the first
         * `/`, `?` or `#`. Returns the end index, or -1 when the authority is empty or
         * contains characters that never appear in a real relay host (`@` userinfo,
         * percent-encoding, commas).
         */
        private fun authorityEnd(
            url: String,
            start: Int,
        ): Int {
            if (start >= url.length) return -1
            var i = start
            if (url[i] == '[') {
                // IPv6 literal: defer validation to the RFC 3986 parser
                while (i < url.length && url[i] != '/' && url[i] != '?' && url[i] != '#') i++
                return i
            }
            while (i < url.length) {
                val c = url[i]
                if (c == '/' || c == '?' || c == '#') break
                if (c == '@' || c == '%' || c == ',') return -1
                i++
            }
            return if (i == start) -1 else i
        }

        /**
         * Accepts a ws/wss url whose host starts at [hostStart] if the authority is sane
         * and the path does not start with `//` (the signature of a second URL or a broken
         * `https//` pasted after the scheme, e.g. `wss://https//nostr.watch/relay/x`).
         */
        private fun fixWs(
            url: String,
            hostStart: Int,
        ): String? {
            val end = authorityEnd(url, hostStart)
            if (end < 0) return null
            if (end + 1 < url.length && url[end] == '/' && url[end + 1] == '/') return null
            return url
        }

        /**
         * Converts an http(s) url to ws(s) only when it is a bare host — nothing after
         * `host[:port]` but an optional trailing `/`. An http url with a path, query or
         * fragment (Mastodon actor urls from bridge `proxy` tags, web pages, images) is
         * a web resource, not a relay: converting it creates a wss:// url that can never
         * answer and only wastes connection attempts.
         */
        private fun fixHttp(
            url: String,
            hostStart: Int,
            newScheme: String,
        ): String? {
            val end = authorityEnd(url, hostStart)
            if (end < 0) return null
            val bareHost = end == url.length || (end == url.length - 1 && url[end] == '/')
            if (!bareHost) return null
            return "$newScheme${url.substring(hostStart)}"
        }

        /**
         * Validates a schemeless candidate: the part before the first `/` must look like
         * `host` or `host:port` — letters, digits, `.`, `-`, `_`, plus at most one `:`
         * followed by digits only. Rejects addressable-event pointers (`31990:hex:dtag`),
         * bare scheme leftovers (`wss:`) and anything else that would otherwise be blindly
         * prefixed with `wss://`.
         */
        private fun isBareHostAndPath(url: String): Boolean {
            if (url[0] == '[') return true // IPv6 literal: defer to the RFC 3986 parser
            var i = 0
            var portStart = -1
            while (i < url.length) {
                val c = url[i]
                if (c == '/') break
                if (c == ':') {
                    if (portStart >= 0) return false
                    portStart = i + 1
                } else if (portStart >= 0) {
                    if (c < '0' || c > '9') return false
                } else if (!c.isLetterOrDigit() && c != '.' && c != '-' && c != '_') {
                    return false
                }
                i++
            }
            if (i == 0) return false
            if (portStart >= 0 && portStart == i) return false
            return true
        }

        @OptIn(ExperimentalContracts::class)
        fun fix(rawUrl: String): String? {
            if (rawUrl.length < 4) return null
            if (rawUrl.contains("%00")) return null

            // Trim trailing %20 (percent-encoded spaces from malformed event data)
            val url =
                rawUrl.trimEnd('%', '2', '0').let { trimmed ->
                    // Only accept if we actually removed a trailing %20 pattern
                    if (trimmed.length < rawUrl.length && rawUrl.endsWith("%20")) trimmed else rawUrl
                }
            if (url.length < 4) return null

            // Reject URLs with %20 in the middle — these are garbage
            if (url.contains("%20")) return null

            if (url.length > 50) {
                // removes multiple urls in the same line
                val schemeIdx = url.indexOf("://")
                val nextScheme = url.indexOf("://", schemeIdx + 3)
                if (nextScheme > 0) {
                    return null
                }
            }

            var trimmed =
                if (url[0].isWhitespace() || url[url.length - 1].isWhitespace()) {
                    url.trim()
                } else {
                    url
                }

            // Single pass: interior whitespace means multiple urls or prose in one field,
            // backslashes never appear in a real relay url; both are garbage. Invisible
            // characters (zero-width spaces, BOM) are copy-paste artifacts — strip them.
            var hasInvisible = false
            for (c in trimmed) {
                if (c == '\\') return null
                if (c.isWhitespace()) return null
                if (isInvisible(c)) hasInvisible = true
            }
            if (hasInvisible) {
                trimmed = buildString(trimmed.length) { for (c in trimmed) if (!isInvisible(c)) append(c) }
                if (trimmed.length < 4) return null
            }

            // fast for good wss:// urls
            if (isRelaySchemePrefix(trimmed)) {
                if (isRelaySchemePrefixSecure(trimmed)) {
                    return fixWs(trimmed, 6)
                } else if (isRelaySchemePrefixInsecure(trimmed)) {
                    return fixWs(trimmed, 5)
                }
            }

            // fast for good https:// urls
            if (isHttpPrefix(trimmed)) {
                if (isHttpSSuffix(trimmed)) {
                    // https://
                    return fixHttp(trimmed, 8, "wss://")
                } else if (isHttpSuffix(trimmed)) {
                    // http://
                    return fixHttp(trimmed, 7, "ws://")
                }
            }

            // fast for good ww:// urls
            if (trimmed.startsWith("ww://")) {
                return fixWs("wss://${trimmed.drop(5)}", 6)
            }

            // fast for good ww:// urls
            if (trimmed.startsWith("was://")) {
                return fixWs("wss://${trimmed.drop(6)}", 6)
            }

            // fast for good ww:// urls
            if (trimmed.startsWith("Wws://")) {
                return fixWs("wss://${trimmed.drop(6)}", 6)
            }

            // fast for good ww:// urls
            if (trimmed.startsWith("Wss://")) {
                return fixWs("wss://${trimmed.drop(6)}", 6)
            }

            if (trimmed.contains("://")) {
                // some other scheme we cannot connect to.
                Log.d("RelayUrlNormalizer") { "Rejected $url" }
                return null
            }

            // protocol-relative urls (`//host/`) are just missing the scheme
            val bare = if (trimmed.startsWith("//")) trimmed.drop(2) else trimmed
            if (bare.length < 4) return null

            if (!isBareHostAndPath(bare)) {
                Log.d("RelayUrlNormalizer") { "Rejected $url" }
                return null
            }

            return if (isOnion(bare) || isLocalHost(bare)) {
                "ws://$bare"
            } else {
                "wss://$bare"
            }
        }

        fun normalize(url: String): NormalizedRelayUrl {
            val result = normalizeOrNull(url)
            return result ?: throw IllegalArgumentException("Invalid Relay Url: $url")
        }

        fun normalizeOrNull(url: String): NormalizedRelayUrl? {
            if (url.isEmpty()) return null
            // happy path when the url has been fixed already
            normalizedUrls[url]?.let {
                return when (it) {
                    is NormalizationResult.Success -> it.url
                    else -> null
                }
            }

            return try {
                val fixed = fix(url)
                if (fixed != null) {
                    val normalized = norm(fixed)
                    // the RFC 3986 parser can drop or replace the scheme on odd inputs;
                    // anything that is not ws(s):// at this point cannot be connected to.
                    if (!isRelayUrl(normalized.url)) {
                        Log.d("NormalizedRelayUrl") { "Rejected $url" }
                        normalizedUrls.put(url, NormalizationResult.Error)
                        return null
                    }
                    normalizedUrls.put(url, NormalizationResult.Success(normalized))
                    normalized
                } else {
                    Log.d("NormalizedRelayUrl") { "Rejected $url" }
                    normalizedUrls.put(url, NormalizationResult.Error)
                    null
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                normalizedUrls.put(url, NormalizationResult.Error)
                Log.d("NormalizedRelayUrl") { "Rejected $url" }
                null
            }
        }
    }
}

fun String.normalizeRelayUrl() = RelayUrlNormalizer.normalize(this)

fun String.normalizeRelayUrlOrNull() = RelayUrlNormalizer.normalizeOrNull(this)
