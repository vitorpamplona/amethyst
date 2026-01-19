/**
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
package com.vitorpamplona.amethyst.desktop.nwc

import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.desktop.cache.DesktopLocalCache
import com.vitorpamplona.amethyst.desktop.network.DesktopRelayConnectionManager
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip47WalletConnect.LnZapPaymentRequestEvent
import com.vitorpamplona.quartz.nip47WalletConnect.LnZapPaymentResponseEvent
import com.vitorpamplona.quartz.nip47WalletConnect.Nip47WalletConnect
import com.vitorpamplona.quartz.nip47WalletConnect.PayInvoiceErrorResponse
import com.vitorpamplona.quartz.nip47WalletConnect.PayInvoiceSuccessResponse
import com.vitorpamplona.quartz.nip47WalletConnect.Response
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Handles NIP-47 (Nostr Wallet Connect) payments for desktop.
 *
 * Flow:
 * 1. Create payment request event with BOLT11 invoice
 * 2. Register with tracker for persistent tracking in Note.zapPayments
 * 3. Send to wallet's relay
 * 4. Subscribe and wait for wallet response
 *
 * @param relayManager Manages relay connections for sending/subscribing
 * @param localCache Cache for persistent payment tracking
 */
class NwcPaymentHandler(
    private val relayManager: DesktopRelayConnectionManager,
    private val localCache: DesktopLocalCache,
) {
    sealed class PaymentResult {
        data class Success(
            val preimage: String?,
        ) : PaymentResult()

        data class Error(
            val message: String,
        ) : PaymentResult()

        data object Timeout : PaymentResult()
    }

    /**
     * Sends a payment request via NWC and waits for response.
     * Payment is tracked in Note.zapPayments for the zapped note.
     *
     * @param bolt11 The BOLT11 invoice to pay
     * @param nwcConnection The NWC connection details (pubkey, relay, secret)
     * @param zappedNote The note being zapped (for tracking in Note.zapPayments)
     * @param timeoutMs How long to wait for payment response (default 60s)
     * @return PaymentResult indicating success, error, or timeout
     */
    suspend fun payInvoice(
        bolt11: String,
        nwcConnection: Nip47WalletConnect.Nip47URINorm,
        zappedNote: Note? = null,
        timeoutMs: Long = 60_000,
    ): PaymentResult {
        val secret = nwcConnection.secret ?: return PaymentResult.Error("NWC connection has no secret")

        // Create signer from NWC secret
        val nwcSigner = NostrSignerInternal(KeyPair(secret.hexToByteArray()))

        // Create payment request event
        val requestEvent =
            LnZapPaymentRequestEvent.create(
                lnInvoice = bolt11,
                walletServicePubkey = nwcConnection.pubKeyHex,
                signer = nwcSigner,
            )

        // Register request note in cache for tracking
        val requestNote = localCache.getOrCreateNote(requestEvent.id)
        requestNote.loadEvent(requestEvent, localCache.getOrCreateUser(requestEvent.pubKey), emptyList())
        requestNote.addRelay(nwcConnection.relayUri)

        // Link to zapped note for persistent tracking
        zappedNote?.addZapPayment(requestNote, null)

        // Send request to wallet's relay
        relayManager.sendToRelay(nwcConnection.relayUri, requestEvent)

        // Subscribe and wait for response with timeout
        return withTimeoutOrNull(timeoutMs) {
            waitForResponse(requestEvent.id, nwcConnection, nwcSigner, zappedNote, requestNote)
        } ?: PaymentResult.Timeout
    }

    private suspend fun waitForResponse(
        requestId: String,
        nwcConnection: Nip47WalletConnect.Nip47URINorm,
        nwcSigner: NostrSignerInternal,
        zappedNote: Note?,
        requestNote: Note,
    ): PaymentResult =
        suspendCancellableCoroutine { continuation ->
            val filter =
                Filter(
                    kinds = listOf(LnZapPaymentResponseEvent.KIND),
                    authors = listOf(nwcConnection.pubKeyHex),
                    tags = mapOf("e" to listOf(requestId)),
                )

            val subId = "nwc-response-${requestId.take(8)}"

            relayManager.subscribeOnRelay(
                relay = nwcConnection.relayUri,
                subId = subId,
                filters = listOf(filter),
                onEvent = { event, relay ->
                    if (event is LnZapPaymentResponseEvent && event.requestId() == requestId) {
                        // Unsubscribe
                        relayManager.closeSubscription(nwcConnection.relayUri, subId)

                        // Store response note and link to zapped note
                        val responseNote = localCache.getOrCreateNote(event.id)
                        responseNote.loadEvent(event, localCache.getOrCreateUser(event.pubKey), emptyList())
                        responseNote.addRelay(relay)
                        zappedNote?.addZapPayment(requestNote, responseNote)

                        // Decrypt and process response
                        try {
                            kotlinx.coroutines.runBlocking {
                                val response = event.decrypt(nwcSigner)
                                val result = processResponse(response)
                                if (continuation.isActive) {
                                    continuation.resume(result)
                                }
                            }
                        } catch (e: Exception) {
                            if (continuation.isActive) {
                                continuation.resume(PaymentResult.Error("Failed to decrypt response: ${e.message}"))
                            }
                        }
                    }
                },
            )

            continuation.invokeOnCancellation {
                relayManager.closeSubscription(nwcConnection.relayUri, subId)
            }
        }

    private fun processResponse(response: Response): PaymentResult =
        when (response) {
            is PayInvoiceSuccessResponse -> {
                PaymentResult.Success(response.result?.preimage)
            }
            is PayInvoiceErrorResponse -> {
                PaymentResult.Error(response.error?.message ?: "Unknown error")
            }
            else -> {
                PaymentResult.Error("Unexpected response type: ${response.resultType}")
            }
        }
}
