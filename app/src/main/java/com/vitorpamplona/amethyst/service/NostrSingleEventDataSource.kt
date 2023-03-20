package com.vitorpamplona.amethyst.service

import com.vitorpamplona.amethyst.model.AddressableNote
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.service.model.*
import com.vitorpamplona.amethyst.service.relays.FeedType
import com.vitorpamplona.amethyst.service.relays.JsonFilter
import com.vitorpamplona.amethyst.service.relays.TypedFilter
import java.util.Date

object NostrSingleEventDataSource : NostrDataSource("SingleEventFeed") {
    private var eventsToWatch = setOf<Note>()
    private var addressesToWatch = setOf<Note>()

    private fun createTagToAddressFilter(): List<TypedFilter>? {
        val addressesToWatch = eventsToWatch.filter { it.address() != null } + addressesToWatch

        if (addressesToWatch.isEmpty()) {
            return null
        }

        val now = Date().time / 1000

        return addressesToWatch.filter {
            val lastTime = it.lastReactionsDownloadTime
            lastTime == null || lastTime < (now - 10)
        }.mapNotNull {
            it.address()?.let { aTag ->
                TypedFilter(
                    types = FeedType.values().toSet(),
                    filter = JsonFilter(
                        kinds = listOf(
                            TextNoteEvent.kind, LongTextNoteEvent.kind,
                            ReactionEvent.kind, RepostEvent.kind, ReportEvent.kind,
                            LnZapEvent.kind, LnZapRequestEvent.kind,
                            BadgeAwardEvent.kind, BadgeDefinitionEvent.kind, BadgeProfilesEvent.kind,
                            PollNoteEvent.kind
                        ),
                        tags = mapOf("a" to listOf(aTag.toTag())),
                        since = it.lastReactionsDownloadTime
                    )
                )
            }
        }
    }

    private fun createAddressFilter(): List<TypedFilter>? {
        val addressesToWatch = addressesToWatch.filter { it.event == null }

        if (addressesToWatch.isEmpty()) {
            return null
        }

        val now = Date().time / 1000

        return addressesToWatch.filter {
            val lastTime = it.lastReactionsDownloadTime
            lastTime == null || lastTime < (now - 10)
        }.mapNotNull {
            it.address()?.let { aTag ->
                TypedFilter(
                    types = FeedType.values().toSet(),
                    filter = JsonFilter(
                        kinds = listOf(aTag.kind),
                        tags = mapOf("d" to listOf(aTag.dTag)),
                        authors = listOf(aTag.pubKeyHex.substring(0, 8))
                    )
                )
            }
        }
    }

    private fun createRepliesAndReactionsFilter(): List<TypedFilter>? {
        val reactionsToWatch = eventsToWatch

        if (reactionsToWatch.isEmpty()) {
            return null
        }

        val now = Date().time / 1000

        return reactionsToWatch.filter {
            val lastTime = it.lastReactionsDownloadTime
            lastTime == null || lastTime < (now - 10)
        }.map {
            TypedFilter(
                types = FeedType.values().toSet(),
                filter = JsonFilter(
                    kinds = listOf(
                        TextNoteEvent.kind,
                        LongTextNoteEvent.kind,
                        ReactionEvent.kind,
                        RepostEvent.kind,
                        ReportEvent.kind,
                        LnZapEvent.kind,
                        LnZapRequestEvent.kind,
                        PollNoteEvent.kind
                    ),
                    tags = mapOf("e" to listOf(it.idHex)),
                    since = it.lastReactionsDownloadTime
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
                types = FeedType.values().toSet(),
                filter = JsonFilter(
                    kinds = listOf(
                        TextNoteEvent.kind, LongTextNoteEvent.kind, ReactionEvent.kind, RepostEvent.kind, LnZapEvent.kind, LnZapRequestEvent.kind,
                        ChannelMessageEvent.kind, ChannelCreateEvent.kind, ChannelMetadataEvent.kind, BadgeDefinitionEvent.kind, BadgeAwardEvent.kind, BadgeProfilesEvent.kind,
                        PollNoteEvent.kind
                    ),
                    ids = interestedEvents.toList()
                )
            )
        )
    }

    val singleEventChannel = requestNewChannel { time ->
        eventsToWatch.forEach {
            it.lastReactionsDownloadTime = time
        }
        // Many relays operate with limits in the amount of filters.
        // As information comes, the filters will be rotated to get more data.
        invalidateFilters()
    }

    override fun updateChannelFilters() {
        val reactions = createRepliesAndReactionsFilter()
        val missing = createLoadEventsIfNotLoadedFilter()
        val addresses = createAddressFilter()
        val addressReactions = createTagToAddressFilter()

        singleEventChannel.typedFilters = listOfNotNull(reactions, missing, addresses, addressReactions).flatten().ifEmpty { null }
    }

    fun add(eventId: Note) {
        eventsToWatch = eventsToWatch.plus(eventId)
        invalidateFilters()
    }

    fun remove(eventId: Note) {
        eventsToWatch = eventsToWatch.minus(eventId)
        invalidateFilters()
    }

    fun addAddress(aTag: Note) {
        addressesToWatch = addressesToWatch.plus(aTag)
        invalidateFilters()
    }

    fun removeAddress(aTag: Note) {
        addressesToWatch = addressesToWatch.minus(aTag)
        invalidateFilters()
    }
}
