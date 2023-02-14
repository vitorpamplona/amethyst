package com.vitorpamplona.amethyst.service

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.service.model.ChannelCreateEvent
import com.vitorpamplona.amethyst.service.model.ChannelMessageEvent
import com.vitorpamplona.amethyst.service.model.ChannelMetadataEvent
import com.vitorpamplona.amethyst.service.relays.FeedType
import com.vitorpamplona.amethyst.service.relays.TypedFilter
import nostr.postr.JsonFilter
import nostr.postr.events.PrivateDmEvent

object NostrChatroomListDataSource: NostrDataSource<Note>("MailBoxFeed") {
  lateinit var account: Account

  fun createMessagesToMeFilter() = TypedFilter(
    types = setOf(FeedType.PRIVATE_DMS),
      filter = JsonFilter(
      kinds = listOf(PrivateDmEvent.kind),
      tags = mapOf("p" to listOf(account.userProfile().pubkeyHex))
    )
  )

  fun createMessagesFromMeFilter() = TypedFilter(
    types = setOf(FeedType.PRIVATE_DMS),
      filter = JsonFilter(
      kinds = listOf(PrivateDmEvent.kind),
      authors = listOf(account.userProfile().pubkeyHex)
    )
  )

  fun createChannelsCreatedbyMeFilter() = TypedFilter(
    types = setOf(FeedType.PUBLIC_CHATS),
      filter = JsonFilter(
      kinds = listOf(ChannelCreateEvent.kind, ChannelMetadataEvent.kind),
      authors = listOf(account.userProfile().pubkeyHex)
    )
  )

  fun createMyChannelsFilter() = TypedFilter(
    types = setOf(FeedType.PUBLIC_CHATS),
      filter = JsonFilter(
      kinds = listOf(ChannelCreateEvent.kind),
      ids = account.followingChannels.toList()
    )
  )

  fun createLastChannelInfoFilter(): List<TypedFilter> {
    return account.followingChannels.map {
      TypedFilter(
        types = setOf(FeedType.PUBLIC_CHATS),
        filter = JsonFilter(
          kinds = listOf(ChannelMetadataEvent.kind),
          tags = mapOf("e" to listOf(it)),
          limit = 1
        )
      )
    }
  }

  fun createLastMessageOfEachChannelFilter(): List<TypedFilter> {
    return account.followingChannels.map {
      TypedFilter(
        types = setOf(FeedType.PUBLIC_CHATS),
        filter = JsonFilter(
          kinds = listOf(ChannelMessageEvent.kind),
          tags = mapOf("e" to listOf(it)),
          limit = 1
        )
      )
    }
  }

  val chatroomListChannel = requestNewChannel()

  // returns the last Note of each user.
  override fun feed(): List<Note> {
    val privateChatrooms = account.userProfile().privateChatrooms
    val messagingWith = privateChatrooms.keys.filter { account.isAcceptable(it) }

    val privateMessages = messagingWith.mapNotNull {
      privateChatrooms[it]?.roomMessages?.sortedBy { it.event?.createdAt }?.lastOrNull { it.event != null }
    }

    val publicChannels = account.followingChannels().map {
      it.notes.values.filter { account.isAcceptable(it) }.sortedBy { it.event?.createdAt }.lastOrNull { it.event != null }
    }

    return (privateMessages + publicChannels).filterNotNull().sortedBy { it.event?.createdAt }.reversed()
  }

  override fun updateChannelFilters() {
    val list = listOf(
      createMessagesToMeFilter(),
      createMessagesFromMeFilter(),
      createMyChannelsFilter()
    )

    chatroomListChannel.typedFilters = listOfNotNull(
      list,
      createLastChannelInfoFilter(),
      createLastMessageOfEachChannelFilter()
    ).flatten().ifEmpty { null }
  }
}