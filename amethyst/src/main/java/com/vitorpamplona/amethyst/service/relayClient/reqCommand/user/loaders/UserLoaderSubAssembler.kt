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
package com.vitorpamplona.amethyst.service.relayClient.reqCommand.user.loaders

import com.vitorpamplona.amethyst.model.DefaultIndexerRelayList
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.relayClient.eoseManagers.SingleSubNoEoseCacheEoseManager
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.user.UserFinderQueryState
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl

class UserLoaderSubAssembler(
    client: INostrClient,
    allKeys: () -> Set<UserFinderQueryState>,
) : SingleSubNoEoseCacheEoseManager<UserFinderQueryState>(client, allKeys, invalidateAfterEose = true) {
    override fun updateFilter(keys: List<UserFinderQueryState>): List<RelayBasedFilter>? {
        val firstTimers = mutableSetOf<User>()

        keys.forEach {
            if (it.user.latestMetadata == null) {
                firstTimers.add(it.user)
            } else {
                null
            }
        }

        val indexRelays = mutableSetOf<NormalizedRelayUrl>()
        val defaultRelays = mutableSetOf<NormalizedRelayUrl>()

        keys.mapTo(mutableSetOf()) { it.account }.forEach {
            indexRelays.addAll(
                it.indexerRelayList.flow.value
                    .ifEmpty { DefaultIndexerRelayList },
            )
            defaultRelays.addAll(it.followPlusAllMineWithSearch.flow.value)

            it.kind3FollowList.flow.value.authors.forEach {
                val user = LocalCache.getOrCreateUser(it)
                if (user.latestMetadata == null) {
                    firstTimers.add(user)
                } else {
                    null
                }
            }
        }

        if (firstTimers.isEmpty()) return null

        return filterFindUserMetadataForKey(firstTimers, indexRelays, defaultRelays)
    }

    override fun distinct(key: UserFinderQueryState) = key.user
}
