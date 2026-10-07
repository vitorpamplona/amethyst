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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.ui.app.AppRoot
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel

/**
 * The desktop's answers to the shared app root. The window size comes from the default
 * ([AppRoot.rememberScreenLayoutSpec] reads the window), so a narrow window gets the bottom bar and a
 * wide one the rail or the drawer, as on a phone or a laptop.
 */
class DesktopAppRoot(
    private val modules: DesktopAppModules,
) : AppRoot {
    private val host by lazy { DesktopAccountViewModelHost(modules) }

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

    /** Keeps the relay pool and the HTTP clients alive while the window is open. */
    @Composable
    override fun AppEffects() {
        modules.relayProxyClientConnector.relayServices.collectAsState()
        modules.okHttpClients.defaultHttpClient.collectAsState()
        modules.okHttpClients.defaultHttpClientWithoutProxy.collectAsState()
    }
}
