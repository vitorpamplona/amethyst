/**
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
package com.vitorpamplona.amethyst.ui.screen

import android.util.Log
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.screen.loggedIn.LoggedInPage
import com.vitorpamplona.amethyst.ui.screen.loggedOff.LoginOrSignupScreen
import com.vitorpamplona.amethyst.ui.stringRes

@Composable
fun AccountScreen(
    accountStateViewModel: AccountStateViewModel,
    sharedPreferencesViewModel: SharedPreferencesViewModel,
) {
    val accountState by accountStateViewModel.accountContent.collectAsStateWithLifecycle()

    Log.d("ManageRelayServices", "AccountScreen $accountState $accountStateViewModel")

    Crossfade(
        targetState = accountState,
        animationSpec = tween(durationMillis = 100),
    ) { state ->
        when (state) {
            is AccountState.Loading -> LoadingSetup()
            is AccountState.LoggedOff -> LoggedOffSetup(accountStateViewModel)
            is AccountState.LoggedIn -> LoggedInSetup(state, accountStateViewModel, sharedPreferencesViewModel)
        }
    }
}

@Composable
fun LoadingSetup() {
    // A surface container using the 'background' color from the theme
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(stringRes(R.string.loading_account))
        }
    }
}

@Composable
fun LoggedOffSetup(accountStateViewModel: AccountStateViewModel) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        LoginOrSignupScreen(null, accountStateViewModel, isFirstLogin = true)
    }
}

@Composable
fun LoggedInSetup(
    state: AccountState.LoggedIn,
    accountStateViewModel: AccountStateViewModel,
    sharedPreferencesViewModel: SharedPreferencesViewModel,
) {
    SetAccountCentricViewModelStore(state) {
        LoggedInPage(
            state.accountSettings,
            state.route,
            accountStateViewModel,
            sharedPreferencesViewModel,
        )
    }
}
