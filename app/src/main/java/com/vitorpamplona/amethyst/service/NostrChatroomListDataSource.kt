package com.vitorpamplona.amethyst.service

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.service.model.ChannelCreateEvent
import com.vitorpamplona.amethyst.service.model.ChannelMessageEvent
import com.vitorpamplona.amethyst.service.model.ChannelMetadataEvent
import nostr.postr.JsonFilter
import nostr.postr.events.PrivateDmEvent

object NostrChatroomListDataSource: NostrDataSource<Note>("MailBoxFeed") {
  lateinit var account: Account

  fun createMessagesToMeFilter() = JsonFilter(
    kinds = listOf(PrivateDmEvent.kind),
    tags = mapOf("p" to listOf(account.userProfile().pubkeyHex))
  )

  fun createMessagesFromMeFilter() = JsonFilter(
    kinds = listOf(PrivateDmEvent.kind),
    authors = listOf(account.userProfile().pubkeyHex)
  )

  fun createMyChannelsFilter() = JsonFilter(
    kinds = listOf(ChannelCreateEvent.kind),
    ids = account.followingChannels.toList()
  )

  fun createMyChannelsInfoFilter() = JsonFilter(
    kinds = listOf(ChannelMetadataEvent.kind),
    tags = mapOf("e" to account.followingChannels.toList())
  )

  fun createMessagesToMyChannelsFilter() = JsonFilter(
    kinds = listOf(ChannelMessageEvent.kind),
    tags = mapOf("e" to account.followingChannels.toList()),
    since = System.currentTimeMillis() / 1000 - (60 * 60 * 24 * 1), // 24 hours
  )

  val incomingChannel = requestNewChannel()
  val outgoingChannel = requestNewChannel()

  val myChannelsChannel = requestNewChannel()
  val myChannelsInfoChannel = requestNewChannel()
  val myChannelsMessagesChannel = requestNewChannel()

  // returns the last Note of each user.
  override fun feed(): List<Note> {
    val messages = account.userProfile().messages
    val messagingWith = messages.keys().toList()

    val privateMessages = messagingWith.mapNotNull {
      messages[it]?.sortedBy { it.event?.createdAt }?.last { it.event != null }
    }

    val publicChannels = account.followingChannels().map {
      it.notes.values.sortedBy { it.event?.createdAt }.last { it.event != null }
    }

    return (privateMessages + publicChannels).sortedBy { it.event?.createdAt }.reversed()
  }

  override fun updateChannelFilters() {
    incomingChannel.filter = createMessagesToMeFilter()
    outgoingChannel.filter = createMessagesFromMeFilter()
    myChannelsChannel.filter = createMyChannelsFilter()
    myChannelsInfoChannel.filter = createMyChannelsInfoFilter()
    myChannelsMessagesChannel.filter = createMessagesToMyChannelsFilter()
  }
}