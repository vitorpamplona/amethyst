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
package com.vitorpamplona.amethyst.commons.tor

import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer

/**
 * Whether each kind of HTTP traffic (images, video, previews, money, NIP-05, uploads, push
 * registration, relay directories) goes through Tor, for a URL, under the user's [torSettings].
 *
 * Pure functions of [TorSettingsFlow] and [RelayUrlNormalizer], so every platform shares one
 * decision table: the JVM's `RoleBasedHttpClientBuilder` picks its OkHttp client from these, and
 * a Ktor builder picks its client from the same answers. Relays themselves are decided by
 * [TorRelayEvaluation], not here.
 */
class TorRoleRouting(
    val torSettings: TorSettingsFlow,
) {
    fun shouldUseTorForImageDownload(url: String) =
        shouldUseTorFor(
            url,
            torSettings.torType.value,
            torSettings.imagesViaTor.value,
        )

    fun shouldUseTorFor(
        url: String,
        torType: TorType,
        imagesViaTor: Boolean,
    ) = when (torType) {
        TorType.OFF -> false
        TorType.INTERNAL -> shouldUseTor(url, imagesViaTor)
        TorType.EXTERNAL -> shouldUseTor(url, imagesViaTor)
    }

    private fun shouldUseTor(
        normalizedUrl: String,
        final: Boolean,
    ): Boolean =
        if (RelayUrlNormalizer.isLocalHost(normalizedUrl) || RelayUrlNormalizer.isOverlayNetwork(normalizedUrl)) {
            // Overlay-mesh hosts (0200::/7) are reachable only through the local mesh
            // interface — Tor cannot route the range, so proxying only breaks the fetch.
            false
        } else if (RelayUrlNormalizer.isOnion(normalizedUrl)) {
            true
        } else {
            final
        }

    fun shouldUseTorForVideoDownload() =
        when (torSettings.torType.value) {
            TorType.OFF -> false
            TorType.INTERNAL -> torSettings.videosViaTor.value
            TorType.EXTERNAL -> torSettings.videosViaTor.value
        }

    fun shouldUseTorForVideoDownload(url: String) =
        when (torSettings.torType.value) {
            TorType.OFF -> false
            TorType.INTERNAL -> checkLocalHostOnionAndThen(url, torSettings.videosViaTor.value)
            TorType.EXTERNAL -> checkLocalHostOnionAndThen(url, torSettings.videosViaTor.value)
        }

    fun shouldUseTorForPreviewUrl(url: String) =
        when (torSettings.torType.value) {
            TorType.OFF -> false
            TorType.INTERNAL -> checkLocalHostOnionAndThen(url, torSettings.urlPreviewsViaTor.value)
            TorType.EXTERNAL -> checkLocalHostOnionAndThen(url, torSettings.urlPreviewsViaTor.value)
        }

    fun shouldUseTorForTrustedRelays() =
        when (torSettings.torType.value) {
            TorType.OFF -> false
            TorType.INTERNAL -> torSettings.trustedRelaysViaTor.value
            TorType.EXTERNAL -> torSettings.trustedRelaysViaTor.value
        }

    private fun checkLocalHostOnionAndThen(
        url: String,
        final: Boolean,
    ): Boolean = checkLocalHostOnionAndThen(url, torSettings.onionRelaysViaTor.value, final)

    private fun checkLocalHostOnionAndThen(
        normalizedUrl: String,
        isOnionRelaysActive: Boolean,
        final: Boolean,
    ): Boolean =
        if (RelayUrlNormalizer.isLocalHost(normalizedUrl) || RelayUrlNormalizer.isOverlayNetwork(normalizedUrl)) {
            false
        } else if (RelayUrlNormalizer.isOnion(normalizedUrl)) {
            isOnionRelaysActive
        } else {
            final
        }

    fun shouldUseTorForMoneyOperations(url: String) =
        when (torSettings.torType.value) {
            TorType.OFF -> false
            TorType.INTERNAL -> checkLocalHostOnionAndThen(url, torSettings.moneyOperationsViaTor.value)
            TorType.EXTERNAL -> checkLocalHostOnionAndThen(url, torSettings.moneyOperationsViaTor.value)
        }

    fun shouldUseTorForNIP05(url: String) =
        when (torSettings.torType.value) {
            TorType.OFF -> false
            TorType.INTERNAL -> checkLocalHostOnionAndThen(url, torSettings.nip05VerificationsViaTor.value)
            TorType.EXTERNAL -> checkLocalHostOnionAndThen(url, torSettings.nip05VerificationsViaTor.value)
        }

    fun shouldUseTorForUploads(url: String) =
        when (torSettings.torType.value) {
            TorType.OFF -> false
            TorType.INTERNAL -> checkLocalHostOnionAndThen(url, torSettings.mediaUploadsViaTor.value)
            TorType.EXTERNAL -> checkLocalHostOnionAndThen(url, torSettings.mediaUploadsViaTor.value)
        }

    /**
     * Relay directories (the georelays CSV that routes geohash chats) list relays the user has
     * not picked, so they follow the "new relays" switch: the directory is fetched over Tor
     * whenever the relays it names would be dialed over Tor.
     */
    fun shouldUseTorForRelayDirectory(url: String) =
        when (torSettings.torType.value) {
            TorType.OFF -> false
            TorType.INTERNAL -> checkLocalHostOnionAndThen(url, torSettings.newRelaysViaTor.value)
            TorType.EXTERNAL -> checkLocalHostOnionAndThen(url, torSettings.newRelaysViaTor.value)
        }
}
