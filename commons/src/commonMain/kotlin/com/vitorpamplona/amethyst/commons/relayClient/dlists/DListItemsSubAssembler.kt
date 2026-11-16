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
package com.vitorpamplona.amethyst.commons.relayClient.dlists

import com.vitorpamplona.amethyst.commons.relayClient.eoseManagers.SingleSubEoseManager
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.ExplainedFilter
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.SubPurpose
import com.vitorpamplona.amethyst.commons.relays.SincePerRelayMap
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter

/** How many items a header card asks each relay for. The card shows a count and a preview. */
const val DLIST_ITEMS_LIMIT = 100

class DListItemsSubAssembler(
    client: INostrClient,
    allKeys: () -> Set<DListItemsQueryState>,
) : SingleSubEoseManager<DListItemsQueryState>(client, allKeys) {
    override fun updateFilter(
        keys: List<DListItemsQueryState>,
        since: SincePerRelayMap?,
    ): List<RelayBasedFilter> {
        if (keys.isEmpty()) return emptyList()

        val headers =
            keys.mapNotNull { key ->
                val header = key.header.event ?: return@mapNotNull null
                DListHeaderOnRelays(key.header.idHex, header, key.header.relayUrls())
            }
        val requests = planDListItemRequests(headers)

        return requests.map { request ->
            RelayBasedFilter(
                relay = request.relay,
                filter =
                    ExplainedFilter(
                        purpose = SubPurpose.REFERENCED_EVENTS,
                        purposeDetail = "D-list items",
                        kinds = request.kinds,
                        tags = mapOf("z" to request.pointers),
                        // Each header keeps the room it had alone.
                        limit = DLIST_ITEMS_LIMIT * request.headerIds.size,
                        since = since?.get(request.relay)?.time,
                    ),
            )
        }
    }

    override fun distinct(key: DListItemsQueryState) = key.header.idHex
}
