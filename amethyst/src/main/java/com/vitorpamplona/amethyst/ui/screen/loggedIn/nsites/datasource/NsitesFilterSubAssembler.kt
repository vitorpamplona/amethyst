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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.nsites.datasource

import com.vitorpamplona.amethyst.commons.relayClient.eoseManagers.SingleSubEoseManager
import com.vitorpamplona.amethyst.service.relays.SincePerRelayMap
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip5aStaticWebsites.NamedSiteEvent
import com.vitorpamplona.quartz.nip5aStaticWebsites.RootSiteEvent

private const val NSITE_PAGE_LIMIT = 200

/**
 * Emits one REQ per read relay for both static-site manifest kinds. A single subscription per account
 * (deduped by [distinct]) is enough — the screen wants "what nSites exist", not a per-author feed — so
 * this stays far lighter than the follow-list feed assemblers.
 */
class NsitesFilterSubAssembler(
    client: INostrClient,
    allKeys: () -> Set<NsitesQueryState>,
) : SingleSubEoseManager<NsitesQueryState>(client, allKeys) {
    override fun updateFilter(
        keys: List<NsitesQueryState>,
        since: SincePerRelayMap?,
    ): List<RelayBasedFilter> {
        if (keys.isEmpty()) return emptyList()

        return keys.flatMap { key ->
            key.account.homeRelays.flow.value.map { relay ->
                RelayBasedFilter(
                    relay = relay,
                    filter =
                        Filter(
                            kinds = listOf(RootSiteEvent.KIND, NamedSiteEvent.KIND),
                            limit = NSITE_PAGE_LIMIT,
                            since = since?.get(relay)?.time,
                        ),
                )
            }
        }
    }

    override fun distinct(key: NsitesQueryState) = key.account.signer.pubKey
}
