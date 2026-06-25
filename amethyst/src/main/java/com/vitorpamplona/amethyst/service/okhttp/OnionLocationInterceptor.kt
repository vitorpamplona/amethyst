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

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Network interceptor that passively captures `Onion-Location` headers from
 * every HTTP response — NIP-11 documents, WebSocket upgrade handshakes (101),
 * image/media servers, anything — and records the mapping into
 * [OnionLocationCache].
 *
 * No extra requests are made; discovery is a free by-product of traffic that
 * already happens. When the Tor-enabled client later connects to the same host,
 * [OnionUrlRewriteInterceptor] swaps in the cached .onion address so the
 * connection avoids exit nodes entirely.
 *
 * Registered as a network interceptor (not application interceptor) so it sees
 * real server responses rather than cached copies.
 */
class OnionLocationInterceptor(
    private val cache: OnionLocationCache,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        val onionLocation = response.header("Onion-Location")
        if (onionLocation != null) {
            cache.put(chain.request().url.host, onionLocation)
        }
        return response
    }
}
