package com.vitorpamplona.amethyst.ui.dal

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User

class UserProfileBookmarksFeedFilter(val user: User, val account: Account) : FeedFilter<Note>() {
    override fun feedKey(): String {
        return account.userProfile().pubkeyHex + "-" + user.pubkeyHex
    }

    override fun feed(): List<Note> {
        val notes = user.latestBookmarkList?.taggedEvents()?.mapNotNull {
            LocalCache.checkGetOrCreateNote(it)
        }?.toSet() ?: emptySet()

        val addresses = user.latestBookmarkList?.taggedAddresses()?.map {
            LocalCache.getOrCreateAddressableNote(it)
        }?.toSet() ?: emptySet()

        return (notes + addresses)
            .filter { account.isAcceptable(it) }
            .sortedWith(compareBy({ it.createdAt() }, { it.idHex }))
            .reversed()
    }
}
