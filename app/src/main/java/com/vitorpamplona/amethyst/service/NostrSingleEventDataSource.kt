package com.vitorpamplona.amethyst.service

import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.service.model.ReactionEvent
import com.vitorpamplona.amethyst.service.model.RepostEvent
import java.util.Collections
import nostr.postr.JsonFilter
import nostr.postr.events.TextNoteEvent

object NostrSingleEventDataSource: NostrDataSource<Note>("SingleEventFeed") {
  private var eventsToWatch = setOf<String>()

  private fun createRepliesAndReactionsFilter(): JsonFilter? {
    val reactionsToWatch = eventsToWatch.map { it.substring(0, 8) }

    if (reactionsToWatch.isEmpty()) {
      return null
    }

    // downloads all the reactions to a given event.
    return JsonFilter(
      kinds = listOf(TextNoteEvent.kind, ReactionEvent.kind, RepostEvent.kind),
      tags = mapOf("e" to reactionsToWatch)
    )
  }

  fun createLoadEventsIfNotLoadedFilter(): JsonFilter? {
    val directEventsToLoad = eventsToWatch
      .mapNotNull { LocalCache.notes[it] }
      .filter { it.event == null }

    val threadingEventsToLoad = eventsToWatch
      .mapNotNull { LocalCache.notes[it] }
      .mapNotNull { it.replyTo }
      .flatten()
      .filter { it.event == null }

    val interestedEvents =
      (directEventsToLoad + threadingEventsToLoad)
      .map { it.idHex.substring(0, 8) }

    if (interestedEvents.isEmpty()) {
      return null
    }

    // downloads linked events to this event.
    return JsonFilter(
      kinds = listOf(TextNoteEvent.kind, ReactionEvent.kind, RepostEvent.kind),
      ids = interestedEvents
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
    val reactions = createRepliesAndReactionsFilter()
    val missing = createLoadEventsIfNotLoadedFilter()

    repliesAndReactionsChannel.filter = listOfNotNull(reactions).ifEmpty { null }
    loadEventsChannel.filter = listOfNotNull(missing).ifEmpty { null }
  }

  fun add(eventId: String) {
    eventsToWatch = eventsToWatch.plus(eventId)
    resetFilters()
  }

  fun remove(eventId: String) {
    eventsToWatch = eventsToWatch.minus(eventId)
    resetFilters()
  }
}