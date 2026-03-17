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
package com.vitorpamplona.quartz.nip47WalletConnect.rpc

object NwcTransactionType {
    const val INCOMING = "incoming"
    const val OUTGOING = "outgoing"
}

object NwcTransactionState {
    const val PENDING = "PENDING"
    const val SETTLED = "SETTLED"
    const val FAILED = "FAILED"
    const val ACCEPTED = "ACCEPTED"

    fun isSettled(state: String?) = state.equals(SETTLED, ignoreCase = true)

    fun isPending(state: String?) = state.equals(PENDING, ignoreCase = true)

    fun isFailed(state: String?) = state.equals(FAILED, ignoreCase = true)

    fun isAccepted(state: String?) = state.equals(ACCEPTED, ignoreCase = true)
}

object NwcBudgetRenewal {
    const val DAILY = "daily"
    const val WEEKLY = "weekly"
    const val MONTHLY = "monthly"
    const val YEARLY = "yearly"
    const val NEVER = "never"
}

class NwcTransaction(
    var type: String? = null,
    var state: String? = null,
    var invoice: String? = null,
    var description: String? = null,
    var description_hash: String? = null,
    var preimage: String? = null,
    var payment_hash: String? = null,
    var amount: Long? = null,
    var fees_paid: Long? = null,
    var created_at: Long? = null,
    var expires_at: Long? = null,
    var settled_at: Long? = null,
    var settle_deadline: Long? = null,
    var metadata: Map<String, Any?>? = null,
) {
    fun parsedMetadata(): NwcTransactionMetadata? = NwcTransactionMetadata.parse(metadata)
}

class TlvRecord(
    var type: Long? = null,
    var value: String? = null,
)
