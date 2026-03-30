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

    actual fun queryParameterNames(): Set<String> {
        val query = parsedQuery ?: return emptySet()
        return query
            .split('&')
            .mapNotNull { param ->
                val eqIndex = param.indexOf('=')
                if (eqIndex >= 0) param.substring(0, eqIndex) else param
            }.toSet()
    }

    actual fun getQueryParameter(param: String): String? {
        val query = parsedQuery ?: return null
        return query
            .split('&')
            .firstOrNull { part ->
                val eqIndex = part.indexOf('=')
                if (eqIndex >= 0) part.substring(0, eqIndex) == param else part == param
            }?.let { part ->
                val eqIndex = part.indexOf('=')
                if (eqIndex >= 0) part.substring(eqIndex + 1) else ""
            }
    }

    val fragments: Map<String, String> by lazy {
        parsedFragment?.ifBlank { null }?.let { keyValuePair ->
            keyValuePair.split('&').associate { paramValue ->
                val parts = paramValue.split("=", limit = 2)
                if (parts.size == 2) {
                    parts[0] to parts[1]
                } else {
                    parts[0] to ""
                }
            }
        } ?: emptyMap()
    }

    actual fun fragments(): Map<String, String> = fragments
}
