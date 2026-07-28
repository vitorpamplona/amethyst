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

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.util.concurrent.ConcurrentHashMap

/**
 * Retries a Blossom blob download with a BUD-01 `t=get` authorization when the
 * server answers `401`.
 *
 * Most Blossom / NIP-96 hosts serve blobs anonymously, so images load without
 * any signing overhead. A few gate reads behind auth — Buzz's private media
 * relay (`*.communities.buzz.xyz`) returns
 * `401 {"error":"authentication failed"}` to an anonymous `GET`. For those we
 * sign a kind-24242 read-auth event (via [authHeaderProvider]) and replay the
 * request once with `Authorization: Nostr <base64-event>`.
 *
 * Gating is deliberately narrow so we never turn an unrelated `401` into a
 * second request storm:
 *  - only `GET` requests,
 *  - only when the request doesn't already carry an `Authorization` header,
 *  - only when the URL's last path segment is a Blossom sha256 filename
 *    (`<64-hex>` optionally followed by an extension such as `.png` or
 *    `.thumb.jpg`),
 *  - only after the anonymous attempt actually returned `401`,
 *  - and at most one retry (an application interceptor's second `chain.proceed`
 *    runs the downstream chain again, it does not re-enter this interceptor).
 *
 * [authHeaderProvider] is `(host, sha256) -> header?`. It is synchronous by
 * contract (the caller bridges the suspend signer), returns `null` when no
 * signer is available or signing times out, and is only consulted on a real
 * `401`, so an unauthenticated user simply keeps seeing the broken image
 * rather than paying any signing cost.
 *
 * The first blob from an auth-gated host costs an extra round trip (anonymous
 * `GET` → `401` → signed retry), but that host is then remembered in
 * [knownAuthHosts] so every later blob from it is signed **up front** — one
 * round trip, not two. This matters on a Buzz community feed where nearly every
 * image comes from the same gated host: without it each image would keep paying
 * the wasted 401 probe. The learned host also short-circuits to anonymous when
 * no signer is available, so a logged-out user never re-probes needlessly.
 */
class BlossomReadAuthInterceptor(
    private val authHeaderProvider: (host: String, sha256: HexKey) -> String?,
) : Interceptor {
    // Hosts observed to answer 401 to an anonymous Blossom GET. Small (a user
    // follows a handful of auth-gated servers at most) and shared across all
    // clients derived from the same factory. newKeySet() is thread-safe for the
    // concurrent reads/writes of parallel feed downloads.
    private val knownAuthHosts: MutableSet<String> = ConcurrentHashMap.newKeySet()

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        if (!request.method.equals("GET", ignoreCase = true) ||
            request.header("Authorization") != null
        ) {
            return chain.proceed(request)
        }

        val sha256 = blossomHashOrNull(request.url.encodedPath) ?: return chain.proceed(request)
        val host = request.url.host

        // Known-gated host: skip the anonymous probe and sign the first attempt.
        // Falls through to anonymous only when we can't produce a token (no
        // signer / timeout) — the server would 401 either way.
        if (host in knownAuthHosts) {
            authHeaderProvider(host, sha256)?.let { header ->
                return chain.proceed(request.withAuth(header))
            }
        }

        val response = chain.proceed(request)
        if (response.code != 401) return response

        // Learn the host so its next blob is signed up front.
        knownAuthHosts.add(host)

        val header = authHeaderProvider(host, sha256) ?: return response

        // Close the 401 body before replaying so the connection can be reused.
        response.close()

        return chain.proceed(request.withAuth(header))
    }

    private fun Request.withAuth(header: String) =
        newBuilder()
            .header("Authorization", header)
            .build()

    companion object {
        private val SHA256_HEX = Regex("^[0-9a-f]{64}$")

        /**
         * Extracts the sha256 blob id from a Blossom URL path. The blob is the
         * last path segment, up to its first `.` — so both `<hash>.png` and the
         * derived `<hash>.thumb.jpg` resolve to `<hash>`. Returns `null` when the
         * segment isn't a lowercase 64-char hex string.
         */
        fun blossomHashOrNull(encodedPath: String): HexKey? {
            val lastSegment = encodedPath.substringAfterLast('/')
            val base = lastSegment.substringBefore('.').lowercase()
            return if (SHA256_HEX.matches(base)) base else null
        }
    }
}
