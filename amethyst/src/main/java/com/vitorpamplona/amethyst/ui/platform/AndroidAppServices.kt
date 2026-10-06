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
package com.vitorpamplona.amethyst.ui.platform

import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.commons.browser.BrowserHistoryRegistry
import com.vitorpamplona.amethyst.commons.browser.BrowserIconRegistry
import com.vitorpamplona.amethyst.commons.connectedApps.nip46.Nip46ClientStore
import com.vitorpamplona.amethyst.commons.connectedApps.signers.NostrSignerPermissionStore
import com.vitorpamplona.amethyst.commons.favorites.FavoriteAppsRegistry
import com.vitorpamplona.amethyst.commons.model.location.DeviceLocation
import com.vitorpamplona.amethyst.commons.model.preferences.AppPreferenceStores
import com.vitorpamplona.amethyst.commons.model.preferences.NamecoinSettingsStore
import com.vitorpamplona.amethyst.commons.model.preferences.OtsSettingsStore
import com.vitorpamplona.amethyst.commons.napplet.permissions.NappletPermissionLedger
import com.vitorpamplona.amethyst.commons.relayManagement.Nip86Executor
import com.vitorpamplona.amethyst.commons.relayManagement.Nip86Retriever
import com.vitorpamplona.amethyst.commons.service.AppServices
import com.vitorpamplona.amethyst.commons.service.BlossomServerFinder
import com.vitorpamplona.amethyst.commons.service.ai.AltTextSuggester
import com.vitorpamplona.amethyst.commons.service.namecoin.NamecoinClients
import com.vitorpamplona.amethyst.commons.service.upload.BlossomBlobClient
import com.vitorpamplona.amethyst.commons.service.upload.blossom.BlossomMirrorQueue
import com.vitorpamplona.amethyst.commons.tor.TorSettingsFlow
import com.vitorpamplona.amethyst.service.ai.MLKitImageLabelService
import com.vitorpamplona.amethyst.service.calendar.CalendarReminderWorker
import com.vitorpamplona.amethyst.service.location.CachedReversedGeoLocations
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.ElectrumxServer
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.NamecoinCoreRpcConfig
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.NamecoinNameResolver
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.RpcProbeResult
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.ServerTestResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * [AppServices] over the main process's app modules. Every member is a getter, so installing
 * this object costs nothing and is safe in the `:napplet` process, where `Amethyst.instance` is
 * never built, as long as nothing there reads it.
 */
object AndroidAppServices : AppServices {
    override val favoriteApps: FavoriteAppsRegistry get() = Amethyst.instance.favoriteApps

    override val browserHistory: BrowserHistoryRegistry get() = Amethyst.instance.browserHistory

    override val browserIcons: BrowserIconRegistry get() = Amethyst.instance.browserIcons

    override val nappletPermissionLedger: NappletPermissionLedger get() = Amethyst.instance.nappletPermissionLedger

    override val signerPermissionStore: NostrSignerPermissionStore get() = Amethyst.instance.signerPermissionStore

    override val torSettings: TorSettingsFlow get() = Amethyst.instance.torPrefs.value

    override val torBootstrapped: Flow<Boolean> get() =
        Amethyst.instance.torManager.status
            .map { it.isFullyBootstrapped }

    override val appStores: AppPreferenceStores get() = Amethyst.instance.appStores

    override fun blossomClient(serverBaseUrl: String): BlossomBlobClient = Amethyst.instance.blossomClient(serverBaseUrl)

    override val blossomMirrorQueue: BlossomMirrorQueue get() = Amethyst.instance.blossomMirrorQueue

    override fun setCalendarRemindersScheduled(enabled: Boolean) {
        val context = Amethyst.instance.appContext
        if (enabled) CalendarReminderWorker.schedule(context) else CalendarReminderWorker.cancel(context)
    }

    override val namecoinResolver: NamecoinNameResolver get() = Amethyst.instance.namecoinResolver

    override val namecoinSettings: NamecoinSettingsStore get() = Amethyst.instance.namecoinPrefs

    override val namecoinClients: NamecoinClients = AndroidNamecoinClients

    override val otsSettings: OtsSettingsStore get() = Amethyst.instance.otsPrefs

    override val nip46ClientStore: Nip46ClientStore get() = Amethyst.instance.nip46ClientStore

    override val nip86Executor: Nip86Executor by lazy { Nip86Retriever(Amethyst.instance.torEvaluatorFlow::okHttpClientForRelay) }

    override val blossomServerFinder: BlossomServerFinder = AndroidBlossomServerFinder

    override val deviceLocation: DeviceLocation get() = Amethyst.instance.locationManager

    override fun cachedPlaceName(geohash: String): String? = CachedReversedGeoLocations.cached(geohash)

    override fun createAltTextSuggester(): AltTextSuggester = MLKitImageLabelService(Amethyst.instance.appContext)
}

/** [BlossomServerFinder] over the app's BUD-10 resolver, read lazily (main process only). */
private object AndroidBlossomServerFinder : BlossomServerFinder {
    override fun cachedServerUrl(blossomUri: String): String? =
        Amethyst.instance.blossomResolver
            .cachedFindServer(blossomUri)
            ?.serverUrl

    override suspend fun findServerUrl(blossomUri: String): String? =
        Amethyst.instance.blossomResolver
            .findServers(blossomUri)
            ?.serverUrl
}

/** The app's ElectrumX and Namecoin Core clients, built lazily by [Amethyst.instance]. */
private object AndroidNamecoinClients : NamecoinClients {
    override suspend fun testElectrumxServer(server: ElectrumxServer): ServerTestResult = Amethyst.instance.electrumXClient.testServer(server)

    override suspend fun probeCoreRpc(config: NamecoinCoreRpcConfig): RpcProbeResult = Amethyst.instance.namecoinCoreRpcClient.probe(config)

    override fun addPinnedCert(pem: String) {
        Amethyst.instance.electrumXClient.addPinnedCert(pem)
        Amethyst.instance.namecoinCoreRpcClient.addPinnedCert(pem)
    }

    override fun setCoreRpcConfig(config: NamecoinCoreRpcConfig) {
        Amethyst.instance.namecoinCoreRpcClient.setConfig(config)
    }
}
