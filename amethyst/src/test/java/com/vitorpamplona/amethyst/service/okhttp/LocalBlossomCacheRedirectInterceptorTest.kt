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

import okhttp3.Connection
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test

class LocalBlossomCacheRedirectInterceptorTest {
    private val sha = "b1674191a88ec5cdd733e4240a81803105dc412d6c6708d53ab94fc248f4f553"

    @Test
    fun bridgeOffPassesThrough() {
        val interceptor = LocalBlossomCacheRedirectInterceptor { false }
        val captured = mutableListOf<String>()
        val response = interceptor.intercept(fakeChain("https://blossom.example.com/$sha.jpg", captured))
        assertEquals("https://blossom.example.com/$sha.jpg", captured.single())
        response.close()
    }

    @Test
    fun bridgeOnRewritesShaInPath() {
        val interceptor = LocalBlossomCacheRedirectInterceptor { true }
        val captured = mutableListOf<String>()
        val response = interceptor.intercept(fakeChain("https://blossom.example.com/$sha.jpg", captured))
        assertEquals(
            "http://127.0.0.1:24242/$sha.jpg?xs=https%3A%2F%2Fblossom.example.com",
            captured.single(),
        )
        response.close()
    }

    @Test
    fun bridgeOnPreservesNonHexUrls() {
        val interceptor = LocalBlossomCacheRedirectInterceptor { true }
        val captured = mutableListOf<String>()
        val response = interceptor.intercept(fakeChain("https://example.com/avatar.jpg", captured))
        assertEquals("https://example.com/avatar.jpg", captured.single())
        response.close()
    }

    @Test
    fun bridgeOnSkipsLocalhost() {
        val interceptor = LocalBlossomCacheRedirectInterceptor { true }
        val captured = mutableListOf<String>()
        val response = interceptor.intercept(fakeChain("http://127.0.0.1:24242/$sha.jpg", captured))
        assertEquals("http://127.0.0.1:24242/$sha.jpg", captured.single())
        response.close()
    }

    @Test
    fun bridgeOnHandlesNonStandardPort() {
        val interceptor = LocalBlossomCacheRedirectInterceptor { true }
        val captured = mutableListOf<String>()
        val response = interceptor.intercept(fakeChain("https://blossom.example.com:8443/$sha.png", captured))
        assertEquals(
            "http://127.0.0.1:24242/$sha.png?xs=https%3A%2F%2Fblossom.example.com%3A8443",
            captured.single(),
        )
        response.close()
    }

    @Test
    fun bridgeOnHandlesUppercaseSha() {
        val interceptor = LocalBlossomCacheRedirectInterceptor { true }
        val captured = mutableListOf<String>()
        val response = interceptor.intercept(fakeChain("https://blossom.example.com/${sha.uppercase()}.jpg", captured))
        assertEquals(
            "http://127.0.0.1:24242/$sha.jpg?xs=https%3A%2F%2Fblossom.example.com",
            captured.single(),
        )
        response.close()
    }

    @Test
    fun bridgeOnFallsBackToBinExtension() {
        val interceptor = LocalBlossomCacheRedirectInterceptor { true }
        val captured = mutableListOf<String>()
        val response = interceptor.intercept(fakeChain("https://blossom.example.com/$sha", captured))
        assertEquals(
            "http://127.0.0.1:24242/$sha.bin?xs=https%3A%2F%2Fblossom.example.com",
            captured.single(),
        )
        response.close()
    }

    @Test
    fun bridgeOnHandlesShaInDeeperPathSegment() {
        val interceptor = LocalBlossomCacheRedirectInterceptor { true }
        val captured = mutableListOf<String>()
        val response = interceptor.intercept(fakeChain("https://nostr.build/i/cache/$sha.webp", captured))
        assertEquals(
            "http://127.0.0.1:24242/$sha.webp?xs=https%3A%2F%2Fnostr.build%2Fi%2Fcache",
            captured.single(),
        )
        response.close()
    }

    @Test
    fun bridgeOnPreservesNostrBuildPathPrefix() {
        val interceptor = LocalBlossomCacheRedirectInterceptor { true }
        val captured = mutableListOf<String>()
        val response = interceptor.intercept(fakeChain("https://cdn.nostr.build/i/$sha.jpg", captured))
        assertEquals(
            "http://127.0.0.1:24242/$sha.jpg?xs=https%3A%2F%2Fcdn.nostr.build%2Fi",
            captured.single(),
        )
        response.close()
    }

    private fun fakeChain(
        url: String,
        captured: MutableList<String>,
    ): Interceptor.Chain =
        object : Interceptor.Chain {
            private val request = Request.Builder().url(url.toHttpUrl()).build()

            override fun request(): Request = request

            override fun proceed(request: Request): Response {
                captured.add(request.url.toString())
                return Response
                    .Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("".toResponseBody(null))
                    .build()
            }

            override fun connection(): Connection? = null

            override fun call() = throw UnsupportedOperationException()

            override fun connectTimeoutMillis(): Int = 0

            override fun readTimeoutMillis(): Int = 0

            override fun writeTimeoutMillis(): Int = 0

            override fun withConnectTimeout(
                timeout: Int,
                unit: java.util.concurrent.TimeUnit,
            ): Interceptor.Chain = this

            override fun withReadTimeout(
                timeout: Int,
                unit: java.util.concurrent.TimeUnit,
            ): Interceptor.Chain = this

            override fun withWriteTimeout(
                timeout: Int,
                unit: java.util.concurrent.TimeUnit,
            ): Interceptor.Chain = this
        }
}
