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
package com.vitorpamplona.amethyst.service.okhttp

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Response

/**
 * App-wide OkHttp interceptor that transparently rewrites HTTP requests for
 * sha256-keyed blobs to a local Blossom cache running on `127.0.0.1:24242`,
 * per https://github.com/hzrd149/blossom/blob/master/implementations/local-blossom-cache.md
 *
 * Activates when [shouldBridge] returns `true` AND the request URL contains
 * a 64-char hex sha256 segment in its path AND the host isn't already
 * `127.0.0.1`/`localhost`. The original scheme+host is appended as a `xs=`
 * proxy hint so the cache can fetch upstream on miss.
 *
 * Coil's disk cache keys responses by the original `ImageRequest.data`, so
 * disk caching continues to work transparently even though the network
 * request now goes to localhost.
 */
class LocalBlossomCacheRedirectInterceptor(
    private val shouldBridge: () -> Boolean,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        if (!shouldBridge()) return chain.proceed(request)

        val rewritten = rewriteIfApplicable(request.url) ?: return chain.proceed(request)

        return chain.proceed(
            request
                .newBuilder()
                .url(rewritten)
                .build(),
        )
    }

    private fun rewriteIfApplicable(url: HttpUrl): HttpUrl? {
        val host = url.host
        if (host == LOCAL_CACHE_HOST || host.equals("localhost", ignoreCase = true)) return null

        val (shaSegmentIndex, sha, ext) = findSha256AndExtensionInPath(url) ?: return null
        val serverBase = buildServerBase(url, shaSegmentIndex)

        return "$LOCAL_CACHE_BASE/$sha.$ext"
            .toHttpUrl()
            .newBuilder()
            .addQueryParameter("xs", serverBase)
            .build()
    }

    private fun findSha256AndExtensionInPath(url: HttpUrl): Triple<Int, String, String>? {
        // Per Blossom (BUD-01) the last path segment must be exactly
        // `<sha256>` or `<sha256>.<ext>`. URLs whose filename merely embeds a
        // 64-char hex (e.g. "nostr.build_<sha>.jpg") aren't Blossom blobs and
        // the bridge must leave them alone — rewriting them would point the
        // local cache at a fallback `xs=` server that doesn't host the blob.
        // Prefix segments are preserved verbatim via `buildServerBase`.
        val lastIndex = url.pathSegments.lastIndex
        if (lastIndex < 0) return null
        val segment = url.pathSegments[lastIndex]
        val match = BLOSSOM_LAST_SEGMENT_REGEX.matchEntire(segment) ?: return null
        val sha = match.groupValues[1].lowercase()
        val ext = guessExtensionFrom(segment, sha) ?: "bin"
        return Triple(lastIndex, sha, ext)
    }

    /**
     * Builds the URL prefix that the local cache should append `/<sha>` to.
     * Preserves any path prefix the upstream CDN uses (e.g. `/i` for
     * `https://cdn.nostr.build/i/<sha>`) so the cache can fetch the blob
     * from its actual location on miss.
     */
    private fun buildServerBase(
        url: HttpUrl,
        shaSegmentIndex: Int,
    ): String {
        val origin = "${url.scheme}://${url.host}" + if (url.port != HttpUrl.defaultPort(url.scheme)) ":${url.port}" else ""
        if (shaSegmentIndex == 0) return origin
        val prefixSegments = url.pathSegments.subList(0, shaSegmentIndex)
        return "$origin/" + prefixSegments.joinToString("/")
    }

    private fun guessExtensionFrom(
        segment: String,
        sha: String,
    ): String? {
        val idx = segment.indexOf(sha, ignoreCase = true)
        val after = if (idx >= 0) segment.substring(idx + sha.length) else return null
        if (!after.startsWith('.')) return null
        val rest = after.substring(1).lowercase()
        if (rest.isEmpty() || rest.length > 8) return null
        if (!rest.all { it.isLetterOrDigit() }) return null
        return rest
    }

    companion object {
        const val LOCAL_CACHE_HOST = "127.0.0.1"
        const val LOCAL_CACHE_PORT = 24242
        const val LOCAL_CACHE_BASE = "http://$LOCAL_CACHE_HOST:$LOCAL_CACHE_PORT"
        private val BLOSSOM_LAST_SEGMENT_REGEX = Regex("^([0-9a-fA-F]{64})(?:\\.[^./]+)?$")
    }
}
