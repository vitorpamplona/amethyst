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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException

/**
 * Signs and caches BUD-01 read-auth headers for auth-gated Blossom hosts.
 *
 * Signing never blocks a caller's thread. It runs on [scope]; callers either
 * `suspend` on [header] or fire [warm] and pick the token up later. That
 * matters because the consumer used to be [BlossomReadAuthInterceptor], which
 * runs on an OkHttp dispatcher thread — bridging the suspend signer with
 * `runBlocking` there held one of the 16 per-host dispatcher slots for as long
 * as the signer took (up to the timeout), so a feed's first burst against a
 * gated host could occupy every slot and stall every other image from it.
 *
 * One signature per host, however many callers. The token cache alone couldn't
 * provide that: it is only populated *after* a signature returns, so a cold
 * burst of N images all missed it and all signed concurrently — with a NIP-55
 * external signer that meant N IPC round trips (and potentially N prompts).
 * [inFlight] is what collapses them; the leader signs and every follower awaits
 * the same [CompletableDeferred].
 *
 * Tokens are cached per host, not per blob. A BUD-11 `server`-scoped token
 * grants reads for every blob on the host (thumbnails included), so one signed
 * event covers a whole feed's worth of images from an auth-gated host for the
 * life of the token. The blob hash of the request that first triggered signing
 * is still included as the `x` tag for BUD-01 servers that check it.
 */
class BlossomReadAuthTokenProvider(
    private val signerProvider: () -> NostrSigner?,
    private val scope: CoroutineScope,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    private class CachedToken(
        val header: String,
        val expiresAtMs: Long,
    )

    private val cache = ConcurrentHashMap<String, CachedToken>()
    private val inFlight = ConcurrentHashMap<String, CompletableDeferred<String?>>()

    /**
     * The token already held for [host], or null. Pure map read — safe to call
     * from an OkHttp interceptor, and never signs.
     */
    fun cachedHeader(host: String): String? = cache[host]?.takeIf { it.expiresAtMs > clock() }?.header

    /**
     * The token for [host], signing one if none is cached. Suspends rather than
     * blocking, so the caller must already be in a coroutine — on the image path
     * that is Coil's `Fetcher.fetch()`.
     */
    suspend fun header(
        host: String,
        sha256: HexKey,
    ): String? {
        cachedHeader(host)?.let { return it }
        return signOnce(host, sha256)?.await()
    }

    /**
     * Starts a signature for [host] without waiting for it. For callers that
     * cannot suspend (the interceptor) and only need the token to exist by the
     * time some later request needs it.
     */
    fun warm(
        host: String,
        sha256: HexKey,
    ) {
        if (cachedHeader(host) != null) return
        signOnce(host, sha256)
    }

    /**
     * Returns the in-flight signature for [host], starting one if this caller
     * wins the race. Null when there is no signer to sign with.
     *
     * Leader/follower over [ConcurrentHashMap.putIfAbsent] rather than
     * `computeIfAbsent`: the completion handler removes the map entry, and a job
     * that finishes immediately would run that removal *inside* the mapping
     * function, which `ConcurrentHashMap` forbids.
     */
    private fun signOnce(
        host: String,
        sha256: HexKey,
    ): CompletableDeferred<String?>? {
        inFlight[host]?.let { return it }

        val signer = signerProvider() ?: return null

        val fresh = CompletableDeferred<String?>()
        inFlight.putIfAbsent(host, fresh)?.let { return it }

        scope
            .launch {
                val header =
                    try {
                        withTimeoutOrNull(SIGN_TIMEOUT_MS) {
                            BlossomAuth.createGetAuth(
                                hash = sha256,
                                alt = "Downloading media from $host",
                                signer = signer,
                                servers = listOf(host),
                            )
                        }
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        null
                    }

                if (header != null) {
                    cache[host] = CachedToken(header, clock() + CACHE_TTL_MS)
                }
                fresh.complete(header)
            }.invokeOnCompletion {
                inFlight.remove(host, fresh)
                // No-op when the job completed normally; releases followers when
                // it was cancelled (scope torn down) instead of hanging them.
                fresh.complete(null)
            }

        return fresh
    }

    companion object {
        // The signed event expires one hour out (BlossomAuthorizationEvent), so
        // refresh a little early to avoid handing over a token that dies mid-flight.
        private const val CACHE_TTL_MS = 55L * 60L * 1000L

        // Bounds how long an image may wait on a slow signer. No thread is held
        // for this window any more — only the waiting coroutine.
        private const val SIGN_TIMEOUT_MS = 8_000L
    }
}
