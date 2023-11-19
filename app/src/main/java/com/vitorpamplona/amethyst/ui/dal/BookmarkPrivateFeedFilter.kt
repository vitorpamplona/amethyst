package com.vitorpamplona.amethyst.ui.dal

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note

class BookmarkPrivateFeedFilter(val account: Account) : FeedFilter<Note>() {
    override fun feedKey(): String {
        return account.userProfile().latestBookmarkList?.id ?: ""
    }

    override fun feed(): List<Note> {
        val bookmarks = account.userProfile().latestBookmarkList

        if (!account.isWriteable()) return emptyList()

        val privateTags = bookmarks?.cachedPrivateTags() ?: return emptyList()

        val notes = bookmarks.filterEvents(privateTags)
            .mapNotNull { LocalCache.checkGetOrCreateNote(it) }

        val addresses = bookmarks.filterAddresses(privateTags)
            .map { LocalCache.getOrCreateAddressableNote(it) }

        return notes.plus(addresses).toSet()
            .sortedWith(compareBy({ it.createdAt() }, { it.idHex }))
            .reversed()
    }
}
