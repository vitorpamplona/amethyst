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
import com.vitorpamplona.quartz.utils.Ipv4
import com.vitorpamplona.quartz.utils.Ipv6
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
        /**
         * Every host test below is anchored to the **authority** (`host[:port]`), never to the
         * whole url. A plain `contains` reads the path and query too, so
         * `wss://evil.example.com/127.0.0.1` used to answer true here — and since [isLocalHost]
         * is what exempts a relay from Tor, any relay list could hand the app a url that quietly
         * dropped its own Tor routing. Relay urls arrive from other people (NIP-65 lists, relay
         * hints, `r` tags), so they are attacker-controlled input and have to be parsed as such.
         *
         * Anchoring is also what makes `host:port` work: `.onion:8080` never matched the old
         * `.onion/` test, so an onion relay on an explicit port was not recognized as onion at
         * all and its hostname went to the clearnet DNS resolver.
         */
        fun isLocalHost(url: String): Boolean {
            val start = hostStart(url)
            val end = hostEnd(url, start)
            if (end <= start) return false
            val hostEnd = hostEndWithoutPort(url, start, end)
            return isPrivateIpv4(url, start, hostEnd) ||
                // RFC 6761: `localhost` and anything under it — the same rule SurgeDns applies
                // when deciding whether a loopback answer is legitimate. A substring test would
                // also match `notlocalhost.example.com`, which is registrable.
                regionEquals(url, "localhost", start, hostEnd) ||
                regionEndsWith(url, ".localhost", start, hostEnd) ||
                regionEquals(url, "umbrel", start, hostEnd) ||
                regionEndsWith(url, ".local", start, hostEnd) ||
                isPrivateIpv6(url, start, end)
        }

        /**
         * The IPv4 ranges that are never a public relay: `127.0.0.0/8` loopback, the RFC 1918
         * private blocks, `169.254.0.0/16` link-local and `0.0.0.0/8`.
         *
         * Parsed rather than substring-matched, which was wrong both ways: `contains("192.168.")`
         * missed `10.0.0.5` and `172.16.3.4` — so a LAN relay was given `wss://` and dialed
         * through Tor — while matching `192.168.evil.com`, a registrable domain that could
         * therefore exempt itself from Tor.
         */
        private fun isPrivateIpv4(
            url: String,
            start: Int,
            end: Int,
        ): Boolean {
            val bytes = Ipv4.parse(url, start, end) ?: return false
            return Ipv4.isLoopback(bytes) ||
                Ipv4.isPrivate(bytes) ||
                Ipv4.isLinkLocal(bytes) ||
                Ipv4.isUnspecified(bytes)
        }

        fun isOnion(url: String): Boolean {
            val start = hostStart(url)
            val end = hostEnd(url, start)
            if (end <= start) return false
            return regionEndsWith(url, ".onion", start, hostEndWithoutPort(url, start, end))
        }

        /**
         * True for a relay inside an encrypted IPv6 overlay mesh — today `0200::/7`, the range
         * Yggdrasil derives node addresses and subnets from.
         *
         * Unlike [isLocalHost] this is not a private address: it is reachable from anywhere on
         * the mesh. But it is unreachable *off* the mesh, which has two consequences the relay
         * stack has to honour — it can never be dialed through a SOCKS/Tor proxy, and it can
         * never present a CA-issued certificate, so it speaks plain `ws://`. Both are safe:
         * the overlay already encrypts end to end and authenticates the peer by its address,
         * which is derived from the peer's public key.
         */
        fun isOverlayNetwork(url: String): Boolean {
            val start = hostStart(url)
            val bytes = ipv6HostOf(url, start, hostEnd(url, start)) ?: return false
            return Ipv6.isOverlayMesh(bytes)
        }

        /**
         * The IPv6 twins of the literals in [isLocalHost]: `::1` (127.0.0.1), `fc00::/7` unique
         * local addresses (192.168.0.0/16) and `fe80::/10` link-local. All three name a host that
         * only exists on this machine or this LAN, which is what every caller of [isLocalHost]
         * means by the question — so a relay on one must not be Torified, must not need TLS,
         * and must not be advertised to the network.
         */
        private fun isPrivateIpv6(
            url: String,
            start: Int,
            end: Int,
        ): Boolean {
            val bytes = ipv6HostOf(url, start, end) ?: return false
            return Ipv6.isLoopback(bytes) || Ipv6.isUniqueLocal(bytes) || Ipv6.isLinkLocal(bytes)
        }

        /**
         * Parses the authority of [url] as a bracketed IPv6 literal, dropping any `%zone` suffix.
         * Returns null — on a single char comparison — for the overwhelmingly common case of a
         * url with a DNS host.
         */
        private fun ipv6HostOf(
            url: String,
            start: Int,
            end: Int,
        ): ByteArray? {
            if (start >= end || url[start] != '[') return null
            val close = url.indexOf(']', start + 1)
            if (close < 0 || close >= end || close <= start + 1) return null
            val zone = url.indexOf('%', start + 1)
            val addressEnd = if (zone in (start + 1) until close) zone else close
            return Ipv6.parse(url.substring(start + 1, addressEnd))
        }

        /**
         * Index of the first char of the authority: past `://`, or 0 for a schemeless host.
         *
         * The `://` only counts when what precedes it is a real RFC 3986 scheme
         * (`ALPHA *( ALPHA / DIGIT / "+" / "-" / "." )`). Otherwise `relay.com/x://127.0.0.1`
         * would have its *path* read as the authority and answer true to [isLocalHost].
         */
        private fun hostStart(url: String): Int {
            val scheme = url.indexOf("://")
            if (scheme <= 0 || !url[0].isLetter()) return 0
            for (i in 1 until scheme) {
                val c = url[i]
                if (!c.isLetterOrDigit() && c != '+' && c != '-' && c != '.') return 0
            }
            return scheme + 3
        }

        /** Index just past the authority — the first `/`, `?` or `#`, or the end of [url]. */
        private fun hostEnd(
            url: String,
            start: Int,
        ): Int {
            var i = start
            while (i < url.length) {
                val c = url[i]
                if (c == '/' || c == '?' || c == '#') return i
                i++
            }
            return i
        }

        /**
         * [end] trimmed back past a `:port` and any trailing dots, so the host tests see the
         * name alone. RFC 1034's fully-qualified form ends in a dot (`abc.onion.`), and missing
         * that spelling on [isOnion] would send a `.onion` name to the clearnet DNS resolver.
         */
        private fun hostEndWithoutPort(
            url: String,
            start: Int,
            end: Int,
        ): Int {
            var stop =
                if (url[start] == '[') {
                    // In an IPv6 authority only the `:` after the `]` can start a port.
                    val close = url.indexOf(']', start + 1)
                    if (close in start until end) close + 1 else end
                } else {
                    val colon = url.lastIndexOf(':', end - 1)
                    if (colon >= start) colon else end
                }
            while (stop > start && url[stop - 1] == '.') stop--
            return stop
        }

        // Host names are case-insensitive (RFC 4343), and `fix()` asks these questions *before*
        // the RFC 3986 pass folds the case — so a case-sensitive test gave `LOCALHOST:8080` and
        // `ABC.ONION:8080` a `wss://` scheme neither host can ever serve.
        private fun regionEndsWith(
            url: String,
            suffix: String,
            start: Int,
            end: Int,
        ): Boolean = end - start >= suffix.length && url.regionMatches(end - suffix.length, suffix, 0, suffix.length, ignoreCase = true)

        private fun regionEquals(
            url: String,
            name: String,
            start: Int,
            end: Int,
        ): Boolean = end - start == name.length && url.regionMatches(start, name, 0, name.length, ignoreCase = true)

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

        private fun norm(url: String) = NormalizedRelayUrl(canonicalizeIpv6Host(Rfc3986.normalize(url)))

        /**
         * Rewrites a bracketed IPv6 host into its RFC 5952 canonical form.
         *
         * RFC 4291 lets one address be spelled many ways, and the RFC 3986 pass only folds hex
         * case — so `[201:0d0e:9ba5:8bbc:0000:0000:0000:0001]` and `[201:d0e:9ba5:8bbc::1]`
         * survive as two different [NormalizedRelayUrl]s for one host. That value keys the
         * connection pool, the relay-list sets, the NIP-11 cache and the per-relay stats, so the
         * app would dial the same relay twice and count it twice. OkHttp canonicalizes to this
         * exact form when it dials, so folding here makes the stored key the host on the wire.
         *
         * Returns [url] itself — no allocation — when there is no literal or it is already
         * canonical, which is every url with a DNS host.
         */
        private fun canonicalizeIpv6Host(url: String): String {
            // Anchored to the authority: a `[...]` in a path or query is data, and rewriting it
            // would silently corrupt the url.
            val open = hostStart(url)
            if (open >= url.length || url[open] != '[') return url
            val close = url.indexOf(']', open + 1)
            if (close <= open + 1 || close >= hostEnd(url, open)) return url
            val inner = url.substring(open + 1, close)
            val canonical = Ipv6.canonicalizeOrNull(inner) ?: return url
            if (canonical == inner) return url
            return url.substring(0, open + 1) + canonical + url.substring(close)
        }

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

            // Trim trailing %20 (percent-encoded spaces from malformed event data).
            // The endsWith gate keeps the hot path allocation-free: trimEnd would
            // copy the string for ANY url merely ending in '%', '2' or '0' — which
            // includes every port ending in zero ("wss://host:3030").
            val url = if (rawUrl.endsWith("%20")) rawUrl.trimEnd('%', '2', '0') else rawUrl
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
            val protocolRelative = if (trimmed.startsWith("//")) trimmed.drop(2) else trimmed
            if (protocolRelative.length < 4) return null

            // A bare IPv6 literal is missing its brackets, not malformed. This is the shape a
            // user actually has in hand — `yggdrasilctl getSelf` prints the address unbracketed
            // — and without the brackets `isBareHostAndPath` rejects it below as a host with too
            // many colons. Only a string that parses as a whole address is bracketed, so an
            // addressable-event pointer (`31990:hex:dtag`) or a `host:port` still falls through.
            val bare =
                if (protocolRelative[0] != '[' && Ipv6.isLiteral(protocolRelative)) {
                    "[$protocolRelative]"
                } else {
                    protocolRelative
                }

            if (!isBareHostAndPath(bare)) {
                Log.d("RelayUrlNormalizer") { "Rejected $url" }
                return null
            }

            // Overlay and localhost relays cannot hold a certificate, so wss:// could only ever
            // fail its handshake. Both carry their own encryption, so ws:// is not a downgrade.
            return if (isOnion(bare) || isLocalHost(bare) || isOverlayNetwork(bare)) {
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
