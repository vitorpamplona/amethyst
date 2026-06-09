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
import com.vitorpamplona.quartz.experimental.clink.client.DebitClient
import com.vitorpamplona.quartz.experimental.clink.debits.DebitEvent
import com.vitorpamplona.quartz.experimental.clink.debits.DebitResponse
import com.vitorpamplona.quartz.experimental.clink.pointers.NDebit
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.SubscriptionListener
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Drives the CLINK Debits payer round-trip: publishes a kind-21002 request asking the
 * pointed-to wallet to pay a BOLT-11, and waits for the encrypted reply. The wallet
 * authorizes against the account's own identity (no shared secret).
 *
 * This is the CLINK-debit spend rail that the zap button / offer card route through
 * when a debit pointer is the selected default payment source. It MUST only be invoked
 * after an explicit user confirmation — a debit pulls real sats.
 *
 * Consume-only: Amethyst sends debit requests, it never answers them.
 */
object ClinkDebitPayer {
    const val DEFAULT_TIMEOUT_MS = 30_000L

    /**
     * @return the decrypted response (`res:"ok"` with optional preimage, or a `GFY`
     *   failure), or null if no reply arrived in time or the pointer carried no relay.
     */
    suspend fun payInvoice(
        account: Account,
        pointer: NDebit,
        bolt11: String,
        amountSats: Long? = null,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    ): DebitResponse? {
        val relays = pointer.relays.toSet()
        if (relays.isEmpty()) return null

        val client = DebitClient(pointer, account.signer)
        val request = client.payInvoice(bolt11, amountSats)

        val reply = CompletableDeferred<DebitEvent>()
        val subId = "clink-debit-${request.id}"
        val filters: Map<NormalizedRelayUrl, List<Filter>> = relays.associateWith { listOf(client.responseFilter(request.id)) }

        val listener =
            object : SubscriptionListener {
                override fun onEvent(
                    event: Event,
                    isLive: Boolean,
                    relay: NormalizedRelayUrl,
                    forFilters: List<Filter>?,
                ) {
                    if (event is DebitEvent && event.requestId() == request.id && !reply.isCompleted) {
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
