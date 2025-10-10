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

        @OptIn(ExperimentalContracts::class)
        fun fix(url: String): String? {
            if (url.length < 4) return null
            if (url.contains("%00") || url.contains("%20")) return null

            if (url.length > 50) {
                // removes multiple urls in the same line
                val schemeIdx = url.indexOf("://")
                val nextScheme = url.indexOf("://", schemeIdx + 3)
                if (nextScheme > 0) {
                    return null
                }
            }

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

            // fast for good ww:// urls
            if (trimmed.startsWith("Wss://")) {
                return "wss://${trimmed.drop(6)}"
            }

            if (trimmed.contains("://")) {
                // some other scheme we cannot connect to.
                Log.w("RelayUrlNormalizer", "Rejected $url")
                return null
            }

            return if (isOnion(trimmed) || isLocalHost(trimmed)) {
                "ws://$trimmed"
            } else {
                "wss://$trimmed"
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
                    normalizedUrls.put(url, NormalizationResult.Success(normalized))
                    normalized
                } else {
                    Log.w("NormalizedRelayUrl", "Rejected $url")
                    normalizedUrls.put(url, NormalizationResult.Error)
                    null
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                normalizedUrls.put(url, NormalizationResult.Error)
                Log.w("NormalizedRelayUrl", "Rejected $url")
                null
            }
        }
    }
}
