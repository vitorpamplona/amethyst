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
package com.vitorpamplona.amethyst.ios.napplet

import com.vitorpamplona.amethyst.commons.account.AccountState
import com.vitorpamplona.amethyst.commons.connectedApps.signers.NostrSignerPermissionLedger
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.napplet.DataStoreNappletStorage
import com.vitorpamplona.amethyst.commons.napplet.NappletBroker
import com.vitorpamplona.amethyst.commons.napplet.NappletPromptQueues
import com.vitorpamplona.amethyst.commons.napplet.NappletThemeColors
import com.vitorpamplona.amethyst.commons.napplet.NappletThemeGateway
import com.vitorpamplona.amethyst.commons.napplet.NsiteNetworkChoices
import com.vitorpamplona.amethyst.commons.napplet.blobs.NappletBlobCache
import com.vitorpamplona.amethyst.commons.napplet.blobs.NappletHttp
import com.vitorpamplona.amethyst.commons.napplet.blobs.iosNappletHttp
import com.vitorpamplona.amethyst.commons.napplet.buildAccountNappletBroker
import com.vitorpamplona.amethyst.commons.tor.TorType
import com.vitorpamplona.amethyst.commons.util.KmpLock
import com.vitorpamplona.amethyst.commons.util.withLock
import com.vitorpamplona.amethyst.ios.IosAppModules
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import okio.Path.Companion.toPath
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask
import kotlin.concurrent.Volatile

/**
 * What the iOS app keeps for napplets and nSites, one per process: the broker of each account
 * ([buildAccountNappletBroker], as Android and Desktop build theirs), the consent questions it asks
 * ([consent], answered by the shared consent cards over the app's screen), each site's Tor choice
 * ([networkChoices]), the storage and grants, the blob cache and the HTTP every fetch goes out on.
 * It mirrors the desktop's `DesktopNappletHost`; the WKWebView sessions that use it come next (the
 * plan's N5).
 *
 * Built lazily by [IosAppModules]: nothing here runs, reads or connects until a napplet opens.
 *
 * Not offered yet, so a napplet's `window.napplet` leaves them out: uploads (the file picker is the
 * plan's N7) and notifications.
 */
class IosNappletHost(
    private val modules: IosAppModules,
) {
    /** The questions every broker here asks, for the screen to show and answer. */
    val consent = NappletPromptQueues()

    /** Where each nSite goes: the per-site choice, else "Applets and Websites" in the Tor settings. */
    val networkChoices = NsiteNetworkChoices(modules.appStores.getDataStore("napplet_network"), modules.applicationIOScope) { appletsAndWebsitesViaTor() }

    /**
     * Every blob, resource and archive fetch: the app's Darwin clients, through Tor or not as each
     * request asks. The Tor route fails while Tor has no port, so it never falls back to direct.
     */
    val http: NappletHttp = iosNappletHttp(modules.httpClients)

    /** The verified blobs of every launch, per site and account; the system may purge it. */
    val blobCache: NappletBlobCache by lazy {
        val caches = (NSSearchPathForDirectoriesInDomains(NSCachesDirectory, NSUserDomainMask, true).first() as String).toPath()
        NappletBlobCache(NappletBlobCache.dirFor(caches))
    }

    private val accountScope: () -> String = { modules.sessionManager.loggedInAccount()?.pubKey ?: "" }

    private val storage by lazy { DataStoreNappletStorage(modules.appStores.getDataStore("napplet_storage"), accountScope) }

    private val signerLedger by lazy { NostrSignerPermissionLedger(modules.signerPermissionStore) }

    // Each broker is tied to the Account it was built on: logging out and back in makes a new Account
    // (the old one's scope and signer are closed), so a broker for the old one must not be reused.
    private val lock = KmpLock()
    private val brokers = HashMap<String, Pair<Account, NappletBroker>>()

    /** What `theme.get` answers; the screen sets it from the app's theme as it changes. */
    @Volatile
    var themeColors = NappletThemeColors("#0E0E10", "#E6E6E6", "#9A82DB")

    init {
        // Brokers of an account that is no longer the one logged in go with it.
        modules.applicationIOScope.launch {
            modules.sessionManager.accountContent
                .map { (it as? AccountState.LoggedIn)?.account?.pubKey }
                .distinctUntilChanged()
                .collect { pubKey -> lock.withLock { brokers.keys.retainAll(setOfNotNull(pubKey)) } }
        }
    }

    /** Whether Tor is on for the app. */
    fun torEnabled(): Boolean = modules.torPrefs.torType.value != TorType.OFF

    private fun appletsAndWebsitesViaTor(): Boolean = modules.torPrefs.value.appletsAndWebsitesViaTor.value

    /** Whether a napplet's own files go through Tor ("Applets and Websites"), as on Desktop. */
    fun nappletBlobsUseTor(): Boolean = torEnabled() && appletsAndWebsitesViaTor()

    /**
     * Whether the app at [coordinate] reaches the network through Tor: its per-site choice, read once
     * the stored choices have loaded (Tor until then), and never while Tor is off for the app.
     */
    suspend fun useTor(coordinate: String): Boolean {
        networkChoices.awaitReady()
        return torEnabled() && networkChoices.useTor(coordinate)
    }

    /** The broker that answers [account]'s napplets, built on first use. */
    fun brokerFor(account: Account): NappletBroker =
        lock.withLock {
            brokers[account.pubKey]?.takeIf { it.first === account }?.second
                ?: buildBroker(account).also { brokers[account.pubKey] = account to it }
        }

    private fun buildBroker(account: Account): NappletBroker =
        buildAccountNappletBroker(
            account = account,
            browserIcons = modules.browserIcons,
            // The account's own grants and storage, whoever is logged in when it asks.
            ledger = modules.nappletPermissionLedger.forAccount(account.pubKey),
            signerLedger = signerLedger,
            storage = storage.forAccount(account.pubKey),
            http = http,
            useTor = ::useTor,
            prompts = consent,
            theme = NappletThemeGateway { themeColors },
        )
}
