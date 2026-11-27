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
package com.vitorpamplona.amethyst.commons.model

import androidx.compose.runtime.Stable

/**
 * A BOLT-11 invoice this account settled by melting NIP-60 ecash, registered on the zapped
 * [Note] at settlement. The melt's preimage is the receipt, so the zap icon and zapped amount
 * update for the sender before (or without) a kind:9735 receipt, which many LNURL servers
 * never publish and private zaps never do — the role [Note.zapPayments] plays for the NWC
 * rail. In-memory only: nothing is published, and [Note.zappedAmountWithNWCPayments]
 * deduplicates it against receipts by invoice.
 */
@Stable
data class CashuMeltZapEntry(
    /** The full BOLT-11 string: the dedup key shared with kind:9735 receipts and NWC payments. */
    val invoice: String,
    /** The Lightning preimage the mint returned; null when only the payment is known (a melt whose recording failed). */
    val preimage: String?,
    /** The sats the invoice was settled for. */
    val amountSats: Long,
    /** When the melt settled: the role the NWC response's `createdAt` plays for [Note.zapPayments]. */
    val paidAtInSeconds: Long,
)
