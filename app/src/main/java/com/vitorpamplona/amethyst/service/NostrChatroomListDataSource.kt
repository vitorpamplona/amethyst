package com.vitorpamplona.amethyst.service

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.Note
import nostr.postr.JsonFilter
import nostr.postr.events.PrivateDmEvent

object NostrChatroomListDataSource: NostrDataSource("MailBoxFeed") {
  lateinit var account: Account

  fun createMessagesToMeFilter() = JsonFilter(
    kinds = listOf(PrivateDmEvent.kind),
    tags = mapOf("p" to listOf(account.userProfile().pubkeyHex))
  )

  fun createMessagesFromMeFilter() = JsonFilter(
    kinds = listOf(PrivateDmEvent.kind),
    authors = listOf(account.userProfile().pubkeyHex)
  )

  val incomingChannel = requestNewChannel()
  val outgoingChannel = requestNewChannel()

  // returns the last Note of each user.
  override fun feed(): List<Note> {
    val messages = account.userProfile().messages
    val messagingWith = messages.keys().toList()

    return messagingWith.mapNotNull {
      messages[it]?.sortedBy { it.event?.createdAt }?.last { it.event != null }
    }.sortedBy { it.event?.createdAt }.reversed()
  }

  override fun updateChannelFilters() {
    incomingChannel.filter = createMessagesToMeFilter()
    outgoingChannel.filter = createMessagesFromMeFilter()
  }
}