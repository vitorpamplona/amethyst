package com.vitorpamplona.amethyst.service

import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import java.util.Collections
import nostr.postr.JsonFilter

object NostrSingleEventDataSource: NostrDataSource("SingleEventFeed") {
  val eventsToWatch = Collections.synchronizedList(mutableListOf<String>())

  fun createRepliesAndReactionsFilter(): JsonFilter? {
    val reactionsToWatch = eventsToWatch.map { it.substring(0, 8) }

    if (reactionsToWatch.isEmpty()) {
      return null
    }

    return JsonFilter(
      tags = mapOf("e" to reactionsToWatch)
    )
  }

  fun createLoadEventsIfNotLoadedFilter(): JsonFilter? {
    val eventsToLoad = eventsToWatch
      .map { LocalCache.notes[it] }
      .filterNotNull()
      .filter { it.event == null }
      .map { it.idHex.substring(0, 8) }

    if (eventsToLoad.isEmpty()) {
      return null
    }

    return JsonFilter(
      ids = eventsToLoad
    )
  }

  val repliesAndReactionsChannel = requestNewChannel()
  val loadEventsChannel = requestNewChannel()

  override fun feed(): List<Note> {
    return eventsToWatch.map {
      LocalCache.notes[it]
    }.filterNotNull()
  }

  override fun updateChannelFilters() {
    repliesAndReactionsChannel.filter = createRepliesAndReactionsFilter()
    loadEventsChannel.filter = createLoadEventsIfNotLoadedFilter()
  }

  fun add(eventId: String) {
    eventsToWatch.add(eventId)
    resetFilters()
  }

  fun remove(eventId: String) {
    eventsToWatch.remove(eventId)
    resetFilters()
  }
}