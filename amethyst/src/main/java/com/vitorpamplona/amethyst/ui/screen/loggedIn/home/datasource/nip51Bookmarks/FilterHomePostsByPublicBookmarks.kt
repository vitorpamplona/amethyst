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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.home.datasource.nip51Bookmarks

import com.vitorpamplona.amethyst.model.topNavFeeds.bookmarks.PublicBookmarkListKinds
import com.vitorpamplona.amethyst.model.topNavFeeds.bookmarks.PublicBookmarksTopNavPerRelayFilterSet
import com.vitorpamplona.amethyst.service.relays.SincePerRelayMap
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl

private const val ID_CHUNK_SIZE = 200

fun filterHomePostsByPublicBookmarks(
    bookmarkSet: PublicBookmarksTopNavPerRelayFilterSet,
    since: SincePerRelayMap?,
): List<RelayBasedFilter> {
    if (bookmarkSet.set.isEmpty()) return emptyList()

    return bookmarkSet.set.flatMap { (relay, filter) ->
        val relaySince = since?.get(relay)?.time

        filterBookmarkLists(relay, filter.bookmarkAuthors, relaySince) +
            filterBookmarkEvents(relay, filter.eventIds) +
            filterBookmarkAddressables(relay, filter.addresses)
    }
}

private fun filterBookmarkLists(
    relay: NormalizedRelayUrl,
    authors: Set<String>,
    since: Long?,
): List<RelayBasedFilter> {
    if (authors.isEmpty()) return emptyList()

    return listOf(
        RelayBasedFilter(
            relay = relay,
            filter =
                Filter(
                    kinds = PublicBookmarkListKinds,
                    authors = authors.sorted(),
                    since = since,
                    limit = authors.size * PublicBookmarkListKinds.size,
                ),
        ),
    )
}

private fun filterBookmarkEvents(
    relay: NormalizedRelayUrl,
    ids: Set<String>,
): List<RelayBasedFilter> =
    ids
        .sorted()
        .chunked(ID_CHUNK_SIZE)
        .map {
            RelayBasedFilter(
                relay = relay,
                filter =
                    Filter(
                        ids = it,
                        limit = it.size,
                    ),
            )
        }

private fun filterBookmarkAddressables(
    relay: NormalizedRelayUrl,
    addresses: Set<Address>,
): List<RelayBasedFilter> =
    addresses.map { address ->
        RelayBasedFilter(
            relay = relay,
            filter =
                if (address.kind < 25000 && address.dTag.isBlank()) {
                    Filter(
                        kinds = listOf(address.kind),
                        authors = listOf(address.pubKeyHex),
                        limit = 1,
                    )
                } else {
                    Filter(
                        kinds = listOf(address.kind),
                        tags = mapOf("d" to listOf(address.dTag)),
                        authors = listOf(address.pubKeyHex),
                        limit = 1,
                    )
                },
        )
    }
