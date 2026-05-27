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
package com.vitorpamplona.amethyst.model.topNavFeeds.bookmarks

import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.nip02FollowLists.Kind3FollowListState
import com.vitorpamplona.amethyst.model.topNavFeeds.IFeedFlowsType
import com.vitorpamplona.amethyst.model.topNavFeeds.IFeedTopNavFilter
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.transformLatest

class PublicBookmarksFeedFlow(
    val kind3Follows: StateFlow<Kind3FollowListState.Kind3Follows>,
    val defaultRelays: StateFlow<Set<NormalizedRelayUrl>>,
    val blockedRelays: StateFlow<Set<NormalizedRelayUrl>>,
    val proxyRelays: StateFlow<Set<NormalizedRelayUrl>>,
    val cache: LocalCache,
) : IFeedFlowsType {
    fun convert(
        authors: Set<HexKey>,
        proxyRelays: Set<NormalizedRelayUrl>,
        snapshot: PublicBookmarkFeedSnapshot,
    ): IFeedTopNavFilter =
        if (proxyRelays.isEmpty()) {
            PublicBookmarksByOutboxTopNavFilter(
                authors = authors,
                snapshot = snapshot,
                defaultRelays = defaultRelays,
                blockedRelays = blockedRelays,
            )
        } else {
            PublicBookmarksByProxyTopNavFilter(
                authors = authors,
                snapshot = snapshot,
                proxyRelays = proxyRelays,
            )
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun flow() =
        combine(kind3Follows, proxyRelays) { follows, proxyRelays ->
            follows.authors to proxyRelays
        }.transformLatest { (authors, proxyRelays) ->
            publicBookmarkSnapshotFlow(authors, cache).collect { snapshot ->
                emit(convert(authors, proxyRelays, snapshot))
            }
        }

    override fun startValue(): IFeedTopNavFilter =
        convert(
            authors = kind3Follows.value.authors,
            proxyRelays = proxyRelays.value,
            snapshot = publicBookmarkSnapshotForAuthors(kind3Follows.value.authors, cache),
        )

    override suspend fun startValue(collector: FlowCollector<IFeedTopNavFilter>) {
        collector.emit(startValue())
    }
}
