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
package com.vitorpamplona.amethyst.desktop.napplet

import com.vitorpamplona.amethyst.commons.account.AccountState
import com.vitorpamplona.amethyst.commons.connectedApps.signers.NostrSignerPermissionLedger
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.napplet.DataStoreNappletStorage
import com.vitorpamplona.amethyst.commons.napplet.NappletBroker
import com.vitorpamplona.amethyst.commons.napplet.NappletLaunch
import com.vitorpamplona.amethyst.commons.napplet.NappletPromptQueues
import com.vitorpamplona.amethyst.commons.napplet.NappletThemeColors
import com.vitorpamplona.amethyst.commons.napplet.NappletThemeGateway
import com.vitorpamplona.amethyst.commons.napplet.NsiteNetworkChoices
import com.vitorpamplona.amethyst.commons.napplet.blobs.NappletBlobCache
import com.vitorpamplona.amethyst.commons.napplet.blobs.NappletBlobPrefetcher
import com.vitorpamplona.amethyst.commons.napplet.buildAccountNappletBroker
import com.vitorpamplona.amethyst.commons.tor.TorType
import com.vitorpamplona.amethyst.desktop.app.DesktopAppModules
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip5aStaticWebsites.tags.PathTag
import com.vitorpamplona.quartz.nip5dNapplets.NappletManifest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

/**
 * The desktop's napplet and nSite host: opens each NIP-5D napplet or NIP-5A site in its own window
 * on [DesktopCefEngine], with the broker every platform builds ([buildAccountNappletBroker]) for the
 * logged-in account. Consent questions wait in [consent] under their app, and each app's window
 * shows its own; grants persist in the app's napplet and signer ledgers.
 *
 * An nSite goes through Tor or the open web, as the user chose for that site ([networkChoices], the
 * same store Android keeps); [DesktopNappletRoutes] applies it to Chromium.
 *
 * Upload, notifications and the inc bus are not offered here yet; NIP-5D says a host simply leaves
 * them out of `window.napplet`, and these domains are not in Amethyst's supported set anyway.
 */
