package com.vitorpamplona.amethyst.ui.dal

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note

class BookmarkPublicFeedFilter(val account: Account) : FeedFilter<Note>() {
    override fun feedKey(): String {
        return account.userProfile().latestBookmarkList?.id ?: ""
    }
    override fun feed(): List<Note> {
        val bookmarks = account.userProfile().latestBookmarkList

        val notes = bookmarks?.taggedEvents()?.mapNotNull { LocalCache.checkGetOrCreateNote(it) } ?: emptyList()
        val addresses = bookmarks?.taggedAddresses()?.map { LocalCache.getOrCreateAddressableNote(it) } ?: emptyList()

        return notes.plus(addresses).toSet()
            .sortedWith(compareBy({ it.createdAt() }, { it.idHex }))
            .reversed()
    }
}
