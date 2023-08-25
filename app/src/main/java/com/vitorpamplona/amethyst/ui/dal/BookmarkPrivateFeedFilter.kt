package com.vitorpamplona.amethyst.ui.dal

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.service.IntentUtils
import com.vitorpamplona.amethyst.ui.actions.SignerType
import com.vitorpamplona.amethyst.ui.actions.openAmber
import com.vitorpamplona.quartz.encoders.toHexKey

object BookmarkPrivateFeedFilter : FeedFilter<Note>() {
    lateinit var account: Account
    var content: String = ""
    var isActivityRunning: Boolean = false

    override fun feedKey(): String {
        return account.userProfile().latestBookmarkList?.id ?: ""
    }

    override fun feed(): List<Note> {
        val bookmarks = account.userProfile().latestBookmarkList

        if (account.loginWithAmber) {
            if (content.isBlank()) {
                isActivityRunning = true
                openAmber(
                    bookmarks?.content ?: "",
                    SignerType.NIP04_DECRYPT,
                    IntentUtils.decryptActivityResultLauncher,
                    account.keyPair.pubKey.toHexKey()
                )
                while (isActivityRunning) {
                    Thread.sleep(250)
                }
            }

            val notes = bookmarks?.privateTaggedEvents(content)
                ?.mapNotNull { LocalCache.checkGetOrCreateNote(it) } ?: emptyList()
            val addresses = bookmarks?.privateTaggedAddresses(content)
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
