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

import com.vitorpamplona.quartz.nip01Core.relay.sockets.okhttp.SurgeDns
import com.vitorpamplona.quartz.nip01Core.relay.sockets.okhttp.TcpNoDelaySocketFactory
import com.vitorpamplona.quartz.utils.Log
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import java.net.InetSocketAddress
import java.net.Proxy
import java.time.Duration

class OkHttpClientFactoryForRelays(
    userAgent: String,
    private val dns: SurgeDns,
    private val onionCache: OnionLocationCache,
) {
    companion object {
        // by picking a random proxy port, the connection will fail as it should.
        const val DEFAULT_SOCKS_PORT: Int = 9050
        const val DEFAULT_IS_MOBILE: Boolean = false
        const val DEFAULT_TIMEOUT_ON_WIFI_SECS: Int = 10
        const val DEFAULT_TIMEOUT_ON_MOBILE_SECS: Int = 30

        // Every WebSocket ping on an otherwise-idle cellular connection promotes
        // the radio out of its low-power state and pays the multi-second "tail"
        // energy cost — per connection. On mobile data the interval is doubled to
        // halve those wake-ups while staying under the ~5-minute idle timeout of
        // the most aggressive carrier NATs (so connections aren't silently dropped
        // between pings). Wifi keeps the tighter interval: pings there are cheap
        // and detect dead connections sooner.
        const val WEBSOCKET_PING_INTERVAL_ON_WIFI_SECS: Long = 120
        const val WEBSOCKET_PING_INTERVAL_ON_MOBILE_SECS: Long = 240
    }

    val myDispatcher =
        Dispatcher().apply {
            if (!isEmulator()) {
                maxRequestsPerHost = 10
                maxRequests = 1024
            } else {
                maxRequestsPerHost = 5
                maxRequests = 256
                Log.i("OkHttpClientFactory", "Emulator detected, using default maxRequests: 64.")
            }
        }

    private val rootClient =
        OkHttpClient
            .Builder()
            .dispatcher(myDispatcher)
            // TCP_NODELAY: a CLOSE (never answered by relays) followed by a
            // REQ — every feed switch — otherwise nagles the REQ behind the
            // unACKed CLOSE for the peer's delayed-ACK window (~40 ms+).
            // See quartz TcpNoDelaySocketFactory. Direct connections only;
            // the Tor SOCKS path is unaffected.
            .socketFactory(TcpNoDelaySocketFactory)
            .dns(dns)
            .eventListenerFactory(DnsInvalidatingEventListener.Factory(dns))
            .followRedirects(true)
            .followSslRedirects(true)
            .addInterceptor(DefaultContentTypeInterceptor(userAgent))
            .addInterceptor(OnionLocationInterceptor(onionCache))
            .build()

    private var lastProxy: Proxy? = null

    fun buildHttpClient(
        proxy: Proxy?,
        timeoutSeconds: Int,
        pingIntervalSeconds: Long = WEBSOCKET_PING_INTERVAL_ON_WIFI_SECS,
    ): OkHttpClient {
        if (proxy != lastProxy) {
            rootClient.connectionPool.evictAll()
            lastProxy = proxy
        }
        val seconds = if (proxy != null) timeoutSeconds * 3 else timeoutSeconds
        return rootClient
            .newBuilder()
            .proxy(proxy)
            .apply { if (proxy != null) addInterceptor(OnionUrlRewriteInterceptor(onionCache)) }
            .connectTimeout(Duration.ofSeconds(seconds.toLong()))
            .readTimeout(Duration.ofSeconds(seconds.toLong() * 3))
            .writeTimeout(Duration.ofSeconds(seconds.toLong() * 3))
            .pingInterval(Duration.ofSeconds(pingIntervalSeconds))
            .build()
    }

    fun buildHttpClient(
        localSocksProxyPort: Int?,
        isMobile: Boolean?,
    ): OkHttpClient =
        buildHttpClient(
            buildLocalSocksProxy(localSocksProxyPort),
            buildTimeout(isMobile ?: DEFAULT_IS_MOBILE),
            buildPingInterval(isMobile ?: DEFAULT_IS_MOBILE),
        )

    fun buildHttpClient(isMobile: Boolean?): OkHttpClient =
        buildHttpClient(
            null,
            buildTimeout(isMobile ?: DEFAULT_IS_MOBILE),
            buildPingInterval(isMobile ?: DEFAULT_IS_MOBILE),
        )

    fun buildTimeout(isMobile: Boolean): Int =
        if (isMobile) {
            DEFAULT_TIMEOUT_ON_MOBILE_SECS
        } else {
            DEFAULT_TIMEOUT_ON_WIFI_SECS
        }

    fun buildPingInterval(isMobile: Boolean): Long =
        if (isMobile) {
            WEBSOCKET_PING_INTERVAL_ON_MOBILE_SECS
        } else {
            WEBSOCKET_PING_INTERVAL_ON_WIFI_SECS
        }

    fun buildLocalSocksProxy(port: Int?) = Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", port ?: DEFAULT_SOCKS_PORT))
}
