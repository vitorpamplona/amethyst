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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.home.datasource.nip90Dvms

import com.vitorpamplona.amethyst.model.topNavFeeds.favoriteDvm.FavoriteDvmTopNavPerRelayFilter
import com.vitorpamplona.amethyst.model.topNavFeeds.favoriteDvm.FavoriteDvmTopNavPerRelayFilterSet
import com.vitorpamplona.amethyst.service.relays.SincePerRelayMap
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip90Dvms.contentDiscoveryResponse.NIP90ContentDiscoveryResponseEvent
import com.vitorpamplona.quartz.nip90Dvms.status.NIP90StatusEvent

/**
 * Builds relay REQ filters for a favourite-DVM home feed.
 *
 * Two distinct subscription kinds with two distinct relay sets:
 *
 * - **Content fetch** — for each of the user's outbox/proxy relays, request the
 *   note IDs and addressable references the DVM curated. Notes typically live on
 *   the user's normal relays, so this is where we fetch them.
 *
 * - **Response listen** — for each relay the DVM advertised (where it received
 *   the kind-5300 request and will publish its 6300/7000 reply), subscribe to
 *   future kind 6300 / 7000 events tagged with the request id. The DVM almost
 *   never publishes responses on the user's outbox, so listening anywhere else
 *   would silently miss them.
 */
fun filterHomePostsByDvmIds(
    set: FavoriteDvmTopNavPerRelayFilterSet,
    @Suppress("UNUSED_PARAMETER") since: SincePerRelayMap?,
    @Suppress("UNUSED_PARAMETER") defaultSince: Long?,
): List<RelayBasedFilter> {
    val out = mutableListOf<RelayBasedFilter>()

    set.contentFetches.forEach { (relay, filter) ->
        out += contentFetchFilters(relay, filter)
    }

    val requestId = set.requestId
    if (requestId != null) {
        set.listenRelays.forEach { relay ->
            out += responseListenFilter(relay, requestId)
        }
    }

    return out
}

private fun contentFetchFilters(
    relay: NormalizedRelayUrl,
    filter: FavoriteDvmTopNavPerRelayFilter,
): List<RelayBasedFilter> {
    val out = mutableListOf<RelayBasedFilter>()

    if (filter.ids.isNotEmpty()) {
        out +=
            RelayBasedFilter(
                relay = relay,
                filter =
                    Filter(
                        ids = filter.ids.toList(),
                        limit = filter.ids.size,
                    ),
            )
    }

    if (filter.addresses.isNotEmpty()) {
        out +=
            RelayBasedFilter(
                relay = relay,
                filter =
                    Filter(
                        tags = mapOf("a" to filter.addresses.toList()),
                        limit = filter.addresses.size,
                    ),
            )
    }

    return out
}

private fun responseListenFilter(
    relay: NormalizedRelayUrl,
    requestId: HexKey,
) = RelayBasedFilter(
    relay = relay,
    filter =
        Filter(
            kinds =
                listOf(
                    NIP90ContentDiscoveryResponseEvent.KIND,
                    NIP90StatusEvent.KIND,
                ),
            tags = mapOf("e" to listOf(requestId)),
            limit = 10,
        ),
)
