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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.followPacks.feed.datasource

import com.vitorpamplona.amethyst.model.topNavFeeds.allUserFollows.AllUserFollowsByOutboxTopNavFilter
import com.vitorpamplona.amethyst.model.topNavFeeds.allUserFollows.AllUserFollowsByProxyTopNavFilter
import com.vitorpamplona.amethyst.service.relayClient.eoseManagers.SingleSubEoseManager
import com.vitorpamplona.amethyst.service.relays.SincePerRelayMap
import com.vitorpamplona.amethyst.ui.screen.loggedIn.home.datasource.nip65Follows.filterHomePostsByAuthors
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip51Lists.followList.FollowListEvent

class FollowPackFeedFilterSubAssembler(
    client: INostrClient,
    allKeys: () -> Set<FollowPackFeedQueryState>,
) : SingleSubEoseManager<FollowPackFeedQueryState>(client, allKeys) {
    override fun updateFilter(
        keys: List<FollowPackFeedQueryState>,
        since: SincePerRelayMap?,
    ): List<RelayBasedFilter> {
        if (keys.isEmpty()) return emptyList()
        return keys.flatMap {
            val followPack = it.followPack.event
            if (followPack is FollowListEvent) {
                val filter =
                    if (it.account.proxyRelayList.flow.value
                            .isEmpty()
                    ) {
                        AllUserFollowsByOutboxTopNavFilter(
                            authors = followPack.followIdSet(),
                            defaultRelays = it.account.defaultGlobalRelays.flow,
                            blockedRelays = it.account.blockedRelayList.flow,
                        ).startValue(it.account.cache)
                    } else {
                        AllUserFollowsByProxyTopNavFilter(
                            authors = followPack.followIdSet(),
                            proxyRelays = it.account.proxyRelayList.flow.value,
                        ).startValue(it.account.cache)
                    }

                filterHomePostsByAuthors(filter, since, null, null)
            } else {
                emptyList()
            }
        }
    }

    override fun distinct(key: FollowPackFeedQueryState) = key.followPack.idHex
}
