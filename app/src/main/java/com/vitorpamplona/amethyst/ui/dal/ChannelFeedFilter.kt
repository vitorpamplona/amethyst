package com.vitorpamplona.amethyst.ui.dal

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.Channel
import com.vitorpamplona.amethyst.model.Note

class ChannelFeedFilter(val channel: Channel, val account: Account) : AdditiveFeedFilter<Note>() {
    // returns the last Note of each user.
    override fun feed(): List<Note> {
        return channel.notes
            .values
            .filter { account.isAcceptable(it) }
            .sortedWith(compareBy({ it.createdAt() }, { it.idHex }))
            .reversed()
    }

    override fun applyFilter(collection: Set<Note>): Set<Note> {
        return collection
            .filter { it.idHex in channel.notes.keys && account.isAcceptable(it) }
            .toSet()
    }

    override fun sort(collection: Set<Note>): List<Note> {
        return collection.sortedWith(compareBy({ it.createdAt() }, { it.idHex })).reversed()
    }
}
