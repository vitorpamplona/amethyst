package com.vitorpamplona.amethyst.service

import com.vitorpamplona.amethyst.model.AddressableNote
import com.vitorpamplona.amethyst.service.model.CommunityDefinitionEvent
import com.vitorpamplona.amethyst.service.model.CommunityPostApprovalEvent
import com.vitorpamplona.amethyst.service.relays.COMMON_FEED_TYPES
import com.vitorpamplona.amethyst.service.relays.JsonFilter
import com.vitorpamplona.amethyst.service.relays.TypedFilter

object NostrCommunityDataSource : NostrDataSource("SingleCommunityFeed") {
    private var communityToWatch: AddressableNote? = null

    private fun createLoadCommunityFilter(): TypedFilter? {
        val myCommunityToWatch = communityToWatch ?: return null

        val community = myCommunityToWatch.event as? CommunityDefinitionEvent ?: return null

        return TypedFilter(
            types = COMMON_FEED_TYPES,
            filter = JsonFilter(
                authors = community.moderators().map { it.key }.plus(listOfNotNull(myCommunityToWatch.author?.pubkeyHex)),
                tags = mapOf(
                    "a" to listOf(myCommunityToWatch.address.toTag())
                ),
                kinds = listOf(CommunityPostApprovalEvent.kind),
                limit = 500
            )
        )
    }

    val loadCommunityChannel = requestNewChannel()

    override fun updateChannelFilters() {
        loadCommunityChannel.typedFilters = listOfNotNull(createLoadCommunityFilter()).ifEmpty { null }
    }

    fun loadCommunity(note: AddressableNote?) {
        communityToWatch = note
        invalidateFilters()
    }
}
