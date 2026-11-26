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
package com.vitorpamplona.amethyst.commons.napplet.blobs

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Authenticator
import okhttp3.Call
import okhttp3.Callback
import okhttp3.CookieJar
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.TimeUnit

/**
 * [NappletHttp] on OkHttp, for Android and Desktop: the code the blob store and the resource fetcher
 * ran on their own clients before the port, unchanged.
 *
 * [clientFor] gives the client for a route (Tor or not). A plain request goes out on that client as
 * it is, executed on the calling IO thread as the blob store always did, so the client's own limits
 * apply (a launch's blob client has its call timeout and User-Agent). A request that doesn't follow
 * redirects or is [NappletHttpRequest.publicOnly] goes out on a hardened copy of it, the resource
 * fetcher's: no cookies, credentials or cache, every resolved address public, its own call timeout.
 */
class OkHttpNappletHttp(
    private val clientFor: (viaTor: Boolean) -> OkHttpClient,
) : NappletHttp {
    /** One client for every request: a launch whose route was decided when it was built. */
    constructor(client: OkHttpClient) : this({ _: Boolean -> client })

    override suspend fun get(request: NappletHttpRequest): NappletHttpResponse {
        val base = clientFor(request.viaTor)
        return try {
            if (request.publicOnly || !request.followRedirects) {
                hardened(base, request).newCall(request(request.url)).await().use { read(it, request.maxBytes) }
            } else {
                executeCancellable(base.newCall(request(request.url)), request.maxBytes)
            }
        } catch (e: InterruptedIOException) {
            throw NappletHttpTimeoutException("${request.url} timed out", e)
        }
    }

    private fun hardened(
        baseClient: OkHttpClient,
        request: NappletHttpRequest,
    ): OkHttpClient {
        val builder =
            baseClient
                .newBuilder()
                .followRedirects(request.followRedirects)
                .followSslRedirects(request.followRedirects)
                .callTimeout(request.timeoutMs, TimeUnit.MILLISECONDS)
        if (request.publicOnly) {
            builder
                .cache(null)
                .cookieJar(CookieJar.NO_COOKIES)
                .authenticator(Authenticator.NONE)
                .proxyAuthenticator(Authenticator.NONE)
                .dns { hostname ->
                    baseClient.dns.lookup(hostname).also { addresses ->
                        if (addresses.isEmpty() || !addresses.all(NappletInetAddresses::isPublic)) {
                            throw NappletHttpBlockedException("Resolved address is not public.")
                        }
                    }
                }.addNetworkInterceptor { chain ->
                    chain.proceed(
                        chain
                            .request()
                            .newBuilder()
                            .removeHeader("Authorization")
                            .removeHeader("Cookie")
                            .removeHeader("Proxy-Authorization")
                            .build(),
                    )
                }
        }
        return builder.build()
    }

    /**
     * Runs [call] on an IO thread and cancels it when the caller is cancelled, which ends a blocking
     * read that coroutine cancellation alone can't reach.
     */
    private suspend fun executeCancellable(
        call: Call,
        maxBytes: Long,
    ): NappletHttpResponse =
        coroutineScope {
            val reading = async(Dispatchers.IO) { call.execute().use { read(it, maxBytes) } }
            try {
                reading.await()
            } catch (e: CancellationException) {
                call.cancel()
                throw e
            }
        }

    private fun request(url: String) =
        Request
            .Builder()
            .url(url)
            .get()
            .build()

    private fun read(
        response: Response,
        maxBytes: Long,
    ): NappletHttpResponse {
        val location = response.header("Location")
        if (!response.isSuccessful) return NappletHttpResponse(response.code, location, null, tooLarge = false, received = 0)
        if (response.body.contentLength() > maxBytes) return NappletHttpResponse(response.code, location, null, tooLarge = true, received = 0)
        // A server that sends no length is still held to the cap: buffer one byte past it and refuse
        // anything that fills it. (Okio, not InputStream.readNBytes, which Android only has from API 33.)
        val source = response.body.source()
        val tooBig = source.request(maxBytes + 1)
        val received = source.buffer.size
        return if (tooBig) {
            NappletHttpResponse(response.code, location, null, tooLarge = true, received = received)
        } else {
            NappletHttpResponse(response.code, location, source.buffer.readByteArray(), tooLarge = false, received = received)
        }
    }

    private suspend fun Call.await(): Response =
        suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { cancel() }
            enqueue(
                object : Callback {
                    override fun onFailure(
                        call: Call,
                        e: IOException,
                    ) {
                        if (continuation.isActive) continuation.resumeWith(Result.failure(e))
                    }

                    override fun onResponse(
                        call: Call,
                        response: Response,
                    ) {
                        if (continuation.isActive) {
                            continuation.resumeWith(Result.success(response))
                        } else {
                            response.close()
                        }
                    }
                },
            )
        }
}
