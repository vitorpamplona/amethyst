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

import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.ui.app.AppRoot
import com.vitorpamplona.amethyst.commons.ui.app.RedialRelaysOnReturn
import com.vitorpamplona.amethyst.commons.ui.navigation.host.NavDestinations
import com.vitorpamplona.amethyst.commons.ui.navigation.host.UnavailableScreen
import com.vitorpamplona.amethyst.commons.ui.navigation.host.composableFromEndArgs
import com.vitorpamplona.amethyst.commons.ui.tor.TorConnectionFailureDialog
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.amethyst.ios.napplet.IosNostrAppScreen

/**
 * iOS's answers to the shared app root. The window size picks the shell, as everywhere: the bottom
 * bar on a phone, the rail or the drawer on an iPad.
 */
class IosAppRoot(
    private val modules: IosAppModules,
) : AppRoot {
    private val host by lazy { IosAccountViewModelHost(modules) }

    override fun createAccountViewModel(account: Account): AccountViewModel =
        AccountViewModel(
            account = account,
            settings = modules.uiState,
            torSettings = modules.torPrefs.value,
            dataSources = modules.sources,
            httpClientBuilder = modules.roleBasedHttpClientBuilder,
            nip05ClientBuilder = { modules.nip05Client },
            host = host,
        )

    /**
     * While the app is on screen: the relay pool stays connected and re-dials the relays whose
     * route changed, and the Tor route stays collected, which is what runs Tor (TorManager starts
     * it for as long as its status is watched). As on Android, Tor's stuck dialog sits here too.
     */
    @Composable
    override fun AppEffects() {
        modules.relayProxyClientConnector.relayServices.collectAsStateWithLifecycle()
        RedialRelaysOnReturn(modules.relayProxyClientConnector.foreground)
        modules.httpClients.torRoute.collectAsStateWithLifecycle()

        TorConnectionFailureDialog(modules.torManager)
    }

    override fun registerDestinations(
        destinations: NavDestinations,
        accountViewModel: AccountViewModel,
    ) {
        // A napplet as a screen of its own (plans/2026-11-26-ios-napplets.md, N5), as on the desktop.
        destinations.composableFromEndArgs<Route.NostrApp>(capWidth = false) { route -> IosNostrAppScreen(route, nav, modules.nappletHost, accountViewModel) }
        // The NIP-82 software catalogue ("App Store" in the menu) lists apps to download and install,
        // mostly Android ones. An app that offers a store of other apps is rejected by App Review
        // (guideline 3.2.2(i)), so iOS leaves it out; a software app note in a feed still renders,
        // and opening one shows its note (UnavailableScreen).
        destinations.withdraw(listOf(Route.SoftwareApps::class, Route.SoftwareAppDetail::class))
        // Screens still in jvmAndroid (A4) have ways in from links in notes: the closest screen
        // there is (the note, the system browser) or a notice, never a crash.
        destinations.unavailable { route -> UnavailableScreen(route, accountViewModel, nav) }
    }
}
