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
package com.vitorpamplona.quartz.nip47WalletConnect

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner

/**
 * High-level NIP-47 Wallet Connect server (wallet service).
 *
 * Simplifies building a wallet service that receives NWC requests from clients,
 * processes them, and sends back responses and notifications.
 *
 * Usage:
 * ```kotlin
 * val server = Nip47Server(walletSigner, supportedMethods, relayUrl)
 *
 * // Publish capabilities
 * val infoEvent = server.buildInfoEvent()
 * // Send infoEvent to relay
 *
 * // Subscribe using server.requestsFilter() on your relay
 *
 * // When a request arrives:
 * val request = server.parseRequest(requestEvent)
 * when (request) {
 *     is GetBalanceMethod -> {
 *         val response = server.respondGetBalance(requestEvent, balance = 2100000L)
 *         // Send response to relay
 *     }
 *     is PayInvoiceMethod -> {
 *         // Process payment, then:
 *         val response = server.respondPayInvoice(requestEvent, preimage = "abc123")
 *         // Or on error:
 *         val error = server.respondError(requestEvent, NwcErrorCode.PAYMENT_FAILED, "Route not found")
 *         // Send response to relay
 *     }
 * }
 * ```
 */
class Nip47Server(
    val signer: NostrSigner,
    val capabilities: List<String> = emptyList(),
    val useNip44: Boolean = false,
    val encryptionSchemes: List<String>? = null,
    val notificationTypes: List<String>? = null,
) {
    // --- Info event ---

    /**
     * Builds a kind 13194 info event advertising wallet capabilities.
     * Sign and publish this event to your relay.
     */
    fun buildInfoEvent() =
        NwcInfoEvent.build(
            capabilities = capabilities,
            encryptionSchemes = encryptionSchemes,
            notificationTypes = notificationTypes,
        )

    // --- Request parsing ---

    /**
     * Decrypts and parses an incoming client request.
     */
    suspend fun parseRequest(event: LnZapPaymentRequestEvent): Request = event.decryptRequest(signer)

    // --- Response builders ---

    /**
     * Builds a response event from any [Response] object.
     */
    suspend fun buildResponse(
        response: Response,
        requestEvent: LnZapPaymentRequestEvent,
    ): LnZapPaymentResponseEvent =
        LnZapPaymentResponseEvent.createResponse(
            response = response,
            requestEvent = requestEvent,
            signer = signer,
            useNip44 = useNip44,
        )

    /**
     * Builds an error response for any method.
     */
    suspend fun respondError(
        requestEvent: LnZapPaymentRequestEvent,
        code: NwcErrorCode,
        message: String,
        resultType: String? = null,
    ): LnZapPaymentResponseEvent {
        val method = resultType ?: requestEvent.decryptRequest(signer).method ?: NwcMethod.PAY_INVOICE
        return buildResponse(NwcErrorResponse(method, NwcError(code, message)), requestEvent)
    }

    /**
     * Builds a pay_invoice success response.
     */
    suspend fun respondPayInvoice(
        requestEvent: LnZapPaymentRequestEvent,
        preimage: String? = null,
        feesPaid: Long? = null,
    ): LnZapPaymentResponseEvent =
        buildResponse(
            PayInvoiceSuccessResponse(PayInvoiceSuccessResponse.PayInvoiceResultParams(preimage, feesPaid)),
            requestEvent,
        )

    /**
     * Builds a pay_keysend success response.
     */
    suspend fun respondPayKeysend(
        requestEvent: LnZapPaymentRequestEvent,
        preimage: String? = null,
        feesPaid: Long? = null,
    ): LnZapPaymentResponseEvent =
        buildResponse(
            PayKeysendSuccessResponse(PayKeysendSuccessResponse.PayKeysendResult(preimage, feesPaid)),
            requestEvent,
        )

    /**
     * Builds a get_balance success response.
     */
    suspend fun respondGetBalance(
        requestEvent: LnZapPaymentRequestEvent,
        balance: Long,
    ): LnZapPaymentResponseEvent =
        buildResponse(
            GetBalanceSuccessResponse(GetBalanceSuccessResponse.GetBalanceResult(balance)),
            requestEvent,
        )

    /**
     * Builds a get_info success response.
     */
    suspend fun respondGetInfo(
        requestEvent: LnZapPaymentRequestEvent,
        alias: String? = null,
        color: String? = null,
        pubkey: String? = null,
        network: String? = null,
        blockHeight: Long? = null,
        blockHash: String? = null,
        methods: List<String>? = null,
        notifications: List<String>? = null,
        lud16: String? = null,
    ): LnZapPaymentResponseEvent =
        buildResponse(
            GetInfoSuccessResponse(
                GetInfoSuccessResponse.GetInfoResult(
                    alias,
                    color,
                    pubkey,
                    network,
                    blockHeight,
                    blockHash,
                    methods,
                    notifications,
                    null,
                    lud16,
                ),
            ),
            requestEvent,
        )

    /**
     * Builds a make_invoice success response.
     */
    suspend fun respondMakeInvoice(
        requestEvent: LnZapPaymentRequestEvent,
        transaction: NwcTransaction,
    ): LnZapPaymentResponseEvent = buildResponse(MakeInvoiceSuccessResponse(transaction), requestEvent)

    /**
     * Builds a lookup_invoice success response.
     */
    suspend fun respondLookupInvoice(
        requestEvent: LnZapPaymentRequestEvent,
        transaction: NwcTransaction,
    ): LnZapPaymentResponseEvent = buildResponse(LookupInvoiceSuccessResponse(transaction), requestEvent)

    /**
     * Builds a list_transactions success response.
     */
    suspend fun respondListTransactions(
        requestEvent: LnZapPaymentRequestEvent,
        transactions: List<NwcTransaction>,
        totalCount: Long? = null,
    ): LnZapPaymentResponseEvent =
        buildResponse(
            ListTransactionsSuccessResponse(
                ListTransactionsSuccessResponse.ListTransactionsResult(transactions, totalCount),
            ),
            requestEvent,
        )

    /**
     * Builds a get_budget success response.
     */
    suspend fun respondGetBudget(
        requestEvent: LnZapPaymentRequestEvent,
        usedBudget: Long? = null,
        totalBudget: Long? = null,
        renewsAt: Long? = null,
        renewalPeriod: String? = null,
    ): LnZapPaymentResponseEvent =
        buildResponse(
            GetBudgetSuccessResponse(
                GetBudgetSuccessResponse.GetBudgetResult(usedBudget, totalBudget, renewsAt, renewalPeriod),
            ),
            requestEvent,
        )

    /**
     * Builds a sign_message success response.
     */
    suspend fun respondSignMessage(
        requestEvent: LnZapPaymentRequestEvent,
        message: String,
        signature: String,
    ): LnZapPaymentResponseEvent =
        buildResponse(
            SignMessageSuccessResponse(SignMessageSuccessResponse.SignMessageResult(message, signature)),
            requestEvent,
        )

    // --- Notification builders ---

    /**
     * Builds a payment_received notification event.
     */
    suspend fun notifyPaymentReceived(
        clientPubkey: HexKey,
        transaction: NwcTransaction,
    ): NwcNotificationEvent =
        NwcNotificationEvent.createNotification(
            notification = PaymentReceivedNotification(transaction),
            clientPubkey = clientPubkey,
            signer = signer,
        )

    /**
     * Builds a payment_sent notification event.
     */
    suspend fun notifyPaymentSent(
        clientPubkey: HexKey,
        transaction: NwcTransaction,
    ): NwcNotificationEvent =
        NwcNotificationEvent.createNotification(
            notification = PaymentSentNotification(transaction),
            clientPubkey = clientPubkey,
            signer = signer,
        )

    /**
     * Builds a notification event from any [Notification] object.
     */
    suspend fun buildNotification(
        notification: Notification,
        clientPubkey: HexKey,
    ): NwcNotificationEvent =
        NwcNotificationEvent.createNotification(
            notification = notification,
            clientPubkey = clientPubkey,
            signer = signer,
        )

    // --- Filter helpers ---

    /**
     * Creates a filter to subscribe for incoming client requests.
     * Use this to subscribe on your relay.
     */
    fun requestsFilter(since: Long? = null): Filter =
        Filter(
            kinds = listOf(LnZapPaymentRequestEvent.KIND),
            tags = mapOf("p" to listOf(signer.pubKey)),
            since = since,
        )
}
