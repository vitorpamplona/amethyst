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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetSocketAddress
import java.net.Proxy

class DualHttpClientManager(
    userAgent: String,
    proxyPortProvider: StateFlow<Int?>,
    isMobileDataProvider: StateFlow<Boolean?>,
    keyCache: EncryptionKeyCache,
    scope: CoroutineScope,
) : IHttpClientManager {
    val factory = OkHttpClientFactory(keyCache, userAgent)

    val defaultHttpClient: StateFlow<OkHttpClient> =
        combine(proxyPortProvider, isMobileDataProvider) { proxy, mobile ->
            factory.buildHttpClient(proxy, mobile)
        }.stateIn(
            scope,
            SharingStarted.WhileSubscribed(1000),
            factory.buildHttpClient(proxyPortProvider.value, isMobileDataProvider.value),
        )

    val defaultHttpClientWithoutProxy: StateFlow<OkHttpClient> =
        isMobileDataProvider
            .map { mobile ->
                factory.buildHttpClient(mobile)
            }.stateIn(
                scope,
                SharingStarted.WhileSubscribed(1000),
                factory.buildHttpClient(isMobileDataProvider.value),
            )

    fun getCurrentProxy(): Proxy? = defaultHttpClient.value.proxy

    override fun getCurrentProxyPort(useProxy: Boolean): Int? =
        if (useProxy) {
            (getCurrentProxy()?.address() as? InetSocketAddress)?.port
        } else {
            null
        }

    override fun getHttpClient(useProxy: Boolean): OkHttpClient =
        if (useProxy) {
            defaultHttpClient.value
        } else {
            defaultHttpClientWithoutProxy.value
        }

    fun getDynamicCallFactory(useProxy: Boolean) = DynamicCallFactory(useProxy, this)
}

/**
 * the okhttp can change on the manager without affecting other systems.
 */
class DynamicCallFactory(
    val useProxy: Boolean,
    val manager: DualHttpClientManager,
) : Call.Factory {
    override fun newCall(request: Request): Call = manager.getHttpClient(useProxy).newCall(request)
}
