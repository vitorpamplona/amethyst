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
package com.vitorpamplona.amethyst.commons.services.nwc.wallet

/**
 * State for a send (pay invoice) operation via NWC.
 */
sealed class SendState {
    data object Idle : SendState()

    data object Sending : SendState()

    data class Success(
        val preimage: String?,
    ) : SendState()

    data class Error(
        val message: String,
    ) : SendState()
}

/**
 * State for a receive (create invoice) operation via NWC.
 */
sealed class ReceiveState {
    data object Idle : ReceiveState()

    data object Creating : ReceiveState()

    data class Created(
        val invoice: String,
        val amount: Long,
    ) : ReceiveState()

    data class Error(
        val message: String,
    ) : ReceiveState()
}

/**
 * Filter type for wallet transactions.
 */
enum class TransactionFilter {
    ALL,
    ZAPS,
    NON_ZAPS,
}

/**
 * Summary info for a connected NWC wallet.
 */
data class WalletInfo(
    val walletId: String,
    val name: String,
    val alias: String? = null,
    val balanceSats: Long? = null,
    val isDefault: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
)
