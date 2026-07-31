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

import androidx.compose.runtime.Immutable
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.ExplainedFilter
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.SubPurpose
import com.vitorpamplona.amethyst.commons.relays.SincePerRelayMap
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip60Cashu.history.CashuSpendingHistoryEvent
import com.vitorpamplona.quartz.nip60Cashu.quote.CashuMintQuoteEvent
import com.vitorpamplona.quartz.nip60Cashu.token.CashuTokenEvent
import com.vitorpamplona.quartz.nip60Cashu.wallet.CashuWalletEvent
import com.vitorpamplona.quartz.nip61Nutzaps.info.NutzapInfoEvent
import com.vitorpamplona.quartz.nip61Nutzaps.nutzap.NutzapEvent
import com.vitorpamplona.quartz.nip87Ecash.recommendation.MintRecommendationEvent

/**
 * Query state for the NIP-60 / NIP-61 wallet subscription.
 *
 * `pubkey` is the wallet owner — used both as `authors=` for their own
 * NIP-60 events and as the `#p` tag value for inbound nutzaps.
 *
 * The two filters read from different relay sets, following the NIP-65
 * outbox model:
 *  - [ownEventRelays] — the user's own write/outbox relays, where they
 *    published their NIP-60 wallet/token/history events. Restoring those
 *    means reading from where they were written.
 *  - [inboxRelays] — where *other* people deliver kind:9321 nutzaps to this
 *    user. Per NIP-61 the source of truth is the `relay` tags in the user's
 *    own kind:10019; in practice we listen on the union of those plus the
 *    user's NIP-65 inbox + DM relays so a nutzap can't slip past us.
 */
@Immutable
data class CashuWalletQueryState(
    val pubkey: HexKey,
    val ownEventRelays: Set<NormalizedRelayUrl>,
    val inboxRelays: Set<NormalizedRelayUrl>,
)

/**
 * Every NIP-60 / NIP-61 filter for one account's Cashu wallet:
 *
 *  - kind 17375 — the wallet event (replaceable)
 *  - kind 7375  — unspent proofs (token events)
 *  - kind 7376  — spending history
 *  - kind 7374  — quote state events
 *  - kind 10019 — nutzap info (replaceable, for incoming nutzaps)
 *  - kind 9321  — inbound nutzaps tagged with the user's pubkey
 *
 * Authored events are queried by `authors=[pubkey]`; nutzaps by `#p=[pubkey]`, since we receive
 * those rather than send them.
 *
 * A plain function rather than a subscription manager: the wallet's lifetime belongs to the account,
 * so it is mounted by the account-level assembler alongside notifications, DMs and NWC, and this only
 * has to describe the query.
 */
fun cashuWalletFilters(
    key: CashuWalletQueryState,
    since: SincePerRelayMap?,
): List<RelayBasedFilter> {
    val pubkey = key.pubkey
    val ownEventRelays = key.ownEventRelays
    val inboxRelays = key.inboxRelays
    if (ownEventRelays.isEmpty() && inboxRelays.isEmpty()) return emptyList()

    val ownedFilter =
        ExplainedFilter(
            purpose = SubPurpose.WALLET,
            kinds =
                listOf(
                    CashuWalletEvent.KIND,
                    CashuTokenEvent.KIND,
                    CashuSpendingHistoryEvent.KIND,
                    CashuMintQuoteEvent.KIND,
                    NutzapInfoEvent.KIND,
                    // NIP-87 mint recommendations the user has published.
                    // Pulled here (instead of relying on the general
                    // account filter) so the Cashu Settings screen can
                    // list and retract them without any extra subscription.
                    MintRecommendationEvent.KIND,
                ),
            authors = listOf(pubkey),
            accountPubKeys = listOfNotNull(pubkey),
        )

    val inboundNutzapsFilter =
        ExplainedFilter(
            purpose = SubPurpose.NUTZAP_INBOX,
            kinds = listOf(NutzapEvent.KIND),
            tags = mapOf("p" to listOf(pubkey)),
            accountPubKeys = listOfNotNull(pubkey),
        )

    // Own NIP-60 events are read from the user's outbox; inbound nutzaps
    // from the user's inbox set. A relay that appears in both gets both
    // filters.
    val ownedSubs =
        ownEventRelays.map { relay ->
            val sinceTime = since?.get(relay)?.time
            RelayBasedFilter(
                relay,
                if (sinceTime != null) ownedFilter.copy(since = sinceTime) else ownedFilter,
            )
        }
    val inboundSubs =
        inboxRelays.map { relay ->
            val sinceTime = since?.get(relay)?.time
            RelayBasedFilter(
                relay,
                if (sinceTime != null) inboundNutzapsFilter.copy(since = sinceTime) else inboundNutzapsFilter,
            )
        }

    return ownedSubs + inboundSubs
}
