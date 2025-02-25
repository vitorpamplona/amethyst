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
package com.vitorpamplona.amethyst.service.okhttp

import android.util.Log
import com.vitorpamplona.quartz.nip17Dm.files.encryption.NostrCipher
import okhttp3.OkHttpClient
import java.net.InetSocketAddress
import java.net.Proxy
import java.time.Duration

object HttpClientManager {
    private val rootClient =
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

    private val cache = EncryptionKeyCache()

    fun setDefaultProxy(proxy: Proxy?) {
        if (currentProxy != proxy) {
            Log.d("HttpClient", "Changing proxy to: ${proxy != null}")
            currentProxy = proxy

            // recreates singleton
            defaultHttpClient = buildHttpClient(currentProxy, defaultTimeout)
        }
    }

    fun getCurrentProxy(): Proxy? = currentProxy

    fun setDefaultTimeout(timeout: Duration) {
        Log.d("HttpClient", "Changing timeout to: $timeout")
        if (defaultTimeout.seconds != timeout.seconds) {
            defaultTimeout = timeout

            // recreates singleton
            defaultHttpClient = buildHttpClient(currentProxy, defaultTimeout)
            defaultHttpClientWithoutProxy = buildHttpClient(null, defaultTimeout)
        }
    }

    fun setDefaultUserAgent(userAgentHeader: String) {
        Log.d("HttpClient", "Changing userAgent")
        if (userAgent != userAgentHeader) {
            userAgent = userAgentHeader
            defaultHttpClient = buildHttpClient(currentProxy, defaultTimeout)
            defaultHttpClientWithoutProxy = buildHttpClient(null, defaultTimeout)
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
            .addNetworkInterceptor(EncryptedBlobInterceptor(cache))
            .build()
    }

    fun getCurrentProxyPort(useProxy: Boolean): Int? =
        if (useProxy) {
            (currentProxy?.address() as? InetSocketAddress)?.port
        } else {
            null
        }

    fun getHttpClient(useProxy: Boolean): OkHttpClient =
        if (useProxy) {
            if (defaultHttpClient == null) {
                defaultHttpClient = buildHttpClient(currentProxy, defaultTimeout)
            }
            defaultHttpClient!!
        } else {
            if (defaultHttpClientWithoutProxy == null) {
                defaultHttpClientWithoutProxy = buildHttpClient(null, defaultTimeout)
            }
            defaultHttpClientWithoutProxy!!
        }

    fun setDefaultProxyOnPort(port: Int) {
        setDefaultProxy(Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", port)))
    }

    fun addCipherToCache(
        url: String,
        cipher: NostrCipher,
        expectedMimeType: String?,
    ) = cache.add(url, cipher, expectedMimeType)
}
