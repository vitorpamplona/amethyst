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
     * Parsed once and reused, mirroring the JVM actual's lazy map. The previous version
     * re-split the entire query string on every [getQueryParameter] call, so a URI read
     * for four parameters was parsed four times.
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
                    currentValue.add(percentDecode(parts[1]))
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
                    parts[0] to percentDecode(parts[1])
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

/**
 * `java.net.URLDecoder.decode(value, "UTF-8")` — which is literally what the JVM actual
 * calls — for a target with no `java.net`.
 *
 * This is the whole reason NIP-47 failed on linuxX64: the parser returned query values
 * exactly as they appeared in the URI, so `relay=wss%3A%2F%2Frelay.damus.io` reached
 * `RelayUrlNormalizer` still percent-encoded and came back "Invalid relay Url". Decoding
 * belongs here rather than in each caller, because the JVM and Apple actuals both hand
 * back decoded values and common code is written against that.
 *
 * Matches `URLDecoder` in the details that are observable: `+` becomes a space, a run of
 * consecutive `%XX` is decoded as one UTF-8 sequence (so multi-byte characters survive),
 * every other character passes through, and a malformed escape throws
 * [IllegalArgumentException] rather than being silently kept — the same failure the JVM
 * gives for the same input.
 */
private fun percentDecode(value: String): String {
    // The short-circuit URLDecoder also makes: with nothing to change, return the
    // original instance rather than rebuilding it. Most query values hit this.
    if (value.indexOf('%') < 0 && value.indexOf('+') < 0) return value

    val result = StringBuilder(value.length)
    var index = 0
    // Sized on first use for the longest run that could still follow, then reused —
    // one allocation for the whole string, as in URLDecoder.
    var escaped: ByteArray? = null

    while (index < value.length) {
        when (val char = value[index]) {
            '+' -> {
                result.append(' ')
                index++
            }

            '%' -> {
                val buffer = escaped ?: ByteArray((value.length - index) / 3).also { escaped = it }
                var count = 0
                while (index + 2 < value.length && value[index] == '%') {
                    buffer[count++] = decodeEscape(value, index)
                    index += 3
                }
                if (index < value.length && value[index] == '%') {
                    throw IllegalArgumentException("URLDecoder: Incomplete trailing escape (%) pattern")
                }
                result.append(buffer.decodeToString(0, count))
            }

            else -> {
                result.append(char)
                index++
            }
        }
    }

    return result.toString()
}

private fun decodeEscape(
    value: String,
    index: Int,
): Byte {
    val high = hexDigit(value[index + 1])
    val low = hexDigit(value[index + 2])
    if (high < 0 || low < 0) {
        throw IllegalArgumentException(
            "URLDecoder: Illegal hex characters in escape (%) pattern - ${value.substring(index, index + 3)}",
        )
    }
    return ((high shl 4) or low).toByte()
}

private fun hexDigit(char: Char): Int =
    when (char) {
        in '0'..'9' -> char - '0'
        in 'a'..'f' -> char - 'a' + 10
        in 'A'..'F' -> char - 'A' + 10
        else -> -1
    }
