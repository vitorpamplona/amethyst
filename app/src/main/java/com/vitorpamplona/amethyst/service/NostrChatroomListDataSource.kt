package com.vitorpamplona.amethyst.service

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
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

  fun createChannelsCreatedbyMeFilter() = JsonFilter(
    kinds = listOf(ChannelCreateEvent.kind, ChannelMetadataEvent.kind),
    authors = listOf(account.userProfile().pubkeyHex)
  )

  fun createMyChannelsFilter() = JsonFilter(
    kinds = listOf(ChannelCreateEvent.kind),
    ids = account.followingChannels.toList()
  )

  fun createLastChannelInfoFilter(): List<JsonFilter> {
    return account.followingChannels.map {
      JsonFilter(
        kinds = listOf(ChannelMetadataEvent.kind),
        tags = mapOf("e" to listOf(it)),
        limit = 1
      )
    }
  }

  fun createLastMessageOfEachChannelFilter(): List<JsonFilter> {
    return account.followingChannels.map {
      JsonFilter(
        kinds = listOf(ChannelMessageEvent.kind),
        tags = mapOf("e" to listOf(it)),
        limit = 1
      )
    }
  }

  val incomingChannel = requestNewChannel()
  val outgoingChannel = requestNewChannel()

  val myChannelsChannel = requestNewChannel()
  val myChannelsInfoChannel = requestNewChannel()
  val myChannelsMessagesChannel = requestNewChannel()

  val myChannelsCreatedbyMeChannel = requestNewChannel()

  // returns the last Note of each user.
  override fun feed(): List<Note> {
    val messages = account.userProfile().messages
    val messagingWith = messages.keys().toList().filter { account.isAcceptable(it) }

    val privateMessages = messagingWith.mapNotNull {
      messages[it]?.sortedBy { it.event?.createdAt }?.lastOrNull { it.event != null }
    }

    val publicChannels = account.followingChannels().map {
      it.notes.values.filter { account.isAcceptable(it) }.sortedBy { it.event?.createdAt }.lastOrNull { it.event != null }
    }

    val channelsCreatedByMe = LocalCache.channels.values.filter {
      it.creator == account.userProfile()
    }.map {
      it.notes.values.sortedBy { it.event?.createdAt }.lastOrNull { it.event != null }
    }

    return (privateMessages + publicChannels).filterNotNull().sortedBy { it.event?.createdAt }.reversed()
  }

  override fun updateChannelFilters() {
    incomingChannel.filter = listOf(createMessagesToMeFilter()).ifEmpty { null }
    outgoingChannel.filter = listOf(createMessagesFromMeFilter()).ifEmpty { null }
    myChannelsChannel.filter = listOf(createMyChannelsFilter()).ifEmpty { null }
    myChannelsInfoChannel.filter = createLastChannelInfoFilter().ifEmpty { null }
    myChannelsMessagesChannel.filter = createLastMessageOfEachChannelFilter().ifEmpty { null }

    //myChannelsCreatedbyMeChannel.filter = listOf(createChannelsCreatedbyMeFilter()).ifEmpty { null }
  }
}