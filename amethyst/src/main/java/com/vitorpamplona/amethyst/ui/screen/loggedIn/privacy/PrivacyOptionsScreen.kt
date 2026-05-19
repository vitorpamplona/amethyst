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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.privacy

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.i2p.I2pSettings
import com.vitorpamplona.amethyst.commons.privacy.PrivacyTransport
import com.vitorpamplona.amethyst.commons.tor.TorSettings
import com.vitorpamplona.amethyst.ui.i2p.I2pDialogViewModel
import com.vitorpamplona.amethyst.ui.i2p.I2pSettingsBody
import com.vitorpamplona.amethyst.ui.i2p.I2pSettingsFlow
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.topbars.SavingTopBar
import com.vitorpamplona.amethyst.ui.privacy.PreferredTransportPicker
import com.vitorpamplona.amethyst.ui.privacy.PrivacyTransportPickerViewModel
import com.vitorpamplona.amethyst.ui.tor.PrivacySettingsBody
import com.vitorpamplona.amethyst.ui.tor.TorDialogViewModel
import com.vitorpamplona.amethyst.ui.tor.TorSettingsFlow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyOptionsScreen(nav: INav) {
    val app = Amethyst.instance
    PrivacyOptionsScreen(
        torSettingsFlow = app.torPrefs.value,
        i2pSettingsFlow = app.i2pPrefs.value,
        preferredTransport = app.privacyPrefs.preferredClearnetTransport.value,
        onSavePreferred = { app.privacyPrefs.preferredClearnetTransport.value = it },
        nav = nav,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyOptionsScreen(
    torSettingsFlow: TorSettingsFlow,
    i2pSettingsFlow: I2pSettingsFlow,
    preferredTransport: PrivacyTransport,
    onSavePreferred: (PrivacyTransport) -> Unit,
    nav: INav,
) {
    val torVm = viewModel<TorDialogViewModel>()
    val i2pVm = viewModel<I2pDialogViewModel>()
    val pickerVm = viewModel<PrivacyTransportPickerViewModel>()

    // Reset all three view models from disk-loaded state exactly once,
    // mirroring how the old screen reset only the Tor VM.
    LaunchedEffect(torVm, i2pVm, pickerVm) {
        torVm.reset(torSettingsFlow.toSettings())
        i2pVm.reset(i2pSettingsFlow.toSettings())
        pickerVm.reset(preferredTransport)
    }

    PrivacyOptionsScreenContents(
        torVm = torVm,
        i2pVm = i2pVm,
        pickerVm = pickerVm,
        onPostTor = torSettingsFlow::update,
        onPostI2p = i2pSettingsFlow::update,
        onPostPreferred = onSavePreferred,
        nav = nav,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyOptionsScreenContents(
    torVm: TorDialogViewModel,
    i2pVm: I2pDialogViewModel,
    pickerVm: PrivacyTransportPickerViewModel,
    onPostTor: (TorSettings) -> Unit,
    onPostI2p: (I2pSettings) -> Unit,
    onPostPreferred: (PrivacyTransport) -> Unit,
    nav: INav,
) {
    Scaffold(
        topBar = {
            SavingTopBar(
                titleRes = R.string.privacy_options,
                onCancel = { nav.popBack() },
                onPost = {
                    onPostTor(torVm.save())
                    onPostI2p(i2pVm.save())
                    onPostPreferred(pickerVm.save())
                    nav.popBack()
                },
            )
        },
    ) {
        Column(
            Modifier
                .padding(it)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 10.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PreferredTransportPicker(pickerVm)

            HorizontalDivider()

            PrivacySettingsBody(torVm)

            HorizontalDivider()

            I2pSettingsBody(i2pVm)
        }
    }
}
