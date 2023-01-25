package com.vitorpamplona.amethyst.ui.screen

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel

@Composable
fun AccountScreen(accountStateViewModel: AccountStateViewModel, startingPage: String?) {
  val accountState by accountStateViewModel.accountContent.collectAsState()

  Column() {
    Crossfade(targetState = accountState) { state ->
      when (state) {
        is AccountState.LoggedOff -> {
          LoginPage(accountStateViewModel)
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

