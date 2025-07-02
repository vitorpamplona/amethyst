/**
 * Copyright (c) 2025 Vitor Pamplona
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

import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.service.relayClient.eoseManagers.SingleSubEoseManager
import com.vitorpamplona.amethyst.service.relays.SincePerRelayMap
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip72ModCommunities.definition.CommunityDefinitionEvent
import kotlin.collections.ifEmpty

class CommunityFeedFilterSubAssembler(
    client: NostrClient,
    allKeys: () -> Set<CommunityQueryState>,
) : SingleSubEoseManager<CommunityQueryState>(client, allKeys) {
    override fun updateFilter(
        keys: List<CommunityQueryState>,
        since: SincePerRelayMap?,
    ): List<RelayBasedFilter> {
        if (keys.isEmpty()) return emptyList()

        return keys.flatMap {
            val commEvent = it.community.event
            if (commEvent is CommunityDefinitionEvent) {
                val relays =
                    commEvent.relayUrls().ifEmpty { null }
                        ?: LocalCache.relayHints.hintsForAddress(commEvent.addressTag()).ifEmpty { null }
                        ?: it.community.relayUrls()

                relays.toSet().map {
                    filterCommunityPosts(it, commEvent, since?.get(it)?.time)
                }
            } else {
                emptyList()
            }
        }
    }

    override fun distinct(key: CommunityQueryState) = key.community.idHex
}
