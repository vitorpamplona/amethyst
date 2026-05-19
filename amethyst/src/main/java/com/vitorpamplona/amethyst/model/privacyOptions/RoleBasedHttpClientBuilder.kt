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
package com.vitorpamplona.amethyst.model.privacyOptions

import com.vitorpamplona.amethyst.commons.privacy.FeatureRole
import com.vitorpamplona.amethyst.commons.privacy.PrivacyRoute
import com.vitorpamplona.amethyst.service.okhttp.DualHttpClientManager
import okhttp3.OkHttpClient
import java.net.InetSocketAddress
import java.net.Proxy
import javax.net.SocketFactory

/**
 * Role-based router for ad-hoc HTTP traffic (images, videos, NIP-05, etc.).
 *
 * Delegates to [PrivacyRoutingFlow] for the per-call decision and to
 * [DualHttpClientManager] for the actual OkHttpClient with the right SOCKS
 * proxy attached. Hidden-service hostnames hard-pin to their matching
 * daemon — [DualHttpClientManager.getHttpClient] throws on `Blocked` rather
 * than silently leak to the clearnet.
 *
 * Legacy `shouldUseTorForX` methods remain, returning `true` iff the route
 * would end up on Tor (call-site references in AppModules still depend on
 * them as method-references); they no longer drive routing themselves.
 */
class RoleBasedHttpClientBuilder(
    val okHttpClient: DualHttpClientManager,
    val routing: PrivacyRoutingFlow,
) : IRoleBasedHttpClientBuilder {
    private fun routeFor(
        role: FeatureRole,
        url: String,
    ): PrivacyRoute = routing.routeFor(role, url)

    override fun proxyPortForVideo(url: String): Int? = okHttpClient.getCurrentProxyPort(routeFor(FeatureRole.VIDEO, url))

    override fun okHttpClientForNip05(url: String): OkHttpClient = okHttpClient.getHttpClient(routeFor(FeatureRole.NIP05, url))

    override fun okHttpClientForUploads(url: String): OkHttpClient = okHttpClient.getHttpClient(routeFor(FeatureRole.UPLOAD, url))

    override fun okHttpClientForImage(url: String): OkHttpClient = okHttpClient.getHttpClient(routeFor(FeatureRole.IMAGE, url))

    override fun okHttpClientForVideo(url: String): OkHttpClient = okHttpClient.getHttpClient(routeFor(FeatureRole.VIDEO, url))

    override fun okHttpClientForMoney(url: String): OkHttpClient = okHttpClient.getHttpClient(routeFor(FeatureRole.MONEY, url))

    override fun okHttpClientForPreview(url: String): OkHttpClient = okHttpClient.getHttpClient(routeFor(FeatureRole.URL_PREVIEW, url))

    // Push registration goes to a trusted relay. Reuse the URL_PREVIEW role for now —
    // the legacy code used `shouldUseTorForTrustedRelays()` which was a no-URL
    // global flag; under the new model we need a URL to compute a route, so the
    // closest analogue is "treat it like any other clearnet ad-hoc HTTP call".
    override fun okHttpClientForPushRegistration(url: String): OkHttpClient = okHttpClient.getHttpClient(routeFor(FeatureRole.URL_PREVIEW, url))

    fun shouldUseTorForImageDownload(url: String) = routeFor(FeatureRole.IMAGE, url) is PrivacyRoute.Tor

    fun shouldUseTorForVideoDownload(url: String) = routeFor(FeatureRole.VIDEO, url) is PrivacyRoute.Tor

    fun shouldUseTorForPreviewUrl(url: String) = routeFor(FeatureRole.URL_PREVIEW, url) is PrivacyRoute.Tor

    fun shouldUseTorForMoneyOperations(url: String) = routeFor(FeatureRole.MONEY, url) is PrivacyRoute.Tor

    fun shouldUseTorForNIP05(url: String) = routeFor(FeatureRole.NIP05, url) is PrivacyRoute.Tor

    fun shouldUseTorForUploads(url: String) = routeFor(FeatureRole.UPLOAD, url) is PrivacyRoute.Tor

    /**
     * Returns a [SocketFactory] that routes through the user's Tor proxy when
     * NIP-05 verification traffic should use Tor.
     *
     * Used by ElectrumXClient so that Namecoin lookups respect the same proxy
     * settings as HTTP-based NIP-05 verification, preventing IP leaks through
     * direct socket connections.
     *
     * I2P-routed NIP-05 lookups are NOT bridged here yet — ElectrumX over I2P
     * needs a SAM/streaming endpoint, not a plain SOCKS hop, and Namecoin name
     * servers aren't deployed on I2P in practice. Falls through to default.
     */
    fun socketFactoryForNip05(): SocketFactory {
        val useTor = shouldUseTorForNIP05("https://electrumx.example.com")
        if (!useTor) return SocketFactory.getDefault()

        val proxy = okHttpClient.getCurrentProxy() ?: return SocketFactory.getDefault()
        val proxyAddr = proxy.address() as? InetSocketAddress ?: return SocketFactory.getDefault()

        return ProxiedSocketFactory(Proxy(Proxy.Type.SOCKS, proxyAddr))
    }
}
