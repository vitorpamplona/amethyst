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
package com.vitorpamplona.amethyst.commons.service.http

import com.vitorpamplona.amethyst.commons.service.http.OkHttpClientFactoryForRelays.Companion.DEFAULT_IS_MOBILE
import com.vitorpamplona.amethyst.commons.service.http.OkHttpClientFactoryForRelays.Companion.DEFAULT_SOCKS_PORT
import com.vitorpamplona.amethyst.commons.service.http.OkHttpClientFactoryForRelays.Companion.DEFAULT_TIMEOUT_ON_MOBILE_SECS
import com.vitorpamplona.amethyst.commons.service.http.OkHttpClientFactoryForRelays.Companion.DEFAULT_TIMEOUT_ON_WIFI_SECS
import com.vitorpamplona.quartz.nip01Core.relay.sockets.okhttp.SurgeDns
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import java.net.InetSocketAddress
import java.net.Proxy
import java.time.Duration
import java.util.concurrent.TimeUnit

/**
 * Builder for the general-purpose (non-relay) OkHttp client used by image
 * downloads, NIP-96/Blossom uploads, NIP-05 lookups, LNURL/money, link
 * previews, push registration, etc. Each [DualHttpClientManager] holds one
 * of these and rebuilds clients on Tor proxy / mobile-data changes.
 *
 * [OnionLocationCache] is **required**: every OkHttp client minted by this
 * factory installs [OnionLocationInterceptor] on the no-proxy and Tor-proxied
 * variants so any HTTP/WebSocket response that carries an `Onion-Location`
 * header (NIP-11 docs, image hosts, blossom servers, NIP-96 endpoints, LNURL
 * providers, anything) populates the cache. Tor-proxied clients additionally
 * install [OnionUrlRewriteInterceptor] so subsequent requests are rerouted
 * to the cached `.onion` and avoid exit nodes entirely. Treating the cache as
 * required (not nullable) prevents a future call site from silently disabling
 * onion-routing for an entire HTTP role.
 */
