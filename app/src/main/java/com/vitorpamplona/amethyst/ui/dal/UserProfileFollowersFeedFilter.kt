package com.vitorpamplona.amethyst.ui.dal

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.User

object UserProfileFollowersFeedFilter: FeedFilter<User>() {
  lateinit var account: Account
  var user: User? = null

  fun loadUserProfile(accountLoggedIn: Account, userId: String) {
    account = accountLoggedIn
    user = LocalCache.users[userId]
  }

  override fun feed(): List<User> {
    return user?.followers
      ?.filter { account.isAcceptable(it) } ?: emptyList()
  }
}
