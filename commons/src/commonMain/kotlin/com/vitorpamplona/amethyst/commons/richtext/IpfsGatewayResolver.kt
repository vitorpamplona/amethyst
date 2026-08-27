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

import com.vitorpamplona.amethyst.commons.originless.OriginlessUrls
import kotlin.concurrent.Volatile

object IpfsGatewayResolver {
    /**
     * Live Originless node list for singleton Coil/OkHttp. Bound once from the
     * session manager to the current account's `originlessServerUrls` flow.
     * Empty means the public [OriginlessUrls.DEFAULT_SERVER] fetch fallback.
     */
    @Volatile
    var serverBasesProvider: () -> List<String> = { emptyList() }

    fun fetchBases(): List<String> = OriginlessUrls.normalizeList(serverBasesProvider()).ifEmpty { listOf(OriginlessUrls.DEFAULT_SERVER) }

    fun isIpfsUri(url: String): Boolean =
        url.startsWith("ipfs://", ignoreCase = true) ||
            url.startsWith("ipfs:", ignoreCase = true)

    /**
     * Resolves an `ipfs://...` or `ipfs:...` URI into an HTTP gateway URL
     * on the first configured Originless node (`{base}/ipfs/{cid}`).
     */
    fun toHttpUrl(
        ipfsUri: String,
        gateway: String = OriginlessUrls.gatewayPrefix(fetchBases().firstOrNull() ?: OriginlessUrls.DEFAULT_SERVER),
    ): String {
        if (!isIpfsUri(ipfsUri)) return ipfsUri

        val cleanPath = cidPath(ipfsUri)
        val base = if (gateway.endsWith("/")) gateway else "$gateway/"
        return "$base$cleanPath"
    }

    /**
     * Returns candidate HTTP URLs for failover fetching. Each configured
     * Originless node is tried in list order; [customGateway] is tried first
     * when the caller already has one.
     */
    fun getAllCandidateUrls(
        ipfsUri: String,
        customGateway: String? = null,
        serverBases: List<String> = fetchBases(),
    ): List<String> {
        val cleanPath = cidPath(ipfsUri)
        val list = mutableListOf<String>()
        if (!customGateway.isNullOrBlank()) {
            val base = if (customGateway.endsWith("/")) customGateway else "$customGateway/"
            list.add("$base$cleanPath")
        }
        serverBases.ifEmpty { listOf(OriginlessUrls.DEFAULT_SERVER) }.forEach { server ->
            list.add(OriginlessUrls.gatewayUrl(server, cleanPath))
        }
        return list.distinct()
    }

    /**
     * HTTP URLs to try for a fetch. `ipfs://` expands to every configured
     * Originless gateway; anything else is returned as a single-item list.
     */
    fun httpFetchUrls(
        url: String,
        serverBases: List<String> = fetchBases(),
    ): List<String> = if (isIpfsUri(url)) getAllCandidateUrls(url, serverBases = serverBases) else listOf(url)

    /**
     * Inverse of [toHttpUrl]: an `ipfs://` / `ipfs:` URI, or an HTTP
     * `{base}/ipfs/{cid}` gateway URL, becomes `ipfs://{cid}`.
     */
    fun ipfsUriFromGatewayUrl(url: String): String? {
        val path =
            if (isIpfsUri(url)) {
                cidPath(url)
            } else {
                val marker = "/ipfs/"
                val idx = url.indexOf(marker, ignoreCase = true)
                if (idx < 0) return null
                url
                    .substring(idx + marker.length)
                    .substringBefore('?')
                    .substringBefore('#')
                    .trimEnd('/')
            }
        return path.takeIf { it.isNotEmpty() }?.let { "ipfs://$it" }
    }

    /**
     * URLs that must share an AES key for NIP-17 encrypted media. Kind 15
     * stores `ipfs://CID`; Coil/OkHttp fetch `{gateway}/ipfs/{CID}`, so the
     * decryptor has to recognize both. Callers register every alias on the
     * string-to-cipher map; the cache itself stays URL-exact.
     */
    fun decryptionKeyUrls(
        url: String,
        serverBases: List<String> = fetchBases(),
    ): List<String> {
        val ipfs = ipfsUriFromGatewayUrl(url)
        if (ipfs == null) return listOf(url)
        return (listOf(url, ipfs) + httpFetchUrls(ipfs, serverBases)).distinct()
    }

    private fun cidPath(ipfsUri: String): String =
        ipfsUri
            .removePrefix("ipfs://")
            .removePrefix("IPFS://")
            .removePrefix("ipfs:")
            .removePrefix("IPFS:")
            .removePrefix("/")
}
