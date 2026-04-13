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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.tor.TorType
import com.vitorpamplona.amethyst.model.preferences.OtsSharedPreferences
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.tor.TorSettingsFlow
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OtsSettingsScreen(nav: INav) {
    OtsSettingsScreen(Amethyst.instance.otsPrefs, Amethyst.instance.torPrefs.value, nav)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OtsSettingsScreen(
    otsPrefs: OtsSharedPreferences,
    torSettings: TorSettingsFlow,
    nav: INav,
) {
    val otsSettings by otsPrefs.settings.collectAsState()
    val torType by torSettings.torType.collectAsState()
    val moneyViaTor by torSettings.moneyOperationsViaTor.collectAsState()
    val scope = rememberCoroutineScope()

    val isTorActiveForMoney = torType != TorType.OFF && moneyViaTor

    Scaffold(
        topBar = {
            TopBarWithBackButton(stringRes(id = R.string.ots_explorer_settings), nav::popBack)
        },
    ) {
        Column(
            Modifier
                .padding(it)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 10.dp),
        ) {
            OtsSettingsSection(
                settings = otsSettings,
                isTorActive = isTorActiveForMoney,
                onSetCustomUrl = { url ->
                    scope.launch { otsPrefs.setCustomExplorerUrl(url) }
                },
                onReset = {
                    scope.launch { otsPrefs.reset() }
                },
            )
        }
    }
}
