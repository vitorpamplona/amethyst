package com.vitorpamplona.amethyst.ui.dal

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note

object BookmarkPrivateFeedFilter : FeedFilter<Note>() {
    lateinit var account: Account

    override fun feed(): List<Note> {
        val privKey = account.loggedIn.privKey ?: return emptyList()

        val bookmarks = account.userProfile().latestBookmarkList

        val notes = bookmarks?.privateTaggedEvents(privKey)
            ?.mapNotNull { LocalCache.checkGetOrCreateNote(it) } ?: emptyList()

        val addresses = bookmarks?.privateTaggedAddresses(privKey)
            ?.map { LocalCache.getOrCreateAddressableNote(it) } ?: emptyList()

        return notes.plus(addresses).toSet()
            .sortedWith(compareBy({ it.createdAt() }, { it.idHex }))
            .reversed()
    }
}
