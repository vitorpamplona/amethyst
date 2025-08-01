/**
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

import android.util.Log
import androidx.collection.LruCache
import kotlinx.coroutines.CancellationException
import org.czeal.rfc3986.URIReference
import kotlin.contracts.ExperimentalContracts

val normalizedUrls = LruCache<String, NormalizedRelayUrl>(5000)

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

        private fun norm(url: String) =
            NormalizedRelayUrl(
                URIReference
                    .parse(url)
                    .normalize()
                    .toString()
                    .intern(),
            )

        @OptIn(ExperimentalContracts::class)
        fun fix(url: String): String? {
            if (url.length < 3) return null

            val trimmed =
                if (url[0].isWhitespace() || url[url.length - 1].isWhitespace()) {
                    url.trim()
                } else {
                    url
                }

            // fast for good wss:// urls
            if (isRelaySchemePrefix(trimmed)) {
                if (isRelaySchemePrefixSecure(trimmed) || isRelaySchemePrefixInsecure(trimmed)) {
                    return trimmed
                }
            }

            // fast for good https:// urls
            if (isHttpPrefix(trimmed)) {
                if (isHttpSSuffix(trimmed)) {
                    // https://
                    return "wss://${trimmed.drop(8)}"
                } else if (isHttpSuffix(trimmed)) {
                    // http://
                    return "ws://${trimmed.drop(7)}"
                }
            }

            // fast for good ww:// urls
            if (trimmed.startsWith("ww://")) {
                return "wss://${trimmed.drop(5)}"
            }

            // fast for good ww:// urls
            if (trimmed.startsWith("was://")) {
                return "wss://${trimmed.drop(6)}"
            }

            // fast for good ww:// urls
            if (trimmed.startsWith("Wws://")) {
                return "wss://${trimmed.drop(6)}"
            }

            if (trimmed.contains("://")) {
                // some other scheme we cannot connect to.
                Log.w("RelayUrlNormalizer", "Rejected relay URL: $url")
                return null
            }

            return if (isOnion(trimmed) || isLocalHost(trimmed)) {
                "ws://$trimmed"
            } else {
                "wss://$trimmed"
            }
        }

        fun normalize(url: String): NormalizedRelayUrl {
            normalizedUrls[url]?.let { return it }

            return try {
                val fixed = fix(url) ?: return NormalizedRelayUrl(url)
                val normalized = norm(fixed)
                normalizedUrls.put(url, normalized)
                normalized
            } catch (e: Exception) {
                NormalizedRelayUrl(url)
            }
        }

        fun normalizeOrNull(url: String): NormalizedRelayUrl? {
            if (url.isEmpty()) return null
            normalizedUrls[url]?.let { return it }

            return try {
                val fixed = fix(url)
                if (fixed != null) {
                    val normalized = norm(fixed)
                    normalizedUrls.put(url, normalized)
                    return normalized
                } else {
                    Log.w("NormalizedRelayUrl", "Rejected Error $url")
                    null
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.w("NormalizedRelayUrl", "Rejected Error $url")
                null
            }
        }
    }
}
