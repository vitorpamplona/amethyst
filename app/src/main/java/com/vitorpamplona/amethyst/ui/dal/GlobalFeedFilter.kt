package com.vitorpamplona.amethyst.ui.dal

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.service.model.ChannelMessageEvent
import com.vitorpamplona.amethyst.service.model.LongTextNoteEvent
import com.vitorpamplona.amethyst.service.model.TextNoteEvent

object GlobalFeedFilter : FeedFilter<Note>() {
    lateinit var account: Account

    override fun feed() = LocalCache.notes.values
        .filter {
            (it.event is TextNoteEvent || it.event is LongTextNoteEvent || it.event is ChannelMessageEvent) &&
                it.replyTo.isNullOrEmpty()
        }
        .filter {
            // does not show events already in the public chat list
            (it.channel() == null || it.channel() !in account.followingChannels()) &&
                // does not show people the user already follows
                (it.author !in account.userProfile().follows)
        }
        .filter { account.isAcceptable(it) }
        .sortedBy { it.createdAt() }
        .reversed()
}
