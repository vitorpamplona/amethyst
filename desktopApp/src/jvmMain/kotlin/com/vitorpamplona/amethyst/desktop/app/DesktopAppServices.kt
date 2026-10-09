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
package com.vitorpamplona.amethyst.desktop.app

import com.vitorpamplona.amethyst.commons.browser.BrowserHistoryRegistry
import com.vitorpamplona.amethyst.commons.browser.BrowserIconRegistry
import com.vitorpamplona.amethyst.commons.connectedApps.nip46.Nip46ClientStore
import com.vitorpamplona.amethyst.commons.connectedApps.signers.NostrSignerPermissionStore
import com.vitorpamplona.amethyst.commons.cordn.BlossomCordnBlobStore
import com.vitorpamplona.amethyst.commons.cordn.CordnBlobStore
import com.vitorpamplona.amethyst.commons.favorites.FavoriteAppsRegistry
import com.vitorpamplona.amethyst.commons.model.preferences.AppPreferenceStores
import com.vitorpamplona.amethyst.commons.model.preferences.DrawerSectionCollapsePreferences
import com.vitorpamplona.amethyst.commons.model.preferences.NamecoinSettingsStore
import com.vitorpamplona.amethyst.commons.model.preferences.OtsSettingsStore
import com.vitorpamplona.amethyst.commons.napplet.permissions.NappletPermissionLedger
import com.vitorpamplona.amethyst.commons.relayClient.auth.AuthCoordinator
import com.vitorpamplona.amethyst.commons.relayManagement.Nip86Executor
import com.vitorpamplona.amethyst.commons.relayManagement.Nip86Retriever
import com.vitorpamplona.amethyst.commons.scheduledposts.ScheduledPostStore
import com.vitorpamplona.amethyst.commons.service.AppServices
import com.vitorpamplona.amethyst.commons.service.namecoin.NamecoinClients
import com.vitorpamplona.amethyst.commons.service.pow.PoWPublishQueue
import com.vitorpamplona.amethyst.commons.service.upload.BlossomBlobClient
import com.vitorpamplona.amethyst.commons.service.upload.blossom.BlossomMirrorQueue
import com.vitorpamplona.amethyst.commons.tor.TorServiceStatus
import com.vitorpamplona.amethyst.commons.tor.TorSettingsFlow
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.NamecoinNameResolver
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** The app-wide stores the shared screens read on desktop, all from [modules]. */
class DesktopAppServices(
    private val modules: DesktopAppModules,
) : AppServices {
    override val favoriteApps: FavoriteAppsRegistry get() = modules.favoriteApps

    override val browserHistory: BrowserHistoryRegistry get() = modules.browserHistory

    override val browserIcons: BrowserIconRegistry get() = modules.browserIcons

    override val nappletPermissionLedger: NappletPermissionLedger get() = modules.nappletPermissionLedger

    override val signerPermissionStore: NostrSignerPermissionStore get() = modules.signerPermissionStore

    override val torSettings: TorSettingsFlow get() = modules.torPrefs.value

    override val torBootstrapped: Flow<Boolean> get() = modules.torManager.status.map { it.isFullyBootstrapped }

    override val torStatus: Flow<TorServiceStatus> get() = modules.torManager.status

    override val drawerSectionCollapsePrefs: DrawerSectionCollapsePreferences get() = modules.drawerSectionCollapsePrefs

    override val scheduledPostStore: ScheduledPostStore get() = modules.scheduledPostStore

    override val appStores: AppPreferenceStores get() = modules.appStores

    override val namecoinResolver: NamecoinNameResolver get() = modules.namecoinServices.resolver

    override val namecoinSettings: NamecoinSettingsStore get() = modules.namecoinPrefs

    override val namecoinClients: NamecoinClients get() = modules.namecoinServices

    override val otsSettings: OtsSettingsStore get() = modules.otsPrefs

    override val nip86Executor: Nip86Executor by lazy { Nip86Retriever(modules.torEvaluatorFlow::okHttpClientForRelay) }

    override val nip46ClientStore: Nip46ClientStore get() = modules.nip46ClientStore

    override suspend fun takeCrashReport(): String? = modules.crashReportCache.loadAndDelete()

    override fun blossomClient(serverBaseUrl: String): BlossomBlobClient = modules.blossomClient(serverBaseUrl)

    override val blossomMirrorQueue: BlossomMirrorQueue get() = modules.blossomMirrorQueue

    override fun cordnBlobStore(servers: List<String>): CordnBlobStore = BlossomCordnBlobStore(servers, modules::blossomClient)

    override val authCoordinator: AuthCoordinator get() = modules.authCoordinator

    override val powPublishQueue: PoWPublishQueue get() = modules.powPublishQueue
}
