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
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.napplet.DataStoreNappletStorage
import com.vitorpamplona.amethyst.commons.napplet.NappletBroker
import com.vitorpamplona.amethyst.commons.napplet.NappletConsentPrompt
import com.vitorpamplona.amethyst.commons.napplet.NappletIdentityGateway
import com.vitorpamplona.amethyst.commons.napplet.NappletResourceGateway
import com.vitorpamplona.amethyst.commons.napplet.NappletThemeColors
import com.vitorpamplona.amethyst.commons.napplet.NappletThemeGateway
import com.vitorpamplona.amethyst.commons.napplet.gateways.AccountIdentityReader
import com.vitorpamplona.amethyst.commons.napplet.gateways.AccountNappletRelayGateway
import com.vitorpamplona.amethyst.commons.napplet.gateways.AccountNappletWalletGateway
import com.vitorpamplona.amethyst.commons.napplet.gateways.NappletResourceFetcher
import com.vitorpamplona.amethyst.commons.napplet.ui.NappletConsentSummary
import com.vitorpamplona.amethyst.commons.tor.TorType
import com.vitorpamplona.amethyst.desktop.app.DesktopAppModules
import com.vitorpamplona.quartz.nip5dNapplets.NappletManifest
import com.vitorpamplona.quartz.nip89AppHandlers.clientTag.withoutClientTag
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

/**
 * The desktop's NIP-5D napplet host: opens each napplet in its own window on [DesktopCefEngine],
 * and wires the shared [NappletBroker] to the logged-in account the way Android's
 * `AccountNappletGateways` does (relay, storage, resource, identity, wallet, theme). Consent is asked
 * in the napplet's window ([DesktopNappletConsent]); grants persist in the app's napplet ledger.
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
            blobs = DesktopNappletBlobs(File(modules.filesDir, "napplet-blobs")) { modules.okHttpClients.getHttpClient(torEnabled()) },
            scope = modules.applicationIOScope,
        )

    val engine get() = runtime.engine

    val consent = DesktopNappletConsent()

    private val accountScope: () -> String = { modules.sessionManager.loggedInAccount()?.pubKey ?: "" }

    private val storage by lazy { DataStoreNappletStorage(modules.appStores.getDataStore("napplet_storage"), accountScope) }

    private val brokers = HashMap<String, NappletBroker>()

    private val mutableSessions = MutableStateFlow<List<DesktopNappletSession>>(emptyList())
    val sessions: StateFlow<List<DesktopNappletSession>> = mutableSessions.asStateFlow()

    @Volatile
    private var themeColors = NappletThemeColors("#0E0E10", "#E6E6E6", "#9A82DB")

    init {
        // A napplet signs as the account that opened it. When that account is no longer the one
        // logged in, its windows close rather than keep acting for someone who left.
        modules.applicationIOScope.launch {
            modules.sessionManager.accountContent
                .map { (it as? AccountState.LoggedIn)?.account?.pubKey }
                .distinctUntilChanged()
                .collect { pubKey -> sessions.value.filter { it.launch.accountPubKey != pubKey }.forEach(::close) }
        }
    }

    private fun torEnabled(): Boolean = modules.torPrefs.torType.value != TorType.OFF

    /** Validates [manifest] and opens it in a new window, or says why it can't. */
    fun open(manifest: NappletManifest): Result<DesktopNappletSession> {
        val account = modules.sessionManager.loggedInAccount() ?: return Result.failure(IllegalStateException("Log in to open napplets"))
        return DesktopNappletLaunch.from(manifest, account.pubKey).map { launch ->
            DesktopNappletSession(launch, account, brokerFor(account), runtime).also { session ->
                mutableSessions.update { it + session }
                session.start()
            }
        }
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
            val resources = NappletResourceFetcher(account, { useProxy -> modules.okHttpClients.getHttpClient(useProxy) }) { torEnabled() }
            val identity = AccountIdentityReader(account)
            NappletBroker(
                // Everything a napplet signs is the napplet's, not Amethyst's: no client tag.
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
            )
        }
}
