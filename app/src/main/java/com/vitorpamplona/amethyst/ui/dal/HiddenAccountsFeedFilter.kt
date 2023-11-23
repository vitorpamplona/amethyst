package com.vitorpamplona.amethyst.ui.dal

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.User

class HiddenAccountsFeedFilter(val account: Account) : FeedFilter<User>() {
    override fun feedKey(): String {
        return account.userProfile().pubkeyHex
    }

    override fun showHiddenKey(): Boolean {
        return true
    }

    override fun feed(): List<User> {
        return account.flowHiddenUsers.value.hiddenUsers.map {
            LocalCache.getOrCreateUser(it)
        }
    }
}

class HiddenWordsFeedFilter(val account: Account) : FeedFilter<String>() {
    override fun feedKey(): String {
        return account.userProfile().pubkeyHex
    }

    override fun showHiddenKey(): Boolean {
        return true
    }

    override fun feed(): List<String> {
        return account.flowHiddenUsers.value.hiddenWords.toList()
    }
}

class SpammerAccountsFeedFilter(val account: Account) : FeedFilter<User>() {
    override fun feedKey(): String {
        return account.userProfile().pubkeyHex
    }

    override fun showHiddenKey(): Boolean {
        return true
    }

    override fun feed(): List<User> {
        return (account.transientHiddenUsers).map { LocalCache.getOrCreateUser(it) }
    }
}
