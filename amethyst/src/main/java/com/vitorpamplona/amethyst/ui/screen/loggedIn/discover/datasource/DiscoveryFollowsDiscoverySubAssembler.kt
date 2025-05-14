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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.discover.datasource

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.relayClient.eoseManagers.PerUserAndFollowListEoseManager
import com.vitorpamplona.amethyst.ui.screen.loggedIn.discover.nip23LongForm.subassemblies.filterLongFormByFollows
import com.vitorpamplona.amethyst.ui.screen.loggedIn.discover.nip28Chats.subassemblies.filterPublicChatsByFollows
import com.vitorpamplona.amethyst.ui.screen.loggedIn.discover.nip51FollowSets.subassemblies.filterFollowSetsByFollows
import com.vitorpamplona.amethyst.ui.screen.loggedIn.discover.nip53LiveActivities.subassemblies.filterLiveActivitiesByFollows
import com.vitorpamplona.amethyst.ui.screen.loggedIn.discover.nip72Communities.subassemblies.filterCommunitiesByFollows
import com.vitorpamplona.amethyst.ui.screen.loggedIn.discover.nip90DVMs.subassemblies.filterContentDVMsByFollows
import com.vitorpamplona.amethyst.ui.screen.loggedIn.discover.nip99Classifieds.subassemblies.filterClassifiedsByFollows
import com.vitorpamplona.ammolite.relays.NostrClient
import com.vitorpamplona.ammolite.relays.TypedFilter
import com.vitorpamplona.ammolite.relays.datasources.Subscription
import com.vitorpamplona.ammolite.relays.filters.EOSETime
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch
import kotlin.collections.flatten

class DiscoveryFollowsDiscoverySubAssembler(
    client: NostrClient,
    allKeys: () -> Set<DiscoveryQueryState>,
) : PerUserAndFollowListEoseManager<DiscoveryQueryState>(client, allKeys) {
    override fun updateFilter(
        key: DiscoveryQueryState,
        since: Map<String, EOSETime>?,
    ): List<TypedFilter>? = updateFilter(key.followsPerRelay(), key.followLists(), since)

    override fun user(key: DiscoveryQueryState) = key.account.userProfile()

    override fun list(key: DiscoveryQueryState) = key.listName()

    fun DiscoveryQueryState.listNameFlow() = account.settings.defaultDiscoveryFollowList

    fun DiscoveryQueryState.listName() = listNameFlow().value

    fun DiscoveryQueryState.followListsFlow() = account.liveDiscoveryFollowLists

    fun DiscoveryQueryState.followLists() = followListsFlow().value

    fun DiscoveryQueryState.followsPerRelayFlow() = account.liveDiscoveryListAuthorsPerRelay

    fun DiscoveryQueryState.followsPerRelay() = followsPerRelayFlow().value

    fun updateFilter(
        follows: Map<String, List<HexKey>>?,
        followLists: Account.LiveFollowList?,
        since: Map<String, EOSETime>?,
    ): List<TypedFilter>? {
        if (follows != null && follows.isEmpty()) return null

        return listOfNotNull(
            filterClassifiedsByFollows(follows, since),
            filterFollowSetsByFollows(follows, since),
            filterLongFormByFollows(follows, since),
            filterPublicChatsByFollows(follows, since),
            filterContentDVMsByFollows(follows, since),
            filterLiveActivitiesByFollows(follows, followLists?.authorsPlusMe, since),
            filterCommunitiesByFollows(follows, since),
        ).flatten()
    }

    val userJobMap = mutableMapOf<User, List<Job>>()

    @OptIn(FlowPreview::class)
    override fun newSub(key: DiscoveryQueryState): Subscription {
        val user = user(key)
        userJobMap[user]?.forEach { it.cancel() }
        userJobMap[user] =
            listOf(
                key.scope.launch(Dispatchers.Default) {
                    key.listNameFlow().collectLatest {
                        invalidateFilters()
                    }
                },
                key.scope.launch(Dispatchers.Default) {
                    key.followsPerRelayFlow().sample(5000).collectLatest {
                        invalidateFilters()
                    }
                },
            )

        return super.newSub(key)
    }

    override fun endSub(
        key: User,
        subId: String,
    ) {
        return super.endSub(key, subId)
        userJobMap[key]?.forEach { it.cancel() }
    }
}
