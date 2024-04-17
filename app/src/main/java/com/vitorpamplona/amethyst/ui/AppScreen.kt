/**
 * Copyright (c) 2024 Vitor Pamplona
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
package com.vitorpamplona.amethyst.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.adaptive.calculateDisplayFeatures
import com.vitorpamplona.amethyst.ServiceManager
import com.vitorpamplona.amethyst.ui.screen.AccountScreen
import com.vitorpamplona.amethyst.ui.screen.AccountStateViewModel
import com.vitorpamplona.amethyst.ui.screen.SharedPreferencesViewModel
import com.vitorpamplona.amethyst.ui.theme.AmethystTheme

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun prepareSharedViewModel(act: MainActivity): SharedPreferencesViewModel {
    val sharedPreferencesViewModel: SharedPreferencesViewModel = viewModel()

    val displayFeatures = calculateDisplayFeatures(act)
    val windowSizeClass = calculateWindowSizeClass(act)

    LaunchedEffect(key1 = sharedPreferencesViewModel) {
        sharedPreferencesViewModel.init()
        sharedPreferencesViewModel.updateDisplaySettings(windowSizeClass, displayFeatures)
    }

    LaunchedEffect(act.isOnMobileDataState) {
        sharedPreferencesViewModel.updateConnectivityStatusState(act.isOnMobileDataState)
    }

    return sharedPreferencesViewModel
}

@Composable
fun AppScreen(
    sharedPreferencesViewModel: SharedPreferencesViewModel,
    serviceManager: ServiceManager,
) {
    AmethystTheme(sharedPreferencesViewModel) {
        // A surface container using the 'background' color from the theme
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            val accountStateViewModel: AccountStateViewModel = viewModel()
            accountStateViewModel.serviceManager = serviceManager

            LaunchedEffect(key1 = Unit) {
                accountStateViewModel.tryLoginExistingAccountAsync()
            }

            AccountScreen(accountStateViewModel, sharedPreferencesViewModel)
        }
    }
}
