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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.namecoin_settings
import com.vitorpamplona.amethyst.model.preferences.NamecoinSharedPreferences
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.ElectrumXClient
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NamecoinSettingsScreen(nav: INav) {
    NamecoinSettingsScreen(
        Amethyst.instance.namecoinPrefs,
        electrumXClient = { Amethyst.instance.electrumXClient },
        nav,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NamecoinSettingsScreen(
    namecoinPrefs: NamecoinSharedPreferences,
    electrumXClient: () -> ElectrumXClient,
    nav: INav,
) {
    val namecoinSettings by namecoinPrefs.settings.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopBarWithBackButton(stringResource(Res.string.namecoin_settings), nav::popBack)
        },
    ) {
        Column(
            Modifier
                .padding(it)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 10.dp),
        ) {
            NamecoinSettingsSection(
                settings = namecoinSettings,
                onToggleEnabled = { enabled ->
                    scope.launch { namecoinPrefs.setEnabled(enabled) }
                },
                onAddServer = { server ->
                    scope.launch { namecoinPrefs.addServer(server) }
                },
                onRemoveServer = { server ->
                    scope.launch { namecoinPrefs.removeServer(server) }
                },
                onReset = {
                    scope.launch { namecoinPrefs.reset() }
                },
                onTestServer = { server -> electrumXClient().testServer(server) },
                onPinCert = { pem ->
                    scope.launch {
                        namecoinPrefs.addPinnedCert(pem)
                        electrumXClient().addPinnedCert(pem)
                    }
                },
            )
        }
    }
}
