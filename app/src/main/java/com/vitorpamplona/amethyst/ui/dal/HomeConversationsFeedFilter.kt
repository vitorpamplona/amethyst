package com.vitorpamplona.amethyst.ui.dal

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.service.model.RepostEvent
import nostr.postr.events.TextNoteEvent

object HomeConversationsFeedFilter: FeedFilter<Note>() {
  lateinit var account: Account

  override fun feed(): List<Note> {
    val user = account.userProfile()

    return LocalCache.notes.values
      .filter {
        (it.event is TextNoteEvent || it.event is RepostEvent)
          && it.author in user.follows
          && account.isAcceptable(it)
          && !it.isNewThread()
      }
      .sortedBy { it.event?.createdAt }
      .reversed()
  }
}