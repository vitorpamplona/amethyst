package com.vitorpamplona.amethyst.service

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import nostr.postr.JsonFilter
import nostr.postr.events.PrivateDmEvent

object NostrChatRoomDataSource: NostrDataSource<Note>("ChatroomFeed") {
  lateinit var account: Account
  var withUser: User? = null

  fun loadMessagesBetween(accountIn: Account, userId: String) {
    account = accountIn
    withUser = LocalCache.users[userId]
  }

  fun createMessagesToMeFilter(): JsonFilter? {
    val myPeer = withUser
    
    return if (myPeer != null) {
      JsonFilter(
        kinds = listOf(PrivateDmEvent.kind),
        authors = listOf(myPeer.pubkeyHex) ,
        tags = mapOf("p" to listOf(account.userProfile().pubkeyHex))
      )
    } else {
      null
    }
  }

  fun createMessagesFromMeFilter(): JsonFilter? {
    val myPeer = withUser
    
    return if (myPeer != null) {
      JsonFilter(
        kinds = listOf(PrivateDmEvent.kind),
        authors = listOf(account.userProfile().pubkeyHex),
        tags = mapOf("p" to listOf(myPeer.pubkeyHex))
      )
    } else {
      null
    }
  }

  val inandoutChannel = requestNewChannel()

  // returns the last Note of each user.
  override fun feed(): List<Note> {
    val messages = account.userProfile().messages[withUser] ?: return emptyList()

    val filteredMessages = synchronized(messages) {
      messages.filter { account.isAcceptable(it) }
    }

    return filteredMessages.sortedBy { it.event?.createdAt }.reversed()
  }

  override fun updateChannelFilters() {
    inandoutChannel.filter = listOfNotNull(createMessagesToMeFilter(), createMessagesFromMeFilter()).ifEmpty { null }
  }
}