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
package com.vitorpamplona.amethyst.service

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.quartz.experimental.clink.client.OfferClient
import com.vitorpamplona.quartz.experimental.clink.offers.OfferEvent
import com.vitorpamplona.quartz.experimental.clink.offers.OfferResponse
import com.vitorpamplona.quartz.experimental.clink.pointers.NOffer
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.SubscriptionListener
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Drives the CLINK Offers payer round-trip: publishes a kind-21001 request to the
 * offer's relays and waits for the service's encrypted reply, returning the decrypted
 * [OfferResponse] (an invoice or an error). UI then hands a successful `bolt11` to the
 * existing pay path (e.g. `payViaIntent`).
 *
 * Consume-only: Amethyst never answers offer requests, it only asks.
 */
object ClinkOfferPayer {
    const val DEFAULT_TIMEOUT_MS = 30_000L

    /**
     * @param amountSats overrides the pointer's embedded price (required for spontaneous offers).
     * @return the decrypted response, or null if no reply arrived before [timeoutMs] (or the
     *   pointer carried no relay to reach).
     */
    suspend fun requestInvoice(
        account: Account,
        offer: NOffer,
        amountSats: Long? = null,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    ): OfferResponse? {
        val relays = offer.relays.toSet()
        if (relays.isEmpty()) return null

        val client = OfferClient(offer, account.signer)
        val request = client.requestInvoice(amountSats)

        val reply = CompletableDeferred<OfferEvent>()
        val subId = "clink-offer-${request.id}"
        val filters: Map<NormalizedRelayUrl, List<Filter>> = relays.associateWith { listOf(client.responseFilter(request.id)) }

        val listener =
            object : SubscriptionListener {
                override fun onEvent(
                    event: Event,
                    isLive: Boolean,
                    relay: NormalizedRelayUrl,
                    forFilters: List<Filter>?,
                ) {
                    if (event is OfferEvent && event.requestId() == request.id && !reply.isCompleted) {
                        reply.complete(event)
                    }
                }
            }

        account.client.subscribe(subId, filters, listener)
        return try {
            account.client.publish(request, relays)
            val response = withTimeoutOrNull(timeoutMs) { reply.await() } ?: return null
            client.parseResponse(response)
        } finally {
            account.client.unsubscribe(subId)
        }
    }
}
