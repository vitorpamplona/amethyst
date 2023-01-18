package com.vitorpamplona.amethyst.service

import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import java.util.Collections
import nostr.postr.JsonFilter
import nostr.postr.events.MetadataEvent

object NostrSingleUserDataSource: NostrDataSource<Note>("SingleUserFeed") {
  var usersToWatch = setOf<String>()

  fun createUserFilter(): List<JsonFilter>? {
    if (usersToWatch.isEmpty()) return null

    return usersToWatch.map {
      JsonFilter(
        kinds = listOf(MetadataEvent.kind),
        authors = listOf(it.substring(0, 8)),
        limit = 1
      )
    }
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
    invalidateFilters()
  }

  fun remove(userId: String) {
    usersToWatch = usersToWatch.minus(userId)
    invalidateFilters()
  }
}