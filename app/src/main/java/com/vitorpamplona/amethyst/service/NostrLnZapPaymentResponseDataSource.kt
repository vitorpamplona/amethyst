package com.vitorpamplona.amethyst.service

import com.vitorpamplona.amethyst.service.model.LnZapPaymentResponseEvent
import com.vitorpamplona.amethyst.service.model.RelayAuthEvent
import com.vitorpamplona.amethyst.service.relays.Client
import com.vitorpamplona.amethyst.service.relays.FeedType
import com.vitorpamplona.amethyst.service.relays.JsonFilter
import com.vitorpamplona.amethyst.service.relays.Relay
import com.vitorpamplona.amethyst.service.relays.TypedFilter

class NostrLnZapPaymentResponseDataSource(
    private val fromServiceHex: String,
    private val toUserHex: String,
    private val replyingToHex: String,
    private val authSigningKey: ByteArray
) : NostrDataSource("LnZapPaymentResponseFeed") {

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

    override fun auth(relay: Relay, challenge: String) {
        super.auth(relay, challenge)

        val event = RelayAuthEvent.create(relay.url, challenge, authSigningKey)
        Client.send(
            event,
            relay.url
        )
    }
}
