package com.vitorpamplona.amethyst.service

import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import nostr.postr.JsonFilter
import nostr.postr.events.TextNoteEvent

object NostrGlobalDataSource: NostrDataSource<Note>("GlobalFeed") {
  val fifteenMinutes = (60*15) // 15 mins

  fun createGlobalFilter() = JsonFilter(
      kinds = listOf(TextNoteEvent.kind),
      since = System.currentTimeMillis() / 1000 - fifteenMinutes
    )

  val globalFeedChannel = requestNewChannel()

  fun equalTime(list1:Long?, list2:Long?): Boolean {
    if (list1 == null && list2 == null) return true
    if (list1 == null) return false
    if (list2 == null) return false

    return Math.abs(list1 - list2) < (4*fifteenMinutes)
  }

  fun equalFilters(list1:JsonFilter?, list2:JsonFilter?): Boolean {
    if (list1 == null && list2 == null) return true
    if (list1 == null) return false
    if (list2 == null) return false

    return equalTime(list1.since, list2.since)
  }

  override fun feed() = LocalCache.notes.values
    .filter {
      it.event is TextNoteEvent && (it.event as TextNoteEvent).replyTos.isEmpty()
    }
    .sortedBy { it.event!!.createdAt }
    .reversed()

  override fun updateChannelFilters() {
    val newFilter = createGlobalFilter()

    if (!equalFilters(newFilter, globalFeedChannel.filter)) {
      globalFeedChannel.filter = newFilter
    }
  }
}