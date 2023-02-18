package com.vitorpamplona.amethyst.ui.dal

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User

object UserProfileNoteFeedFilter: FeedFilter<Note>() {
  lateinit var account: Account
  var user: User? = null

  fun loadUserProfile(accountLoggedIn: Account, userId: String) {
    account = accountLoggedIn
    user = LocalCache.getOrCreateUser(userId)
  }

  override fun feed(): List<Note> {
    return user?.notes
      ?.filter { account.isAcceptable(it) }
      ?.sortedBy { it.event?.createdAt }
      ?.reversed()
      ?: emptyList()
  }
}