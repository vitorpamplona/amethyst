/**
 * Copyright (c) 2024 Vitor Pamplona
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the
 * Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.vitorpamplona.amethyst.service

import com.vitorpamplona.amethyst.model.AddressableNote
import com.vitorpamplona.ammolite.relays.COMMON_FEED_TYPES
import com.vitorpamplona.ammolite.relays.TypedFilter
import com.vitorpamplona.ammolite.relays.filters.SincePerRelayFilter
import com.vitorpamplona.quartz.nip72ModCommunities.CommunityDefinitionEvent
import com.vitorpamplona.quartz.nip72ModCommunities.CommunityPostApprovalEvent

object NostrCommunityDataSource : AmethystNostrDataSource("SingleCommunityFeed") {
    private var communityToWatch: AddressableNote? = null

    private fun createLoadCommunityFilter(): TypedFilter? {
        val myCommunityToWatch = communityToWatch ?: return null

        val community = myCommunityToWatch.event as? CommunityDefinitionEvent ?: return null

        val authors =
            community
                .moderators()
                .map { it.key }
                .plus(listOfNotNull(myCommunityToWatch.author?.pubkeyHex))

        if (authors.isEmpty()) return null

        return TypedFilter(
            types = COMMON_FEED_TYPES,
            filter =
                SincePerRelayFilter(
                    authors = authors,
                    tags =
                        mapOf(
                            "a" to listOf(myCommunityToWatch.address.toTag()),
                        ),
                    kinds = listOf(CommunityPostApprovalEvent.KIND),
                    limit = 500,
                ),
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
