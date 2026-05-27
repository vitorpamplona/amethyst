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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.music.datasource

import com.vitorpamplona.amethyst.model.TopFilter
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.relayClient.eoseManagers.PerUserAndFollowListEoseManager
import com.vitorpamplona.amethyst.service.relays.SincePerRelayMap
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.client.subscriptions.Subscription
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch

class MusicTracksSubAssembler(
    client: INostrClient,
    allKeys: () -> Set<MusicTracksQueryState>,
) : PerUserAndFollowListEoseManager<MusicTracksQueryState, TopFilter>(client, allKeys) {
    override fun updateFilter(
        key: MusicTracksQueryState,
        since: SincePerRelayMap?,
    ): List<RelayBasedFilter> {
        val feedSettings = key.followsPerRelay()
        // One REQ asks both kinds (36787 + 34139), so the `since` cursor must cover both
        // feeds — pick the older of the two `lastNoteCreatedAt`s so neither feed misses
        // older events. If either side hasn't paged yet (null), the union is null too.
        val defaultSince =
            listOfNotNull(
                key.feedStates.musicTracksFeed.lastNoteCreatedAtIfFilled(),
                key.feedStates.musicPlaylistsFeed.lastNoteCreatedAtIfFilled(),
            ).minOrNull()
        return makeMusicTracksFilter(feedSettings, since, defaultSince)
    }

    override fun user(key: MusicTracksQueryState) = key.account.userProfile()

    override fun list(key: MusicTracksQueryState) = key.listName()

    fun MusicTracksQueryState.listNameFlow() = account.settings.defaultMusicTracksFollowList

    fun MusicTracksQueryState.listName() = listNameFlow().value

    fun MusicTracksQueryState.followsPerRelayFlow() = account.liveMusicTracksFollowListsPerRelay

    fun MusicTracksQueryState.followsPerRelay() = followsPerRelayFlow().value

    private val userJobMap = mutableMapOf<User, List<Job>>()

    @OptIn(FlowPreview::class)
    override fun newSub(key: MusicTracksQueryState): Subscription {
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
                    key.feedStates.musicTracksFeed.lastNoteCreatedAtWhenFullyLoaded.sample(5000).collectLatest {
                        invalidateFilters()
                    }
                },
                key.account.scope.launch(Dispatchers.IO) {
                    // The REQ also asks for playlists (kind 34139), so pagination needs to
                    // listen to the playlists feed's cursor as well — otherwise an older
                    // playlist that should be fetched stays out of view.
                    key.feedStates.musicPlaylistsFeed.lastNoteCreatedAtWhenFullyLoaded.sample(5000).collectLatest {
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
