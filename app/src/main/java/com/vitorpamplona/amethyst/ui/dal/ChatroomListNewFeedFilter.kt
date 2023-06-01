package com.vitorpamplona.amethyst.ui.dal

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.Note

class ChatroomListNewFeedFilter(val account: Account) : FeedFilter<Note>() {

    // returns the last Note of each user.
    override fun feed(): List<Note> {
        val me = account.userProfile()
        val followingKeySet = account.followingKeySet()

        val privateChatrooms = account.userProfile().privateChatrooms
        val messagingWith = privateChatrooms.keys.filter {
            it.pubkeyHex !in followingKeySet && !me.hasSentMessagesTo(it) && account.isAcceptable(it)
        }

        val privateMessages = messagingWith.mapNotNull { it ->
            privateChatrooms[it]
                ?.roomMessages
                ?.sortedWith(compareBy({ it.createdAt() }, { it.idHex }))
                ?.lastOrNull { it.event != null }
        }

        return privateMessages
            .sortedWith(compareBy({ it.createdAt() }, { it.idHex }))
            .reversed()
    }
}