class DesktopNappletHost(
    private val modules: DesktopAppModules,
) {
    val runtime =
        DesktopNappletRuntime(
            engine = DesktopCefEngine(File(modules.filesDir, "jcef"), File(modules.filesDir, "jcef-cache")),
            blobCache = NappletBlobCache(NappletBlobCache.dirFor(modules.filesDir)),
            httpClient = { useTor -> modules.okHttpClients.getHttpClient(useTor) },
            scope = modules.applicationIOScope,
            torPort = modules.torManager.activePortOrNull,
            nappletBlobsUseTor = { torEnabled() },
        )

    val engine get() = runtime.engine

    val consent = NappletPromptQueues()

    val networkChoices = NsiteNetworkChoices(modules.appStores.getDataStore("napplet_network"), modules.applicationIOScope)

    private val accountScope: () -> String = { modules.sessionManager.loggedInAccount()?.pubKey ?: "" }

    private val storage by lazy { DataStoreNappletStorage(modules.appStores.getDataStore("napplet_storage"), accountScope) }

    private val signerLedger by lazy { NostrSignerPermissionLedger(modules.signerPermissionStore) }

    // Each broker is tied to the Account it was built on: logging out and back in makes a new Account
    // (the old one's scope and signer are closed), so a broker for the old one must not be reused.
    private val brokers = HashMap<String, Pair<Account, NappletBroker>>()

    private val mutableSessions = MutableStateFlow<List<CefNappletSession>>(emptyList())
    val sessions: StateFlow<List<CefNappletSession>> = mutableSessions.asStateFlow()

    @Volatile
    private var themeColors = NappletThemeColors("#0E0E10", "#E6E6E6", "#9A82DB")

    init {
        // A window signs as the account that opened it. When that account is no longer the one
        // logged in, its windows close rather than keep acting for someone who left.
        modules.applicationIOScope.launch {
            modules.sessionManager.accountContent
                .map { (it as? AccountState.LoggedIn)?.account?.pubKey }
                .distinctUntilChanged()
                .collect { pubKey ->
                    sessions.value.filter { it.accountPubKey != pubKey }.forEach(::close)
                    synchronized(this@DesktopNappletHost) { brokers.keys.retainAll(setOfNotNull(pubKey)) }
                }
        }
    }

    /** Whether Tor is on for the app; a site that chose Tor then goes through it. */
    val torEnabled: Boolean get() = torEnabled()

    private fun torEnabled(): Boolean = modules.torPrefs.torType.value != TorType.OFF

    /** Validates [manifest] and opens it in a new window, or says why it can't. */
    fun open(manifest: NappletManifest): Result<CefNappletSession> {
        val account = modules.sessionManager.loggedInAccount() ?: return Result.failure(IllegalStateException("Log in to open napplets"))
        return NappletLaunch.fromManifest(manifest).map { open(it, account, useTor = false) }
    }

    /**
     * Opens an nSite ([com.vitorpamplona.quartz.nip5aStaticWebsites.RootSiteEvent] or
     * [com.vitorpamplona.quartz.nip5aStaticWebsites.NamedSiteEvent]) in a new window. It goes
     * through Tor when Tor is on, unless the user moved this site to the open web.
     */
    fun openSite(event: Event): Result<CefNappletSession> {
        val account = modules.sessionManager.loggedInAccount() ?: return Result.failure(IllegalStateException("Log in to open sites"))
        return NappletLaunch.fromSite(event).map { launch ->
            open(launch, account, useTor = torEnabled() && networkChoices.useTor(launch.identity.coordinate))
        }
    }

    /**
     * Moves an open site to Tor or the open web: remembers the choice, then reopens the window on
     * the new route, as Android relaunches its activity.
     */
    fun setNetworkMode(
        session: CefNappletSession,
        useTor: Boolean,
    ) {
        val account = modules.sessionManager.loggedInAccount() ?: return
        networkChoices.set(session.launch.identity.coordinate, useTor)
        close(session)
        open(session.launch, account, useTor && torEnabled())
    }

    /** Opens [session]'s app again in a fresh window, after a failure the user wants to retry. */
    fun reopen(session: CefNappletSession) {
        val account = modules.sessionManager.loggedInAccount() ?: return
        close(session)
        open(session.launch, account, session.useTor)
    }

    private fun open(
        launch: NappletLaunch,
        account: Account,
        useTor: Boolean,
    ): CefNappletSession =
        CefNappletSession(launch, account.pubKey, useTor, account, brokerFor(account), runtime).also { session ->
            mutableSessions.update { it + session }
            session.start()
        }

    fun close(session: CefNappletSession) {
        mutableSessions.update { it - session }
        session.close()
        if (sessions.value.none { it.launch.identity.coordinate == session.launch.identity.coordinate }) {
            consent.dismissApp(session.launch.identity.coordinate)
        }
        if (sessions.value.isEmpty()) modules.nappletPermissionLedger.endSession()
    }

    /**
     * Downloads a card's files into the blob cache while it is on screen, so Open starts at once.
     * Never in the clear while Tor is on and not running yet: the launch waits for it instead.
     */
    suspend fun prefetch(
        paths: List<PathTag>,
        servers: List<String>,
    ) {
        val torPort = modules.torManager.activePortOrNull.value
        if (torPort == null && torEnabled()) return
        NappletBlobPrefetcher.prefetch(paths, servers, modules.filesDir, torPort ?: -1)
    }

    /** The app's colors, for `theme.get` and the `theme.changed` push. */
    fun updateTheme(colors: NappletThemeColors) {
        if (colors == themeColors) return
        themeColors = colors
        sessions.value.forEach { it.pushTheme(colors) }
    }

    fun shutdown() {
        sessions.value.forEach(::close)
        runtime.engine.shutdown()
    }

    @Synchronized
    private fun brokerFor(account: Account): NappletBroker =
        brokers[account.pubKey]?.takeIf { it.first === account }?.second
            ?: buildBroker(account).also { brokers[account.pubKey] = account to it }

    private fun buildBroker(account: Account): NappletBroker =
        buildAccountNappletBroker(
            account = account,
            browserIcons = modules.browserIcons,
            ledger = modules.nappletPermissionLedger,
            signerLedger = signerLedger,
            storage = storage,
            httpClient = { useProxy -> modules.okHttpClients.getHttpClient(useProxy) },
            useTor = { coordinate ->
                networkChoices.awaitReady()
                torEnabled() && networkChoices.useTor(coordinate)
            },
            prompts = consent,
            theme = NappletThemeGateway { themeColors },
        )
}
