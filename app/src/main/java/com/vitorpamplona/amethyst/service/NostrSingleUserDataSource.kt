package com.vitorpamplona.amethyst.service

import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.model.ReportEvent
import com.vitorpamplona.amethyst.service.relays.FeedType
import com.vitorpamplona.amethyst.service.relays.TypedFilter
import nostr.postr.JsonFilter
import nostr.postr.events.MetadataEvent

object NostrSingleUserDataSource: NostrDataSource("SingleUserFeed") {
  var usersToWatch = setOf<String>()

  fun createUserFilter(): List<TypedFilter>? {
    if (usersToWatch.isEmpty()) return null

    return usersToWatch.filter { LocalCache.getOrCreateUser(it).latestMetadata == null }.map {
      TypedFilter(
        types = FeedType.values().toSet(),
        filter = JsonFilter(
          kinds = listOf(MetadataEvent.kind),
          authors = listOf(it),
          limit = 1
        )
      )
    }
  }

  fun createUserReportFilter(): List<TypedFilter>? {
    if (usersToWatch.isEmpty()) return null

    return usersToWatch.map {
      TypedFilter(
        types = FeedType.values().toSet(),
        filter = JsonFilter(
          kinds = listOf(ReportEvent.kind),
          tags = mapOf("p" to listOf(it)),
          since = LocalCache.users[it]?.latestReportTime
        )
      )
    }
  }

  val userChannel = requestNewChannel(){
    // Many relays operate with limits in the amount of filters.
    // As information comes, the filters will be rotated to get more data.
    invalidateFilters()
  }

  val userChannelOnce = requestNewChannel()

  override fun updateChannelFilters() {
    userChannel.typedFilters = listOfNotNull(createUserFilter()).flatten().ifEmpty { null }
    userChannelOnce.typedFilters = listOfNotNull(createUserReportFilter()).flatten().ifEmpty { null }
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