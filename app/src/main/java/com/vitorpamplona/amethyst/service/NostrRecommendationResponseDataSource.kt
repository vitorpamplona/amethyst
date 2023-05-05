package com.vitorpamplona.amethyst.service

import com.vitorpamplona.amethyst.service.model.RecommendationResponseEvent
import com.vitorpamplona.amethyst.service.relays.COMMON_FEED_TYPES
import com.vitorpamplona.amethyst.service.relays.JsonFilter
import com.vitorpamplona.amethyst.service.relays.TypedFilter

class NostrRecommendationResponseDataSource(
    private var fromServiceHex: String,
    private var toUserHex: String,
    private var replyingToHex: String
) : NostrDataSource("RecommendationResponseFeed") {

    val feedTypes = COMMON_FEED_TYPES

    private fun createRecommendationWatcher(): TypedFilter {
        // downloads all the reactions to a given event.
        return TypedFilter(
            types = feedTypes,
            filter = JsonFilter(
                kinds = listOf(RecommendationResponseEvent.kind),
                authors = listOf(fromServiceHex),
                tags = mapOf(
                    "e" to listOf(replyingToHex),
                    "p" to listOf(toUserHex)
                ),
                limit = 10
            )
        )
    }

    val channel = requestNewChannel()

    override fun updateChannelFilters() {
        channel.typedFilters = listOfNotNull(createRecommendationWatcher()).ifEmpty { null }
    }
}
