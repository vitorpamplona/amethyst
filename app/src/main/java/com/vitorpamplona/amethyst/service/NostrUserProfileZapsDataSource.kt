package com.vitorpamplona.amethyst.service

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.model.LnZapEvent
import nostr.postr.JsonFilter
import nostr.postr.events.ContactListEvent

object NostrUserProfileZapsDataSource: NostrDataSource<Pair<Note,Note>>("UserProfileZapsFeed") {
  lateinit var account: Account
  var user: User? = null

  fun loadUserProfile(userId: String) {
    user = LocalCache.users[userId]
    resetFilters()
  }

  override fun feed(): List<Pair<Note,Note>> {
    return (user?.zaps?.filter { it.value != null }?.toList()?.sortedBy { (it.second?.event as? LnZapEvent)?.amount }?.reversed() ?: emptyList()) as List<Pair<Note, Note>>
  }

  override fun updateChannelFilters() {}
}