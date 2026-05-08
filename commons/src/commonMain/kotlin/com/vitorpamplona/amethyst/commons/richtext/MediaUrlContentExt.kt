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
private val sha256InPathRegex = Regex("(?<![0-9a-fA-F])[0-9a-fA-F]{64}(?![0-9a-fA-F])")

/**
 * Converts this media content into a Coil/ExoPlayer-friendly model string.
 *
 * When the local-Blossom-cache bridge is active and the content has a
 * known sha256 hash, returns a `blossom:<sha256>.<ext>?xs=<originalHostBase>&as=<authorPubKey>`
 * URI. The Coil pipeline recognises the scheme and routes the request
 * through `BlossomServerResolver`, which short-circuits to the local cache
 * at `127.0.0.1:24242`.
 *
 * Otherwise (bridge off, no hash, hash invalid, already a `blossom:` URI,
 * or a live stream) returns the original URL unchanged so today's
 * direct-to-CDN behaviour is preserved.
 */
fun MediaUrlContent.toCoilModel(useLocalBlossomBridge: Boolean): String =
    bridgeUrl(
        url = url,
        useBridge = useLocalBlossomBridge,
        explicitHash = hash,
        mimeType = mimeType,
        authorPubKey = authorPubKey,
        skipBridge = this is MediaUrlVideo && isLiveStream,
    )

/**
 * Bridge entry point for raw URL strings (e.g. profile pictures) that go
 * through a Coil fetcher routed by type (`ProfilePictureUrl`) and therefore
 * bypass [com.vitorpamplona.quartz.nipB7Blossom.BlossomUri] processing
 * entirely.
 *
 * Returns a direct `http://127.0.0.1:24242/<sha>.<ext>?xs=<host>&as=<pubkey>`
 * URL so the request can flow through `NetworkFetcher` unchanged. Falls
 * back to the original URL when no sha256 can be recovered from the path
 * or when the bridge is off.
 *
 * @param localCacheBase the local Blossom cache origin (default `http://127.0.0.1:24242`).
 * @param authorPubKey 64-char lowercase hex pubkey appended as `as=` so the
 *                     cache can consult that author's BUD-03 server list on miss.
 */
fun bridgeProfilePictureUrl(
    url: String?,
    useBridge: Boolean,
    authorPubKey: String? = null,
    localCacheBase: String = DEFAULT_LOCAL_CACHE_BASE,
): String? {
    if (url == null) return null
    if (!useBridge) return url
    if (url.startsWith("blossom:", ignoreCase = true)) return url
    if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) return url

    val sha = extractSha256FromUrlPath(url) ?: return url
    val ext = guessExtension(url, null)
    val hostBase = extractHostBase(url) ?: return url

    val params =
        buildList {
            add("xs=${percentEncode(hostBase)}")
            authorPubKey
                ?.lowercase()
                ?.takeIf { sha256HexRegex.matches(it) }
                ?.let { add("as=$it") }
        }

    return "${localCacheBase.removeSuffix("/")}/$sha.$ext?${params.joinToString("&")}"
}

const val DEFAULT_LOCAL_CACHE_BASE = "http://127.0.0.1:24242"

private fun bridgeUrl(
    url: String,
    useBridge: Boolean,
    explicitHash: String?,
    mimeType: String?,
    authorPubKey: String?,
    skipBridge: Boolean,
): String {
    if (!useBridge || skipBridge) return url
    if (url.startsWith("blossom:", ignoreCase = true)) return url
    if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) return url

    val sha =
        explicitHash?.lowercase()?.takeIf { sha256HexRegex.matches(it) }
            ?: extractSha256FromUrlPath(url)
            ?: return url

    val ext = guessExtension(url, mimeType)
    val hostBase = extractHostBase(url) ?: return url

    val authors =
        authorPubKey
            ?.lowercase()
            ?.takeIf { sha256HexRegex.matches(it) }
            ?.let { listOf(it) }
            ?: emptyList()

    return BlossomUri(
        sha256 = sha,
        extension = ext,
        servers = listOf(hostBase),
        authors = authors,
        size = null,
    ).toUriString()
}

private fun percentEncode(input: String): String {
    val sb = StringBuilder(input.length)
    for (c in input) {
        if (c.isLetterOrDigit() || c in "-._~") {
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

private fun extractSha256FromUrlPath(url: String): String? {
    val pathPart = url.substringBefore('?').substringBefore('#')
    val match = sha256InPathRegex.find(pathPart) ?: return null
    return match.value.lowercase()
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
