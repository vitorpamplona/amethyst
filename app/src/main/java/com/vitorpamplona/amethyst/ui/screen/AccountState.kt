package com.vitorpamplona.amethyst.ui.screen

import com.vitorpamplona.amethyst.model.Account

sealed class AccountState {
    object LoggedOff : AccountState()
    class LoggedInViewOnly(val account: Account) : AccountState()
    class LoggedIn(val account: Account) : AccountState()
}
