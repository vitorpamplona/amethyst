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

import com.vitorpamplona.amethyst.commons.model.TopFilter
import com.vitorpamplona.amethyst.commons.model.cache.ICacheProvider
import com.vitorpamplona.amethyst.commons.model.location.LocationResult
import com.vitorpamplona.amethyst.commons.model.nip02FollowLists.Kind3FollowListState
import com.vitorpamplona.amethyst.commons.model.serverList.MergedFollowListsState
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.FeedDecryptionCaches
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.IFeedFlowsType
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.aroundMe.GeohashFeedFlow
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.chess.ChessFeedFlow
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.global.GlobalFeedFlow
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.hashtag.HashtagFeedFlow
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.noteBased.NoteFeedFlow
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.relay.RelayFeedFlow
import com.vitorpamplona.amethyst.model.topNavFeeds.allFollows.AllFollowsFeedFlow
import com.vitorpamplona.amethyst.model.topNavFeeds.allUserFollows.AllUserFollowsFeedFlow
import com.vitorpamplona.amethyst.model.topNavFeeds.allUserFollows.Kind3UserFollowsFeedFlow
import com.vitorpamplona.amethyst.model.topNavFeeds.aroundMe.AroundMeFeedFlow
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.normalizeRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import kotlinx.coroutines.flow.StateFlow

fun buildLoadFlowsFor(
    kind3Follows: StateFlow<Kind3FollowListState.Kind3Follows>,
    allFollows: StateFlow<MergedFollowListsState.AllFollows>,
    locationFlow: () -> StateFlow<LocationResult>,
    followsRelays: StateFlow<Set<NormalizedRelayUrl>>,
    blockedRelays: StateFlow<Set<NormalizedRelayUrl>>,
    proxyRelays: StateFlow<Set<NormalizedRelayUrl>>,
    relayFeeds: StateFlow<Set<NormalizedRelayUrl>>,
    caches: FeedDecryptionCaches,
    signer: NostrSigner,
    cache: ICacheProvider,
): (TopFilter) -> IFeedFlowsType =
    { listName ->
        when (listName) {
            TopFilter.Global -> {
                GlobalFeedFlow(followsRelays, proxyRelays, relayFeeds)
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
                AroundMeFeedFlow(locationFlow(), followsRelays, proxyRelays)
            }

            TopFilter.Chess -> {
                ChessFeedFlow(followsRelays, proxyRelays)
            }

            is TopFilter.Community -> {
                NoteFeedFlow(
                    cache
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
                    cache
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
                    cache
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
                GeohashFeedFlow(listName.tag, followsRelays, proxyRelays)
            }

            is TopFilter.Hashtag -> {
                HashtagFeedFlow(listName.tag, followsRelays, proxyRelays)
            }

            is TopFilter.Relay -> {
                RelayFeedFlow(listName.url.normalizeRelayUrl())
            }
        }
    }
