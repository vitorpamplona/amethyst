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
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.vitorpamplona.quartz.nipB7Blossom

import com.vitorpamplona.quartz.nip01Core.core.HexKey

/**
 * Parsed representation of a BUD-10 Blossom URI.
 *
 * Format: `blossom:<sha256>.<ext>[?parameters]`
 *
 * @param sha256      64-character lowercase hex hash of the blob.
 * @param extension   File extension (e.g. "jpg", "mp4"). Defaults to "bin" when unknown.
 * @param servers     Server base-URL hints where the blob may exist (`xs` params, tried first).
 * @param authors     Hex pubkeys of blob uploaders used for BUD-03 server-list lookup (`as` params).
 * @param size        Blob size in bytes for verification and progress display (`sz` param).
 */
data class BlossomUri(
    val sha256: HexKey,
    val extension: String,
    val servers: List<String>,
    val authors: List<HexKey>,
    val size: Long?,
) {
    /**
     * Serialises back to a canonical `blossom:` URI string.
     * Server URLs are percent-encoded so that `&`, `=`, and `#` inside them
     * do not break the query string.
     */
    fun toUriString(): String =
        buildString {
            append("blossom:")
            append(sha256)
            append('.')
            append(extension)
            val params =
                buildList {
                    servers.forEach { add("xs=${percentEncodeQueryValue(it)}") }
                    authors.forEach { add("as=$it") }
                    size?.let { add("sz=$it") }
                }
            if (params.isNotEmpty()) {
                append('?')
                append(params.joinToString("&"))
            }
        }

    companion object {
        private const val SCHEME = "blossom:"
        private val SHA256_REGEX = Regex("^[0-9a-f]{64}$")

        /**
         * Parses a BUD-10 URI string into a [BlossomUri], or returns `null` if the
         * input is not a valid blossom URI (wrong scheme or malformed SHA-256).
         *
         * `xs` and `as` parameters may appear multiple times; all values are collected.
         */
        fun parse(uri: String): BlossomUri? {
            if (!uri.startsWith(SCHEME, ignoreCase = true)) return null
            val rest = uri.substring(SCHEME.length)

            val queryIndex = rest.indexOf('?')
            val pathPart = if (queryIndex >= 0) rest.substring(0, queryIndex) else rest
            val queryPart = if (queryIndex >= 0) rest.substring(queryIndex + 1) else ""

            // Separate sha256 from extension at the last dot.
            val dotIndex = pathPart.lastIndexOf('.')
            val sha256: String
            val extension: String
            if (dotIndex > 0) {
                sha256 = pathPart.substring(0, dotIndex).lowercase()
                extension = pathPart.substring(dotIndex + 1).ifEmpty { "bin" }
            } else {
                sha256 = pathPart.lowercase()
                extension = "bin"
            }

            if (!SHA256_REGEX.matches(sha256)) return null

            // Collect repeated query parameters.
            val servers = mutableListOf<String>()
            val authors = mutableListOf<HexKey>()
            var size: Long? = null

            if (queryPart.isNotEmpty()) {
                for (param in queryPart.split('&')) {
                    val eqIndex = param.indexOf('=')
                    if (eqIndex < 0) continue
                    val key = param.substring(0, eqIndex)
                    val value = percentDecode(param.substring(eqIndex + 1))
                    when (key) {
                        "xs" -> if (value.isNotEmpty()) servers.add(value)
                        "as" -> if (value.isNotEmpty()) authors.add(value)
                        "sz" -> value.toLongOrNull()?.let { size = it }
                    }
                }
            }

            return BlossomUri(
                sha256 = sha256,
                extension = extension,
                servers = servers,
                authors = authors,
                size = size,
            )
        }

        /** Decodes percent-encoded sequences (e.g. `%3A` → `:`). */
        private fun percentDecode(input: String): String {
            if ('%' !in input) return input
            val sb = StringBuilder(input.length)
            var i = 0
            while (i < input.length) {
                val c = input[i]
                if (c == '%' && i + 2 < input.length) {
                    val hex = input.substring(i + 1, i + 3)
                    val code = hex.toIntOrNull(16)
                    if (code != null) {
                        sb.append(code.toChar())
                        i += 3
                        continue
                    }
                }
                sb.append(c)
                i++
            }
            return sb.toString()
        }

        /**
         * Percent-encodes characters that are not safe inside a query-string value.
         * Letters, digits, and unreserved chars (`-._~`) pass through unchanged.
         * Everything else (notably `&`, `=`, `#`, and non-ASCII) is encoded.
         */
        private fun percentEncodeQueryValue(input: String): String {
            val sb = StringBuilder(input.length)
            for (c in input) {
                if (c.isLetterOrDigit() || c in "-._~:/?@!$'()*+,;") {
                    sb.append(c)
                } else {
                    for (b in c.toString().encodeToByteArray()) {
                        sb.append('%')
                        sb.append((b.toInt() and 0xFF).toString(16).padStart(2, '0').uppercase())
                    }
                }
            }
            return sb.toString()
        }
    }
}
