package com.vitorpamplona.amethyst.service

import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import java.util.Collections
import nostr.postr.JsonFilter
import nostr.postr.events.MetadataEvent

object NostrSingleUserDataSource: NostrDataSource<User>("SingleUserFeed") {
  var usersToWatch = setOf<String>()

  fun createUserFilter(): List<JsonFilter>? {
    if (usersToWatch.isEmpty()) return null

    return usersToWatch.map {
      JsonFilter(
        kinds = listOf(MetadataEvent.kind),
        authors = listOf(it),
        limit = 1
      )
    }
  }

  val userChannel = requestNewChannel()

  override fun feed(): List<User> {
    return synchronized(usersToWatch) {
      usersToWatch.map {
        LocalCache.users[it]
      }.filterNotNull()
    }
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