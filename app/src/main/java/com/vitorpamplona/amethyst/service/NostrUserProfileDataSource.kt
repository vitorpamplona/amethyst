package com.vitorpamplona.amethyst.service

import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.model.LnZapEvent
import com.vitorpamplona.amethyst.service.relays.FeedType
import com.vitorpamplona.amethyst.service.relays.TypedFilter
import nostr.postr.JsonFilter
import nostr.postr.events.ContactListEvent
import nostr.postr.events.MetadataEvent
import nostr.postr.events.TextNoteEvent

object NostrUserProfileDataSource: NostrDataSource("UserProfileFeed") {
  var user: User? = null

  fun loadUserProfile(userId: String?) {
    if (userId != null) {
      user = LocalCache.getOrCreateUser(userId)
    }

    resetFilters()
  }

  fun createUserInfoFilter(): TypedFilter {
    return TypedFilter(
      types = FeedType.values().toSet(),
      filter = JsonFilter(
        kinds = listOf(MetadataEvent.kind),
        authors = listOf(user!!.pubkeyHex),
        limit = 1
      )
    )
  }

  fun createUserPostsFilter(): TypedFilter {
    return TypedFilter(
      types = FeedType.values().toSet(),
      filter = JsonFilter(
        kinds = listOf(TextNoteEvent.kind),
        authors = listOf(user!!.pubkeyHex),
        limit = 100
      )
    )
  }

  fun createUserReceivedZapsFilter(): TypedFilter {
    return TypedFilter(
      types = FeedType.values().toSet(),
      filter = JsonFilter(
        kinds = listOf(LnZapEvent.kind),
        tags = mapOf("p" to listOf(user!!.pubkeyHex))
      )
    )
  }

  fun createFollowFilter(): TypedFilter {
    return TypedFilter(
      types = FeedType.values().toSet(),
      filter = JsonFilter(
        kinds = listOf(ContactListEvent.kind),
        authors = listOf(user!!.pubkeyHex),
        limit = 1
      )
    )
  }

  fun createFollowersFilter() = TypedFilter(
    types = FeedType.values().toSet(),
    filter = JsonFilter(
      kinds = listOf(ContactListEvent.kind),
      tags = mapOf("p" to listOf(user!!.pubkeyHex))
    )
  )

  val userInfoChannel = requestNewChannel()

  override fun updateChannelFilters() {
    userInfoChannel.typedFilters = listOf(
      createUserInfoFilter(),
      createUserPostsFilter(),
      createFollowFilter(),
      createFollowersFilter(),
      createUserReceivedZapsFilter()
    ).ifEmpty { null }
  }
}