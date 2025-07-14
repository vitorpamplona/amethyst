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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.discover.nip51FollowSets.subassemblies

import com.vitorpamplona.amethyst.model.topNavFeeds.noteBased.allcommunities.AllCommunitiesTopNavPerRelayFilterSet
import com.vitorpamplona.amethyst.service.relays.SincePerRelayMap
import com.vitorpamplona.amethyst.ui.screen.loggedIn.discover.nip90DVMs.subassemblies.filterContentDVMsAllCommunities
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip51Lists.FollowListEvent
import com.vitorpamplona.quartz.nip72ModCommunities.approval.CommunityPostApprovalEvent

fun filterFollowSetsAllCommunities(
    relay: NormalizedRelayUrl,
    communities: Set<String>,
    since: Long? = null,
): List<RelayBasedFilter> {
    val communityList = communities.sorted()

    return listOf(
        // approved
        RelayBasedFilter(
            relay = relay,
            filter =
                Filter(
                    kinds = CommunityPostApprovalEvent.KIND_LIST,
                    tags =
                        mapOf(
                            "a" to communityList,
                            "k" to listOf(FollowListEvent.KIND.toString()),
                        ),
                    limit = 300,
                    since = since,
                ),
        ),
        // not approved
        RelayBasedFilter(
            relay = relay,
            filter =
                Filter(
                    tags = mapOf("k" to listOf("5300"), "a" to communityList),
                    kinds = listOf(FollowListEvent.KIND),
                    limit = 300,
                    since = since,
                ),
        ),
    )
}

fun filterFollowSetsByAllCommunities(
    communitySet: AllCommunitiesTopNavPerRelayFilterSet,
    since: SincePerRelayMap?,
): List<RelayBasedFilter> {
    if (communitySet.set.isEmpty()) return emptyList()

    return communitySet.set
        .mapNotNull {
            filterContentDVMsAllCommunities(
                relay = it.key,
                communities = it.value.communities,
                since = since?.get(it.key)?.time,
            )
        }.flatten()
}
