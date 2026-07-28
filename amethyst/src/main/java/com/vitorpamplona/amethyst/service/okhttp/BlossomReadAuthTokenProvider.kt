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

import com.vitorpamplona.amethyst.commons.service.upload.BlossomAuth
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap

/**
 * Signs and caches BUD-01 read-auth headers for [BlossomReadAuthInterceptor].
 *
 * The interceptor is synchronous (it runs on an OkHttp dispatcher thread) but
 * signing is `suspend`, so [authHeader] bridges with [runBlocking] guarded by a
 * timeout: an internal key signs instantly, while a remote (NIP-46) or external
 * (NIP-55) signer that hangs or needs user interaction simply yields `null` and
 * the download stays unauthenticated instead of pinning the thread.
 *
 * Tokens are cached per host, not per blob. A BUD-11 `server`-scoped token
 * grants reads for every blob on the host (thumbnails included), so one signed
 * event covers a whole feed's worth of images from an auth-gated host for the
 * life of the token. The blob hash of the request that first triggered signing
 * is still included as the `x` tag for BUD-01 servers that check it.
 */
class BlossomReadAuthTokenProvider(
    private val signerProvider: () -> NostrSigner?,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    private class CachedToken(
        val header: String,
        val expiresAtMs: Long,
    )

    private val cache = ConcurrentHashMap<String, CachedToken>()

    fun authHeader(
        host: String,
        sha256: HexKey,
    ): String? {
        val now = clock()

        cache[host]?.let { if (it.expiresAtMs > now) return it.header }

        val signer = signerProvider() ?: return null

        val header =
            runBlocking {
                withTimeoutOrNull(SIGN_TIMEOUT_MS) {
                    BlossomAuth.createGetAuth(
                        hash = sha256,
                        alt = "Downloading media from $host",
                        signer = signer,
                        servers = listOf(host),
                    )
                }
            } ?: return null

        cache[host] = CachedToken(header, now + CACHE_TTL_MS)
        return header
    }

    companion object {
        // The signed event expires one hour out (BlossomAuthorizationEvent), so
        // refresh a little early to avoid handing over a token that dies mid-flight.
        private const val CACHE_TTL_MS = 55L * 60L * 1000L

        // Bounds how long an image download may block waiting on a slow signer.
        private const val SIGN_TIMEOUT_MS = 8_000L
    }
}
