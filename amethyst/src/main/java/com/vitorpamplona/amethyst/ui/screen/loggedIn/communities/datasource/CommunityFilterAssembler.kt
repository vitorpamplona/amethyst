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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.communities.datasource

import com.vitorpamplona.amethyst.model.AddressableNote
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.QueryBasedSubscriptionOrchestrator
import com.vitorpamplona.ammolite.relays.COMMON_FEED_TYPES
import com.vitorpamplona.ammolite.relays.NostrClient
import com.vitorpamplona.ammolite.relays.TypedFilter
import com.vitorpamplona.ammolite.relays.filters.SincePerRelayFilter
import com.vitorpamplona.quartz.nip72ModCommunities.approval.CommunityPostApprovalEvent
import com.vitorpamplona.quartz.nip72ModCommunities.definition.CommunityDefinitionEvent
import kotlin.collections.ifEmpty

// This allows multiple screen to be listening to tags, even the same tag
class CommunityQueryState(
    var community: AddressableNote,
)

class CommunityFilterAssembler(
    client: NostrClient,
) : QueryBasedSubscriptionOrchestrator<CommunityQueryState>(client) {
    private fun createLoadCommunityFilter(keys: Set<CommunityQueryState>): List<TypedFilter> {
        if (keys.isEmpty()) return emptyList()

        val uniqueCommunities =
            keys.associate {
                it.community.address.toValue() to it.community
            }

        return uniqueCommunities.mapNotNull {
            val commEvent = it.value.event
            if (commEvent is CommunityDefinitionEvent) {
                TypedFilter(
                    types = COMMON_FEED_TYPES,
                    filter =
                        SincePerRelayFilter(
                            authors = commEvent.moderators().map { it.pubKey }.plus(listOfNotNull(it.value.author?.pubkeyHex)),
                            tags = mapOf("a" to listOf(it.value.address.toValue())),
                            kinds = listOf(CommunityPostApprovalEvent.KIND),
                            limit = 500,
                        ),
                )
            } else {
                null
            }
        }
    }

    val loadCommunityChannel = requestNewSubscription()

    override fun updateSubscriptions(keys: Set<CommunityQueryState>) {
        loadCommunityChannel.typedFilters = createLoadCommunityFilter(keys).ifEmpty { null }
    }
}
