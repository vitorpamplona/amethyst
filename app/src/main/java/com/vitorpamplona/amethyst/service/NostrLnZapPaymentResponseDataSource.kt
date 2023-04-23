package com.vitorpamplona.amethyst.service

import com.vitorpamplona.amethyst.service.model.LnZapPaymentResponseEvent
import com.vitorpamplona.amethyst.service.relays.FeedType
import com.vitorpamplona.amethyst.service.relays.JsonFilter
import com.vitorpamplona.amethyst.service.relays.TypedFilter

class NostrLnZapPaymentResponseDataSource(
    private var fromServiceHex: String,
    private var toUserHex: String,
    private var replyingToHex: String,
): NostrDataSource("LnZapPaymentResponseFeed") {

    val feedTypes = setOf(FeedType.WALLET_CONNECT)

    private fun createWalletConnectServiceWatcher(): TypedFilter {
        // downloads all the reactions to a given event.
        return TypedFilter(
            types = feedTypes,
            filter = JsonFilter(
                kinds = listOf(LnZapPaymentResponseEvent.kind),
                authors = listOf(fromServiceHex),
                tags = mapOf(
                    "e" to listOf(replyingToHex),
                    "p" to listOf(toUserHex)
                ),
                limit = 1
            )
        )
    }

    val channel = requestNewChannel()

    override fun updateChannelFilters() {
        val wc = createWalletConnectServiceWatcher()

        channel.typedFilters = listOfNotNull(wc).ifEmpty { null }
    }
}
