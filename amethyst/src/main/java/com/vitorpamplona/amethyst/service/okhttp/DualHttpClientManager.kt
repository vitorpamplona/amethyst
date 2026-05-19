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

import com.vitorpamplona.amethyst.commons.privacy.BlockReason
import com.vitorpamplona.amethyst.commons.privacy.PrivacyRoute
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
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
    dns: SurgeDns,
    // EXTERNAL-only for now (the i2pd / Java I2P SOCKS port). Null while I2P is
    // OFF or not configured; tri-state routing falls back to Direct rather than
    // hold a request when the daemon is unavailable on a clearnet feature.
    private val i2pProxyPortProvider: StateFlow<Int?> = MutableStateFlow(null),
    shouldBridgeBlossomCache: (() -> Boolean)? = null,
) : IHttpClientManager {
    val factory = OkHttpClientFactory(keyCache, userAgent, dns, shouldBridgeBlossomCache)

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

    /**
     * Route-aware client lookup. Direct → no proxy, Tor → Tor SOCKS, I2p → I2P
     * SOCKS, Blocked → IOException so the request fails closed instead of
     * silently leaking to the clearnet.
     *
     * If a daemon-required route lands here while its proxy port is null (daemon
     * not yet started), the I2P / Tor client is still returned — the underlying
     * OkHttp call will fail to connect rather than fall back to a direct route,
     * which is consistent with the fail-closed contract.
     */
    fun getHttpClient(route: PrivacyRoute): OkHttpClient =
        when (route) {
            PrivacyRoute.Direct -> defaultHttpClientWithoutProxy.value
            PrivacyRoute.Tor -> defaultHttpClient.value
            PrivacyRoute.I2p -> i2pHttpClient.value
            is PrivacyRoute.Blocked -> throw blockedException(route.reason)
        }

    fun getCurrentProxyPort(route: PrivacyRoute): Int? =
        when (route) {
            PrivacyRoute.Direct -> null
            PrivacyRoute.Tor -> (getCurrentProxy()?.address() as? InetSocketAddress)?.port
            PrivacyRoute.I2p -> (getCurrentI2pProxy()?.address() as? InetSocketAddress)?.port
            is PrivacyRoute.Blocked -> throw blockedException(route.reason)
        }

    fun getDynamicCallFactory(useProxy: Boolean) = DynamicCallFactory(useProxy, this)

    /**
     * Resolves to the OkHttpClient whose attached SOCKS proxy matches [port]. Used
     * by ExoPlayer's per-port pool to pick the correct transport — `0` / `null`
     * means direct, the live Tor port maps to the Tor-proxied client, the live I2P
     * port maps to the I2P-proxied client. Unknown non-zero ports fall back to
     * direct rather than guess.
     */
    fun getHttpClientForPort(port: Int?): OkHttpClient {
        if (port == null || port <= 0) return defaultHttpClientWithoutProxy.value
        val torPort = (defaultHttpClient.value.proxy?.address() as? InetSocketAddress)?.port
        val i2pPort = (i2pHttpClient.value.proxy?.address() as? InetSocketAddress)?.port
        return when (port) {
            torPort -> defaultHttpClient.value
            i2pPort -> i2pHttpClient.value
            else -> defaultHttpClientWithoutProxy.value
        }
    }

    fun getDynamicCallFactoryForPort(port: Int) = PortBasedCallFactory(port, this)

    companion object {
        fun blockedException(reason: BlockReason): BlockedRouteException = BlockedRouteException(reason)
    }
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

/**
 * Port-keyed version of [DynamicCallFactory]. Lets ExoPlayer's per-port pool
 * route through Tor or I2P (or direct) based on the SOCKS port the caller asked
 * for — resolved live so a proxy-port change without a pool rebuild still picks
 * the right OkHttpClient.
 */
class PortBasedCallFactory(
    val port: Int,
    val manager: DualHttpClientManager,
) : Call.Factory {
    override fun newCall(request: Request): Call = manager.getHttpClientForPort(port).newCall(request)
}