class OkHttpClientFactory(
    keyCache: EncryptionKeyCache,
    val userAgent: String,
    private val dns: SurgeDns,
    /**
     * Returns `true` when sha256-keyed HTTP requests should be transparently
     * rewritten to the local Blossom cache (master toggle on, profile-pictures-only
     * restriction off, probe up). When `null`, the interceptor is disabled —
     * useful for tests or pre-configuration call sites.
     */
    val shouldBridgeBlossomCache: (() -> Boolean)? = null,
    private val onionCache: OnionLocationCache,
    /**
     * Resource-usage ledger counter, installed OUTERMOST on the shared base
     * client so every request through any derived client is accounted —
     * per-role tagging wrappers only relabel, they never bypass. Null in
     * tests / pre-configuration call sites.
     */
    private val usageInterceptor: Interceptor? = null,
    /**
     * Retries auth-gated Blossom blob downloads with a signed BUD-01 `t=get`
     * token when the host answers `401` (e.g. Buzz's private media relay). Null
     * in tests / pre-configuration call sites, in which case such downloads stay
     * anonymous. See [BlossomReadAuthInterceptor].
     */
    private val blossomReadAuth: Interceptor? = null,
) {
    // val logging = LoggingInterceptor()
    val keyDecryptor = EncryptedBlobInterceptor(keyCache)
    private val blossomCacheRedirect =
        shouldBridgeBlossomCache?.let { LocalBlossomCacheRedirectInterceptor(it) }

    // Most images/videos in a feed come from a small set of hosts (e.g. a single
    // Blossom/imgproxy server). OkHttp's default dispatcher caps inflight requests
    // per host at 5, which serializes feed loading, so we lift that.
    //
    // The TOTAL, though, has to stay in phone territory. Dispatcher's executor is
    // an unbounded cached pool (SynchronousQueue), so maxRequests is literally the
    // thread ceiling: 128 meant up to 128 threads doing 128 concurrent TLS
    // handshakes on a handset. Nothing upstream bounds the arrival rate either --
    // Coil's enqueue is unbounded and PrefetchFeedMedia warms ±3 notes on BOTH
    // sides of the viewport on every visible-range change -- so the queue really
    // does reach the cap on a fast scroll. Past the point where the radio and the
    // CPU are saturated, more concurrency doesn't add throughput, it just slices
    // the same bandwidth thinner and pushes every image's completion out
    // together, including the one actually on screen. A tighter total lets the
    // visible images finish and paint while the rest wait their turn.
    private val dispatcher =
        Dispatcher().apply {
            if (!HttpClientEnvironment.isEmulator) {
                maxRequestsPerHost = 8
                maxRequests = 32
            } else {
                maxRequestsPerHost = 5
                maxRequests = 64
            }
        }

    // Keep more HTTP/2 connections warm so scrolling doesn't repeatedly re-TLS
    // to the same media host.
    private val connectionPool = ConnectionPool(32, 5, TimeUnit.MINUTES)

    private val rootClient =
        OkHttpClient
            .Builder()
            .dispatcher(dispatcher)
            .connectionPool(connectionPool)
            .dns(dns)
            .eventListenerFactory(MediaCallEventListenerFactory(dispatcher, connectionPool, dns))
            // Some media hosts (e.g. blossom.primal.net) silently drop idle HTTP/2
            // connections between the bursts of a feed scroll. Without a keepalive
            // ping, OkHttp can pull such a dead connection from the 5-min pool and
            // the request stalls until the read timeout (30s wifi / 90s mobile),
            // which shows up as "the first image after a pause takes forever".
            // An HTTP/2 ping detects the dead connection in seconds and lets
            // retryOnConnectionFailure re-issue the request on a fresh one.
            .pingInterval(Duration.ofSeconds(HTTP2_PING_INTERVAL_SECS))
            .followRedirects(true)
            .followSslRedirects(true)
            .apply { usageInterceptor?.let { addInterceptor(it) } }
            .addInterceptor(DefaultContentTypeInterceptor(userAgent))
            .apply {
                blossomCacheRedirect?.let { addInterceptor(it) }
            }
            // Sits outside the network interceptors so its retry re-runs the
            // full stack (content-type, blossom cache, key decryptor) for the
            // authenticated response. Only signs on an actual 401.
            .apply {
                blossomReadAuth?.let { addInterceptor(it) }
            }
            // .addNetworkInterceptor(logging)
            .addNetworkInterceptor(keyDecryptor)
            // Passively populates [onionCache] from any HTTP/WebSocket response
            // that carries an `Onion-Location` header. Application-scoped so the
            // cache key is always the original clearnet host (see kdoc on
            // [OnionLocationInterceptor]).
            .addInterceptor(OnionLocationInterceptor(onionCache))
            .build()

    private val proxyRoutes = ProxyRouteTracker()

    fun buildHttpClient(
        proxy: Proxy?,
        timeoutSeconds: Int,
    ): OkHttpClient {
        if (proxyRoutes.shouldEvictFor(proxy)) {
            rootClient.connectionPool.evictAll()
        }
        val seconds = if (proxy != null) timeoutSeconds * 3 else timeoutSeconds
        return rootClient
            .newBuilder()
            .proxy(proxy)
            // Only the Tor-routed variant rewrites outbound URLs to known
            // `.onion`s — clearnet clients must never try to resolve `.onion`
            // (DNS would fail, and we don't want fingerprintable lookups).
            .apply { if (proxy != null) addInterceptor(OnionUrlRewriteInterceptor(onionCache)) }
            .connectTimeout(Duration.ofSeconds(seconds.toLong()))
            .readTimeout(Duration.ofSeconds(seconds.toLong() * 3))
            .writeTimeout(Duration.ofSeconds(seconds.toLong() * 3))
            .build()
    }

    fun buildHttpClient(
        localSocksProxyPort: Int?,
        isMobile: Boolean?,
    ): OkHttpClient =
        buildHttpClient(
            buildLocalSocksProxy(localSocksProxyPort),
            buildTimeout(isMobile ?: DEFAULT_IS_MOBILE),
        )

    fun buildHttpClient(isMobile: Boolean?): OkHttpClient =
        buildHttpClient(
            null,
            buildTimeout(isMobile ?: DEFAULT_IS_MOBILE),
        )

    fun buildTimeout(isMobile: Boolean): Int =
        if (isMobile) {
            DEFAULT_TIMEOUT_ON_MOBILE_SECS
        } else {
            DEFAULT_TIMEOUT_ON_WIFI_SECS
        }

    fun buildLocalSocksProxy(port: Int?) = Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", port ?: DEFAULT_SOCKS_PORT))

    companion object {
        // HTTP/2 keepalive ping for pooled media connections. Short enough to
        // evict a silently-dropped connection well before the read timeout would
        // otherwise stall a request for the full 30s/90s.
        const val HTTP2_PING_INTERVAL_SECS: Long = 10
    }
}

/**
 * Decides when a rebuilt client must drop the pooled connections it shares with every other
 * client [OkHttpClientFactory] mints.
 *
 * The eviction exists so a changed Tor route doesn't leave usable connections behind on the old
 * one. The trap is that a single factory mints BOTH long-lived variants — [DualHttpClientManager]
 * builds `defaultHttpClient` (always SOCKS, because `buildLocalSocksProxy` falls back to 9050
 * rather than returning null) and `defaultHttpClientWithoutProxy` (always null) from the same
 * instance, and they share one `rootClient.connectionPool`. Comparing every build against one
 * "last proxy" field therefore saw the two variants alternate forever: each rebuild looked like a
 * route change and wiped the pool they share. Both `stateIn` flows re-emit on every
 * `isMobileDataProvider` change and every resubscribe (they are `WhileSubscribed(1000)`, collected
 * from a composable), so in practice the pool was emptied whenever the network flapped or the app
 * came back to the foreground — and the next image then paid a fresh DNS + TCP + TLS.
 *
 * Two rules fix it:
 *
 *  - A direct build (`proxy == null`) never evicts. `null` is that variant's permanent route, so
 *    it can never have changed.
 *  - A proxied build evicts only when the proxy differs from the one the PREVIOUS proxied build
 *    used, i.e. the Tor port actually moved.
 *
 * Nothing is lost by being this narrow: OkHttp's `Address` — the connection-pool key — includes
 * the proxy, so a direct connection and a SOCKS connection to the same host are already distinct
 * entries that can never be handed to each other's calls.
 */
internal class ProxyRouteTracker {
    private var lastProxy: Proxy? = null

    @Synchronized
    fun shouldEvictFor(proxy: Proxy?): Boolean {
        if (proxy == null) return false

        val previous = lastProxy
        lastProxy = proxy
        return previous != null && previous != proxy
    }
}
