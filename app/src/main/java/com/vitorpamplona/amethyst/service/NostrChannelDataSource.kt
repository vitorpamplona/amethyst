package com.vitorpamplona.amethyst.service

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.service.model.ChannelMessageEvent
import com.vitorpamplona.amethyst.service.relays.FeedType
import com.vitorpamplona.amethyst.service.relays.TypedFilter
import nostr.postr.JsonFilter

object NostrChannelDataSource: NostrDataSource<Note>("ChatroomFeed") {
  lateinit var account: Account
  var channel: com.vitorpamplona.amethyst.model.Channel? = null

  fun loadMessagesBetween(channelId: String) {
    channel = LocalCache.getOrCreateChannel(channelId)
    resetFilters()
  }

  fun createMessagesToChannelFilter(): TypedFilter? {
    if (channel != null) {
      return TypedFilter(
        types = setOf(FeedType.PUBLIC_CHATS),
        filter = JsonFilter(
          kinds = listOf(ChannelMessageEvent.kind),
          tags = mapOf("e" to listOfNotNull(channel?.idHex)),
          limit = 200
        )
      )
    }
    return null
  }

  val messagesChannel = requestNewChannel()

  // returns the last Note of each user.
  override fun feed(): List<Note> {
    return channel?.notes?.values?.filter { account.isAcceptable(it) }?.sortedBy { it.event?.createdAt }?.reversed() ?: emptyList()
  }

  override fun updateChannelFilters() {
    messagesChannel.typedFilters = listOfNotNull(createMessagesToChannelFilter()).ifEmpty { null }
  }
}