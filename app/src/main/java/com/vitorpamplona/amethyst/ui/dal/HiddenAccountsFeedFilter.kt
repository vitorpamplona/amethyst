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
        val blockList = account.getBlockList()
        val decryptedContent = blockList?.decryptedContent ?: ""
        if (account.loginWithExternalSigner) {
            if (decryptedContent.isEmpty()) return emptyList()

            return blockList
                ?.publicAndPrivateUsers(decryptedContent)
                ?.map { LocalCache.getOrCreateUser(it) }
                ?: emptyList()
        }

        return blockList
            ?.publicAndPrivateUsers(account.keyPair.privKey)
            ?.map { LocalCache.getOrCreateUser(it) }
            ?: emptyList()
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
        val blockList = account.getBlockList()
        val decryptedContent = blockList?.decryptedContent ?: ""
        if (account.loginWithExternalSigner) {
            if (decryptedContent.isEmpty()) return emptyList()

            return blockList
                ?.publicAndPrivateWords(decryptedContent)?.toList()
                ?: emptyList()
        }

        return blockList
            ?.publicAndPrivateWords(account.keyPair.privKey)?.toList()
            ?: emptyList()
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
