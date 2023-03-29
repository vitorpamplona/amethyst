package com.vitorpamplona.amethyst.ui.dal

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.Channel
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note

object ChannelFeedFilter : AdditiveFeedFilter<Note>() {
    lateinit var account: Account
    lateinit var channel: Channel

    fun loadMessagesBetween(accountLoggedIn: Account, channelId: String) {
        account = accountLoggedIn
        channel = LocalCache.getOrCreateChannel(channelId)
    }

    // returns the last Note of each user.
    override fun feed(): List<Note> {
        return channel.notes
            .values
            .filter { account.isAcceptable(it) }
            .sortedBy { it.createdAt() }
            .reversed()
    }

    override fun applyFilter(collection: Set<Note>): List<Note> {
        return collection
            .filter { it.idHex in channel.notes.keys }
            .filter { account.isAcceptable(it) }
    }

    override fun sort(collection: List<Note>): List<Note> {
        return collection.sortedBy { it.createdAt() }.reversed()
    }
}
