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
package com.vitorpamplona.amethyst.ui.screen

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.AccountSettings
import com.vitorpamplona.amethyst.ui.screen.loggedIn.LoggedInPage
import com.vitorpamplona.amethyst.ui.screen.loggedOff.LoginOrSignupScreen
import com.vitorpamplona.amethyst.ui.stringRes

@Composable
fun AccountScreen(
    accountStateViewModel: AccountStateViewModel,
    sharedPreferencesViewModel: SharedPreferencesViewModel,
) {
    val accountState by accountStateViewModel.accountContent.collectAsStateWithLifecycle()

    Crossfade(
        targetState = accountState,
        animationSpec = tween(durationMillis = 100),
        label = "AccountState",
    ) { state ->
        when (state) {
            is AccountState.Loading -> {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    LoadingAccounts()
                }
            }
            is AccountState.LoggedOff -> { // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    LoginOrSignupScreen(null, accountStateViewModel, isFirstLogin = true)
                }
            }
            is AccountState.LoggedIn -> {
                CompositionLocalProvider(
                    LocalViewModelStoreOwner provides state.currentViewModelStore,
                ) {
                    LoggedInPage(
                        state.accountSettings,
                        state.route,
                        accountStateViewModel,
                        sharedPreferencesViewModel,
                    )
                }

                DisposableEffect(key1 = accountState) {
                    onDispose {
                        state.currentViewModelStore.viewModelStore.clear()
                    }
                }
            }
        }
    }
}

class AccountCentricViewModelStore(
    val accountSettings: AccountSettings,
) : ViewModelStoreOwner {
    override val viewModelStore = ViewModelStore()
}

@Composable
fun LoadingAccounts() {
    Column(
        Modifier.fillMaxHeight().fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(stringRes(R.string.loading_account))
    }
}
