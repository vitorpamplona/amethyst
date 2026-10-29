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
import com.vitorpamplona.amethyst.commons.connectedApps.signers.NostrConnectPrompt
import com.vitorpamplona.amethyst.commons.connectedApps.signers.NostrSignerConsentPrompt
import com.vitorpamplona.amethyst.commons.connectedApps.signers.NostrSignerPermissionLedger
import com.vitorpamplona.amethyst.commons.connectedApps.signers.SignerOpGrant
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.napplet.DataStoreNappletStorage
import com.vitorpamplona.amethyst.commons.napplet.NappletBroker
import com.vitorpamplona.amethyst.commons.napplet.NappletConsentPrompt
import com.vitorpamplona.amethyst.commons.napplet.NappletIdentityGateway
import com.vitorpamplona.amethyst.commons.napplet.NappletNetworkChoices
import com.vitorpamplona.amethyst.commons.napplet.NappletRecentEncryptions
import com.vitorpamplona.amethyst.commons.napplet.NappletResourceGateway
import com.vitorpamplona.amethyst.commons.napplet.NappletThemeColors
import com.vitorpamplona.amethyst.commons.napplet.NappletThemeGateway
import com.vitorpamplona.amethyst.commons.napplet.gateways.AccountIdentityReader
import com.vitorpamplona.amethyst.commons.napplet.gateways.AccountNappletRelayGateway
import com.vitorpamplona.amethyst.commons.napplet.gateways.AccountNappletWalletGateway
import com.vitorpamplona.amethyst.commons.napplet.gateways.NappletResourceFetcher
import com.vitorpamplona.amethyst.commons.napplet.ui.NappletConsentSummary
import com.vitorpamplona.amethyst.commons.napplet.ui.buildConnectInfo
import com.vitorpamplona.amethyst.commons.napplet.ui.buildSignerConsentInfo
import com.vitorpamplona.amethyst.commons.tor.TorType
import com.vitorpamplona.amethyst.desktop.app.DesktopAppModules
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip5dNapplets.NappletManifest
import com.vitorpamplona.quartz.nip89AppHandlers.clientTag.withoutClientTag
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import kotlin.time.Duration.Companion.seconds

