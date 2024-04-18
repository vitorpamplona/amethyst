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
package com.vitorpamplona.amethyst.service

import android.util.Log
import com.vitorpamplona.amethyst.BuildConfig
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.time.Duration
import kotlin.properties.Delegates

object HttpClientManager {
    val DEFAULT_TIMEOUT_ON_WIFI: Duration = Duration.ofSeconds(10L)
    val DEFAULT_TIMEOUT_ON_MOBILE: Duration = Duration.ofSeconds(30L)

    var proxyChangeListeners = ArrayList<() -> Unit>()
    private var defaultTimeout = DEFAULT_TIMEOUT_ON_WIFI
    private var defaultHttpClient: OkHttpClient? = null
    private var defaultHttpClientWithoutProxy: OkHttpClient? = null

    // fires off every time value of the property changes
    private var internalProxy: Proxy? by
        Delegates.observable(null) { _, oldValue, newValue ->
            if (oldValue != newValue) {
                proxyChangeListeners.forEach { it() }
            }
        }

    fun setDefaultProxy(proxy: Proxy?) {
        if (internalProxy != proxy) {
            Log.d("HttpClient", "Changing proxy to: ${proxy != null}")
            this.internalProxy = proxy

            // recreates singleton
            this.defaultHttpClient = buildHttpClient(internalProxy, defaultTimeout)
        }
    }

    fun getDefaultProxy(): Proxy? {
        return this.internalProxy
    }

    fun setDefaultTimeout(timeout: Duration) {
        Log.d("HttpClient", "Changing timeout to: $timeout")
        if (this.defaultTimeout.seconds != timeout.seconds) {
            this.defaultTimeout = timeout

            // recreates singleton
            this.defaultHttpClient = buildHttpClient(internalProxy, defaultTimeout)
        }
    }

    private fun buildHttpClient(
        proxy: Proxy?,
        timeout: Duration,
    ): OkHttpClient {
        val seconds = if (proxy != null) timeout.seconds * 3 else timeout.seconds
        val duration = Duration.ofSeconds(seconds)
        return OkHttpClient.Builder()
            .proxy(proxy)
            .readTimeout(duration)
            .connectTimeout(duration)
            .writeTimeout(duration)
            .addInterceptor(DefaultContentTypeInterceptor())
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    class DefaultContentTypeInterceptor : Interceptor {
        @Throws(IOException::class)
        override fun intercept(chain: Interceptor.Chain): Response {
            val originalRequest: Request = chain.request()
            val requestWithUserAgent: Request =
                originalRequest
                    .newBuilder()
                    .header("User-Agent", "Amethyst/${BuildConfig.VERSION_NAME}")
                    .build()
            return chain.proceed(requestWithUserAgent)
        }
    }

    fun getHttpClient(useProxy: Boolean = true): OkHttpClient {
        return if (useProxy) {
            if (this.defaultHttpClient == null) {
                this.defaultHttpClient = buildHttpClient(internalProxy, defaultTimeout)
            }
            defaultHttpClient!!
        } else {
            if (this.defaultHttpClientWithoutProxy == null) {
                this.defaultHttpClientWithoutProxy = buildHttpClient(null, defaultTimeout)
            }
            defaultHttpClientWithoutProxy!!
        }
    }

    fun initProxy(
        useProxy: Boolean,
        hostname: String,
        port: Int,
    ): Proxy? {
        return if (useProxy) Proxy(Proxy.Type.SOCKS, InetSocketAddress(hostname, port)) else null
    }
}
