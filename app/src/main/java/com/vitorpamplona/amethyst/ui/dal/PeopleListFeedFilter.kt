package com.vitorpamplona.amethyst.ui.dal

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.service.model.PeopleListEvent

object PeopleListFeedFilter : FeedFilter<Note>() {
    lateinit var account: Account

    override fun feed(): List<Note> {
        val privKey = account.loggedIn.privKey ?: return emptyList()

        val lists = LocalCache.addressables.values
            .asSequence()
            .filter {
                (it.event is PeopleListEvent)
            }
            .toSet()

        return lists
            .sortedWith(compareBy({ it.createdAt() }, { it.idHex }))
            .reversed()
    }
}
