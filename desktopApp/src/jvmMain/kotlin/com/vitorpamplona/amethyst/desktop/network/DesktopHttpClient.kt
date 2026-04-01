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
package com.vitorpamplona.amethyst.desktop.network

import com.vitorpamplona.amethyst.commons.tor.ITorManager
import com.vitorpamplona.amethyst.commons.tor.TorType
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.isLocalHost
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.isOnion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit

/**
 * Desktop HTTP client with optional SOCKS proxy support for Tor.
 *
 * Maintains two clients: one with SOCKS proxy (for Tor-routed traffic)
 * and one without (for direct connections). Selects the appropriate
 * client per relay URL based on Tor settings.
 *
 * When Tor is OFF, all relays use the direct client.
 * When Tor is ON, .onion relays always use the proxy client.
 * Localhost relays always use the direct client.
 */
class DesktopHttpClient(
    torManager: ITorManager,
    private val shouldUseTorForRelay: (NormalizedRelayUrl) -> Boolean,
    private val torTypeProvider: () -> TorType,
    scope: CoroutineScope,
) {
    /** Returns true if user expects Tor routing (INTERNAL or EXTERNAL mode). */
    fun isTorExpected(): Boolean = torTypeProvider() != TorType.OFF

    private val sharedConnectionPool = ConnectionPool()

    private val directClient: OkHttpClient by lazy {
        OkHttpClient
            .Builder()
            .connectionPool(sharedConnectionPool)
            .connectTimeout(BASE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(BASE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(BASE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .pingInterval(BASE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    /** Proxy client, rebuilt when SOCKS port changes. */
    val proxyClient: StateFlow<OkHttpClient?> =
        torManager.activePortOrNull
            .map { port ->
                if (port == null) {
                    null
                } else {
                    val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", port))
                    val torTimeout = BASE_TIMEOUT_SECONDS * TOR_TIMEOUT_MULTIPLIER
                    OkHttpClient
                        .Builder()
                        .connectionPool(sharedConnectionPool)
                        .proxy(proxy)
                        .connectTimeout(torTimeout, TimeUnit.SECONDS)
                        .readTimeout(torTimeout, TimeUnit.SECONDS)
                        .writeTimeout(torTimeout, TimeUnit.SECONDS)
                        .pingInterval(BASE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                        .retryOnConnectionFailure(true)
                        .build()
                }
            }.stateIn(scope, SharingStarted.Eagerly, null)

    /**
     * Returns the appropriate OkHttpClient for the given relay URL.
     *
     * - Localhost relays → always direct (no proxy)
     * - .onion relays when Tor active → proxy client
     * - Other relays → based on TorRelayEvaluation routing decision
     * - If proxy needed but not available (Tor bootstrapping) → direct client as fallback
     */
    fun getHttpClient(url: NormalizedRelayUrl): OkHttpClient {
        if (url.isLocalHost()) return directClient

        val torActive = proxyClient.value != null
        if (!torActive) return directClient

        // .onion always needs Tor
        if (url.isOnion()) return proxyClient.value ?: directClient

        // Delegate to TorRelayEvaluation for routing decision
        return if (shouldUseTorForRelay(url)) {
            proxyClient.value ?: directClient
        } else {
            directClient
        }
    }

    /** Returns the direct (non-proxy) client for non-relay HTTP traffic. */
    fun getNonProxyClient(): OkHttpClient = directClient

    companion object {
        private const val BASE_TIMEOUT_SECONDS = 30L
        private const val TOR_TIMEOUT_MULTIPLIER = 2 // Desktop: 2x, not Android's 3x

        lateinit var instance: DesktopHttpClient
            private set

        fun setInstance(client: DesktopHttpClient) {
            instance = client
        }

        /** Fail-closed client: SOCKS proxy on dead port 1. Requests fail instead of leaking IP. */
        private val failClosedClient: OkHttpClient by lazy {
            OkHttpClient
                .Builder()
                .proxy(Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", 1)))
                .connectTimeout(1, TimeUnit.SECONDS)
                .readTimeout(1, TimeUnit.SECONDS)
                .build()
        }

        /** Simple direct client for pre-init only (tests, startup). */
        private val simpleClient: OkHttpClient by lazy {
            OkHttpClient
                .Builder()
                .connectTimeout(BASE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(BASE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .writeTimeout(BASE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .pingInterval(BASE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build()
        }

        /**
         * Returns the current Tor-aware client.
         * - Tor active → proxy client (SOCKS)
         * - Tor off → direct client
         * - Tor expected but bootstrapping → FAIL-CLOSED (dead proxy, requests fail not leak)
         * - Instance not set → simpleClient (pre-init/tests only)
         */
        fun currentClient(): OkHttpClient {
            if (!::instance.isInitialized) return simpleClient
            val client = instance
            val proxyClient = client.proxyClient.value
            if (proxyClient != null) return proxyClient
            return if (client.isTorExpected()) failClosedClient else client.getNonProxyClient()
        }

        /** Backward-compatible static accessor for relay WebSocket builder. */
        fun getSimpleHttpClient(url: NormalizedRelayUrl): OkHttpClient = currentClient()
    }
}
