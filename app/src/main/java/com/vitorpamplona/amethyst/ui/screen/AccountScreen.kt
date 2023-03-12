package com.vitorpamplona.amethyst.ui.screen

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.MainScreen
import com.vitorpamplona.amethyst.ui.screen.loggedOff.LoginPage

@Composable
fun AccountScreen(accountStateViewModel: AccountStateViewModel, startingPage: String?) {
    val accountState by accountStateViewModel.accountContent.collectAsState()

    Column() {
        Crossfade(targetState = accountState, animationSpec = tween(durationMillis = 100)) { state ->
            when (state) {
                is AccountState.LoggedOff -> {
                    LoginPage(accountStateViewModel, isFirstLogin = true)
                }
                is AccountState.LoggedIn -> {
                    MainScreen(AccountViewModel(state.account), accountStateViewModel, startingPage)
                }
                is AccountState.LoggedInViewOnly -> {
                    MainScreen(AccountViewModel(state.account), accountStateViewModel, startingPage)
                }
            }
        }
    }
}
