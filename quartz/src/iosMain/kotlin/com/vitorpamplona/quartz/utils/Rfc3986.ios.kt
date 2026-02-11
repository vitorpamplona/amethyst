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

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSURLComponents
import swiftbridge.Rfc3986UriBridge

@OptIn(ExperimentalForeignApi::class)
actual object Rfc3986 {
    private val rfc3986UriBridge = Rfc3986UriBridge()

    actual fun normalize(uri: String): String =
        rfc3986UriBridge
            .normalizeUrlWithUrl(uri, null)
            .let { if (it?.last() == '/') it else "$it/" }

    actual fun isValidUrl(url: String): Boolean = rfc3986UriBridge.isUrlValidWithUrl(url)

    actual fun normalizeAndRemoveFragment(url: String): String =
        NSURLComponents(url)
            .toStringNoFragment()
            .internIfPossible()

    actual fun host(url: String): String = rfc3986UriBridge.hostFromUriWithUrl(url, null) ?: throw Exception("Could not retrieve host from URL.")
}

fun NSURLComponents.toStringNoFragment(): String {
    val sb = StringBuilder()

    if (scheme != null) sb.append(scheme).append(":")
    if (host != null) sb.append("//").append(host.toString())
    if (path != null) sb.append(path)
    if (query != null) sb.append("?").append(query)

    return sb.toString()
}
