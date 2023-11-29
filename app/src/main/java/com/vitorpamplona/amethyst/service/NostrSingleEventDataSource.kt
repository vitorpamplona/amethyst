package com.vitorpamplona.amethyst.service

import com.vitorpamplona.amethyst.model.AddressableNote
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.relays.COMMON_FEED_TYPES
import com.vitorpamplona.amethyst.service.relays.EOSETime
import com.vitorpamplona.amethyst.service.relays.JsonFilter
import com.vitorpamplona.amethyst.service.relays.TypedFilter
import com.vitorpamplona.quartz.events.CommunityPostApprovalEvent
import com.vitorpamplona.quartz.events.GenericRepostEvent
import com.vitorpamplona.quartz.events.LiveActivitiesChatMessageEvent
import com.vitorpamplona.quartz.events.LnZapEvent
import com.vitorpamplona.quartz.events.PollNoteEvent
import com.vitorpamplona.quartz.events.ReactionEvent
import com.vitorpamplona.quartz.events.ReportEvent
import com.vitorpamplona.quartz.events.RepostEvent
import com.vitorpamplona.quartz.events.TextNoteEvent

object NostrSingleEventDataSource : NostrDataSource("SingleEventFeed") {
    private var eventsToWatch = setOf<Note>()
    private var addressesToWatch = setOf<Note>()

    private fun createReactionsToWatchInAddressFilter(): List<TypedFilter>? {
        val addressesToWatch = eventsToWatch.filter { it.address() != null } + addressesToWatch.filter { it.address() != null }

        if (addressesToWatch.isEmpty()) {
            return null
        }

        return groupByEOSEPresence(eventsToWatch).mapNotNull {
            TypedFilter(
                types = COMMON_FEED_TYPES,
                filter = JsonFilter(
                    kinds = listOf(
                        TextNoteEvent.kind,
                        ReactionEvent.kind,
                        RepostEvent.kind,
                        GenericRepostEvent.kind,
                        ReportEvent.kind,
                        LnZapEvent.kind,
                        PollNoteEvent.kind,
                        CommunityPostApprovalEvent.kind,
                        LiveActivitiesChatMessageEvent.kind
                    ),
                    tags = mapOf("a" to it.mapNotNull { it.address()?.toTag() }),
                    since = findMinimumEOSEs(it),
                    limit = 1000 // Max amount of "replies" to download on a specific event.
                )
            )
        }
    }

    private fun createAddressFilter(): List<TypedFilter>? {
        val addressesToWatch = addressesToWatch.filter { it.event == null }

        if (addressesToWatch.isEmpty()) {
            return null
        }

        return addressesToWatch.mapNotNull {
            it.address()?.let { aTag ->
                if (aTag.kind < 25000 && aTag.dTag.isBlank()) {
                    TypedFilter(
                        types = COMMON_FEED_TYPES,
                        filter = JsonFilter(
                            kinds = listOf(aTag.kind),
                            authors = listOf(aTag.pubKeyHex),
                            limit = 5
                        )
                    )
                } else {
                    TypedFilter(
                        types = COMMON_FEED_TYPES,
                        filter = JsonFilter(
                            kinds = listOf(aTag.kind),
                            tags = mapOf("d" to listOf(aTag.dTag)),
                            authors = listOf(aTag.pubKeyHex),
                            limit = 5
                        )
                    )
                }
            }
        }
    }

    private fun createRepliesAndReactionsFilter(): List<TypedFilter>? {
        if (eventsToWatch.isEmpty()) {
            return null
        }

        return groupByEOSEPresence(eventsToWatch).map {
            TypedFilter(
                types = COMMON_FEED_TYPES,
                filter = JsonFilter(
                    kinds = listOf(
                        TextNoteEvent.kind,
                        ReactionEvent.kind,
                        RepostEvent.kind,
                        GenericRepostEvent.kind,
                        ReportEvent.kind,
                        LnZapEvent.kind,
                        PollNoteEvent.kind
                    ),
                    tags = mapOf("e" to it.map { it.idHex }),
                    since = findMinimumEOSEs(it),
                    limit = 1000 // Max amount of "replies" to download on a specific event.
                )
            )
        }
    }