/**
 * The desktop's napplet and nSite host: opens each NIP-5D napplet or NIP-5A site in its own window
 * on [DesktopCefEngine], and wires the shared [NappletBroker] to the logged-in account the way
 * Android's `AccountNappletGateways` does (relay, storage, resource, identity, wallet, theme, and the
 * NIP-07 signer with its connect and per-operation prompts). Consent is asked in the app's window
 * ([DesktopNappletConsent]); grants persist in the app's napplet and signer ledgers.
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
            blobs = DesktopNappletBlobs(File(modules.filesDir, "napplet-blobs")) { useTor -> modules.okHttpClients.getHttpClient(useTor) },
            scope = modules.applicationIOScope,
            torPort = modules.torManager.activePortOrNull,
            nappletBlobsUseTor = { torEnabled() },
        )

    val engine get() = runtime.engine

    val consent = DesktopNappletConsent()

    val networkChoices = NappletNetworkChoices(modules.appStores.getDataStore("napplet_network"), modules.applicationIOScope)

    private val accountScope: () -> String = { modules.sessionManager.loggedInAccount()?.pubKey ?: "" }

    private val storage by lazy { DataStoreNappletStorage(modules.appStores.getDataStore("napplet_storage"), accountScope) }

    private val signerLedger by lazy { NostrSignerPermissionLedger(modules.signerPermissionStore) }

    private val brokers = HashMap<String, NappletBroker>()

    private val mutableSessions = MutableStateFlow<List<DesktopNappletSession>>(emptyList())
    val sessions: StateFlow<List<DesktopNappletSession>> = mutableSessions.asStateFlow()

    @Volatile
    private var themeColors = NappletThemeColors("#0E0E10", "#E6E6E6", "#9A82DB")

    init {
        // A window signs as the account that opened it. When that account is no longer the one
        // logged in, its windows close rather than keep acting for someone who left.
        modules.applicationIOScope.launch {
            modules.sessionManager.accountContent
                .map { (it as? AccountState.LoggedIn)?.account?.pubKey }
                .distinctUntilChanged()
                .collect { pubKey -> sessions.value.filter { it.launch.accountPubKey != pubKey }.forEach(::close) }
        }
    }

    /** Whether Tor is on for the app; a site that chose Tor then goes through it. */
    val torEnabled: Boolean get() = torEnabled()

    private fun torEnabled(): Boolean = modules.torPrefs.torType.value != TorType.OFF

    /** Validates [manifest] and opens it in a new window, or says why it can't. */
    fun open(manifest: NappletManifest): Result<DesktopNappletSession> {
        val account = modules.sessionManager.loggedInAccount() ?: return Result.failure(IllegalStateException("Log in to open napplets"))
        return DesktopNappletLaunch.from(manifest, account.pubKey).map { open(it, account) }
    }

    /**
     * Opens an nSite ([com.vitorpamplona.quartz.nip5aStaticWebsites.RootSiteEvent] or
     * [com.vitorpamplona.quartz.nip5aStaticWebsites.NamedSiteEvent]) in a new window. It goes
     * through Tor when Tor is on, unless the user moved this site to the open web.
     */
    fun openSite(event: Event): Result<DesktopNappletSession> {
        val account = modules.sessionManager.loggedInAccount() ?: return Result.failure(IllegalStateException("Log in to open sites"))
        val coordinate =
            DesktopNappletLaunch
                .fromSite(event, account.pubKey, useTor = false)
                .getOrElse { return Result.failure(it) }
                .identity.coordinate
        val useTor = torEnabled() && networkChoices.useTor(coordinate)
        return DesktopNappletLaunch.fromSite(event, account.pubKey, useTor).map { open(it, account) }
    }

    /**
     * Moves an open site to Tor or the open web: remembers the choice, then reopens the window on
     * the new route, as Android relaunches its activity.
     */
    fun setNetworkMode(
        session: DesktopNappletSession,
        useTor: Boolean,
    ) {
        val account = modules.sessionManager.loggedInAccount() ?: return
        networkChoices.set(session.launch.identity.coordinate, useTor)
        close(session)
        open(session.launch.withUseTor(useTor && torEnabled()), account)
    }

    /** Opens [session]'s app again in a fresh window, after a failure the user wants to retry. */
    fun reopen(session: DesktopNappletSession) {
        val account = modules.sessionManager.loggedInAccount() ?: return
        close(session)
        open(session.launch, account)
    }

    private fun open(
        launch: DesktopNappletLaunch,
        account: Account,
    ): DesktopNappletSession =
        DesktopNappletSession(launch, account, brokerFor(account), runtime).also { session ->
            mutableSessions.update { it + session }
            session.start()
        }

    fun close(session: DesktopNappletSession) {
        mutableSessions.update { it - session }
        session.close()
        if (sessions.value.none { it.launch.identity.coordinate == session.launch.identity.coordinate }) {
            consent.dismissAll(session.launch.identity.coordinate)
        }
        if (sessions.value.isEmpty()) modules.nappletPermissionLedger.endSession()
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
        brokers.getOrPut(account.pubKey) {
            val summary = NappletConsentSummary(modules.browserIcons, account)
            val resources =
                NappletResourceFetcher(account, { useProxy -> modules.okHttpClients.getHttpClient(useProxy) }) { coordinate ->
                    networkChoices.awaitReady()
                    torEnabled() && networkChoices.useTor(coordinate)
                }
            val identity = AccountIdentityReader(account)
            val recentEncryptions = NappletRecentEncryptions()
            NappletBroker(
                // Everything a napplet or site signs is its own, not Amethyst's: no client tag. It
                // would also corrupt the template a NIP-07 caller re-checks the returned event against.
                signer = account.signer.withoutClientTag(),
                ledger = modules.nappletPermissionLedger,
                consentPrompt =
                    NappletConsentPrompt { appIdentity, capability, request ->
                        consent.request(appIdentity.coordinate, summary.info(appIdentity, capability, request))
                    },
                relay = AccountNappletRelayGateway(account),
                storage = storage,
                wallet = AccountNappletWalletGateway(account),
                resource = NappletResourceGateway { url, coordinate -> resources.fetch(url, coordinate) },
                identityReads = NappletIdentityGateway { method, argument -> identity.read(method, argument) },
                theme = NappletThemeGateway { themeColors },
                signerLedger = signerLedger,
                nostrConnectPrompt =
                    NostrConnectPrompt { appIdentity, declared ->
                        consent.connect(appIdentity.coordinate, buildConnectInfo(modules.browserIcons, appIdentity, declared))
                    },
                signerConsentPrompt =
                    NostrSignerConsentPrompt { appIdentity, op, request ->
                        // Fail closed if the prompt is never answered: the broker holds its consent lock
                        // while this waits, and an abandoned dialog would block every later request.
                        withTimeoutOrNull(SIGNER_CONSENT_TIMEOUT) {
                            consent.signerOp(appIdentity.coordinate, buildSignerConsentInfo(modules.browserIcons, appIdentity, op, request, account, recentEncryptions))
                        } ?: SignerOpGrant.DenyOnce
                    },
                recentEncryptions = recentEncryptions,
            )
        }

    private companion object {
        // The same bound as Android's.
        val SIGNER_CONSENT_TIMEOUT = 120.seconds
    }
}
