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
package com.vitorpamplona.amethyst.service.uploads.blossom

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.quartz.lightning.LnInvoiceUtil
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.PayInvoiceSuccessResponse
import com.vitorpamplona.quartz.nipB7Blossom.BlossomPaymentProof
import com.vitorpamplona.quartz.nipB7Blossom.BlossomPaymentRequired
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Settles a BUD-07 [BlossomPaymentRequired] challenge so a blocked upload/mirror can
 * be retried. Reuses the account's existing NIP-47 (Nostr Wallet Connect) lightning
 * path — this handler never touches keys or moves funds itself, it only asks the
 * user's connected wallet to pay the invoice and returns the resulting preimage as
 * the [BlossomPaymentProof].
 *
 * Cashu-only servers (`X-Cashu`, no `X-Lightning`) are not yet supported here; the
 * caller should surface that a lightning wallet is required.
 */
object BlossomPaymentHandler {
    /** True when this account has a wallet we can pay the lightning invoice with. */
    fun canPay(
        account: Account,
        payment: BlossomPaymentRequired,
    ): Boolean = payment.lightning != null && account.nip47SignerState.hasWalletConnectSetup()

    /** The invoice amount in sats, for display in a confirmation prompt. */
    fun amountSats(payment: BlossomPaymentRequired): Long? =
        payment.lightning?.let {
            runCatching { LnInvoiceUtil.getAmountInSats(it).toLong() }.getOrNull()
        }

    /**
     * Pays the challenge's BOLT-11 invoice via NWC and returns the proof, or null if
     * there is no payable invoice, no wallet, or the wallet didn't confirm in time.
     */
    suspend fun pay(
        account: Account,
        payment: BlossomPaymentRequired,
    ): BlossomPaymentProof? {
        val invoice = payment.lightning ?: return null
        if (!account.nip47SignerState.hasWalletConnectSetup()) return null

        val preimageResult = CompletableDeferred<String?>()
        try {
            account.sendZapPaymentRequestFor(invoice, null) { response ->
                // CompletableDeferred.complete is idempotent, so extra callbacks are harmless.
                preimageResult.complete((response as? PayInvoiceSuccessResponse)?.result?.preimage)
            }
        } catch (e: Exception) {
            Log.w("BlossomPayment", "Failed to send NWC payment request", e)
            return null
        }

        val preimage = withTimeoutOrNull(90_000) { preimageResult.await() } ?: return null
        return BlossomPaymentProof(lightningPreimage = preimage)
    }
}
