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

import com.vitorpamplona.amethyst.model.ALL_FOLLOWS
import com.vitorpamplona.amethyst.model.ALL_USER_FOLLOWS
import com.vitorpamplona.amethyst.model.AROUND_ME
import com.vitorpamplona.amethyst.model.CHESS
import com.vitorpamplona.amethyst.model.GLOBAL_FOLLOWS
import com.vitorpamplona.amethyst.model.KIND3_FOLLOWS
import com.vitorpamplona.amethyst.model.LocalCache
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
    val feedFilterListName: MutableStateFlow<String>,
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
    fun loadFlowsFor(listName: String): IFeedFlowsType =
        when (listName) {
            GLOBAL_FOLLOWS -> {
                GlobalFeedFlow(followsRelays, proxyRelays)
            }

            ALL_FOLLOWS -> {
                AllFollowsFeedFlow(allFollows, followsRelays, blockedRelays, proxyRelays)
            }

            ALL_USER_FOLLOWS -> {
                AllUserFollowsFeedFlow(allFollows, followsRelays, blockedRelays, proxyRelays)
            }

            KIND3_FOLLOWS -> {
                Kind3UserFollowsFeedFlow(kind3Follows, followsRelays, blockedRelays, proxyRelays)
            }

            AROUND_ME -> {
                AroundMeFeedFlow(locationFlow, followsRelays, proxyRelays)
            }

            CHESS -> {
                ChessFeedFlow(followsRelays, proxyRelays)
            }

            else -> {
                val note = LocalCache.checkGetOrCreateAddressableNote(listName)
                if (note != null) {
                    NoteFeedFlow(note.flow().metadata.stateFlow, signer, followsRelays, blockedRelays, proxyRelays, caches)
                } else {
                    UnknownFeedFlow(listName)
                }
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
