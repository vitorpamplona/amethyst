package com.vitorpamplona.amethyst.service

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.service.relays.EOSEAccount
import com.vitorpamplona.amethyst.service.relays.FeedType
import com.vitorpamplona.amethyst.service.relays.JsonFilter
import com.vitorpamplona.amethyst.service.relays.TypedFilter
import com.vitorpamplona.quartz.events.ChatroomKey
import com.vitorpamplona.quartz.events.PrivateDmEvent

object NostrChatroomDataSource : NostrDataSource("ChatroomFeed") {
    lateinit var account: Account
    private var withRoom: ChatroomKey? = null

    private val latestEOSEs = EOSEAccount()

    fun loadMessagesBetween(accountIn: Account, withRoom: ChatroomKey) {
        this.account = accountIn
        this.withRoom = withRoom
        resetFilters()
    }

    fun createMessagesToMeFilter(): TypedFilter? {
        val myPeer = withRoom

        return if (myPeer != null) {
            TypedFilter(
                types = setOf(FeedType.PRIVATE_DMS),
                filter = JsonFilter(
                    kinds = listOf(PrivateDmEvent.kind),
                    authors = myPeer.users.map { it },
                    tags = mapOf("p" to listOf(account.userProfile().pubkeyHex)),
                    since = latestEOSEs.users[account.userProfile()]?.followList?.get(withRoom.hashCode().toString())?.relayList
                )
            )
        } else {
            null
        }
    }

    fun createMessagesFromMeFilter(): TypedFilter? {
        val myPeer = withRoom

        return if (myPeer != null) {
            TypedFilter(
                types = setOf(FeedType.PRIVATE_DMS),
                filter = JsonFilter(
                    kinds = listOf(PrivateDmEvent.kind),
                    authors = listOf(account.userProfile().pubkeyHex),
                    tags = mapOf("p" to myPeer.users.map { it }),
                    since = latestEOSEs.users[account.userProfile()]?.followList?.get(withRoom.hashCode().toString())?.relayList
                )
            )
        } else {
            null
        }
    }

    fun clearEOSEs(account: Account) {
        latestEOSEs.removeDataFor(account.userProfile())
    }

    val inandoutChannel = requestNewChannel { time, relayUrl ->
        latestEOSEs.addOrUpdate(account.userProfile(), withRoom.hashCode().toString(), relayUrl, time)
    }

    override fun updateChannelFilters() {
        inandoutChannel.typedFilters = listOfNotNull(
            createMessagesToMeFilter(),
            createMessagesFromMeFilter()
        ).ifEmpty { null }
    }
}
