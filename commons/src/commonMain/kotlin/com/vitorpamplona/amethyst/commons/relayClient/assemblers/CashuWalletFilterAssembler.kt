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
package com.vitorpamplona.amethyst.commons.relayClient.assemblers

import com.vitorpamplona.amethyst.commons.relayClient.eoseManagers.SingleSubEoseManager
import com.vitorpamplona.amethyst.commons.relays.SincePerRelayMap
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip60Cashu.history.CashuSpendingHistoryEvent
import com.vitorpamplona.quartz.nip60Cashu.quote.CashuMintQuoteEvent
import com.vitorpamplona.quartz.nip60Cashu.token.CashuTokenEvent
import com.vitorpamplona.quartz.nip60Cashu.wallet.CashuWalletEvent
import com.vitorpamplona.quartz.nip61Nutzaps.info.NutzapInfoEvent
import com.vitorpamplona.quartz.nip61Nutzaps.nutzap.NutzapEvent

/**
 * Query state for the NIP-60 / NIP-61 wallet subscription.
 *
 * `pubkey` is the wallet owner — used both as `authors=` for their own
 * NIP-60 events and as the `#p` tag value for inbound nutzaps.
 */
data class CashuWalletQueryState(
    val pubkey: HexKey,
    val relays: Set<NormalizedRelayUrl>,
)

/**
 * Subscribes to all NIP-60 / NIP-61 events that participate in this
 * account's Cashu wallet:
 *
 *  - kind 17375 — the wallet event (replaceable)
 *  - kind 7375  — unspent proofs (token events)
 *  - kind 7376  — spending history
 *  - kind 7374  — quote state events
 *  - kind 10019 — nutzap info (replaceable, for incoming nutzaps)
 *  - kind 9321  — inbound nutzaps tagged with the user's pubkey
 *
 * All authored events are queried by `authors=[pubkey]`; nutzaps are queried by
 * `#p=[pubkey]` (we receive them, not send them, from this filter's perspective).
 */
class CashuWalletFilterAssembler(
    client: INostrClient,
    allKeys: () -> Set<CashuWalletQueryState>,
) : SingleSubEoseManager<CashuWalletQueryState>(client, allKeys, invalidateAfterEose = true) {
    override fun distinct(key: CashuWalletQueryState): Any = key.pubkey

    override fun updateFilter(
        keys: List<CashuWalletQueryState>,
        since: SincePerRelayMap?,
    ): List<RelayBasedFilter>? {
        if (keys.isEmpty()) return null

        val pubkey = keys.first().pubkey
        val relays =
            keys
                .flatMap { it.relays }
                .toSet()
                .ifEmpty { return null }

        val ownedFilter =
            Filter(
                kinds =
                    listOf(
                        CashuWalletEvent.KIND,
                        CashuTokenEvent.KIND,
                        CashuSpendingHistoryEvent.KIND,
                        CashuMintQuoteEvent.KIND,
                        NutzapInfoEvent.KIND,
                    ),
                authors = listOf(pubkey),
            )

        val inboundNutzapsFilter =
            Filter(
                kinds = listOf(NutzapEvent.KIND),
                tags = mapOf("p" to listOf(pubkey)),
            )

        return relays.flatMap { relay ->
            val sinceTime = since?.get(relay)?.time
            listOf(
                RelayBasedFilter(
                    relay,
                    if (sinceTime != null) ownedFilter.copy(since = sinceTime) else ownedFilter,
                ),
                RelayBasedFilter(
                    relay,
                    if (sinceTime != null) inboundNutzapsFilter.copy(since = sinceTime) else inboundNutzapsFilter,
                ),
            )
        }
    }
}
