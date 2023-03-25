package com.vitorpamplona.amethyst.service

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.model.PrivateDmEvent
import com.vitorpamplona.amethyst.service.relays.FeedType
import com.vitorpamplona.amethyst.service.relays.JsonFilter
import com.vitorpamplona.amethyst.service.relays.TypedFilter

object NostrChatroomDataSource : NostrDataSource("ChatroomFeed") {
    lateinit var account: Account
    var withUser: User? = null

    fun loadMessagesBetween(accountIn: Account, userId: String) {
        account = accountIn
        withUser = LocalCache.users[userId]
        resetFilters()
    }

    fun createMessagesToMeFilter(): TypedFilter? {
        val myPeer = withUser

        return if (myPeer != null) {
            TypedFilter(
                types = setOf(FeedType.PRIVATE_DMS),
                filter = JsonFilter(
                    kinds = listOf(PrivateDmEvent.kind),
                    authors = listOf(myPeer.pubkeyHex),
                    tags = mapOf("p" to listOf(account.userProfile().pubkeyHex))
                )
            )
        } else {
            null
        }
    }

    fun createMessagesFromMeFilter(): TypedFilter? {
        val myPeer = withUser

        return if (myPeer != null) {
            TypedFilter(
                types = setOf(FeedType.PRIVATE_DMS),
                filter = JsonFilter(
                    kinds = listOf(PrivateDmEvent.kind),
                    authors = listOf(account.userProfile().pubkeyHex),
                    tags = mapOf("p" to listOf(myPeer.pubkeyHex))
                )
            )
        } else {
            null
        }
    }

    val inandoutChannel = requestNewChannel()

    override fun updateChannelFilters() {
        inandoutChannel.typedFilters = listOfNotNull(createMessagesToMeFilter(), createMessagesFromMeFilter()).ifEmpty { null }
    }
}
