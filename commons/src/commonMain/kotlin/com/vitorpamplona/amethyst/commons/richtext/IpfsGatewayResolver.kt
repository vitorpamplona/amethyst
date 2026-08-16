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
    const val PRIMARY_GATEWAY = "https://dweb.link/ipfs/"
    const val SECONDARY_GATEWAY = "https://ipfs.io/ipfs/"

    val DEFAULT_GATEWAYS =
        listOf(
            PRIMARY_GATEWAY,
            SECONDARY_GATEWAY,
        )

    fun isIpfsUri(url: String): Boolean =
        url.startsWith("ipfs://", ignoreCase = true) ||
            url.startsWith("ipfs:", ignoreCase = true)

    /**
     * Resolves an `ipfs://...` or `ipfs:...` URI into an HTTP gateway URL.
     * Default gateway is https://dweb.link/ipfs/
     */
    fun toHttpUrl(
        ipfsUri: String,
        gateway: String = PRIMARY_GATEWAY,
    ): String {
        if (!isIpfsUri(ipfsUri)) return ipfsUri

        val cleanPath =
            ipfsUri
                .removePrefix("ipfs://")
                .removePrefix("IPFS://")
                .removePrefix("ipfs:")
                .removePrefix("IPFS:")
                .removePrefix("/")

        val base = if (gateway.endsWith("/")) gateway else "$gateway/"
        return "$base$cleanPath"
    }

    /**
     * Returns candidate HTTP URLs for failover fetching (primary -> secondary).
     */
    fun getAllCandidateUrls(
        ipfsUri: String,
        customGateway: String? = null,
    ): List<String> {
        val cleanPath =
            ipfsUri
                .removePrefix("ipfs://")
                .removePrefix("IPFS://")
                .removePrefix("ipfs:")
                .removePrefix("IPFS:")
                .removePrefix("/")

        val list = mutableListOf<String>()
        if (!customGateway.isNullOrBlank()) {
            val base = if (customGateway.endsWith("/")) customGateway else "$customGateway/"
            list.add("$base$cleanPath")
        }
        DEFAULT_GATEWAYS.forEach { gw ->
            val base = if (gw.endsWith("/")) gw else "$gw/"
            list.add("$base$cleanPath")
        }
        return list.distinct()
    }
}
