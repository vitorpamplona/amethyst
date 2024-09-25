/**
 * Copyright (c) 2024 Vitor Pamplona
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
package com.vitorpamplona.ammolite.service

import android.util.Log
import com.vitorpamplona.ammolite.service.HttpClientManager.setDefaultProxy
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.time.Duration

class LoggingInterceptor : Interceptor {
    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        val request: Request = chain.request()
        val t1 = System.nanoTime()
        val port =
            (
                chain
                    .connection()
                    ?.route()
                    ?.proxy
                    ?.address() as? InetSocketAddress
            )?.port
        val response: Response = chain.proceed(request)
        val t2 = System.nanoTime()

        Log.d("OkHttpLog", "Req $port ${request.url} in ${(t2 - t1) / 1e6}ms")

        return response
    }
}

object HttpClientManager {
    val rootClient =
        OkHttpClient
            .Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .build()

    val DEFAULT_TIMEOUT_ON_WIFI: Duration = Duration.ofSeconds(10L)
    val DEFAULT_TIMEOUT_ON_MOBILE: Duration = Duration.ofSeconds(30L)

    private var defaultTimeout = DEFAULT_TIMEOUT_ON_WIFI
    private var defaultHttpClient: OkHttpClient? = null
    private var defaultHttpClientWithoutProxy: OkHttpClient? = null
    private var userAgent: String = "Amethyst"

    private var currentProxy: Proxy? = null

    fun setDefaultProxy(proxy: Proxy?) {
        if (currentProxy != proxy) {
            Log.d("HttpClient", "Changing proxy to: ${proxy != null}")
            this.currentProxy = proxy

            // recreates singleton
            this.defaultHttpClient = buildHttpClient(currentProxy, defaultTimeout)
        }
    }

    fun getCurrentProxy(): Proxy? = this.currentProxy

    fun setDefaultTimeout(timeout: Duration) {
        Log.d("HttpClient", "Changing timeout to: $timeout")
        if (this.defaultTimeout.seconds != timeout.seconds) {
            this.defaultTimeout = timeout

            // recreates singleton
            this.defaultHttpClient = buildHttpClient(currentProxy, defaultTimeout)
            this.defaultHttpClientWithoutProxy = buildHttpClient(null, defaultTimeout)
        }
    }

    fun setDefaultUserAgent(userAgentHeader: String) {
        Log.d("HttpClient", "Changing userAgent")
        if (userAgent != userAgentHeader) {
            this.userAgent = userAgentHeader
            this.defaultHttpClient = buildHttpClient(currentProxy, defaultTimeout)
            this.defaultHttpClientWithoutProxy = buildHttpClient(null, defaultTimeout)
        }
    }

    private fun buildHttpClient(
        proxy: Proxy?,
        timeout: Duration,
    ): OkHttpClient {
        val seconds = if (proxy != null) timeout.seconds * 3 else timeout.seconds
        val duration = Duration.ofSeconds(seconds)
        return rootClient
            .newBuilder()
            .proxy(proxy)
            .readTimeout(duration)
            .connectTimeout(duration)
            .writeTimeout(duration)
            .addInterceptor(DefaultContentTypeInterceptor(userAgent))
            .addNetworkInterceptor(LoggingInterceptor())
            .build()
    }

    class DefaultContentTypeInterceptor(
        private val userAgentHeader: String,
    ) : Interceptor {
        @Throws(IOException::class)
        override fun intercept(chain: Interceptor.Chain): Response {
            val originalRequest: Request = chain.request()
            val requestWithUserAgent: Request =
                originalRequest
                    .newBuilder()
                    .header("User-Agent", userAgentHeader)
                    .build()
            return chain.proceed(requestWithUserAgent)
        }
    }

    fun getCurrentProxyPort(useProxy: Boolean): Int? =
        if (useProxy) {
            (currentProxy?.address() as? InetSocketAddress)?.port
        } else {
            null
        }

    fun getHttpClient(useProxy: Boolean): OkHttpClient =
        if (useProxy) {
            if (this.defaultHttpClient == null) {
                this.defaultHttpClient = buildHttpClient(currentProxy, defaultTimeout)
            }
            defaultHttpClient!!
        } else {
            if (this.defaultHttpClientWithoutProxy == null) {
                this.defaultHttpClientWithoutProxy = buildHttpClient(null, defaultTimeout)
            }
            defaultHttpClientWithoutProxy!!
        }

    fun setDefaultProxyOnPort(port: Int) {
        setDefaultProxy(Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", port)))
    }
}
