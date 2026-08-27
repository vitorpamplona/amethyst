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

import com.vitorpamplona.amethyst.commons.richtext.IpfsGatewayResolver
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/**
 * When a GET/HEAD to a configured Originless `{base}/ipfs/{cid}` fails, retries
 * the same CID on the rest of the user's Originless nodes.
 *
 * Notes store `ipfs://CID`; Coil/PdfFetcher rewrite that to the first gateway.
 * This interceptor is how a later node in the list still serves the file when
 * the first is down or never pinned that CID.
 */
class OriginlessGatewayFailoverInterceptor(
    private val bases: () -> List<String> = { IpfsGatewayResolver.currentServerBases },
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val method = request.method
        if (!method.equals("GET", ignoreCase = true) && !method.equals("HEAD", ignoreCase = true)) {
            return chain.proceed(request)
        }

        val cidPath = ipfsCidPathOrNull(request.url) ?: return chain.proceed(request)
        val servers = OriginlessUrls.normalizeList(bases())
        if (servers.size <= 1) return chain.proceed(request)

        val originalOrigin = originOf(request.url)
        if (servers.none { OriginlessUrls.normalizeBase(it) == originalOrigin }) {
            return chain.proceed(request)
        }

        val ordered =
            buildList {
                add(originalOrigin)
                servers.forEach { if (it != originalOrigin) add(it) }
            }

        var lastError: IOException? = null
        var lastFailure: Response? = null
        for (server in ordered) {
            val nextUrl = OriginlessUrls.gatewayUrl(server, cidPath)
            val next =
                if (server == originalOrigin) {
                    request
                } else {
                    request.newBuilder().url(nextUrl).build()
                }
            try {
                val response = chain.proceed(next)
                if (response.isSuccessful || response.code in 300..399) {
                    lastFailure?.close()
                    return response
                }
                if (response.code == 404 || response.code == 410 || response.code in 500..599) {
                    lastFailure?.close()
                    lastFailure = response
                    continue
                }
                lastFailure?.close()
                return response
            } catch (e: IOException) {
                lastError = e
            }
        }
        lastFailure?.let { return it }
        throw lastError ?: IOException("Originless gateway failover exhausted for $cidPath")
    }

    companion object {
        fun ipfsCidPathOrNull(url: HttpUrl): String? {
            val segments = url.pathSegments
            if (segments.size < 2) return null
            if (!segments[0].equals("ipfs", ignoreCase = true)) return null
            val cid = segments.drop(1).filter { it.isNotEmpty() }.joinToString("/")
            return cid.ifBlank { null }
        }

        fun originOf(url: HttpUrl): String {
            val port =
                if (url.port != HttpUrl.defaultPort(url.scheme)) ":${url.port}" else ""
            return OriginlessUrls.normalizeBase("${url.scheme}://${url.host}$port")
        }
    }
}
