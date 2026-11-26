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
package com.vitorpamplona.amethyst.commons.preview

/**
 * A `Content-Type` header, parsed the way OkHttp's `String.toMediaType()` parses it (which the
 * preview fetch used when it was JVM-only): `type/subtype` then `; name=value` parameters, type and
 * subtype lowercased, and [IllegalArgumentException] for a header that is not one.
 */
internal class PreviewMediaType private constructor(
    private val mediaType: String,
    val type: String,
    val subtype: String,
    private val charset: String?,
) {
    /** The `charset` parameter, or null when there is none or this platform cannot decode it. */
    fun charsetName(): String? = charset?.takeIf { isSupportedCharset(it) }

    /** The header as the server sent it. */
    override fun toString(): String = mediaType

    companion object {
        private const val TOKEN = "([a-zA-Z0-9-!#$%&'*+.^_`{|}~]+)"
        private const val QUOTED = "\"([^\"]*)\""
        private val TYPE_SUBTYPE = Regex("$TOKEN/$TOKEN")
        private val PARAMETER = Regex(";\\s*(?:$TOKEN=(?:$TOKEN|$QUOTED))?")

        fun parse(header: String): PreviewMediaType {
            val typeSubtype = TYPE_SUBTYPE.matchAt(header, 0) ?: throw IllegalArgumentException("No subtype found for: \"$header\"")
            val type = typeSubtype.groupValues[1].lowercase()
            val subtype = typeSubtype.groupValues[2].lowercase()

            var charset: String? = null
            var at = typeSubtype.range.last + 1
            while (at < header.length) {
                val parameter =
                    PARAMETER.matchAt(header, at)
                        ?: throw IllegalArgumentException("Parameter is not formatted correctly: \"${header.substring(at)}\" for: \"$header\"")
                at = parameter.range.last + 1

                val name = parameter.groups[1]?.value ?: continue
                val token = parameter.groups[2]?.value
                val value =
                    when {
                        token == null -> parameter.groups[3]?.value ?: ""
                        // Single quotes are not part of the spec, but servers send them.
                        token.startsWith("'") && token.endsWith("'") && token.length > 2 -> token.substring(1, token.length - 1)
                        else -> token
                    }
                if (charset == null && name.equals("charset", ignoreCase = true)) charset = value
            }
            return PreviewMediaType(header, type, subtype, charset)
        }
    }
}
