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

  fun createMessagesToMeFilter() = JsonFilter(
    kinds = listOf(PrivateDmEvent.kind),
    authors = withUser?.let { listOf(it.pubkeyHex) },
    tags = mapOf("p" to listOf(account.userProfile().pubkeyHex))
  )

  fun createMessagesFromMeFilter() = JsonFilter(
    kinds = listOf(PrivateDmEvent.kind),
    authors = listOf(account.userProfile().pubkeyHex),
    tags = withUser?.let { mapOf("p" to listOf(it.pubkeyHex)) }
  )

  val incomingChannel = requestNewChannel()
  val outgoingChannel = requestNewChannel()

  // returns the last Note of each user.
  override fun feed(): List<Note> {
    val messages = account.userProfile().messages[withUser]

    return messages?.sortedBy { it.event!!.createdAt } ?: emptyList()
  }

  override fun updateChannelFilters() {
    incomingChannel.filter = createMessagesToMeFilter()
    outgoingChannel.filter = createMessagesFromMeFilter()
  }
}