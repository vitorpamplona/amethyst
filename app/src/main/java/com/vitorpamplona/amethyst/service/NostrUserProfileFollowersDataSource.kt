package com.vitorpamplona.amethyst.service

import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.User
import nostr.postr.JsonFilter
import nostr.postr.events.ContactListEvent

object NostrUserProfileFollowersDataSource: NostrDataSource<User>("UserProfileFollowerFeed") {
  var user: User? = null

  fun loadUserProfile(userId: String) {
    user = LocalCache.users[userId]
    resetFilters()
  }

  fun createFollowersFilter() = JsonFilter(
    kinds = listOf(ContactListEvent.kind),
    since = System.currentTimeMillis() / 1000 - (60 * 60 * 24 * 7), // 7 days
    tags = mapOf("p" to listOf(user!!.pubkeyHex).filterNotNull())
  )

  val followerChannel = requestNewChannel()

  override fun feed(): List<User> {
    val followers = user?.followers ?: emptyList()

    return synchronized(followers) {
      followers.toList()
    }
  }

  override fun updateChannelFilters() {
    followerChannel.filter = createFollowersFilter()
  }
}