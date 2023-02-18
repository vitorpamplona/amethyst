package com.vitorpamplona.amethyst.ui.dal

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.LocalCacheState
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.model.UserState
import com.vitorpamplona.amethyst.service.model.RepostEvent
import com.vitorpamplona.amethyst.service.relays.FeedType
import com.vitorpamplona.amethyst.service.relays.TypedFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import nostr.postr.JsonFilter
import nostr.postr.events.TextNoteEvent
import nostr.postr.toHex

object HomeNewThreadFeedFilter: FeedFilter<Note>() {
  lateinit var account: Account

  override fun feed(): List<Note> {
    val user = account.userProfile()

    return LocalCache.notes.values
      .filter {
        (it.event is TextNoteEvent || it.event is RepostEvent)
          && it.author in user.follows
          && account.isAcceptable(it)
          && it.isNewThread()
      }
      .sortedBy { it.event?.createdAt }
      .reversed()
  }
}