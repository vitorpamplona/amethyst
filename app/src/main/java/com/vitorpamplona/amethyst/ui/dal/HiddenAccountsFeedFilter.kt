package com.vitorpamplona.amethyst.ui.dal

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.UserInterface

object HiddenAccountsFeedFilter : FeedFilter<UserInterface>() {
    lateinit var account: Account

    override fun feed() = account.hiddenUsers()
}
