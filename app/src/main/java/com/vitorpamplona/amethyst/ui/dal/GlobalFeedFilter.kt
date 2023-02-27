package com.vitorpamplona.amethyst.ui.dal

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.service.model.ChannelMessageEvent
import nostr.postr.events.TextNoteEvent

object GlobalFeedFilter: FeedFilter<Note>() {
  lateinit var account: Account

  override fun feed() = LocalCache.notes.values
    .filter { account.isAcceptable(it) }
    .filter {
      (it.event is TextNoteEvent && (it.event as TextNoteEvent).replyTos.isEmpty()) ||
      (it.event is ChannelMessageEvent && (it.event as ChannelMessageEvent).replyTos.isEmpty())
    }
    .sortedBy { it.event?.createdAt }
    .reversed()

}