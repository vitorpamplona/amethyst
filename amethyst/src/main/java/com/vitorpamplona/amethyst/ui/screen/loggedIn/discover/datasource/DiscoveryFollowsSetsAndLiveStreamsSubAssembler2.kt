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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.discover.datasource

import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.service.relayClient.eoseManagers.PerUserAndFollowListEoseManager
import com.vitorpamplona.amethyst.service.relays.SincePerRelayMap
import com.vitorpamplona.amethyst.ui.screen.loggedIn.discover.nip51FollowSets.makeFollowSetsFilter
import com.vitorpamplona.amethyst.ui.screen.loggedIn.discover.nip53LiveActivities.makeLiveActivitiesFilter
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.client.subscriptions.Subscription
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch

class DiscoveryFollowsSetsAndLiveStreamsSubAssembler2(
    client: INostrClient,
    allKeys: () -> Set<DiscoveryQueryState>,
) : PerUserAndFollowListEoseManager<DiscoveryQueryState, String>(client, allKeys) {
    override fun updateFilter(
        key: DiscoveryQueryState,
        since: SincePerRelayMap?,
    ): List<RelayBasedFilter>? {
        val feedSettings = key.followsPerRelay()

        return makeFollowSetsFilter(feedSettings, since, key.feedStates.discoverFollowSets.lastNoteCreatedAtIfFilled()) +
            makeLiveActivitiesFilter(feedSettings, since, key.feedStates.discoverLive.lastNoteCreatedAtIfFilled())
    }

    override fun user(key: DiscoveryQueryState) = key.account.userProfile()

    override fun list(key: DiscoveryQueryState) = key.listName()

    fun DiscoveryQueryState.listNameFlow() = account.settings.defaultDiscoveryFollowList

    fun DiscoveryQueryState.listName() = listNameFlow().value

    fun DiscoveryQueryState.followsPerRelayFlow() = account.liveDiscoveryFollowListsPerRelay

    fun DiscoveryQueryState.followsPerRelay() = followsPerRelayFlow().value

    val userJobMap = mutableMapOf<User, List<Job>>()

    @OptIn(FlowPreview::class)
    override fun newSub(key: DiscoveryQueryState): Subscription {
        val user = user(key)
        userJobMap[user]?.forEach { it.cancel() }
        userJobMap[user] =
            listOf(
                key.scope.launch(Dispatchers.IO) {
                    key.listNameFlow().collectLatest {
                        invalidateFilters()
                    }
                },
                key.scope.launch(Dispatchers.IO) {
                    key.followsPerRelayFlow().sample(1000).collectLatest {
                        invalidateFilters()
                    }
                },
                key.account.scope.launch(Dispatchers.IO) {
                    combine(
                        key.feedStates.discoverFollowSets.lastNoteCreatedAtWhenFullyLoaded,
                        key.feedStates.discoverLive.lastNoteCreatedAtWhenFullyLoaded,
                    ) {
                        Any()
                    }.sample(5000).collectLatest {
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
        super.endSub(key, subId)
        userJobMap[key]?.forEach { it.cancel() }
    }
}
