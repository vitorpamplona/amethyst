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
package com.vitorpamplona.quartz.nip01Core.tags.references

import com.vitorpamplona.quartz.utils.Rfc3986

class HttpUrlFormatter {
    companion object {
        fun displayHost(url: String): String =
            url
                .trim()
                .removePrefix("https://")
                .removePrefix("http://")
                .removeSuffix("/")

        fun displayUrl(url: String): String =
            url
                .trim()
                .removePrefix("https://")
                .removePrefix("http://")
                .removeSuffix("/")

        fun addSchemeIfNeeded(url: String): String =
            if (!url.startsWith("https://") && !url.startsWith("http://")) {
                // TODO: How to identify relays on the local network?
                val isLocalHost = url.contains("127.0.0.1") || url.contains("localhost")
                if (url.endsWith(".onion") || url.endsWith(".onion/") || isLocalHost) {
                    "http://${url.trim()}"
                } else {
                    "https://${url.trim()}"
                }
            } else {
                url.trim()
            }

        fun normalize(url: String): String {
            val newUrl = addSchemeIfNeeded(url)

            return try {
                Rfc3986.normalize(newUrl)
            } catch (e: Exception) {
                newUrl
            }
        }

        fun isValidUrl(url: String): Boolean = Rfc3986.isValidUrl(addSchemeIfNeeded(url))
    }
}
