package com.vitorpamplona.amethyst.ui.screen

import com.vitorpamplona.amethyst.model.Account

sealed class AccountState {
    object Loading : AccountState()
    object LoggedOff : AccountState()
    class LoggedInViewOnly(val account: Account) : AccountState() {
        val currentViewModelStore = AccountCentricViewModelStore(account)
    }
    class LoggedIn(val account: Account) : AccountState() {
        val currentViewModelStore = AccountCentricViewModelStore(account)
    }
}
