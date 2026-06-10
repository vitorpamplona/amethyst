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
package com.vitorpamplona.quartz.experimental.clink.client

import com.vitorpamplona.quartz.experimental.clink.offers.OfferEvent
import com.vitorpamplona.quartz.experimental.clink.offers.OfferReceipt
import com.vitorpamplona.quartz.experimental.clink.offers.OfferRequest
import com.vitorpamplona.quartz.experimental.clink.offers.OfferResponse
import com.vitorpamplona.quartz.experimental.clink.pointers.NOffer
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * High-level CLINK Offers payer.
 *
 * Wraps a decoded [NOffer] pointer: builds the request event, exposes the relays to
 * publish on, the filter to await the reply, and the response parser.
 *
 * ```kotlin
 * val offer = ClinkPointerParser.parse("noffer1...") as NOffer
 * val client = OfferClient(offer, signer)
 * val request = client.requestInvoice(amountSats = 1000)
 * // publish `request` to client.relays, then subscribe with client.responseFilter(request.id)
 * val response = client.parseResponse(replyEvent)   // -> OfferResponse(bolt11=...) or error
 * ```
 */
class OfferClient(
    val pointer: NOffer,
    val signer: NostrSigner,
) {
    val servicePubKey: HexKey get() = pointer.pubKey
    val relays: List<NormalizedRelayUrl> get() = pointer.relays

    /**
     * Builds the kind-21001 request asking the service for a fresh BOLT-11. When
     * [amountSats] is null it falls back to the pointer's embedded price (for fixed offers).
     */
    suspend fun requestInvoice(
        amountSats: Long? = null,
        description: String? = null,
        payerData: Map<String, Any?>? = null,
        zap: String? = null,
        expiresInSeconds: Long? = null,
        createdAt: Long = TimeUtils.now(),
    ): OfferEvent {
        val request =
            OfferRequest(
                offer = pointer.pointer,
                amount_sats = amountSats ?: pointer.price,
                payer_data = payerData,
                zap = zap,
                expires_in_seconds = expiresInSeconds,
                description = description,
            )
        return OfferEvent.createRequest(request, servicePubKey, signer, createdAt)
    }

    /** Subscribe with this on [relays] after publishing the request to await its reply. */
    fun responseFilter(requestId: HexKey): Filter =
        Filter(
            kinds = listOf(OfferEvent.KIND),
            authors = listOf(servicePubKey),
            tags = mapOf("e" to listOf(requestId), "p" to listOf(signer.pubKey)),
        )

    suspend fun parseResponse(event: OfferEvent): OfferResponse = event.decryptResponse(signer)

    /**
     * Parses a post-settlement receipt — the optional second kind-21001 event the service
     * sends (on the same `e`+`p` filter as [responseFilter]) once the returned invoice is paid.
     */
    suspend fun parseReceipt(event: OfferEvent): OfferReceipt = event.decryptReceipt(signer)
}
