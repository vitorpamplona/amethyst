package com.vitorpamplona.amethyst.service

import com.vitorpamplona.amethyst.model.UserInterface
import com.vitorpamplona.amethyst.service.model.MetadataEvent
import com.vitorpamplona.amethyst.service.model.ReportEvent
import com.vitorpamplona.amethyst.service.relays.FeedType
import com.vitorpamplona.amethyst.service.relays.JsonFilter
import com.vitorpamplona.amethyst.service.relays.TypedFilter

object NostrSingleUserDataSource : NostrDataSource("SingleUserFeed") {
    var usersToWatch = setOf<UserInterface>()

    fun createUserFilter(): List<TypedFilter>? {
        if (usersToWatch.isEmpty()) return null

        return usersToWatch.filter { it.info?.latestMetadata == null }.map {
            TypedFilter(
                types = FeedType.values().toSet(),
                filter = JsonFilter(
                    kinds = listOf(MetadataEvent.kind),
                    authors = listOf(it.pubkeyHex),
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
                    tags = mapOf("p" to listOf(it.pubkeyHex)),
                    since = it.latestReportTime
                )
            )
        }
    }

    val userChannel = requestNewChannel() {
        // Many relays operate with limits in the amount of filters.
        // As information comes, the filters will be rotated to get more data.
        invalidateFilters()
    }

    val userChannelOnce = requestNewChannel()

    override fun updateChannelFilters() {
        userChannel.typedFilters = listOfNotNull(createUserFilter()).flatten().ifEmpty { null }
        userChannelOnce.typedFilters = listOfNotNull(createUserReportFilter()).flatten().ifEmpty { null }
    }

    fun add(user: UserInterface) {
        usersToWatch = usersToWatch.plus(user)
        invalidateFilters()
    }

    fun remove(user: UserInterface) {
        usersToWatch = usersToWatch.minus(user)
        invalidateFilters()
    }
}
