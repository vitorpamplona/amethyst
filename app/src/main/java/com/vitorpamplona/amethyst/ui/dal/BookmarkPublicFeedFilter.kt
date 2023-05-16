package com.vitorpamplona.amethyst.ui.dal

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note

object BookmarkPublicFeedFilter : FeedFilter<Note>() {
    lateinit var account: Account

    override fun feed(): List<Note> {
        val bookmarks = account.userProfile().latestBookmarkList

        val notes = bookmarks?.taggedEvents()?.mapNotNull { LocalCache.checkGetOrCreateNote(it) } ?: emptyList()
        val addresses = bookmarks?.taggedAddresses()?.map { LocalCache.getOrCreateAddressableNote(it) } ?: emptyList()

        return notes.plus(addresses).toSet()
            .sortedWith(compareBy({ it.createdAt() }, { it.idHex }))
            .reversed()
    }
}
