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
package com.vitorpamplona.amethyst.commons.richtext

import com.vitorpamplona.quartz.nipB7Blossom.BlossomUri

private val sha256HexRegex = Regex("[0-9a-f]{64}")

/**
 * Converts this media content into a Coil/ExoPlayer-friendly model string.
 *
 * When the local-Blossom-cache bridge is active and the content has a
 * known sha256 hash, returns a `blossom:<sha256>.<ext>?xs=<originalHostBase>`
 * URI. The Coil pipeline will recognise the scheme and route the request
 * through `BlossomServerResolver`, which in turn short-circuits to the
 * local cache at `127.0.0.1:24242`.
 *
 * Otherwise (bridge off, no hash, hash invalid, already a `blossom:` URI,
 * or a live stream) returns the original URL unchanged so today's
 * direct-to-CDN behaviour is preserved.
 */
fun MediaUrlContent.toCoilModel(useLocalBlossomBridge: Boolean): String {
    if (!useLocalBlossomBridge) return url
    if (this is MediaUrlVideo && isLiveStream) return url
    val sha = hash?.lowercase() ?: return url
    if (!sha256HexRegex.matches(sha)) return url
    if (url.startsWith("blossom:", ignoreCase = true)) return url
    if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) return url

    val ext = guessExtension(url, mimeType)
    val hostBase = extractHostBase(url) ?: return url

    return BlossomUri(
        sha256 = sha,
        extension = ext,
        servers = listOf(hostBase),
        authors = emptyList(),
        size = null,
    ).toUriString()
}

private fun guessExtension(
    url: String,
    mimeType: String?,
): String {
    val pathPart = url.substringBefore('?').substringBefore('#')
    val lastDot = pathPart.lastIndexOf('.')
    val lastSlash = pathPart.lastIndexOf('/')
    if (lastDot > lastSlash && lastDot >= 0) {
        val ext = pathPart.substring(lastDot + 1).lowercase()
        if (ext.isNotEmpty() && ext.length <= 8 && ext.all { it.isLetterOrDigit() }) {
            return ext
        }
    }

    if (mimeType != null) {
        for ((extension, mt) in mimeTypeMap) {
            if (mt.equals(mimeType, ignoreCase = true)) return extension
        }
    }

    return "bin"
}

private fun extractHostBase(url: String): String? {
    val schemeEnd = url.indexOf("://")
    if (schemeEnd < 0) return null
    val afterScheme = schemeEnd + 3
    var end = url.length
    for (i in afterScheme until url.length) {
        val c = url[i]
        if (c == '/' || c == '?' || c == '#') {
            end = i
            break
        }
    }
    if (end <= afterScheme) return null
    return url.substring(0, end)
}
