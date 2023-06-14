package com.vitorpamplona.amethyst.service

import com.vitorpamplona.amethyst.model.toHexKey
import com.vitorpamplona.amethyst.service.model.*
import com.vitorpamplona.amethyst.service.nip19.Nip19
import com.vitorpamplona.amethyst.service.relays.COMMON_FEED_TYPES
import com.vitorpamplona.amethyst.service.relays.FeedType
import com.vitorpamplona.amethyst.service.relays.JsonFilter
import com.vitorpamplona.amethyst.service.relays.TypedFilter
import fr.acinq.secp256k1.Hex

object NostrSearchEventOrUserDataSource : NostrDataSource("SingleEventFeed") {
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
