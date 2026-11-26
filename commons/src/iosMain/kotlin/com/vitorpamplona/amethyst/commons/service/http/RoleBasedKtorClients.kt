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

import com.vitorpamplona.amethyst.commons.tor.TorRoleRouting
import com.vitorpamplona.amethyst.commons.tor.TorSettingsFlow
import io.ktor.client.HttpClient

/**
 * The per-role Ktor clients: each role asks [torRouting], the decision table Android's
 * `RoleBasedHttpClientBuilder` asks too, and takes the Tor or the direct client from [clients].
 * A role that must go through Tor while Tor is down gets [DualKtorClients.unavailable], never the
 * direct client.
 */
class RoleBasedKtorClients(
    val clients: DualKtorClients,
    val torSettings: TorSettingsFlow,
) : IRoleBasedHttpClientBuilder {
    val torRouting = TorRoleRouting(torSettings)

    override fun ktorClientForPreview(url: String): HttpClient = clients.get(torRouting.shouldUseTorForPreviewUrl(url))

    override fun ktorClientForImage(url: String): HttpClient = clients.get(torRouting.shouldUseTorForImageDownload(url))

    override fun ktorClientForVideo(url: String): HttpClient = clients.get(shouldUseTorForVideo(url))

    /**
     * Every request of a video stream whose own URL must go through Tor ([shouldUseTorForVideo]):
     * its playlists, segments and keys, whatever host each one is on. Android's player proxies a
     * whole stream by its first URL too, so a clearnet CDN behind an onion playlist never sees the
     * phone's address. The loopback HLS relay fetches with this.
     */
    @Suppress("UNUSED_PARAMETER")
    fun ktorClientForTorVideoStream(url: String): HttpClient = clients.get(useTor = true)

    override fun ktorClientForMoney(url: String): HttpClient = clients.get(torRouting.shouldUseTorForMoneyOperations(url))

    override fun ktorClientForNip05(url: String): HttpClient = clients.get(torRouting.shouldUseTorForNIP05(url))

    override fun ktorClientForUploads(url: String): HttpClient = clients.get(torRouting.shouldUseTorForUploads(url))

    override fun ktorClientForPushRegistration(url: String): HttpClient = clients.get(torRouting.shouldUseTorForTrustedRelays())

    /** The georelays CSV and other relay directories: Tor when the relays they list would be. */
    fun ktorClientForRelayDirectory(url: String): HttpClient = clients.get(torRouting.shouldUseTorForRelayDirectory(url))

    override fun shouldUseTorForVideo(url: String): Boolean = torRouting.shouldUseTorForVideoDownload(url)

    fun shouldUseTorForMoneyOperations(url: String): Boolean = torRouting.shouldUseTorForMoneyOperations(url)
}
