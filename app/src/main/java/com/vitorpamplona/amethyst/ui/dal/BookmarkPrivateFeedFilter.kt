package com.vitorpamplona.amethyst.ui.dal

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.service.AmberUtils
import com.vitorpamplona.quartz.encoders.toHexKey

object BookmarkPrivateFeedFilter : FeedFilter<Note>() {
    lateinit var account: Account

    override fun feedKey(): String {
        return account.userProfile().latestBookmarkList?.id ?: ""
    }

    override fun feed(): List<Note> {
        val bookmarks = account.userProfile().latestBookmarkList

        if (account.loginWithAmber) {
            val id = bookmarks?.id
            if (id != null) {
                val decryptedContent = AmberUtils.cachedDecryptedContent[id]
                if (decryptedContent == null) {
                    AmberUtils.decryptBookmark(
                        bookmarks.content,
                        account.keyPair.pubKey.toHexKey(),
                        id
                    )
                } else {
                    bookmarks.decryptedContent = decryptedContent
                }
            }
            val decryptedContent = AmberUtils.cachedDecryptedContent[id] ?: ""

            val notes = bookmarks?.privateTaggedEvents(decryptedContent)
                ?.mapNotNull { LocalCache.checkGetOrCreateNote(it) } ?: emptyList()
            val addresses = bookmarks?.privateTaggedAddresses(decryptedContent)
                ?.map { LocalCache.getOrCreateAddressableNote(it) } ?: emptyList()

            return notes.plus(addresses).toSet()
                .sortedWith(compareBy({ it.createdAt() }, { it.idHex }))
                .reversed()
        } else {
            val privKey = account.keyPair.privKey ?: return emptyList()

            val notes = bookmarks?.privateTaggedEvents(privKey)
                ?.mapNotNull { LocalCache.checkGetOrCreateNote(it) } ?: emptyList()

            val addresses = bookmarks?.privateTaggedAddresses(privKey)
                ?.map { LocalCache.getOrCreateAddressableNote(it) } ?: emptyList()

            return notes.plus(addresses).toSet()
                .sortedWith(compareBy({ it.createdAt() }, { it.idHex }))
                .reversed()
        }
    }
}
