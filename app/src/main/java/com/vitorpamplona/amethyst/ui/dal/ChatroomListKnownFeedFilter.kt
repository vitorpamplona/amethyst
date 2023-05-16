package com.vitorpamplona.amethyst.ui.dal

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.Note

object ChatroomListKnownFeedFilter : FeedFilter<Note>() {
    lateinit var account: Account

    // returns the last Note of each user.
    override fun feed(): List<Note> {
        val me = account.userProfile()
        val followingKeySet = account.followingKeySet()

        val privateChatrooms = me.privateChatrooms
        val messagingWith = privateChatrooms.keys.filter {
            (it.pubkeyHex in followingKeySet || me.hasSentMessagesTo(it)) && !account.isHidden(it)
        }

        val privateMessages = messagingWith.mapNotNull { it ->
            privateChatrooms[it]
                ?.roomMessages
                ?.sortedWith(compareBy({ it.createdAt() }, { it.idHex }))
                ?.lastOrNull { it.event != null }
        }

        val publicChannels = account.followingChannels().map { it ->
            it.notes.values
                .filter { account.isAcceptable(it) }
                .sortedWith(compareBy({ it.createdAt() }, { it.idHex }))
                .lastOrNull { it.event != null }
        }

        return (privateMessages + publicChannels)
            .filterNotNull()
            .sortedWith(compareBy({ it.createdAt() }, { it.idHex }))
            .reversed()
    }
}
