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
     * Originless node used as the HTTP gateway for `ipfs://` fetches.
     * Written from account settings; Coil/PdfFetcher read it here so they
     * don't need to thread the URL through every media call site.
     */
    @Volatile
    var currentServerBase: String = OriginlessUrls.DEFAULT_SERVER

    fun primaryGateway(): String = OriginlessUrls.gatewayPrefix(currentServerBase)

    fun isIpfsUri(url: String): Boolean =
        url.startsWith("ipfs://", ignoreCase = true) ||
            url.startsWith("ipfs:", ignoreCase = true)

    /**
     * Resolves an `ipfs://...` or `ipfs:...` URI into an HTTP gateway URL
     * on the configured Originless node (`{base}/ipfs/{cid}`).
     */
    fun toHttpUrl(
        ipfsUri: String,
        gateway: String = primaryGateway(),
    ): String {
        if (!isIpfsUri(ipfsUri)) return ipfsUri

        val cleanPath = cidPath(ipfsUri)
        val base = if (gateway.endsWith("/")) gateway else "$gateway/"
        return "$base$cleanPath"
    }

    /**
     * Returns candidate HTTP URLs for failover fetching. Originless `/ipfs`
     * only serves pins on that node, so the configured node is the source of
     * truth; [customGateway] is tried first when the caller already has one.
     */
    fun getAllCandidateUrls(
        ipfsUri: String,
        customGateway: String? = null,
    ): List<String> {
        val cleanPath = cidPath(ipfsUri)
        val list = mutableListOf<String>()
        if (!customGateway.isNullOrBlank()) {
            val base = if (customGateway.endsWith("/")) customGateway else "$customGateway/"
            list.add("$base$cleanPath")
        }
        val primary = primaryGateway()
        val base = if (primary.endsWith("/")) primary else "$primary/"
        list.add("$base$cleanPath")
        return list.distinct()
    }

    private fun cidPath(ipfsUri: String): String =
        ipfsUri
            .removePrefix("ipfs://")
            .removePrefix("IPFS://")
            .removePrefix("ipfs:")
            .removePrefix("IPFS:")
            .removePrefix("/")
}
