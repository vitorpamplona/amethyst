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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.home.datasource.nip72Communities

import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.relayClient.eoseManagers.PerUserEoseManager
import com.vitorpamplona.amethyst.ui.screen.loggedIn.home.datasource.HomeQueryState
import com.vitorpamplona.ammolite.relays.NostrClient
import com.vitorpamplona.ammolite.relays.TypedFilter
import com.vitorpamplona.ammolite.relays.datasources.Subscription
import com.vitorpamplona.ammolite.relays.filters.EOSETime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class CommunityEventsFilterSubAssembler(
    client: NostrClient,
    allKeys: () -> Set<HomeQueryState>,
) : PerUserEoseManager<HomeQueryState>(client, allKeys) {
    override fun updateFilter(
        key: HomeQueryState,
        since: Map<String, EOSETime>?,
    ): List<TypedFilter>? =
        filterHomePostsFromCommunities(
            key.followLists()?.addresses,
            since,
        )

    override fun user(query: HomeQueryState) = query.account.userProfile()

    fun HomeQueryState.followListsFlow() = account.liveHomeFollowLists

    fun HomeQueryState.followLists() = followListsFlow().value

    val userJobMap = mutableMapOf<User, Job>()

    override fun newSub(key: HomeQueryState): Subscription {
        val user = user(key)
        userJobMap[user]?.cancel()
        userJobMap[user] =
            key.scope.launch(Dispatchers.Default) {
                key.followListsFlow().collectLatest {
                    invalidateFilters()
                }
            }

        return super.newSub(key)
    }

    override fun endSub(
        key: User,
        subId: String,
    ) {
        return super.endSub(key, subId)
        userJobMap[key]?.cancel()
    }
}
