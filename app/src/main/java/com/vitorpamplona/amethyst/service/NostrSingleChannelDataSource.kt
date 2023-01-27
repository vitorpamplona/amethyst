package com.vitorpamplona.amethyst.service

import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.service.model.ChannelCreateEvent
import com.vitorpamplona.amethyst.service.model.ChannelMessageEvent
import com.vitorpamplona.amethyst.service.model.ChannelMetadataEvent
import com.vitorpamplona.amethyst.service.model.ReactionEvent
import com.vitorpamplona.amethyst.service.model.RepostEvent
import java.util.Collections
import nostr.postr.JsonFilter
import nostr.postr.events.TextNoteEvent

object NostrSingleChannelDataSource: NostrDataSource<Note>("SingleChannelFeed") {
  private var channelsToWatch = setOf<String>()

  private fun createRepliesAndReactionsFilter(): JsonFilter? {
    val reactionsToWatch = channelsToWatch.map { it }

    if (reactionsToWatch.isEmpty()) {
      return null
    }

    // downloads all the reactions to a given event.
    return JsonFilter(
      kinds = listOf(ChannelMetadataEvent.kind),
      tags = mapOf("e" to reactionsToWatch)
    )
  }

  fun createLoadEventsIfNotLoadedFilter(): JsonFilter? {
    val directEventsToLoad = channelsToWatch
      .map { LocalCache.getOrCreateChannel(it) }
      .filter { it.notes.isEmpty() }

    val interestedEvents = (directEventsToLoad).map { it.idHex }.toSet()

    if (interestedEvents.isEmpty()) {
      return null
    }

    // downloads linked events to this event.
    return JsonFilter(
      kinds = listOf(ChannelCreateEvent.kind),
      ids = interestedEvents.toList()
    )
  }

  val repliesAndReactionsChannel = requestNewChannel()
  val loadEventsChannel = requestNewChannel()

  override fun feed(): List<Note> {
    return emptyList()
  }

  override fun updateChannelFilters() {
    val reactions = createRepliesAndReactionsFilter()
    val missing = createLoadEventsIfNotLoadedFilter()

    repliesAndReactionsChannel.filter = listOfNotNull(reactions).ifEmpty { null }
    loadEventsChannel.filter = listOfNotNull(missing).ifEmpty { null }
  }

  fun add(eventId: String) {
    channelsToWatch = channelsToWatch.plus(eventId)
    invalidateFilters()
  }

  fun remove(eventId: String) {
    channelsToWatch = channelsToWatch.minus(eventId)
    invalidateFilters()
  }
}