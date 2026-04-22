/*
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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.audiorooms.datasource

import com.vitorpamplona.amethyst.model.TopFilter
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.relayClient.eoseManagers.PerUserAndFollowListEoseManager
import com.vitorpamplona.amethyst.service.relays.SincePerRelayMap
import com.vitorpamplona.amethyst.ui.screen.loggedIn.discover.nip53LiveActivities.makeLiveActivitiesFilter
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.client.subscriptions.Subscription
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch

/**
 * Reuses `makeLiveActivitiesFilter`, which already subscribes to kinds
 * 30311/30312/30313/1311. The feed filter narrows to 30312/30313 client-side;
 * sharing the wire filter avoids duplicate REQs on relays when both the Live
 * Streams and Audio Rooms screens are open for the same user.
 */
class AudioRoomsSubAssembler(
    client: INostrClient,
    allKeys: () -> Set<AudioRoomsQueryState>,
) : PerUserAndFollowListEoseManager<AudioRoomsQueryState, TopFilter>(client, allKeys) {
    override fun updateFilter(
        key: AudioRoomsQueryState,
        since: SincePerRelayMap?,
    ): List<RelayBasedFilter> {
        val feedSettings = key.followsPerRelay()
        return makeLiveActivitiesFilter(feedSettings, since, key.feedStates.audioRoomsFeed.lastNoteCreatedAtIfFilled())
    }

    override fun user(key: AudioRoomsQueryState) = key.account.userProfile()

    override fun list(key: AudioRoomsQueryState) = key.listName()

    fun AudioRoomsQueryState.listNameFlow() = account.settings.defaultLiveStreamsFollowList

    fun AudioRoomsQueryState.listName() = listNameFlow().value

    fun AudioRoomsQueryState.followsPerRelayFlow() = account.liveLiveStreamsFollowListsPerRelay

    fun AudioRoomsQueryState.followsPerRelay() = followsPerRelayFlow().value

    private val userJobMap = mutableMapOf<User, List<Job>>()

    @OptIn(FlowPreview::class)
    override fun newSub(key: AudioRoomsQueryState): Subscription {
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
                    key.followsPerRelayFlow().sample(500).collectLatest {
                        invalidateFilters()
                    }
                },
                key.account.scope.launch(Dispatchers.IO) {
                    key.feedStates.audioRoomsFeed.lastNoteCreatedAtWhenFullyLoaded.sample(5000).collectLatest {
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
