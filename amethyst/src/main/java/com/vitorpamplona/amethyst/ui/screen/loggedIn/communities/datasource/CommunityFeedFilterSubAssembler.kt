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

import com.vitorpamplona.amethyst.service.relayClient.eoseManagers.SingleSubEoseManager
import com.vitorpamplona.ammolite.relays.NostrClient
import com.vitorpamplona.ammolite.relays.TypedFilter
import com.vitorpamplona.ammolite.relays.filters.EOSETime
import com.vitorpamplona.quartz.nip72ModCommunities.definition.CommunityDefinitionEvent

class CommunityFeedFilterSubAssembler(
    client: NostrClient,
    allKeys: () -> Set<CommunityQueryState>,
) : SingleSubEoseManager<CommunityQueryState>(client, allKeys) {
    override fun updateFilter(
        keys: List<CommunityQueryState>,
        since: Map<String, EOSETime>?,
    ): List<TypedFilter>? {
        if (keys.isEmpty()) return emptyList()

        return keys.mapNotNull {
            val commEvent = it.community.event
            if (commEvent is CommunityDefinitionEvent) {
                filterCommunityPosts(commEvent, since)
            } else {
                null
            }
        }
    }

    override fun distinct(key: CommunityQueryState) = key.community.idHex
}
