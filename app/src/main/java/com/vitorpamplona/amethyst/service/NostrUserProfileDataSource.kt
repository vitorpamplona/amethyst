package com.vitorpamplona.amethyst.service

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.model.toByteArray
import nostr.postr.JsonFilter
import nostr.postr.events.ContactListEvent
import nostr.postr.events.MetadataEvent
import nostr.postr.events.TextNoteEvent

object NostrUserProfileDataSource: NostrDataSource<Note>("UserProfileFeed") {
  lateinit var account: Account
  var user: User? = null

  fun loadUserProfile(userId: String) {
    user = LocalCache.getOrCreateUser(userId)
    resetFilters()
  }

  fun createUserInfoFilter(): JsonFilter {
    return JsonFilter(
      kinds = listOf(MetadataEvent.kind),
      authors = listOf(user!!.pubkeyHex),
      limit = 1
    )
  }

  fun createUserPostsFilter(): JsonFilter {
    return JsonFilter(
      kinds = listOf(TextNoteEvent.kind),
      authors = listOf(user!!.pubkeyHex),
      limit = 100
    )
  }

  fun createFollowFilter(): JsonFilter {
    return JsonFilter(
      kinds = listOf(ContactListEvent.kind),
      authors = listOf(user!!.pubkeyHex),
      limit = 1
    )
  }

  fun createFollowersFilter() = JsonFilter(
    kinds = listOf(ContactListEvent.kind),
    tags = mapOf("p" to listOf(user!!.pubkeyHex))
  )

  val userInfoChannel = requestNewChannel()

  override fun feed(): List<Note> {
    return user?.notes
      ?.filter { account.isAcceptable(it) }
      ?.sortedBy { it.event?.createdAt }
      ?.reversed()
      ?: emptyList()
  }

  override fun updateChannelFilters() {
    userInfoChannel.filter = listOf(
      createUserInfoFilter(),
      createUserPostsFilter(),
      createFollowFilter(),
      createFollowersFilter()
    ).ifEmpty { null }
  }
}