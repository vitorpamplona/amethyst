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

/**
 * Mirror of [MusicTracksSubAssembler] but keyed on the user's *playlists* follow list
 * ([com.vitorpamplona.amethyst.model.AccountSettings.defaultMusicPlaylistsFollowList]).
 * The playlists screen exposes its own top-bar spinner, and the previous setup reused the
 * tracks subscription — so if the user picked, e.g., Global for playlists but Following for
 * tracks, the REQ only asked Following's authors and the playlists feed showed empty.
 *
 * Asks for kind 34139 only — tracks referenced by playlists are loaded on demand by each
 * playlist row's own per-track observer (see MusicPlaylist.kt), not bundled into this REQ.
 */
class MusicPlaylistsSubAssembler(
    client: INostrClient,
    allKeys: () -> Set<MusicPlaylistsQueryState>,
) : PerUserAndFollowListEoseManager<MusicPlaylistsQueryState, TopFilter>(client, allKeys) {
    override fun updateFilter(
        key: MusicPlaylistsQueryState,
        since: SincePerRelayMap?,
    ): List<RelayBasedFilter> {
        // "Mine" needs no special-case: the shared TopFilter.Mine flow now resolves to an author
        // filter scoped to the user, so followsPerRelay() already carries authors=[me].
        val feedSettings = key.followsPerRelay()
        // REQ now only asks for kind 34139 (playlists), keyed to this screen's follow
        // list selector — no cross-feed cursor min needed.
        return makeMusicPlaylistsFilter(feedSettings, since, key.feedStates.musicPlaylistsFeed.lastNoteCreatedAtIfFilled())
    }

    override fun user(key: MusicPlaylistsQueryState) = key.account.userProfile()

    override fun list(key: MusicPlaylistsQueryState) = key.listName()

    fun MusicPlaylistsQueryState.listNameFlow() = account.settings.defaultMusicPlaylistsFollowList

    fun MusicPlaylistsQueryState.listName() = listNameFlow().value

    fun MusicPlaylistsQueryState.followsPerRelayFlow() = account.liveMusicPlaylistsFollowListsPerRelay

    fun MusicPlaylistsQueryState.followsPerRelay() = followsPerRelayFlow().value

    private val userJobMap = mutableMapOf<User, List<Job>>()

    @OptIn(FlowPreview::class)
    override fun newSub(key: MusicPlaylistsQueryState): Subscription {
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
