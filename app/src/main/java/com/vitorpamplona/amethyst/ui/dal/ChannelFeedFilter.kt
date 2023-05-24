package com.vitorpamplona.amethyst.ui.dal

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note

object ChannelFeedFilter : AdditiveFeedFilter<Note>() {
    lateinit var account: Account
    var channelId: String? = null

    fun loadMessagesBetween(accountLoggedIn: Account, channelId: String?) {
        this.account = accountLoggedIn
        this.channelId = channelId
    }

    // returns the last Note of each user.
    override fun feed(): List<Note> {
        val processingChannel = channelId ?: return emptyList()
        val channel = LocalCache.getOrCreateChannel(processingChannel)

        return channel.notes
            .values
            .filter { account.isAcceptable(it) }
            .sortedWith(compareBy({ it.createdAt() }, { it.idHex }))
            .reversed()
    }

    override fun applyFilter(collection: Set<Note>): Set<Note> {
        val processingChannel = channelId ?: return emptySet()
        val channel = LocalCache.getOrCreateChannel(processingChannel)

        return collection
            .filter { it.idHex in channel.notes.keys && account.isAcceptable(it) }
            .toSet()
    }

    override fun sort(collection: Set<Note>): List<Note> {
        return collection.sortedWith(compareBy({ it.createdAt() }, { it.idHex })).reversed()
    }
}
