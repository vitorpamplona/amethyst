package com.vitorpamplona.amethyst.ui.dal

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.Note

object ChatroomListNewFeedFilter : FeedFilter<Note>() {
    lateinit var account: Account

    // returns the last Note of each user.
    override fun feed(): List<Note> {
        val me = ChatroomListKnownFeedFilter.account.userProfile()

        val privateChatrooms = account.userProfile().privateChatrooms
        val messagingWith = privateChatrooms.keys.filter {
            !me.hasSentMessagesTo(it) && account.isAcceptable(it)
        }

        val privateMessages = messagingWith.mapNotNull { it ->
            privateChatrooms[it]
                ?.roomMessages
                ?.sortedBy { it.createdAt() }
                ?.lastOrNull { it.event != null }
        }

        return privateMessages
            .sortedBy { it.createdAt() }
            .reversed()
    }
}
