package com.vitorpamplona.amethyst.service

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.relays.FeedType
import com.vitorpamplona.amethyst.service.relays.TypedFilter
import nostr.postr.JsonFilter
import nostr.postr.events.PrivateDmEvent

object NostrChatRoomDataSource: NostrDataSource<Note>("ChatroomFeed") {
  lateinit var account: Account
  var withUser: User? = null

  fun loadMessagesBetween(accountIn: Account, userId: String) {
    account = accountIn
    withUser = LocalCache.users[userId]
  }

  fun createMessagesToMeFilter(): TypedFilter? {
    val myPeer = withUser
    
    return if (myPeer != null) {
      TypedFilter(
        types = setOf(FeedType.PRIVATE_DMS),
        filter = JsonFilter(
          kinds = listOf(PrivateDmEvent.kind),
          authors = listOf(myPeer.pubkeyHex) ,
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
        types = setOf(FeedType.PUBLIC_CHATS),
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

  // returns the last Note of each user.
  override fun feed(): List<Note> {
    val messages = account.userProfile().messages[withUser] ?: return emptyList()

    return messages.filter { account.isAcceptable(it) }.sortedBy { it.event?.createdAt }.reversed()
  }

  override fun updateChannelFilters() {
    inandoutChannel.filter = listOfNotNull(createMessagesToMeFilter(), createMessagesFromMeFilter()).ifEmpty { null }
  }
}