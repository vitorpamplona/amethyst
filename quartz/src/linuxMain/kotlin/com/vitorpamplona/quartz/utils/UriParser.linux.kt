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

actual class UriParser actual constructor(
    uri: String,
) {
    private val parsedScheme: String?
    private val parsedHost: String?
    private val parsedPort: Int?
    private val parsedPath: String?
    private val parsedQuery: String?
    private val parsedFragment: String?

    init {
        var remaining = uri

        // Parse scheme
        val schemeEnd = remaining.indexOf("://")
        if (schemeEnd >= 0) {
            parsedScheme = remaining.substring(0, schemeEnd)
            remaining = remaining.substring(schemeEnd + 3)
        } else {
            parsedScheme = null
        }

        // Parse fragment
        val fragmentStart = remaining.indexOf('#')
        if (fragmentStart >= 0) {
            parsedFragment = remaining.substring(fragmentStart + 1)
            remaining = remaining.substring(0, fragmentStart)
        } else {
            parsedFragment = null
        }

        // Parse query
        val queryStart = remaining.indexOf('?')
        if (queryStart >= 0) {
            parsedQuery = remaining.substring(queryStart + 1)
            remaining = remaining.substring(0, queryStart)
        } else {
            parsedQuery = null
        }

        // Parse host and port
        val pathStart = remaining.indexOf('/')
        val authority =
            if (pathStart >= 0) {
                val auth = remaining.substring(0, pathStart)
                remaining = remaining.substring(pathStart)
                auth
            } else {
                val auth = remaining
                remaining = ""
                auth
            }

        // Remove userinfo
        val atIndex = authority.lastIndexOf('@')
        val hostPort = if (atIndex >= 0) authority.substring(atIndex + 1) else authority

        // Handle IPv6
        if (hostPort.startsWith("[")) {
            val bracketEnd = hostPort.indexOf(']')
            if (bracketEnd >= 0) {
                parsedHost = hostPort.substring(1, bracketEnd)
                val afterBracket = hostPort.substring(bracketEnd + 1)
                parsedPort =
                    if (afterBracket.startsWith(":")) {
                        afterBracket.substring(1).toIntOrNull()
                    } else {
                        null
                    }
            } else {
                parsedHost = hostPort
                parsedPort = null
            }
        } else {
            val colonIndex = hostPort.lastIndexOf(':')
            if (colonIndex >= 0) {
                val potentialPort = hostPort.substring(colonIndex + 1).toIntOrNull()
                if (potentialPort != null) {
                    parsedHost = hostPort.substring(0, colonIndex)
                    parsedPort = potentialPort
                } else {
                    parsedHost = hostPort
                    parsedPort = null
                }
            } else {
                parsedHost = hostPort.ifEmpty { null }
                parsedPort = null
            }
        }

        parsedPath = remaining.ifEmpty { null }
    }

    actual fun scheme(): String? = parsedScheme

    actual fun host(): String? = parsedHost

    actual fun port(): Int? = parsedPort

    actual fun path(): String? = parsedPath

    /**
     * Parsed once and reused, mirroring the JVM actual's lazy map — the previous version
     * re-split the entire query string on every [getQueryParameter] call.
     *
     * Decoded with [UrlEncoder.decode], which matches `URLDecoder.decode(.., "UTF-8")` —
     * what the JVM actual calls. Skipping this is why NIP-47 failed on this target:
     * `relay=wss%3A%2F%2Frelay.damus.io` reached `RelayUrlNormalizer` still encoded and
     * came back "Invalid relay Url".
     */
    private val queryParameters: Map<String, List<String>> by lazy {
        parsedQuery?.ifBlank { null }?.let { query ->
            val params = mutableMapOf<String, MutableList<String>>()

            query.split('&').forEach { paramValue ->
                val parts = paramValue.split("=", limit = 2)
                val currentValue =
                    params.getOrPut(parts[0]) {
                        mutableListOf()
                    }

                if (parts.size == 2) {
                    currentValue.add(UrlEncoder.decode(parts[1]))
                } else {
                    currentValue.add("")
                }
            }

            params
        } ?: emptyMap()
    }

    private val parsedFragments: Map<String, String> by lazy {
        parsedFragment?.ifBlank { null }?.let { keyValuePair ->
            keyValuePair.split('&').associate { paramValue ->
                val parts = paramValue.split("=", limit = 2)
                if (parts.size == 2) {
                    parts[0] to UrlEncoder.decode(parts[1])
                } else {
                    parts[0] to "" // Handle parameters without a value
                }
            }
        } ?: emptyMap()
    }

    actual fun queryParameterNames(): Set<String> = queryParameters.keys

    /** Null — not an empty list — when the parameter is absent, as on the JVM. */
    actual fun getQueryParameter(param: String): List<String>? = queryParameters[param]

    actual fun fragments(): Map<String, String> = parsedFragments
}
