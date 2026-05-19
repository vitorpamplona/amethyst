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

import com.vitorpamplona.amethyst.commons.privacy.PrivacyRoute
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import okhttp3.OkHttpClient
import java.net.InetSocketAddress
import java.net.Proxy

class DualHttpClientManagerForRelays(
    userAgent: String,
    proxyPortProvider: StateFlow<Int?>,
    isMobileDataProvider: StateFlow<Boolean?>,
    scope: CoroutineScope,
    dns: SurgeDns,
    private val i2pProxyPortProvider: StateFlow<Int?> = MutableStateFlow(null),
) : IHttpClientManager {
    val factory = OkHttpClientFactoryForRelays(userAgent, dns)

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

    val i2pHttpClient: StateFlow<OkHttpClient> =
        combine(i2pProxyPortProvider, isMobileDataProvider) { proxy, mobile ->
            factory.buildHttpClient(proxy, mobile)
        }.stateIn(
            scope,
            SharingStarted.WhileSubscribed(1000),
            factory.buildHttpClient(i2pProxyPortProvider.value, isMobileDataProvider.value),
        )

    fun getCurrentProxy(): Proxy? = defaultHttpClient.value.proxy

    fun getCurrentI2pProxy(): Proxy? = i2pHttpClient.value.proxy

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

    fun getHttpClient(route: PrivacyRoute): OkHttpClient =
        when (route) {
            PrivacyRoute.Direct -> defaultHttpClientWithoutProxy.value
            PrivacyRoute.Tor -> defaultHttpClient.value
            PrivacyRoute.I2p -> i2pHttpClient.value
            is PrivacyRoute.Blocked -> throw DualHttpClientManager.blockedException(route.reason)
        }

    fun getCurrentProxyPort(route: PrivacyRoute): Int? =
        when (route) {
            PrivacyRoute.Direct -> null
            PrivacyRoute.Tor -> (getCurrentProxy()?.address() as? InetSocketAddress)?.port
            PrivacyRoute.I2p -> (getCurrentI2pProxy()?.address() as? InetSocketAddress)?.port
            is PrivacyRoute.Blocked -> throw DualHttpClientManager.blockedException(route.reason)
        }
}
