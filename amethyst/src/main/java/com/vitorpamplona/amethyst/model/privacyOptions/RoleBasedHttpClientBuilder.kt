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

import com.vitorpamplona.amethyst.service.okhttp.DualHttpClientManager
import com.vitorpamplona.amethyst.ui.tor.TorSettingsFlow
import com.vitorpamplona.amethyst.ui.tor.TorType
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import okhttp3.OkHttpClient

class RoleBasedHttpClientBuilder(
    val okHttpClient: DualHttpClientManager,
    val torSettings: TorSettingsFlow,
) : IRoleBasedHttpClientBuilder {
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
        if (RelayUrlNormalizer.isLocalHost(normalizedUrl)) {
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
        if (RelayUrlNormalizer.isLocalHost(normalizedUrl)) {
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

    override fun proxyPortForVideo(url: String): Int? = okHttpClient.getCurrentProxyPort(shouldUseTorForVideoDownload(url))

    override fun okHttpClientForNip05(url: String): OkHttpClient = okHttpClient.getHttpClient(shouldUseTorForNIP05(url))

    override fun okHttpClientForUploads(url: String): OkHttpClient = okHttpClient.getHttpClient(shouldUseTorForUploads(url))

    override fun okHttpClientForImage(url: String): OkHttpClient = okHttpClient.getHttpClient(shouldUseTorForImageDownload(url))

    override fun okHttpClientForVideo(url: String): OkHttpClient = okHttpClient.getHttpClient(shouldUseTorForVideoDownload(url))

    override fun okHttpClientForMoney(url: String): OkHttpClient = okHttpClient.getHttpClient(shouldUseTorForMoneyOperations(url))

    override fun okHttpClientForPreview(url: String): OkHttpClient = okHttpClient.getHttpClient(shouldUseTorForPreviewUrl(url))

    override fun okHttpClientForPushRegistration(url: String): OkHttpClient = okHttpClient.getHttpClient(shouldUseTorForTrustedRelays())
}
