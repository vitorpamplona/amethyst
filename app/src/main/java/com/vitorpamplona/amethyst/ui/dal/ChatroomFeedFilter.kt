package com.vitorpamplona.amethyst.ui.dal

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User

object ChatroomFeedFilter: FeedFilter<Note>() {
  lateinit var account: Account
  lateinit var withUser: User

  fun loadMessagesBetween(accountIn: Account, userId: String) {
    account = accountIn
    withUser = LocalCache.getOrCreateUser(userId)
  }

  // returns the last Note of each user.
  override fun feed(): List<Note> {
    val messages = account.userProfile().privateChatrooms[withUser] ?: return emptyList()

    return messages.roomMessages.filter { account.isAcceptable(it) }.sortedBy { it.event?.createdAt }.reversed()
  }
}