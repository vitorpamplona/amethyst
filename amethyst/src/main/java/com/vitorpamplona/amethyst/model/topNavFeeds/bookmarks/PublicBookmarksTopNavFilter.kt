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

import androidx.compose.runtime.Immutable
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.topNavFeeds.IFeedTopNavFilter
import com.vitorpamplona.amethyst.model.topNavFeeds.OutboxRelayLoader
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine

@Immutable
class PublicBookmarksByOutboxTopNavFilter(
    val authors: Set<HexKey>,
    val snapshot: PublicBookmarkFeedSnapshot,
    val defaultRelays: StateFlow<Set<NormalizedRelayUrl>>,
    val blockedRelays: StateFlow<Set<NormalizedRelayUrl>>,
) : IFeedTopNavFilter {
    override fun matchAuthor(pubkey: HexKey): Boolean = false

    override fun match(noteEvent: Event): Boolean = snapshot.matches(noteEvent)

    override fun toPerRelayFlow(cache: LocalCache): Flow<PublicBookmarksTopNavPerRelayFilterSet> {
        val authorsPerRelay = OutboxRelayLoader().toAuthorsPerRelayFlow(authors, cache) { it }

        return combine(authorsPerRelay, defaultRelays, blockedRelays) { perRelayAuthors, default, blocked ->
            convert(perRelayAuthors.minus(blocked).ifEmpty { default.associateWith { authors } })
        }
    }

    override fun startValue(cache: LocalCache): PublicBookmarksTopNavPerRelayFilterSet {
        val authorsPerRelay = OutboxRelayLoader().authorsPerRelaySnapshot(authors, cache) { it }

        return convert(authorsPerRelay.minus(blockedRelays.value).ifEmpty { defaultRelays.value.associateWith { authors } })
    }

    private fun convert(authorsPerRelay: Map<NormalizedRelayUrl, Set<HexKey>>) =
        PublicBookmarksTopNavPerRelayFilterSet(
            authorsPerRelay.mapValues {
                PublicBookmarksTopNavPerRelayFilter(
                    bookmarkAuthors = it.value,
                    eventIds = snapshot.eventIds,
                    addresses = snapshot.addresses,
                )
            },
        )
}

@Immutable
class PublicBookmarksByProxyTopNavFilter(
    val authors: Set<HexKey>,
    val snapshot: PublicBookmarkFeedSnapshot,
    val proxyRelays: Set<NormalizedRelayUrl>,
) : IFeedTopNavFilter {
    override fun matchAuthor(pubkey: HexKey): Boolean = false

    override fun match(noteEvent: Event): Boolean = snapshot.matches(noteEvent)

    override fun toPerRelayFlow(cache: LocalCache): Flow<PublicBookmarksTopNavPerRelayFilterSet> = kotlinx.coroutines.flow.flowOf(startValue(cache))

    override fun startValue(cache: LocalCache): PublicBookmarksTopNavPerRelayFilterSet =
        PublicBookmarksTopNavPerRelayFilterSet(
            proxyRelays.associateWith {
                PublicBookmarksTopNavPerRelayFilter(
                    bookmarkAuthors = authors,
                    eventIds = snapshot.eventIds,
                    addresses = snapshot.addresses,
                )
            },
        )
}

@Immutable
class PublicBookmarksTopNavPerRelayFilterSet(
    val set: Map<NormalizedRelayUrl, PublicBookmarksTopNavPerRelayFilter>,
) : com.vitorpamplona.amethyst.model.topNavFeeds.IFeedTopNavPerRelayFilterSet

@Immutable
class PublicBookmarksTopNavPerRelayFilter(
    val bookmarkAuthors: Set<HexKey>,
    val eventIds: Set<HexKey>,
    val addresses: Set<com.vitorpamplona.quartz.nip01Core.core.Address>,
) : com.vitorpamplona.amethyst.model.topNavFeeds.IFeedTopNavPerRelayFilter
