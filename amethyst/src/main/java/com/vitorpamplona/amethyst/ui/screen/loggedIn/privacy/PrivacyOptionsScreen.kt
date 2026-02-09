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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.topbars.SavingTopBar
import com.vitorpamplona.amethyst.ui.tor.PrivacySettingsBody
import com.vitorpamplona.amethyst.ui.tor.TorDialogViewModel
import com.vitorpamplona.amethyst.ui.tor.TorSettings
import com.vitorpamplona.amethyst.ui.tor.TorSettingsFlow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyOptionsScreen(
    torSettingsFlow: TorSettingsFlow,
    nav: INav,
) {
    val dialogViewModel = viewModel<TorDialogViewModel>()

    // runs only once and before the rest of the screen is build
    // to avoid blinking and animations from the default/previous
    // state to the current state
    val init =
        remember(dialogViewModel) {
            val torSettings = torSettingsFlow.toSettings()
            dialogViewModel.reset(torSettings)
            torSettings
        }

    PrivacyOptionsScreenContents(dialogViewModel, onPost = torSettingsFlow::update, nav)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyOptionsScreenContents(
    dialogViewModel: TorDialogViewModel,
    onPost: (TorSettings) -> Unit,
    nav: INav,
) {
    Scaffold(
        topBar = {
            SavingTopBar(
                titleRes = R.string.privacy_options,
                onCancel = {
                    nav.popBack()
                },
                onPost = {
                    onPost(dialogViewModel.save())
                    nav.popBack()
                },
            )
        },
    ) {
        Column(
            Modifier
                .padding(it)
                .fillMaxSize()
                .verticalScroll(
                    rememberScrollState(),
                ).padding(horizontal = 10.dp),
        ) {
            PrivacySettingsBody(dialogViewModel)
        }
    }
}
