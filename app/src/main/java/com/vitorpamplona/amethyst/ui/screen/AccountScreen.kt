package com.vitorpamplona.amethyst.ui.screen

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.MainScreen
import com.vitorpamplona.amethyst.ui.screen.loggedOff.LoginPage

@Composable
fun AccountScreen(
    accountStateViewModel: AccountStateViewModel,
    sharedPreferencesViewModel: SharedPreferencesViewModel
) {
    val accountState by accountStateViewModel.accountContent.collectAsState()

    Column() {
        Crossfade(
            targetState = accountState,
            animationSpec = tween(durationMillis = 100),
            label = "AccountState"
        ) { state ->
            when (state) {
                is AccountState.Loading -> {
                    LoadingAccounts()
                }
                is AccountState.LoggedOff -> {
                    LoginPage(accountStateViewModel, isFirstLogin = true)
                }
                is AccountState.LoggedIn -> {
                    val accountViewModel: AccountViewModel = viewModel(
                        key = state.account.userProfile().pubkeyHex,
                        factory = AccountViewModel.Factory(state.account, sharedPreferencesViewModel.sharedPrefs)
                    )

                    MainScreen(accountViewModel, accountStateViewModel, sharedPreferencesViewModel)
                }
                is AccountState.LoggedInViewOnly -> {
                    val accountViewModel: AccountViewModel = viewModel(
                        key = state.account.userProfile().pubkeyHex,
                        factory = AccountViewModel.Factory(state.account, sharedPreferencesViewModel.sharedPrefs)
                    )

                    MainScreen(accountViewModel, accountStateViewModel, sharedPreferencesViewModel)
                }
            }
        }
    }
}

@Composable
fun LoadingAccounts() {
    Column(
        Modifier
            .fillMaxHeight()
            .fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(stringResource(R.string.loading_account))
    }
}
