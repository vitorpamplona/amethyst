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
package com.vitorpamplona.amethyst.commons.service

import com.vitorpamplona.amethyst.commons.browser.BrowserHistoryRegistry
import com.vitorpamplona.amethyst.commons.browser.BrowserIconRegistry
import com.vitorpamplona.amethyst.commons.connectedApps.nip46.Nip46ClientStore
import com.vitorpamplona.amethyst.commons.connectedApps.signers.NostrSignerPermissionStore
import com.vitorpamplona.amethyst.commons.cordn.CordnBlobStore
import com.vitorpamplona.amethyst.commons.favorites.FavoriteAppsRegistry
import com.vitorpamplona.amethyst.commons.model.location.DeviceLocation
import com.vitorpamplona.amethyst.commons.model.preferences.AppPreferenceStores
import com.vitorpamplona.amethyst.commons.model.preferences.CALENDAR_REMINDER_SETTINGS_STORE
import com.vitorpamplona.amethyst.commons.model.preferences.CalendarReminderSettingsStore
import com.vitorpamplona.amethyst.commons.model.preferences.DrawerSectionCollapsePreferences
import com.vitorpamplona.amethyst.commons.model.preferences.NamecoinSettingsStore
import com.vitorpamplona.amethyst.commons.model.preferences.OtsSettingsStore
import com.vitorpamplona.amethyst.commons.napplet.permissions.NappletPermissionLedger
import com.vitorpamplona.amethyst.commons.relayManagement.Nip86Executor
import com.vitorpamplona.amethyst.commons.scheduledposts.ScheduledPostStore
import com.vitorpamplona.amethyst.commons.service.ai.AltTextSuggester
import com.vitorpamplona.amethyst.commons.service.namecoin.NamecoinClients
import com.vitorpamplona.amethyst.commons.service.upload.BlossomBlobClient
import com.vitorpamplona.amethyst.commons.service.upload.blossom.BlossomMirrorQueue
import com.vitorpamplona.amethyst.commons.tor.TorServiceStatus
import com.vitorpamplona.amethyst.commons.tor.TorSettingsFlow
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.NamecoinNameResolver
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * App-wide stores that outlive any one account and that screens read directly: the favorites
 * row, the browser's history and favicons, what connected apps may do, the Tor settings and the
 * Namecoin resolver.
 * Account-scoped services reach screens through the account's ViewModel instead.
 *
 * A front end implements it over its own modules; shared UI reads it through `LocalAppServices`.
 */
interface AppServices {
    val favoriteApps: FavoriteAppsRegistry

    val browserHistory: BrowserHistoryRegistry

    val browserIcons: BrowserIconRegistry

    /** What each napplet/nsite has been granted, per account. */
    val nappletPermissionLedger: NappletPermissionLedger

    /** What each remote-signer client has been granted. */
    val signerPermissionStore: NostrSignerPermissionStore

    val torSettings: TorSettingsFlow

    /** Whether the embedded Tor service has fully bootstrapped, so Tor-routed relays can answer. */
    val torBootstrapped: Flow<Boolean>

    /**
     * Tor's state for display: off, still connecting (including a proxy that is bound but still
     * downloading its directory), or ready.
     */
    val torStatus: Flow<TorServiceStatus> get() = flowOf(TorServiceStatus.Off)

    /** Device-wide drawer state: which headings the user folded away. Never synced. */
    val drawerSectionCollapsePrefs: DrawerSectionCollapsePreferences

    /** Posts scheduled for later, across every account on this device. */
    val scheduledPostStore: ScheduledPostStore

    /** The app's preference files, one DataStore per name. */
    val appStores: AppPreferenceStores

    /** Resolves `.bit` names and `d/`/`id/` identifiers over the configured ElectrumX servers. */
    val namecoinResolver: NamecoinNameResolver

    /** The Namecoin backend settings (servers, Core RPC, pinned certificates). */
    val namecoinSettings: NamecoinSettingsStore

    /** The live Namecoin clients the settings screen tests and reconfigures. */
    val namecoinClients: NamecoinClients get() = NamecoinClients.None

    /** The OpenTimestamps blockchain explorer setting. */
    val otsSettings: OtsSettingsStore

    /** Sends NIP-86 relay-management calls (over Tor when the settings say so). */
    val nip86Executor: Nip86Executor get() = Nip86Executor.None

    /** The NIP-46 remote-signer clients this device has connected to. */
    val nip46ClientStore: Nip46ClientStore

    /** Finds the server behind a `blossom:` URI (BUD-10). */
    val blossomServerFinder: BlossomServerFinder get() = BlossomServerFinder.None

    /** The device's position as geohashes. */
    val deviceLocation: DeviceLocation get() = DeviceLocation.None

    /** Device-wide calendar reminder settings; the file is cached by [appStores]. */
    val calendarReminderSettings: CalendarReminderSettingsStore
        get() = CalendarReminderSettingsStore(appStores.getDataStore(CALENDAR_REMINDER_SETTINGS_STORE))

    /**
     * Starts or stops the background job that fires calendar reminders. Platforms without one
     * ignore it.
     */
    fun setCalendarRemindersScheduled(enabled: Boolean) {}

    /** A Blossom client for one server, built on that server's HTTP client (Tor, pooling). */
    fun blossomClient(serverBaseUrl: String): BlossomBlobClient

    /** The app-wide BUD-04 mirror sweep, which keeps running while the user navigates. */
    val blossomMirrorQueue: BlossomMirrorQueue

    /**
     * The Blossom-backed store a Cordn migration hands documents through, uploading to [servers]
     * (empty when it only fetches).
     */
    fun cordnBlobStore(servers: List<String>): CordnBlobStore

    /** The place name already reverse-geocoded for [geohash], or null when none is cached yet. */
    fun cachedPlaceName(geohash: String): String? = null

    /** A new on-device alt-text suggester for image uploads, or null where the platform has none. */
    fun createAltTextSuggester(): AltTextSuggester? = null
}
