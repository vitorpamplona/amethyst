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

        return listOf(
            TypedFilter(
                types = COMMON_FEED_TYPES,
                filter = JsonFilter(
                    kinds = listOf(MetadataEvent.kind, StatusEvent.kind),
                    authors = firstTimers,
                    limit = 10 * firstTimers.size
                )
            )
        )
    }

    fun createUserMetadataFilter(minLatestEOSEs: Map<String, EOSETime>): TypedFilter? {
        if (usersToWatch.isEmpty()) return null

        return TypedFilter(
            types = COMMON_FEED_TYPES,
            filter = JsonFilter(
                kinds = listOf(StatusEvent.kind),
                authors = usersToWatch.map { it.pubkeyHex },
                since = minLatestEOSEs
            )
        )
    }

    fun createUserStatusFilter(minLatestEOSEs: Map<String, EOSETime>): TypedFilter? {
        if (usersToWatch.isEmpty()) return null

        return TypedFilter(
            types = COMMON_FEED_TYPES,
            filter = JsonFilter(
                kinds = listOf(StatusEvent.kind),
                authors = usersToWatch.map { it.pubkeyHex },
                since = minLatestEOSEs
            )
        )
    }

    fun createUserReportFilter(minLatestEOSEs: Map<String, EOSETime>): TypedFilter? {
        if (usersToWatch.isEmpty()) return null

        return TypedFilter(
            types = COMMON_FEED_TYPES,
            filter = JsonFilter(
                kinds = listOf(ReportEvent.kind),
                tags = mapOf("p" to usersToWatch.map { it.pubkeyHex }),
                since = minLatestEOSEs
            )
        )
    }

    val userChannel = requestNewChannel() { time, relayUrl ->
        usersToWatch.forEach {
            val eose = it.latestEOSEs[relayUrl]
            if (eose == null) {
                it.latestEOSEs = it.latestEOSEs + Pair(relayUrl, EOSETime(time))
            } else {
                eose.time = time
            }
        }
    }

    val userChannelFirstTimers = requestNewChannel() { time, relayUrl ->
        // Many relays operate with limits in the amount of filters.
        // As information comes, the filters will be rotated to get more data.
        invalidateFilters()
    }

    override fun updateChannelFilters() {
        val minLatestEOSEs = mutableMapOf<String, EOSETime>()
        val neverGottenAnEOSE = mutableSetOf<String>()
        usersToWatch.forEach {
            if (it.latestEOSEs.isEmpty()) { // first time
                neverGottenAnEOSE.add(it.pubkeyHex)
            } else {
                it.latestEOSEs.forEach {
                    val minEose = minLatestEOSEs[it.key]
                    if (minEose == null) {
                        minLatestEOSEs.put(it.key, EOSETime(it.value.time))
                    } else if (it.value.time < minEose.time) {
                        minEose.time = it.value.time
                    }
                }
            }
        }

        userChannel.typedFilters = listOfNotNull(
            createUserMetadataFilter(minLatestEOSEs),
            createUserStatusFilter(minLatestEOSEs),
            createUserReportFilter(minLatestEOSEs)
        ).ifEmpty { null }
        userChannelFirstTimers.typedFilters = listOfNotNull(
            createUserMetadataFilter()
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
