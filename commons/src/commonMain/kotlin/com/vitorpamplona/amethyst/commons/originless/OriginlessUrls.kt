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
package com.vitorpamplona.amethyst.commons.originless

/**
 * Originless is a NIP-96-style HTTP node: `POST /upload` (multipart field `file`, no auth)
 * returns a CID, and `GET /ipfs/{cid}` serves that pin. Notes store `ipfs://{cid}`; fetches
 * rewrite that URI through this node's gateway.
 */
object OriginlessUrls {
    const val DEFAULT_SERVER = "https://originless.gupt.app"
    const val PROJECT_URL = "https://github.com/besoeasy/Originless"

    private val mediaTypes =
        setOf(
            "image/jpeg",
            "image/jpg",
            "image/png",
            "image/gif",
            "image/webp",
        )

    fun normalizeBase(url: String): String {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return DEFAULT_SERVER
        val withScheme =
            if (trimmed.startsWith("https://", ignoreCase = true) ||
                trimmed.startsWith("http://", ignoreCase = true)
            ) {
                trimmed
            } else {
                "https://$trimmed"
            }
        return withScheme.trimEnd('/')
    }

    /**
     * Dedupes and normalizes a user-configured Originless list. Empty input
     * stays empty — there is no implied upload target and [DEFAULT_SERVER]
     * is never injected here (it is only a fetch fallback).
     */
    fun normalizeList(urls: List<String>): List<String> {
        val seen = mutableListOf<String>()
        urls.forEach { raw ->
            val trimmed = raw.trim()
            if (trimmed.isNotEmpty()) {
                val normalized = normalizeBase(trimmed)
                if (normalized !in seen) seen.add(normalized)
            }
        }
        return seen
    }

    /** Nodes to `POST /upload` (or `/media`) to. Empty means do not upload. */
    fun uploadTargets(urls: List<String>): List<String> = normalizeList(urls)

    fun uploadUrl(base: String): String = "${normalizeBase(base)}/upload"

    /** Originless `POST /media` — opt-in EXIF/GPS/XMP strip for JPEG/PNG/GIF/WebP, then pin. */
    fun mediaUrl(base: String): String = "${normalizeBase(base)}/media"

    fun healthUrl(base: String): String = "${normalizeBase(base)}/health"

    fun isMediaEndpointType(contentType: String?): Boolean {
        val type = contentType?.substringBefore(';')?.trim()?.lowercase() ?: return false
        return type in mediaTypes
    }

    fun gatewayPrefix(base: String): String = "${normalizeBase(base)}/ipfs/"

    fun gatewayUrl(
        base: String,
        cid: String,
    ): String = "${gatewayPrefix(base)}${cid.trim().removePrefix("/")}"

    fun toIpfsUri(cid: String): String {
        val clean =
            cid
                .trim()
                .removePrefix("ipfs://")
                .removePrefix("IPFS://")
                .removePrefix("ipfs:")
                .removePrefix("IPFS:")
                .removePrefix("/")
        return "ipfs://$clean"
    }
}
