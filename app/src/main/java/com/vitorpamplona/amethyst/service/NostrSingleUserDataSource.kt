package com.vitorpamplona.amethyst.service

import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.relays.COMMON_FEED_TYPES
import com.vitorpamplona.amethyst.service.relays.EOSETime
import com.vitorpamplona.amethyst.service.relays.JsonFilter
import com.vitorpamplona.amethyst.service.relays.TypedFilter
import com.vitorpamplona.quartz.events.MetadataEvent
import com.vitorpamplona.quartz.events.ReportEvent
import com.vitorpamplona.quartz.events.StatusEvent

object NostrSingleUserDataSource : NostrDataSource("SingleUserFeed") {
    var usersToWatch = setOf<User>()

    fun createUserMetadataFilter(): List<TypedFilter>? {
        if (usersToWatch.isEmpty()) return null

        val firstTimers = usersToWatch.filter { it.info?.latestMetadata == null }.map { it.pubkeyHex }

        if (firstTimers.isEmpty()) return null

        return listOf(
            TypedFilter(
                types = COMMON_FEED_TYPES,
                filter = JsonFilter(
                    kinds = listOf(MetadataEvent.kind),
                    authors = firstTimers
                )
            )
        )
    }

    fun createUserMetadataStatusReportFilter(): List<TypedFilter>? {
        if (usersToWatch.isEmpty()) return null

        val secondTimers = usersToWatch.filter { it.info?.latestMetadata != null }

        if (secondTimers.isEmpty()) return null

        return groupByEOSEPresence(secondTimers).map { group ->
            val groupIds = group.map { it.pubkeyHex }
            val minEOSEs = findMinimumEOSEsForUsers(group)
            listOf(
                TypedFilter(
                    types = COMMON_FEED_TYPES,
                    filter = JsonFilter(
                        kinds = listOf(MetadataEvent.kind, StatusEvent.kind),
                        authors = groupIds,
                        since = minEOSEs
                    )
                ),
                TypedFilter(
                    types = COMMON_FEED_TYPES,
                    filter = JsonFilter(
                        kinds = listOf(ReportEvent.kind),
                        tags = mapOf("p" to groupIds),
                        since = minEOSEs
                    )
                )
            )
        }.flatten()
    }

    val userChannel = requestNewChannel() { time, relayUrl ->
        checkNotInMainThread()

        usersToWatch.forEach {
            if (it.info?.latestMetadata != null) {
                val eose = it.latestEOSEs[relayUrl]
                if (eose == null) {
                    it.latestEOSEs = it.latestEOSEs + Pair(relayUrl, EOSETime(time))
                } else {
                    eose.time = time
                }
            }
        }
    }

    override fun updateChannelFilters() {
        checkNotInMainThread()

        userChannel.typedFilters = listOfNotNull(
            createUserMetadataFilter(),
            createUserMetadataStatusReportFilter()
        ).flatten().ifEmpty { null }
    }

    fun add(user: User) {
        if (!usersToWatch.contains(user)) {
            usersToWatch = usersToWatch.plus(user)
            invalidateFilters()
        }
    }

    fun remove(user: User) {
        if (usersToWatch.contains(user)) {
            usersToWatch = usersToWatch.minus(user)
            invalidateFilters()
        }
    }
}
