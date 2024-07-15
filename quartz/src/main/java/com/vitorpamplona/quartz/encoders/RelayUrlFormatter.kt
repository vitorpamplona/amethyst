/**
 * Copyright (c) 2024 Vitor Pamplona
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
package com.vitorpamplona.quartz.encoders

import org.czeal.rfc3986.URIReference

class RelayUrlFormatter {
    companion object {
        fun displayUrl(url: String): String =
            url
                .trim()
                .removePrefix("wss://")
                .removePrefix("ws://")
                .removeSuffix("/")

        fun isLocalHost(url: String) = url.contains("127.0.0.1") || url.contains("localhost")

        fun isOnion(url: String) = url.endsWith(".onion") || url.endsWith(".onion/")

        fun normalize(url: String): String {
            val newUrl =
                if (!url.startsWith("wss://") && !url.startsWith("ws://")) {
                    if (isOnion(url) || isLocalHost(url)) {
                        "ws://${url.trim()}"
                    } else {
                        "wss://${url.trim()}"
                    }
                } else {
                    url.trim()
                }

            return try {
                URIReference.parse(newUrl).normalize().toString()
            } catch (e: Exception) {
                newUrl
            }
        }

        fun normalizeOrNull(url: String): String? {
            val newUrl =
                if (!url.startsWith("wss://") && !url.startsWith("ws://")) {
                    if (isOnion(url) || isLocalHost(url)) {
                        "ws://${url.trim()}"
                    } else {
                        "wss://${url.trim()}"
                    }
                } else {
                    url.trim()
                }

            return try {
                URIReference.parse(newUrl).normalize().toString()
            } catch (e: Exception) {
                null
            }
        }

        fun getHttpsUrl(dirtyUrl: String): String =
            if (dirtyUrl.contains("://")) {
                dirtyUrl.replace("wss://", "https://").replace("ws://", "http://")
            } else {
                "https://$dirtyUrl"
            }
    }
}
