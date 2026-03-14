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
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal

/**
 * High-level NIP-47 Wallet Connect client.
 *
 * Simplifies the NWC protocol by handling URI parsing, signer creation,
 * event building, filter construction, and response decryption.
 *
 * Usage:
 * ```kotlin
 * val client = Nip47Client.fromUri("nostr+walletconnect://pubkey?relay=...&secret=...")
 *
 * // Build a request event
 * val requestEvent = client.payInvoice("lnbc50n1...")
 *
 * // Send requestEvent to client.relayUrl via your relay connection
 * // Subscribe using client.responseFilter(requestEvent.id) for the response
 *
 * // When response arrives:
 * val response = client.parseResponse(responseEvent)
 * when (response) {
 *     is PayInvoiceSuccessResponse -> println("Paid! Preimage: ${response.result?.preimage}")
 *     is NwcErrorResponse -> println("Error: ${response.error?.message}")
 * }
 * ```
 */
class Nip47Client(
    val walletPubKeyHex: HexKey,
    val relayUrl: NormalizedRelayUrl,
    val signer: NostrSigner,
    val useNip44: Boolean = false,
) {
    companion object {
        /**
         * Creates an Nip47Client from a NWC connection URI string.
         *
         * @param uri NWC URI (e.g., "nostr+walletconnect://pubkey?relay=...&secret=...")
         * @throws IllegalArgumentException if the URI is invalid or has no secret
         */
        fun fromUri(uri: String): Nip47Client {
            val config = Nip47WalletConnect.parse(uri)
            return fromNip47URI(config)
        }

        /**
         * Creates an Nip47Client from parsed NWC connection details.
         *
         * @param config parsed NWC URI with wallet pubkey, relay, and secret
         * @throws IllegalArgumentException if config has no secret
         */
        fun fromNip47URI(config: Nip47WalletConnect.Nip47URINorm): Nip47Client {
            val secret = config.secret ?: throw IllegalArgumentException("NWC connection requires a secret")
            val signer = NostrSignerInternal(KeyPair(secret.hexToByteArray()))
            return Nip47Client(
                walletPubKeyHex = config.pubKeyHex,
                relayUrl = config.relayUri,
                signer = signer,
            )
        }
    }

    // --- Request builders ---

    /**
     * Builds a pay_invoice request event.
     */
    suspend fun payInvoice(
        bolt11: String,
        amount: Long? = null,
    ): LnZapPaymentRequestEvent =
        buildRequest(
            if (amount != null) {
                PayInvoiceMethod.create(bolt11, amount)
            } else {
                PayInvoiceMethod.create(bolt11)
            },
        )

    /**
     * Builds a pay_keysend request event.
     */
    suspend fun payKeysend(
        amount: Long,
        pubkey: String,
        preimage: String? = null,
        tlvRecords: List<TlvRecord>? = null,
    ): LnZapPaymentRequestEvent = buildRequest(PayKeysendMethod.create(amount, pubkey, preimage, tlvRecords))

    /**
     * Builds a get_balance request event.
     */
    suspend fun getBalance(): LnZapPaymentRequestEvent = buildRequest(GetBalanceMethod.create())

    /**
     * Builds a get_info request event.
     */
    suspend fun getInfo(): LnZapPaymentRequestEvent = buildRequest(GetInfoMethod.create())

    /**
     * Builds a make_invoice request event.
     */
    suspend fun makeInvoice(
        amount: Long,
        description: String? = null,
        descriptionHash: String? = null,
        expiry: Long? = null,
    ): LnZapPaymentRequestEvent = buildRequest(MakeInvoiceMethod.create(amount, description, descriptionHash, expiry))

    /**
     * Builds a lookup_invoice request event by payment hash.
     */
    suspend fun lookupInvoiceByHash(paymentHash: String): LnZapPaymentRequestEvent =
        buildRequest(LookupInvoiceMethod.createByHash(paymentHash))

    /**
     * Builds a lookup_invoice request event by BOLT11 invoice.
     */
    suspend fun lookupInvoiceByInvoice(invoice: String): LnZapPaymentRequestEvent =
        buildRequest(LookupInvoiceMethod.createByInvoice(invoice))

    /**
     * Builds a list_transactions request event.
     */
    suspend fun listTransactions(
        from: Long? = null,
        until: Long? = null,
        limit: Int? = null,
        offset: Int? = null,
        unpaid: Boolean? = null,
        type: String? = null,
    ): LnZapPaymentRequestEvent = buildRequest(ListTransactionsMethod.create(from, until, limit, offset, unpaid, type))

    /**
     * Builds a get_budget request event.
     */
    suspend fun getBudget(): LnZapPaymentRequestEvent = buildRequest(GetBudgetMethod.create())

    /**
     * Builds a sign_message request event.
     */
    suspend fun signMessage(message: String): LnZapPaymentRequestEvent = buildRequest(SignMessageMethod.create(message))

    /**
     * Builds a make_hold_invoice request event.
     */
    suspend fun makeHoldInvoice(
        amount: Long,
        paymentHash: String,
        description: String? = null,
        descriptionHash: String? = null,
        expiry: Long? = null,
        minCltvExpiryDelta: Int? = null,
    ): LnZapPaymentRequestEvent =
        buildRequest(MakeHoldInvoiceMethod.create(amount, paymentHash, description, descriptionHash, expiry, minCltvExpiryDelta))

    /**
     * Builds a cancel_hold_invoice request event.
     */
    suspend fun cancelHoldInvoice(paymentHash: String): LnZapPaymentRequestEvent =
        buildRequest(CancelHoldInvoiceMethod.create(paymentHash))

    /**
     * Builds a settle_hold_invoice request event.
     */
    suspend fun settleHoldInvoice(preimage: String): LnZapPaymentRequestEvent =
        buildRequest(SettleHoldInvoiceMethod.create(preimage))

    /**
     * Builds a request event from any [Request] object.
     * This is the low-level method used by all convenience methods above.
     */
    suspend fun buildRequest(request: Request): LnZapPaymentRequestEvent =
        LnZapPaymentRequestEvent.createRequest(
            request = request,
            walletServicePubkey = walletPubKeyHex,
            signer = signer,
            useNip44 = useNip44,
        )

    // --- Response handling ---

    /**
     * Decrypts and parses a response event from the wallet.
     */
    suspend fun parseResponse(event: LnZapPaymentResponseEvent): Response = event.decrypt(signer)

    /**
     * Decrypts and parses a notification event from the wallet.
     */
    suspend fun parseNotification(event: NwcNotificationEvent): Notification = event.decryptNotification(signer)

    // --- Filter helpers ---

    /**
     * Creates a filter to subscribe for responses to a specific request.
     * Use this to subscribe on [relayUrl] after sending a request event.
     */
    fun responseFilter(requestEventId: HexKey): Filter =
        Filter(
            kinds = listOf(LnZapPaymentResponseEvent.KIND),
            authors = listOf(walletPubKeyHex),
            tags = mapOf("e" to listOf(requestEventId)),
        )

    /**
     * Creates a filter to subscribe for all responses from the wallet
     * directed to this client.
     */
    fun allResponsesFilter(since: Long? = null): Filter =
        Filter(
            kinds = listOf(LnZapPaymentResponseEvent.KIND),
            authors = listOf(walletPubKeyHex),
            tags = mapOf("p" to listOf(signer.pubKey)),
            since = since,
        )

    /**
     * Creates a filter to subscribe for wallet notifications.
     */
    fun notificationsFilter(since: Long? = null): Filter =
        Filter(
            kinds = listOf(NwcNotificationEvent.KIND, NwcNotificationEvent.LEGACY_KIND),
            authors = listOf(walletPubKeyHex),
            tags = mapOf("p" to listOf(signer.pubKey)),
            since = since,
        )

    /**
     * Creates a filter to fetch the wallet's info event (kind 13194).
     */
    fun infoFilter(): Filter =
        Filter(
            kinds = listOf(NwcInfoEvent.KIND),
            authors = listOf(walletPubKeyHex),
            limit = 1,
        )
}
