/**
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

import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import java.net.InetSocketAddress
import java.net.Proxy
import java.time.Duration

class OkHttpClientFactory(
    keyCache: EncryptionKeyCache,
) {
    companion object {
        // by picking a random proxy port, the connection will fail as it should.
        const val DEFAULT_SOCKS_PORT: Int = 9050
        const val DEFAULT_IS_MOBILE: Boolean = false
        const val DEFAULT_TIMEOUT_ON_WIFI_SECS: Int = 10
        const val DEFAULT_TIMEOUT_ON_MOBILE_SECS: Int = 30
    }

    val logging = LoggingInterceptor()
    val keyDecryptor = EncryptedBlobInterceptor(keyCache)

    val myDispatcher =
        Dispatcher().apply {
            maxRequests = 512
        }

    /*
    DEBUG OK HTTP connections here.
    init {
        if (isDebug) {
            GlobalScope.launch(Dispatchers.IO) {
                while (true) {
                    Log.d("OkHttpClientFactory", "Active threads ${myDispatcher.runningCallsCount()}")
                    delay(5000)
                }
            }
        }
    }
     */

    private val rootClient =
        OkHttpClient
            .Builder()
            .dispatcher(myDispatcher)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()

    fun buildHttpClient(
        proxy: Proxy?,
        timeoutSeconds: Int,
        userAgent: String,
    ): OkHttpClient {
        val seconds = if (proxy != null) timeoutSeconds * 3 else timeoutSeconds
        return rootClient
            .newBuilder()
            .proxy(proxy)
            .connectTimeout(Duration.ofSeconds(seconds.toLong()))
            .readTimeout(Duration.ofSeconds(seconds.toLong() * 3))
            .writeTimeout(Duration.ofSeconds(seconds.toLong() * 3))
            .addInterceptor(DefaultContentTypeInterceptor(userAgent))
            .addNetworkInterceptor(logging)
            .addNetworkInterceptor(keyDecryptor)
            .build()
    }

    fun buildHttpClient(
        localSocksProxyPort: Int?,
        isMobile: Boolean?,
        userAgent: String,
    ): OkHttpClient =
        buildHttpClient(
            buildLocalSocksProxy(localSocksProxyPort),
            buildTimeout(isMobile ?: DEFAULT_IS_MOBILE),
            userAgent,
        )

    fun buildHttpClient(
        isMobile: Boolean?,
        userAgent: String,
    ): OkHttpClient =
        buildHttpClient(
            null,
            buildTimeout(isMobile ?: DEFAULT_IS_MOBILE),
            userAgent,
        )

    fun buildTimeout(isMobile: Boolean): Int =
        if (isMobile) {
            DEFAULT_TIMEOUT_ON_MOBILE_SECS
        } else {
            DEFAULT_TIMEOUT_ON_WIFI_SECS
        }

    fun buildLocalSocksProxy(port: Int?) = Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", port ?: DEFAULT_SOCKS_PORT))
}
