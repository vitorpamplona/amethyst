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
package com.vitorpamplona.amethyst.model.topNavFeeds

import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.TopFilter
import com.vitorpamplona.amethyst.model.nip02FollowLists.Kind3FollowListState
import com.vitorpamplona.amethyst.model.serverList.MergedFollowListsState
import com.vitorpamplona.amethyst.model.topNavFeeds.allFollows.AllFollowsFeedFlow
import com.vitorpamplona.amethyst.model.topNavFeeds.allUserFollows.AllUserFollowsFeedFlow
import com.vitorpamplona.amethyst.model.topNavFeeds.allUserFollows.Kind3UserFollowsFeedFlow
import com.vitorpamplona.amethyst.model.topNavFeeds.aroundMe.AroundMeFeedFlow
import com.vitorpamplona.amethyst.model.topNavFeeds.chess.ChessFeedFlow
import com.vitorpamplona.amethyst.model.topNavFeeds.global.GlobalFeedFlow
import com.vitorpamplona.amethyst.model.topNavFeeds.noteBased.NoteFeedFlow
import com.vitorpamplona.amethyst.model.topNavFeeds.unknown.UnknownFeedFlow
import com.vitorpamplona.amethyst.service.location.LocationState
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest

class FeedTopNavFilterState(
    val feedFilterListName: MutableStateFlow<TopFilter>,
    val kind3Follows: StateFlow<Kind3FollowListState.Kind3Follows>,
    val allFollows: StateFlow<MergedFollowListsState.AllFollows>,
    val locationFlow: StateFlow<LocationState.LocationResult>,
    val followsRelays: StateFlow<Set<NormalizedRelayUrl>>,
    val blockedRelays: StateFlow<Set<NormalizedRelayUrl>>,
    val proxyRelays: StateFlow<Set<NormalizedRelayUrl>>,
    val caches: FeedDecryptionCaches,
    val signer: NostrSigner,
    val scope: CoroutineScope,
) {
    fun loadFlowsFor(listName: TopFilter): IFeedFlowsType =
        when (listName) {
            TopFilter.Global -> {
                GlobalFeedFlow(followsRelays, proxyRelays)
            }

            TopFilter.AllFollows -> {
                AllFollowsFeedFlow(allFollows, followsRelays, blockedRelays, proxyRelays)
            }

            TopFilter.AllUserFollows -> {
                AllUserFollowsFeedFlow(allFollows, followsRelays, blockedRelays, proxyRelays)
            }

            TopFilter.DefaultFollows -> {
                Kind3UserFollowsFeedFlow(kind3Follows, followsRelays, blockedRelays, proxyRelays)
            }

            TopFilter.AroundMe -> {
                AroundMeFeedFlow(locationFlow, followsRelays, proxyRelays)
            }

            TopFilter.Chess -> {
                ChessFeedFlow(followsRelays, proxyRelays)
            }

            is TopFilter.Community -> {
                NoteFeedFlow(
                    LocalCache
                        .getOrCreateAddressableNote(listName.address)
                        .flow()
                        .metadata.stateFlow,
                    signer,
                    followsRelays,
                    blockedRelays,
                    proxyRelays,
                    caches,
                )
            }

            is TopFilter.PeopleList -> {
                NoteFeedFlow(
                    LocalCache
                        .getOrCreateAddressableNote(listName.address)
                        .flow()
                        .metadata.stateFlow,
                    signer,
                    followsRelays,
                    blockedRelays,
                    proxyRelays,
                    caches,
                )
            }

            is TopFilter.MuteList -> {
                NoteFeedFlow(
                    LocalCache
                        .getOrCreateAddressableNote(listName.address)
                        .flow()
                        .metadata.stateFlow,
                    signer,
                    followsRelays,
                    blockedRelays,
                    proxyRelays,
                    caches,
                )
            }

            is TopFilter.Geohash -> {
                UnknownFeedFlow(listName)
            }

            is TopFilter.Hashtag -> {
                UnknownFeedFlow(listName)
            }

            is TopFilter.Relay -> {
                UnknownFeedFlow(listName)
            }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    val flow: StateFlow<IFeedTopNavFilter> =
        feedFilterListName
            .transformLatest { listName ->
                emitAll(loadFlowsFor(listName).flow())
            }.onStart {
                loadFlowsFor(feedFilterListName.value).startValue(this)
            }.flowOn(Dispatchers.IO)
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                loadFlowsFor(feedFilterListName.value).startValue(),
            )
}
