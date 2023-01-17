package com.vitorpamplona.amethyst.service

import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import java.util.Collections
import nostr.postr.JsonFilter
import nostr.postr.events.MetadataEvent

object NostrSingleUserDataSource: NostrDataSource<Note>("SingleUserFeed") {
  var usersToWatch = listOf<String>()

  fun createUserFilter(): JsonFilter? {
    if (usersToWatch.isEmpty()) return null

    return JsonFilter(
      kinds = listOf(MetadataEvent.kind),
      authors = usersToWatch.map { it.substring(0, 8) }
    )
  }

  val userChannel = requestNewChannel()

  override fun feed(): List<Note> {
    return usersToWatch.map {
      LocalCache.notes[it]
    }.filterNotNull()
  }

  override fun updateChannelFilters() {
    userChannel.filter = createUserFilter()
  }

  fun add(userId: String) {
    usersToWatch = usersToWatch.plus(userId)
    resetFilters()
  }

  fun remove(userId: String) {
    usersToWatch = usersToWatch.minus(userId)
    resetFilters()
  }
}