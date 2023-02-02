package com.vitorpamplona.amethyst.service

import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.service.model.ChannelCreateEvent
import com.vitorpamplona.amethyst.service.model.ChannelMessageEvent
import com.vitorpamplona.amethyst.service.model.ChannelMetadataEvent
import com.vitorpamplona.amethyst.service.model.ReactionEvent
import com.vitorpamplona.amethyst.service.model.ReportEvent
import com.vitorpamplona.amethyst.service.model.RepostEvent
import java.util.Collections
import nostr.postr.JsonFilter
import nostr.postr.events.TextNoteEvent

object NostrSingleEventDataSource: NostrDataSource<Note>("SingleEventFeed") {
  private var eventsToWatch = setOf<String>()

  private fun createRepliesAndReactionsFilter(): JsonFilter? {
    val reactionsToWatch = eventsToWatch.map { it }

    if (reactionsToWatch.isEmpty()) {
      return null
    }

    // downloads all the reactions to a given event.
    return JsonFilter(
      kinds = listOf(
        TextNoteEvent.kind, ReactionEvent.kind, RepostEvent.kind, ReportEvent.kind
      ),
      tags = mapOf("e" to reactionsToWatch)
    )
  }

  fun createLoadEventsIfNotLoadedFilter(): JsonFilter? {
    val directEventsToLoad = eventsToWatch
      .map { LocalCache.getOrCreateNote(it) }
      .filter { it.event == null }

    val threadingEventsToLoad = eventsToWatch
      .map { LocalCache.getOrCreateNote(it) }
      .mapNotNull { it.replyTo }
      .flatten()
      .filter { it.event == null }

    val interestedEvents =
      (directEventsToLoad + threadingEventsToLoad)
      .map { it.idHex }.toSet()

    if (interestedEvents.isEmpty()) {
      return null
    }

    // downloads linked events to this event.
    return JsonFilter(
      kinds = listOf(
        TextNoteEvent.kind, ReactionEvent.kind, RepostEvent.kind,
        ChannelMessageEvent.kind, ChannelCreateEvent.kind, ChannelMetadataEvent.kind
      ),
      ids = interestedEvents.toList()
    )
  }

  val singleEventChannel = requestNewChannel()

  override fun feed(): List<Note> {
    return synchronized(eventsToWatch) {
      eventsToWatch.map {
        LocalCache.notes[it]
      }.filterNotNull()
    }
  }

  override fun updateChannelFilters() {
    val reactions = createRepliesAndReactionsFilter()
    val missing = createLoadEventsIfNotLoadedFilter()

    singleEventChannel.filter = listOfNotNull(reactions, missing).ifEmpty { null }
  }

  fun add(eventId: String) {
    eventsToWatch = eventsToWatch.plus(eventId)
    invalidateFilters()
  }

  fun remove(eventId: String) {
    eventsToWatch = eventsToWatch.minus(eventId)
    invalidateFilters()
  }
}