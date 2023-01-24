package com.vitorpamplona.amethyst.service

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import nostr.postr.JsonFilter
import nostr.postr.events.TextNoteEvent

object NostrGlobalDataSource: NostrDataSource<Note>("GlobalFeed") {
  lateinit var account: Account
  fun createGlobalFilter() = JsonFilter(
    kinds = listOf(TextNoteEvent.kind),
    limit = 50
  )

  val globalFeedChannel = requestNewChannel()

  override fun feed() = LocalCache.notes.values
    .filter { account.isAcceptable(it) }
    .filter {
      it.event is TextNoteEvent && (it.event as TextNoteEvent).replyTos.isEmpty()
    }
    .sortedBy { it.event?.createdAt }
    .reversed()

  override fun updateChannelFilters() {
    globalFeedChannel.filter = listOf(createGlobalFilter()).ifEmpty { null }
  }
}