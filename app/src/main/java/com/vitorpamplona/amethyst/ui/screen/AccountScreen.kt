package com.vitorpamplona.amethyst.ui.screen

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.MainScreen
import com.vitorpamplona.amethyst.ui.screen.loggedOff.LoginPage

@Composable
fun AccountScreen(
    accountStateViewModel: AccountStateViewModel,
    sharedPreferencesViewModel: SharedPreferencesViewModel
) {
    val accountState by accountStateViewModel.accountContent.collectAsStateWithLifecycle()

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
                    CompositionLocalProvider(
                        LocalViewModelStoreOwner provides state.currentViewModelStore
                    ) {
                        LoggedInPage(
                            state.account,
                            accountStateViewModel,
                            sharedPreferencesViewModel
                        )
                    }
                }
                is AccountState.LoggedInViewOnly -> {
                    CompositionLocalProvider(
                        LocalViewModelStoreOwner provides state.currentViewModelStore
                    ) {
                        LoggedInPage(
                            state.account,
                            accountStateViewModel,
                            sharedPreferencesViewModel
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LoggedInPage(
    account: Account,
    accountStateViewModel: AccountStateViewModel,
    sharedPreferencesViewModel: SharedPreferencesViewModel
) {
    val accountViewModel: AccountViewModel = viewModel(
        key = "AccountStateViewModel",
        factory = AccountViewModel.Factory(
            account,
            sharedPreferencesViewModel.sharedPrefs
        )
    )

    MainScreen(accountViewModel, accountStateViewModel, sharedPreferencesViewModel)
}

class AccountCentricViewModelStore(val account: Account) : ViewModelStoreOwner {
    override val viewModelStore = ViewModelStore()
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
