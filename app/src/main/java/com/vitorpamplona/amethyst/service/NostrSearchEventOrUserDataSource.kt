package com.vitorpamplona.amethyst.service

import com.vitorpamplona.amethyst.service.relays.COMMON_FEED_TYPES
import com.vitorpamplona.amethyst.service.relays.FeedType
import com.vitorpamplona.amethyst.service.relays.JsonFilter
import com.vitorpamplona.amethyst.service.relays.TypedFilter
import com.vitorpamplona.quartz.encoders.Hex
import com.vitorpamplona.quartz.encoders.Nip19
import com.vitorpamplona.quartz.encoders.toHexKey
import com.vitorpamplona.quartz.events.*

object NostrSearchEventOrUserDataSource : NostrDataSource("SearchEventFeed") {
    private var searchString: String? = null

    private fun createAnythingWithIDFilter(): List<TypedFilter>? {
        val mySearchString = searchString
        if (mySearchString.isNullOrBlank()) {
            return null
        }

        val hexToWatch = try {
            Nip19.uriToRoute(mySearchString)?.hex ?: Hex.decode(mySearchString).toHexKey()
        } catch (e: Exception) {
            null
        }

        // downloads all the reactions to a given event.
        return listOfNotNull(
            hexToWatch?.let {
                TypedFilter(
                    types = COMMON_FEED_TYPES,
                    filter = JsonFilter(
                        ids = listOfNotNull(hexToWatch)
                    )
                )
            },
            hexToWatch?.let {
                TypedFilter(
                    types = COMMON_FEED_TYPES,
                    filter = JsonFilter(
                        kinds = listOf(MetadataEvent.kind),
                        authors = listOfNotNull(hexToWatch)
                    )
                )
            },
            TypedFilter(
                types = setOf(FeedType.SEARCH),
                filter = JsonFilter(
                    kinds = listOf(MetadataEvent.kind),
                    search = mySearchString,
                    limit = 100
                )
            ),
            TypedFilter(
                types = setOf(FeedType.SEARCH),
                filter = JsonFilter(
                    kinds = listOf(
                        TextNoteEvent.kind, LongTextNoteEvent.kind, BadgeDefinitionEvent.kind,
                        PeopleListEvent.kind, BookmarkListEvent.kind, AudioTrackEvent.kind, PinListEvent.kind,
                        PollNoteEvent.kind, ChannelCreateEvent.kind
                    ),
                    search = mySearchString,
                    limit = 100
                )
            ),
            TypedFilter(
                types = setOf(FeedType.SEARCH),
                filter = JsonFilter(
                    kinds = listOf(
                        ChannelMetadataEvent.kind,
                        ClassifiedsEvent.kind,
                        CommunityDefinitionEvent.kind,
                        EmojiPackEvent.kind,
                        HighlightEvent.kind,
                        LiveActivitiesEvent.kind,
                        PollNoteEvent.kind,
                        NNSEvent.kind
                    ),
                    search = mySearchString,
                    limit = 100
                )
            )
        )
    }

    val searchChannel = requestNewChannel()

    override fun updateChannelFilters() {
        searchChannel.typedFilters = createAnythingWithIDFilter()
    }

    fun search(searchString: String) {
        this.searchString = searchString
        invalidateFilters()
    }

    fun clear() {
        searchString = null
    }
}
