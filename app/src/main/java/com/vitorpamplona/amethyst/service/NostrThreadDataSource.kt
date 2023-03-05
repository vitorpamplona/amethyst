package com.vitorpamplona.amethyst.service

import com.vitorpamplona.amethyst.model.ThreadAssembler
import com.vitorpamplona.amethyst.service.relays.FeedType
import com.vitorpamplona.amethyst.service.relays.TypedFilter
import com.vitorpamplona.amethyst.service.relays.JsonFilter

object NostrThreadDataSource: NostrDataSource("SingleThreadFeed") {
  private var eventToWatch: String? = null

  fun createLoadEventsIfNotLoadedFilter(): TypedFilter? {
    val threadToLoad = eventToWatch ?: return null

    val eventsToLoad = ThreadAssembler().findThreadFor(threadToLoad)
      .filter { it.event == null }
      .map { it.idHex }
      .toSet()
      .ifEmpty { null } ?: return null

    return TypedFilter(
      types = FeedType.values().toSet(),
      filter = JsonFilter(
        ids = eventsToLoad.map { it.substring(0, 8) }
      )
    )
  }

  val loadEventsChannel = requestNewChannel(){
    // Many relays operate with limits in the amount of filters.
    // As information comes, the filters will be rotated to get more data.
    invalidateFilters()
  }

  override fun updateChannelFilters() {
    loadEventsChannel.typedFilters = listOfNotNull(createLoadEventsIfNotLoadedFilter()).ifEmpty { null }
  }

  fun loadThread(noteId: String?) {
    eventToWatch = noteId

    invalidateFilters()
  }
}