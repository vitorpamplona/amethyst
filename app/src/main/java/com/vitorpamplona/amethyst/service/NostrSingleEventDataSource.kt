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
import java.util.Date
import nostr.postr.JsonFilter
import nostr.postr.events.MetadataEvent
import nostr.postr.events.TextNoteEvent

object NostrSingleEventDataSource: NostrDataSource<Note>("SingleEventFeed") {
  private var eventsToWatch = setOf<String>()

  private fun createRepliesAndReactionsFilter(): List<JsonFilter>? {
    val reactionsToWatch = eventsToWatch.map { LocalCache.getOrCreateNote(it) }

    if (reactionsToWatch.isEmpty()) {
      return null
    }

    val now = Date().time / 1000

    return reactionsToWatch.filter {
      val lastTime = it.lastReactionsDownloadTime;
      lastTime == null || lastTime < (now - 10)
    }.map {
      JsonFilter(
        kinds = listOf(
          TextNoteEvent.kind, ReactionEvent.kind, RepostEvent.kind, ReportEvent.kind
        ),
        tags = mapOf("e" to listOf(it.idHex)),
        since = it.lastReactionsDownloadTime
      )
    }
  }

  fun createLoadEventsIfNotLoadedFilter(): List<JsonFilter>? {
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
    return listOf(JsonFilter(
      kinds = listOf(
        TextNoteEvent.kind, ReactionEvent.kind, RepostEvent.kind,
        ChannelMessageEvent.kind, ChannelCreateEvent.kind, ChannelMetadataEvent.kind
      ),
      ids = interestedEvents.toList()
    ))
  }

  val singleEventChannel = requestNewChannel() { time ->
    eventsToWatch.forEach {
      LocalCache.getOrCreateNote(it).lastReactionsDownloadTime = time
    }
    // Many relays operate with limits in the amount of filters.
    // As information comes, the filters will be rotated to get more data.
    invalidateFilters()
  }

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

    singleEventChannel.filter = listOfNotNull(reactions, missing).flatten().ifEmpty { null }
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