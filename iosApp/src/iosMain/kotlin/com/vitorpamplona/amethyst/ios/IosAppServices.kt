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
package com.vitorpamplona.amethyst.ios

import com.vitorpamplona.amethyst.commons.browser.BrowserHistoryRegistry
import com.vitorpamplona.amethyst.commons.browser.BrowserIconRegistry
import com.vitorpamplona.amethyst.commons.connectedApps.nip46.Nip46ClientStore
import com.vitorpamplona.amethyst.commons.connectedApps.signers.NostrSignerPermissionStore
import com.vitorpamplona.amethyst.commons.cordn.BlossomCordnBlobStore
import com.vitorpamplona.amethyst.commons.cordn.KtorCordnHttp
import com.vitorpamplona.amethyst.commons.favorites.FavoriteAppsRegistry
import com.vitorpamplona.amethyst.commons.model.dock.DockLayoutPreferences
import com.vitorpamplona.amethyst.commons.model.location.DeviceLocation
import com.vitorpamplona.amethyst.commons.model.location.PlaceHit
import com.vitorpamplona.amethyst.commons.model.preferences.AppPreferenceStores
import com.vitorpamplona.amethyst.commons.model.preferences.DrawerSectionCollapsePreferences
import com.vitorpamplona.amethyst.commons.model.preferences.NamecoinSettingsStore
import com.vitorpamplona.amethyst.commons.model.preferences.OtsSettingsStore
import com.vitorpamplona.amethyst.commons.model.preferences.PaneWidthPreferences
import com.vitorpamplona.amethyst.commons.napplet.permissions.NappletPermissionLedger
import com.vitorpamplona.amethyst.commons.relayClient.auth.AuthCoordinator
import com.vitorpamplona.amethyst.commons.relayManagement.KtorNip86Retriever
import com.vitorpamplona.amethyst.commons.relayManagement.Nip86Executor
import com.vitorpamplona.amethyst.commons.scheduledposts.ScheduledPostStore
import com.vitorpamplona.amethyst.commons.service.AppServices
import com.vitorpamplona.amethyst.commons.service.pow.PoWPublishQueue
import com.vitorpamplona.amethyst.commons.service.upload.BlossomBlobClient
import com.vitorpamplona.amethyst.commons.service.upload.blossom.BlossomMirrorQueue
import com.vitorpamplona.amethyst.commons.tor.TorServiceStatus
import com.vitorpamplona.amethyst.commons.tor.TorSettingsFlow
import com.vitorpamplona.cordn.client.CordnBlobStore
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.ElectrumxServer
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.IElectrumXClient
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.NameShowResult
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.NamecoinNameResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** The app-wide services the shared screens read, from [IosAppModules]. */
class IosAppServices(
    private val modules: IosAppModules,
) : AppServices {
    override val favoriteApps: FavoriteAppsRegistry get() = modules.favoriteApps

    override val browserHistory: BrowserHistoryRegistry get() = modules.browserHistory

    override val browserIcons: BrowserIconRegistry get() = modules.browserIcons

    override val nappletPermissionLedger: NappletPermissionLedger get() = modules.nappletPermissionLedger

    override val signerPermissionStore: NostrSignerPermissionStore get() = modules.signerPermissionStore

    override val torSettings: TorSettingsFlow get() = modules.torPrefs.value

    override val torBootstrapped: Flow<Boolean> get() = modules.torManager.status.map { it.isFullyBootstrapped }

    // The manager's status as is: Bootstrapping (a bound port still downloading its directory) reads
    // as connecting in the shared UI, as on Android.
    override val torStatus: Flow<TorServiceStatus> get() = modules.torManager.status

    override val torStuck: Flow<Boolean> get() = modules.torManager.connectionFailure

    override val drawerSectionCollapsePrefs: DrawerSectionCollapsePreferences get() = modules.drawerSectionCollapsePrefs

    override val paneWidthPrefs: PaneWidthPreferences get() = modules.paneWidthPrefs

    override val dockLayoutPrefs: DockLayoutPreferences get() = modules.dockLayoutPrefs

    override val scheduledPostStore: ScheduledPostStore get() = modules.scheduledPostStore

    override val appStores: AppPreferenceStores get() = modules.appStores

    // Namecoin lookups need an ElectrumX client over TLS sockets (A2); until then no name resolves.
    override val namecoinResolver: NamecoinNameResolver =
        NamecoinNameResolver(
            electrumxClient =
                object : IElectrumXClient {
                    override suspend fun nameShowWithFallback(
                        identifier: String,
                        servers: List<ElectrumxServer>,
                    ): NameShowResult? = null
                },
            serverListProvider = { emptyList() },
        )

    override val namecoinSettings: NamecoinSettingsStore get() = modules.namecoinPrefs

    override val otsSettings: OtsSettingsStore get() = modules.otsPrefs

    override val nip46ClientStore: Nip46ClientStore get() = modules.nip46ClientStore

    override fun blossomClient(serverBaseUrl: String): BlossomBlobClient = modules.blossom

    override val blossomMirrorQueue: BlossomMirrorQueue get() = modules.blossomMirrorQueue

    override fun cordnBlobStore(servers: List<String>): CordnBlobStore = BlossomCordnBlobStore(servers, KtorCordnHttp(modules.blossom))

    override val authCoordinator: AuthCoordinator get() = modules.authCoordinator

    override val powPublishQueue: PoWPublishQueue get() = modules.powPublishQueue

    override val deviceLocation: DeviceLocation get() = modules.deviceLocation

    override fun cachedPlaceName(geohash: String): String? = IosPlaceNames.cached(geohash)

    // The location picker's place search, on Core Location's geocoder.
    override val hasPlaceSearch: Boolean get() = true

    override suspend fun searchPlaces(query: String): List<PlaceHit> = withContext(Dispatchers.Main) { IosPlaceNames.search(query) }

    // NIP-86 relay management on each relay's own route (Tor when the relay goes through Tor).
    override val nip86Executor: Nip86Executor by lazy {
        KtorNip86Retriever { relay -> modules.httpClients.get(modules.torRelayState.shouldUseTorForRelay(relay)) }
    }

    // The report the last run's crash left (IosCrashReports), offered to the user at start.
    override suspend fun takeCrashReport(): String? = withContext(Dispatchers.IO) { IosCrashReports.takeReport() }
}
