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

object IpfsGatewayResolver {
    const val DEFAULT_GATEWAY = "https://dweb.link/"
    const val SECONDARY_GATEWAY = "https://ipfs.io/"

    val DEFAULT_GATEWAYS =
        listOf(
            DEFAULT_GATEWAY,
            SECONDARY_GATEWAY,
        )

    fun isIpfsUri(url: String): Boolean {
        val separator = url.indexOf(':')
        return separator == IPFS_SCHEME.length &&
            url.regionMatches(0, IPFS_SCHEME, 0, IPFS_SCHEME.length, ignoreCase = true)
    }

    /**
     * Resolves an `ipfs://<cid>/...` or `ipfs:<cid>/...` URI into an HTTP path-gateway URL.
     *
     * [gateway] is the path-gateway server root, not its `/ipfs/` endpoint. A pasted path-gateway
     * URL ending in `/ipfs` is accepted and normalized so the path is never duplicated.
     */
    fun toHttpUrl(
        ipfsUri: String,
        gateway: String = DEFAULT_GATEWAY,
    ): String {
        val ipfsPath = extractIpfsPath(ipfsUri) ?: return ipfsUri
        val gatewayRoot = normalizeGatewayUrl(gateway) ?: return ipfsUri
        return "$gatewayRoot/ipfs/$ipfsPath"
    }

    /**
     * Returns distinct HTTP candidates in caller preference order.
     *
     * [extraGateways] are prepended before [customGateway] and the built-in
     * public gateways. An Originless server URL listed here will be tried first,
     * letting the user resolve IPFS content through their own node.
     */
    fun getAllCandidateUrls(
        ipfsUri: String,
        customGateway: String? = null,
        extraGateways: List<String> = emptyList(),
    ): List<String> =
        buildList {
            addAll(extraGateways)
            customGateway?.let(::add)
            addAll(DEFAULT_GATEWAYS)
        }.mapNotNull { gateway ->
            toHttpUrl(ipfsUri, gateway).takeUnless { it == ipfsUri }
        }.distinct()

    /**
     * Normalizes a user-entered IPFS path-gateway root.
     *
     * HTTP is intentionally accepted for a node on localhost or the user's LAN. Remote cleartext
     * gateways remain subject to Android's normal network-security policy.
     */
    fun normalizeGatewayUrl(gateway: String): String? {
        val trimmed = gateway.trim().trimEnd('/')
        val schemeEnd =
            when {
                trimmed.startsWith("https://", ignoreCase = true) -> HTTPS_PREFIX.length
                trimmed.startsWith("http://", ignoreCase = true) -> HTTP_PREFIX.length
                else -> return null
            }
        val withoutIpfsPath = trimmed.removeSuffixIgnoreCase("/ipfs").trimEnd('/')
        val authority = withoutIpfsPath.substring(schemeEnd).substringBefore('/')
        if (authority.isBlank() || authority == "." || authority == "..") return null
        if (withoutIpfsPath.any { it.isWhitespace() || it == '\\' }) return null
        if ('?' in withoutIpfsPath || '#' in withoutIpfsPath) return null
        if (withoutIpfsPath
                .substring(schemeEnd)
                .substringAfter('/', "")
                .split('/')
                .any(::isDotSegment)
        ) {
            return null
        }
        return withoutIpfsPath
    }

    private fun extractIpfsPath(ipfsUri: String): String? {
        if (!isIpfsUri(ipfsUri)) return null
        val path = ipfsUri.substringAfter(':').removePrefix("//").trimStart('/')
        if (path.isBlank()) return null

        val cid = path.substringBefore('/').substringBefore('?').substringBefore('#')
        if (cid.isBlank() || isDotSegment(cid) || !cid.all(::isUnreservedAscii)) return null

        val resourcePath = path.substringBefore('?').substringBefore('#')
        if (resourcePath.split('/').any(::isDotSegment)) return null
        return path
    }

    private fun isDotSegment(segment: String): Boolean {
        val normalized = segment.lowercase()
        return normalized == "." ||
            normalized == ".." ||
            normalized == "%2e" ||
            normalized == "%2e%2e" ||
            normalized == ".%2e" ||
            normalized == "%2e."
    }

    private fun isUnreservedAscii(char: Char): Boolean =
        char in 'a'..'z' ||
            char in 'A'..'Z' ||
            char in '0'..'9' ||
            char == '-' ||
            char == '.' ||
            char == '_' ||
            char == '~'

    private fun String.removeSuffixIgnoreCase(suffix: String): String =
        if (endsWith(suffix, ignoreCase = true)) {
            dropLast(suffix.length)
        } else {
            this
        }

    private const val IPFS_SCHEME = "ipfs"
    private const val HTTP_PREFIX = "http://"
    private const val HTTPS_PREFIX = "https://"
}
