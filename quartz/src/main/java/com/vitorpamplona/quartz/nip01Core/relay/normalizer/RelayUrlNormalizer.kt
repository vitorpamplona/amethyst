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
import org.czeal.rfc3986.URIReference

val normalizedUrls = LruCache<String, NormalizedRelayUrl>(5000)

class RelayUrlNormalizer {
    companion object {
        fun isLocalHost(url: String) = url.contains("127.0.0.1") || url.contains("localhost")

        fun isOnion(url: String) = url.endsWith(".onion") || url.endsWith(".onion/")

        private fun norm(url: String) =
            NormalizedRelayUrl(
                URIReference
                    .parse(url)
                    .normalize()
                    .toString()
                    .intern(),
            )

        fun fix(url: String): String {
            val trimmed = url.trim()

            // fast
            if (trimmed.length > 4 && trimmed[0] == 'w' && trimmed[1] == 's') {
                if (trimmed[2] == 's' && trimmed[3] == ':' && trimmed[4] == '/' && trimmed[5] == '/') {
                    return trimmed
                } else if (trimmed[2] == ':' && trimmed[3] == '/' && trimmed[4] == '/') {
                    return trimmed
                }
            }

            // fast
            if (trimmed.length > 8 && trimmed[0] == 'h' && trimmed[1] == 't' && trimmed[2] == 't' && trimmed[3] == 'p') {
                if (trimmed[4] == 's' && trimmed[5] == ':' && trimmed[6] == '/' && trimmed[7] == '/') {
                    // https://
                    return "wss://${trimmed.drop(8)}"
                } else if (trimmed[4] == ':' && trimmed[5] == '/' && trimmed[6] == '/') {
                    // http://
                    return "ws://${trimmed.drop(7)}"
                }
            }

            return if (isOnion(trimmed) || isLocalHost(trimmed)) {
                "ws://$trimmed"
            } else {
                "wss://$trimmed"
            }
        }

        fun normalize(url: String): NormalizedRelayUrl {
            normalizedUrls.get(url)?.let { return it }

            return try {
                val normalized = norm(fix(url))
                normalizedUrls.put(url, normalized)
                normalized
            } catch (e: Exception) {
                NormalizedRelayUrl(url)
            }
        }

        fun normalizeOrNull(url: String): NormalizedRelayUrl? {
            normalizedUrls.get(url)?.let { return it }

            return try {
                val normalized = norm(fix(url))
                normalizedUrls.put(url, normalized)
                normalized
            } catch (e: Exception) {
                null
            }
        }
    }
}
