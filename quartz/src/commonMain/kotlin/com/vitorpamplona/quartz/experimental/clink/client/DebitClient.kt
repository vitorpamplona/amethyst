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

import com.vitorpamplona.quartz.experimental.clink.debits.DebitEvent
import com.vitorpamplona.quartz.experimental.clink.debits.DebitFrequency
import com.vitorpamplona.quartz.experimental.clink.debits.DebitRequest
import com.vitorpamplona.quartz.experimental.clink.debits.DebitResponse
import com.vitorpamplona.quartz.experimental.clink.pointers.NDebit
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * High-level CLINK Debits requestor.
 *
 * Wraps a decoded [NDebit] pointer. A session pointer's single-use `k1` (TLV 3) is
 * carried automatically into every request; static pointers send no `k1`, per spec.
 */
class DebitClient(
    val pointer: NDebit,
    val signer: NostrSigner,
) {
    val servicePubKey: HexKey get() = pointer.pubKey
    val relays: List<NormalizedRelayUrl> get() = pointer.relays

    /** Asks the service to pay [bolt11] from the pointed-to wallet (kind 21002). */
    suspend fun payInvoice(
        bolt11: String,
        amountSats: Long? = null,
        description: String? = null,
        createdAt: Long = TimeUtils.now(),
    ): DebitEvent {
        val request =
            DebitRequest(
                pointer = pointer.pointer,
                amount_sats = amountSats,
                bolt11 = bolt11,
                description = description,
                k1 = pointer.k1,
            )
        return DebitEvent.createRequest(request, servicePubKey, signer, createdAt)
    }

    /** Requests a spending budget; omit [frequency] for a one-time budget. */
    suspend fun requestBudget(
        amountSats: Long,
        frequency: DebitFrequency? = null,
        description: String? = null,
        createdAt: Long = TimeUtils.now(),
    ): DebitEvent {
        val request =
            DebitRequest(
                pointer = pointer.pointer,
                amount_sats = amountSats,
                description = description,
                k1 = pointer.k1,
                frequency = frequency,
            )
        return DebitEvent.createRequest(request, servicePubKey, signer, createdAt)
    }

    fun responseFilter(requestId: HexKey): Filter =
        Filter(
            kinds = listOf(DebitEvent.KIND),
            authors = listOf(servicePubKey),
            tags = mapOf("e" to listOf(requestId)),
        )

    suspend fun parseResponse(event: DebitEvent): DebitResponse = event.decryptResponse(signer)
}
