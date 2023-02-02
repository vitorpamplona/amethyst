package com.vitorpamplona.amethyst.service

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.User
import nostr.postr.JsonFilter
import nostr.postr.events.ContactListEvent

object NostrUserProfileFollowsDataSource: NostrDataSource<User>("UserProfileFollowsFeed") {
  lateinit var account: Account
  var user: User? = null

  fun loadUserProfile(userId: String) {
    user = LocalCache.users[userId]
    resetFilters()
  }

  override fun feed(): List<User> {
    val follows = user?.follows ?: emptyList()

    return synchronized(follows) {
      follows.filter { account.isAcceptable(it) }.toList()
    }
  }

  override fun updateChannelFilters() {}
}