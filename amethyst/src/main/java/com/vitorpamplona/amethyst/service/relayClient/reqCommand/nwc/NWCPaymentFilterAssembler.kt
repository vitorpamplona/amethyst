/**
 * Copyright (c) 2024 Vitor Pamplona
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
package com.vitorpamplona.amethyst.service.relayClient.reqCommand.nwc

import com.vitorpamplona.amethyst.service.relayClient.reqCommand.QueryBasedSubscriptionOrchestrator
import com.vitorpamplona.ammolite.relays.FeedType
import com.vitorpamplona.ammolite.relays.NostrClient
import com.vitorpamplona.ammolite.relays.TypedFilter
import com.vitorpamplona.ammolite.relays.filters.SincePerRelayFilter
import com.vitorpamplona.quartz.nip47WalletConnect.LnZapPaymentResponseEvent

// This allows multiple screen to be listening to tags, even the same tag
class NWCPaymentQueryState(
    val fromServiceHex: String,
    val toUserHex: String,
    val replyingToHex: String,
)

class NWCPaymentFilterAssembler(
    client: NostrClient,
) : QueryBasedSubscriptionOrchestrator<NWCPaymentQueryState>(client) {
    fun createAccountMetadataFilter(keys: Set<NWCPaymentQueryState>): TypedFilter? {
        val fromAuthors = keys.mapTo(mutableSetOf()) { it.fromServiceHex }
        val replyingToPayments = keys.mapTo(mutableSetOf()) { it.replyingToHex }
        val aboutUsers = keys.mapTo(mutableSetOf()) { it.toUserHex }

        if (fromAuthors.isEmpty()) return null

        return TypedFilter(
            types = setOf(FeedType.WALLET_CONNECT),
            filter =
                SincePerRelayFilter(
                    kinds = listOf(LnZapPaymentResponseEvent.KIND),
                    authors = fromAuthors.toList(),
                    tags =
                        mapOf(
                            "e" to replyingToPayments.toList(),
                            "p" to aboutUsers.toList(),
                        ),
                    limit = 1,
                ),
        )
    }

    val channel = requestNewSubscription()

    override fun updateSubscriptions(keys: Set<NWCPaymentQueryState>) {
        val filter = createAccountMetadataFilter(keys)
        if (filter != null) {
            channel.typedFilters = listOf(filter)
        } else {
            channel.typedFilters = null
        }
    }
}
