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

import com.vitorpamplona.amethyst.commons.service.http.DualHttpClientManager
import com.vitorpamplona.amethyst.commons.service.http.IRoleBasedHttpClientBuilder
import com.vitorpamplona.amethyst.commons.service.http.ProxiedSocketFactory
import com.vitorpamplona.amethyst.commons.service.resourceusage.UsageKeys
import com.vitorpamplona.amethyst.commons.tor.TorRoleRouting
import com.vitorpamplona.amethyst.commons.tor.TorSettingsFlow
import com.vitorpamplona.amethyst.commons.tor.TorType
import okhttp3.OkHttpClient
import java.net.InetSocketAddress
import java.net.Proxy
import javax.net.SocketFactory

class RoleBasedHttpClientBuilder(
    val okHttpClient: DualHttpClientManager,
    val torSettings: TorSettingsFlow,
    /**
     * When present, every role's client is wrapped with a byte-counting
     * interceptor so the resource-usage ledger can attribute HTTP traffic
     * per subsystem. Null keeps the raw shared clients (tests).
     */
    val usageMeter: ((role: String, base: OkHttpClient) -> OkHttpClient)? = null,
) : IRoleBasedHttpClientBuilder {
    private fun metered(
        role: String,
        base: OkHttpClient,
    ): OkHttpClient = usageMeter?.invoke(role, base) ?: base

    /**
     * The per-role Tor decisions. They live in commonMain ([TorRoleRouting]) so the platforms
     * without OkHttp share the same table; the methods below are its JVM face, unchanged.
     */
    val torRouting = TorRoleRouting(torSettings)

    fun shouldUseTorForImageDownload(url: String) = torRouting.shouldUseTorForImageDownload(url)

    fun shouldUseTorFor(
        url: String,
        torType: TorType,
        imagesViaTor: Boolean,
    ) = torRouting.shouldUseTorFor(url, torType, imagesViaTor)

    fun shouldUseTorForVideoDownload() = torRouting.shouldUseTorForVideoDownload()

    fun shouldUseTorForVideoDownload(url: String) = torRouting.shouldUseTorForVideoDownload(url)

    fun shouldUseTorForPreviewUrl(url: String) = torRouting.shouldUseTorForPreviewUrl(url)

    fun shouldUseTorForTrustedRelays() = torRouting.shouldUseTorForTrustedRelays()

    fun shouldUseTorForMoneyOperations(url: String) = torRouting.shouldUseTorForMoneyOperations(url)

    fun shouldUseTorForNIP05(url: String) = torRouting.shouldUseTorForNIP05(url)

    fun shouldUseTorForUploads(url: String) = torRouting.shouldUseTorForUploads(url)

    /**
     * Relay directories (the georelays CSV that routes geohash chats) list relays the user has
     * not picked, so they follow the "new relays" switch: the directory is fetched over Tor
     * whenever the relays it names would be dialed over Tor.
     */
    fun shouldUseTorForRelayDirectory(url: String) = torRouting.shouldUseTorForRelayDirectory(url)

    // Not [metered]: untagged requests already count as UsageKeys.ROLE_OTHER.
    fun okHttpClientForRelayDirectory(url: String): OkHttpClient = okHttpClient.getHttpClient(shouldUseTorForRelayDirectory(url))

    override fun proxyPortForVideo(url: String): Int? = okHttpClient.getCurrentProxyPort(shouldUseTorForVideoDownload(url))

    override fun okHttpClientForNip05(url: String): OkHttpClient = metered(UsageKeys.ROLE_NIP05, okHttpClient.getHttpClient(shouldUseTorForNIP05(url)))

    override fun okHttpClientForUploads(url: String): OkHttpClient = metered(UsageKeys.ROLE_UPLOADS, okHttpClient.getHttpClient(shouldUseTorForUploads(url)))

    override fun okHttpClientForImage(url: String): OkHttpClient = metered(UsageKeys.ROLE_IMAGE, okHttpClient.getHttpClient(shouldUseTorForImageDownload(url)))

    override fun okHttpClientForVideo(url: String): OkHttpClient = metered(UsageKeys.ROLE_VIDEO, okHttpClient.getHttpClient(shouldUseTorForVideoDownload(url)))

    override fun okHttpClientForMoney(url: String): OkHttpClient = metered(UsageKeys.ROLE_MONEY, okHttpClient.getHttpClient(shouldUseTorForMoneyOperations(url)))

    override fun okHttpClientForPreview(url: String): OkHttpClient = metered(UsageKeys.ROLE_PREVIEW, okHttpClient.getHttpClient(shouldUseTorForPreviewUrl(url)))

    override fun okHttpClientForPushRegistration(url: String): OkHttpClient = metered(UsageKeys.ROLE_PUSH, okHttpClient.getHttpClient(shouldUseTorForTrustedRelays()))

    /**
     * Returns a [SocketFactory] that routes through the user's Tor proxy
     * when NIP-05 verification traffic should use Tor.
     *
     * Used by [ElectrumxClient] so that Namecoin lookups respect the
     * same proxy/Tor settings as HTTP-based NIP-05 verification,
     * preventing IP leaks through direct socket connections.
     */
    fun socketFactoryForNip05(): SocketFactory {
        // ElectrumX servers are always external, so we use a dummy
        // non-localhost, non-onion URL to query the Tor policy.
        val useTor = shouldUseTorForNIP05("https://electrumx.example.com")
        if (!useTor) return SocketFactory.getDefault()

        val proxy = okHttpClient.getCurrentProxy() ?: return SocketFactory.getDefault()
        val proxyAddr = proxy.address() as? InetSocketAddress ?: return SocketFactory.getDefault()

        return ProxiedSocketFactory(Proxy(Proxy.Type.SOCKS, proxyAddr))
    }
}
