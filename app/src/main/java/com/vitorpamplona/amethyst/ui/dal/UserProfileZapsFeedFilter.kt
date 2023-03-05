package com.vitorpamplona.amethyst.ui.dal

import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.model.LnZapEvent

object UserProfileZapsFeedFilter: FeedFilter<Pair<Note, Note>>() {
  var user: User? = null

  fun loadUserProfile(userId: String) {
    user = LocalCache.checkGetOrCreateUser(userId)
  }

  override fun feed(): List<Pair<Note, Note>> {
    return (user?.zaps
      ?.filter { it.value != null }
      ?.toList()
      ?.sortedBy { (it.second?.event as? LnZapEvent)?.amount }
      ?.reversed() ?: emptyList()) as List<Pair<Note, Note>>
  }
}
