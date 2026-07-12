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
package com.vitorpamplona.amethyst.service.resourceusage

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import okio.ForwardingSource
import okio.Source
import okio.buffer
import java.util.concurrent.ConcurrentHashMap

/**
 * Application interceptor that attributes HTTP traffic to a ledger subsystem
 * (image/video/uploads/money/nip05/preview/push). Upload size comes from the
 * request body's contentLength; download size is counted as the app actually
 * consumes the streamed response body, so partially-read streams (video seek,
 * cancelled image loads) count only what crossed the wire to the app.
 *
 * Network/visibility dims are sampled when bytes flow, not when the request
 * is created — a request issued on wifi that finishes on cellular counts as
 * cellular, matching what the radio actually did.
 */
class UsageCountingInterceptor(
    private val role: String,
    private val accountant: ResourceUsageAccountant,
    private val isMobile: () -> Boolean,
    private val isForeground: () -> Boolean,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val requestBytes = request.body?.contentLength()?.coerceAtLeast(0L) ?: 0L
        if (requestBytes > 0) {
            accountant.add(UsageKeys.net(role, isMobile(), isForeground(), received = false), requestBytes)
        }

        val response = chain.proceed(request)
        val body = response.body
        return response
            .newBuilder()
            .body(
                CountingResponseBody(body) { bytes ->
                    accountant.add(UsageKeys.net(role, isMobile(), isForeground(), received = true), bytes)
                },
            ).build()
    }

    private class CountingResponseBody(
        private val delegate: ResponseBody,
        private val onBytes: (Long) -> Unit,
    ) : ResponseBody() {
        override fun contentType() = delegate.contentType()

        override fun contentLength() = delegate.contentLength()

        private val countedSource by lazy {
            object : ForwardingSource(delegate.source() as Source) {
                override fun read(
                    sink: Buffer,
                    byteCount: Long,
                ): Long {
                    val read = super.read(sink, byteCount)
                    if (read > 0) onBytes(read)
                    return read
                }
            }.buffer()
        }

        override fun source() = countedSource
    }
}

/**
 * Caches per-(role, base client) wrapped OkHttp clients so each role gets its
 * counting interceptor without rebuilding the shared clients. Base clients
 * are rebuilt on proxy/network changes (new identity), so the cache is
 * cleared when it grows past a small bound.
 */
class HttpUsageMeter(
    private val accountant: ResourceUsageAccountant,
    private val isMobile: () -> Boolean,
    private val isForeground: () -> Boolean,
) {
    private val wrapped = ConcurrentHashMap<Pair<String, OkHttpClient>, OkHttpClient>()

    fun counted(
        role: String,
        base: OkHttpClient,
    ): OkHttpClient {
        if (wrapped.size > MAX_CACHED) wrapped.clear()
        return wrapped.getOrPut(role to base) {
            base
                .newBuilder()
                .addInterceptor(UsageCountingInterceptor(role, accountant, isMobile, isForeground))
                .build()
        }
    }

    companion object {
        // roles (7) x live base clients (proxy on/off ~2) with headroom for
        // network-change rebuilds before the clear kicks in.
        private const val MAX_CACHED = 32
    }
}