    fun createLoadEventsIfNotLoadedFilter(): List<TypedFilter>? {
        val directEventsToLoad = eventsToWatch
            .filter { it.event == null }

        val threadingEventsToLoad = eventsToWatch
            .mapNotNull { it.replyTo }
            .flatten()
            .filter { it !is AddressableNote && it.event == null }

        val interestedEvents =
            (directEventsToLoad + threadingEventsToLoad)
                .map { it.idHex }.toSet()

        if (interestedEvents.isEmpty()) {
            return null
        }

        // downloads linked events to this event.
        return listOf(
            TypedFilter(
                types = COMMON_FEED_TYPES,
                filter = JsonFilter(
                    ids = interestedEvents.toList()
                )
            )
        )
    }

    val singleEventChannel = requestNewChannel { time, relayUrl ->
        // Ignores EOSE if it is in the middle of a filter change.
        if (changingFilters.get()) return@requestNewChannel

        checkNotInMainThread()

        eventsToWatch.forEach {
            val eose = it.lastReactionsDownloadTime[relayUrl]
            if (eose == null) {
                it.lastReactionsDownloadTime = it.lastReactionsDownloadTime + Pair(relayUrl, EOSETime(time))
            } else {
                eose.time = time
            }
        }

        addressesToWatch.forEach {
            val eose = it.lastReactionsDownloadTime[relayUrl]
            if (eose == null) {
                it.lastReactionsDownloadTime = it.lastReactionsDownloadTime + Pair(relayUrl, EOSETime(time))
            } else {
                eose.time = time
            }
        }

        // Many relays operate with limits in the amount of filters.
        // As information comes, the filters will be rotated to get more data.
        invalidateFilters()
    }

    override fun updateChannelFilters() {
        val reactions = createRepliesAndReactionsFilter()
        val missing = createLoadEventsIfNotLoadedFilter()
        val addresses = createAddressFilter()
        val addressReactions = createReactionsToWatchInAddressFilter()

        singleEventChannel.typedFilters = listOfNotNull(missing, addresses, reactions, addressReactions).flatten().ifEmpty { null }
    }

    fun add(eventId: Note) {
        if (!eventsToWatch.contains(eventId)) {
            eventsToWatch = eventsToWatch.plus(eventId)
            invalidateFilters()
        }
    }

    fun remove(eventId: Note) {
        if (eventsToWatch.contains(eventId)) {
            eventsToWatch = eventsToWatch.minus(eventId)
            invalidateFilters()
        }
    }

    fun addAddress(addressableNote: Note) {
        if (!addressesToWatch.contains(addressableNote)) {
            addressesToWatch = addressesToWatch.plus(addressableNote)
            invalidateFilters()
        }
    }

    fun removeAddress(addressableNote: Note) {
        if (addressesToWatch.contains(addressableNote)) {
            addressesToWatch = addressesToWatch.minus(addressableNote)
            invalidateFilters()
        }
    }
}

fun groupByEOSEPresence(notes: Set<Note>): Collection<List<Note>> {
    return notes.groupBy { it.lastReactionsDownloadTime.keys.sorted().joinToString(",") }.values
}

fun groupByEOSEPresence(users: Iterable<User>): Collection<List<User>> {
    return users.groupBy { it.latestEOSEs.keys.sorted().joinToString(",") }.values
}

fun findMinimumEOSEs(notes: List<Note>): Map<String, EOSETime> {
    val minLatestEOSEs = mutableMapOf<String, EOSETime>()

    notes.forEach {
        it.lastReactionsDownloadTime.forEach {
            val minEose = minLatestEOSEs[it.key]
            if (minEose == null) {
                minLatestEOSEs.put(it.key, EOSETime(it.value.time))
            } else if (it.value.time < minEose.time) {
                minEose.time = it.value.time
            }
        }
    }

    return minLatestEOSEs
}

fun findMinimumEOSEsForUsers(users: List<User>): Map<String, EOSETime> {
    val minLatestEOSEs = mutableMapOf<String, EOSETime>()

    users.forEach {
        it.latestEOSEs.forEach {
            val minEose = minLatestEOSEs[it.key]
            if (minEose == null) {
                minLatestEOSEs.put(it.key, EOSETime(it.value.time))
            } else if (it.value.time < minEose.time) {
                minEose.time = it.value.time
            }
        }
    }

    return minLatestEOSEs
}
