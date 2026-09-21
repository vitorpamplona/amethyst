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
package com.vitorpamplona.quartz.utils

import java.net.URI
import java.net.URLDecoder

actual class UriParser actual constructor(
    uri: String,
) {
    private val myUri = URI.create(uri)

    /**
     * The query string, including the one [java.net.URI] refuses to find.
     *
     * A URI whose scheme is followed by anything other than `/` is *opaque* to `java.net.URI`:
     * `amethyst+walletconnect:dlnwc?value=...` has no query at all as far as it is concerned, and
     * the whole `dlnwc?value=...` is one scheme-specific part. Every other actual of this class
     * looks for `?` wherever it sits, so on JVM and Android alone the parameters of an opaque URI
     * silently vanished -- which is how a perfectly valid wallet-connect deep link came back as
     * "that uri was invalid", while the `//` spelling of the very same link worked.
     *
     * The fragment needs no such rescue: `java.net.URI` does parse `#...` off an opaque URI.
     */
    private val rawQuery: String? =
        myUri.rawQuery
            ?: myUri
                .takeIf { it.isOpaque }
                ?.rawSchemeSpecificPart
                ?.substringAfter('?', "")
                ?.ifBlank { null }

    private val queryParameters: Map<String, List<String>> by lazy {
        rawQuery?.ifBlank { null }?.let { query ->
            val params = mutableMapOf<String, MutableList<String>>()

            query.split('&').forEach { paramValue ->
                val parts = paramValue.split("=", limit = 2)
                val currentValue =
                    params.getOrPut(parts[0]) {
                        mutableListOf()
                    }

                if (parts.size == 2) {
                    currentValue.add(URLDecoder.decode(parts[1], "UTF-8"))
                } else {
                    currentValue.add("")
                }
            }

            params
        } ?: emptyMap()
    }

    private val fragments: Map<String, String> by lazy {
        myUri.rawFragment?.ifBlank { null }?.let { keyValuePair ->
            keyValuePair.split('&').associate { paramValue ->
                val parts = paramValue.split("=", limit = 2)
                if (parts.size == 2) {
                    parts[0] to URLDecoder.decode(parts[1], "UTF-8")
                } else {
                    parts[0] to "" // Handle parameters without a value
                }
            }
        } ?: emptyMap()
    }

    actual fun scheme(): String? = myUri.scheme

    actual fun host(): String? = myUri.host

    actual fun port(): Int? {
        val port = myUri.port
        return if (port == -1) null else port
    }

    actual fun path(): String? = myUri.path

    actual fun queryParameterNames(): Set<String> = queryParameters.keys

    actual fun getQueryParameter(param: String): List<String>? = queryParameters[param]

    actual fun fragments(): Map<String, String> = fragments
}
