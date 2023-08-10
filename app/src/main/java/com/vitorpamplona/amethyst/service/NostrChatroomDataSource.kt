package com.vitorpamplona.amethyst.service

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.ChatroomKey
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.service.model.Event
import com.vitorpamplona.amethyst.service.model.GiftWrapEvent
import com.vitorpamplona.amethyst.service.model.PrivateDmEvent
import com.vitorpamplona.amethyst.service.model.SealedGossipEvent
import com.vitorpamplona.amethyst.service.relays.EOSEAccount
import com.vitorpamplona.amethyst.service.relays.FeedType
import com.vitorpamplona.amethyst.service.relays.JsonFilter
import com.vitorpamplona.amethyst.service.relays.Relay
import com.vitorpamplona.amethyst.service.relays.TypedFilter

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

    /* DOESN'T Load gift wraps here because it is already loaded on Chatroom List.
       There is no way to filter for gifts only in this conversation.
    fun createGiftWrapsToMeFilter(): TypedFilter? {
        val myPeer = withRoom

        return if (myPeer != null) {
            TypedFilter(
                types = setOf(FeedType.PRIVATE_DMS),
                filter = JsonFilter(
                    kinds = listOf(GiftWrapEvent.kind),
                    tags = mapOf("p" to listOf(account.userProfile().pubkeyHex)),
                    since = latestEOSEs.users[account.userProfile()]?.followList?.get(withRoom.hashCode().toString())?.relayList
                )
            )
        } else {
            null
        }
    }
    */

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

    override fun consume(event: Event, relay: Relay) {
        if (this::account.isInitialized && LocalCache.justVerify(event)) {
            if (event is GiftWrapEvent) {
                val privateKey = account.keyPair.privKey
                if (privateKey != null) {
                    event.cachedGift(privateKey)?.let {
                        this.consume(it, relay)
                    }
                }
            }

            if (event is SealedGossipEvent) {
                val privateKey = account.keyPair.privKey
                if (privateKey != null) {
                    event.cachedGossip(privateKey)?.let {
                        LocalCache.justConsume(it, relay)
                    }
                }

                // Don't store sealed gossips to avoid rebroadcasting by mistake.
            } else {
                LocalCache.justConsume(event, relay)
            }
        }
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
