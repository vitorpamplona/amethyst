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
package com.vitorpamplona.quartz.utils

import org.czeal.rfc3986.URIReference

actual object Rfc3986 {
    actual fun normalize(uri: String) =
        URIReference
            .parse(uri)
            .normalize()
            .toString()

    actual fun isValidUrl(url: String): Boolean =
        runCatching {
            URIReference.parse(url)
        }.isSuccess

    actual fun normalizeAndRemoveFragment(url: String): String =
        URIReference
            .parse(url)
            .normalize()
            .toStringNoFragment()
            .intern()

    actual fun host(url: String): String = URIReference.parse(url).host.value
}

fun URIReference.toStringSchemeHost(): String {
    val sb = StringBuilder()

    if (scheme != null) sb.append(scheme).append(":")
    if (authority != null) sb.append("//").append(authority.toString())

    return sb.toString()
}

fun URIReference.toStringNoFragment(): String {
    val sb = StringBuilder()

    if (scheme != null) sb.append(scheme).append(":")
    if (authority != null) sb.append("//").append(authority.toString())
    if (path != null) sb.append(path)
    if (query != null) sb.append("?").append(query)

    return sb.toString()
}
