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
package com.vitorpamplona.amethyst.model

import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.onchain.OnchainZapSendError
import com.vitorpamplona.amethyst.commons.onchain.OnchainZapSendResult
import com.vitorpamplona.amethyst.commons.onchain.OnchainZapSendStage
import com.vitorpamplona.amethyst.commons.onchain.OnchainZapSender
import com.vitorpamplona.amethyst.commons.onchain.OnchainZapShare
import com.vitorpamplona.amethyst.model.nip47WalletConnect.NwcSignerState
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip47WalletConnect.Nip47WalletConnect
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.IErrorResponseLike
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.NwcMethod
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.PayMethod
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.PaySuccessResponse
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.Request
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.Response
import com.vitorpamplona.quartz.nip57Zaps.LnZapEvent
import com.vitorpamplona.quartz.nip57Zaps.LnZapRequestEvent
import com.vitorpamplona.quartz.nipB1Bolt12Zaps.builder.Bolt12ZapBuilder
import com.vitorpamplona.quartz.nipB1Bolt12Zaps.verify.Bolt12ZapValidation
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.launch
import java.math.BigDecimal
import kotlin.coroutines.cancellation.CancellationException

private const val ONCHAIN_BACKEND_NOT_CONFIGURED = "Bitcoin chain backend is not configured"

/**
 * Zap and payment orchestration for an [Account]: NIP-57 zap requests, NIP-47
 * NWC wallet requests (with spoof tracking), NIP-B1 BOLT12 zaps, and NIP-BC
 * onchain zaps/sends. Event building lives in the commons ZapActions/
 * Bolt12ZapActions; this class wires wallet selection, signing, and relay
 * routing to the account.
 */
class AccountZapActions(
    private val account: Account,
) {
    suspend fun createZapRequestFor(
        event: Event,
        pollOption: Int?,
        message: String = "",
        zapType: LnZapEvent.ZapType,
        toUser: User?,
        additionalRelays: Set<NormalizedRelayUrl>? = null,
        amountMillisats: Long? = null,
        lnurl: String? = null,
    ) = LnZapRequestEvent.create(
        zappedEvent = event,
        // Where the provider should publish the receipt. Zapping group content pins that to the room's
        // host relay: the receipt belongs where the message it pays for lives, so the room can show it
        // and the recipient's group query can find it — and, for a private or closed group, so a
        // kind-9735 naming the room never lands on a relay outside it. Everything else keeps the
        // ordinary NIP-65 inbox routing.
        relays =
            account.cache.relayGroupHostsFor(event).ifEmpty {
                account.nip65RelayList.inboxFlow.value
            } + (additionalRelays ?: emptySet()),
        signer = account.signer,
        pollOption = pollOption,
        message = message,
        zapType = zapType,
        toUserPubHex = toUser?.pubkeyHex,
        amountMillisats = amountMillisats,
        lnurl = lnurl,
    )

    suspend fun calculateIfNoteWasZappedByAccount(
        zappedNote: Note?,
        afterTimeInSeconds: Long,
    ): Boolean = zappedNote?.isZappedBy(account.userProfile(), afterTimeInSeconds, account) == true

    suspend fun calculateZappedAmount(zappedNote: Note): BigDecimal = zappedNote.zappedAmountWithNWCPayments(account.nip47SignerState)

    suspend fun sendNwcRequest(
        request: Request,
        onTimeout: () -> Unit = {},
        onResponse: (Response?) -> Unit,
    ) {
        val (event, relay) = account.nip47SignerState.sendNwcRequest(request, onTimeout, onResponse)
        account.client.publish(event, setOf(relay))
    }

    suspend fun sendNwcRequestToWallet(
        walletUri: Nip47WalletConnect.Nip47URINorm,
        request: Request,
        onTimeout: () -> Unit = {},
        onResponse: (Response?) -> Unit,
    ): HexKey {
        val (event, relay) = account.nip47SignerState.sendNwcRequestToWallet(walletUri, request, onTimeout, onResponse)
        account.client.publish(event, setOf(relay))
        return event.id
    }

    /**
     * Number of spoofed (wrong-author) NIP-47 replies that have arrived for
     * the given request id. 0 if the request is unknown or already resolved.
     */
    fun nwcSpoofAttempts(requestId: HexKey): Int = LocalCache.paymentTracker.spoofAttemptsFor(requestId)

    /**
     * Removes a pending NIP-47 request from the tracker. Call this when the
     * UI gives up waiting (timeout) so the entry doesn't stick around.
     */
    fun cleanupNwcRequest(requestId: HexKey) = LocalCache.paymentTracker.cleanup(requestId)

    /**
     * @param onTimeout invoked when no kind-23195 reply arrives before
     *   [NwcSignerState.NWC_RESPONSE_TIMEOUT_MS]. Pass one on any path with a user
     *   watching: without it a response lost in transit is indistinguishable from
     *   the action never having happened.
     */
    suspend fun sendZapPaymentRequestFor(
        bolt11: String,
        zappedNote: Note?,
        onTimeout: () -> Unit = {},
        metadata: Map<String, Any?>? = null,
        onResponse: (Response?) -> Unit,
    ) {
        val (event, relay) = account.nip47SignerState.sendZapPaymentRequestFor(bolt11, zappedNote, onTimeout, metadata, onResponse)
        account.client.publish(event, setOf(relay))
    }

    /**
     * True when the default NWC wallet advertises the nwc#2 `pay` method — the rail a
     * BOLT12 zap needs to obtain a payer proof. Read from the wallet's cached kind:13194
     * info event (its capability advertisement), which [NwcSignerState] already refreshes
     * on wallet change. A missing/unfetched info event reads as false, so the zap path
     * falls back to lightning rather than attempting a `pay` the wallet can't honor.
     */
    fun defaultWalletSupportsBolt12Pay(): Boolean {
        val uri = account.nip47SignerState.defaultWalletUri.value ?: return false
        return account.nip47SignerState.infoCache
            ?.current(uri)
            ?.supportsMethod(NwcMethod.PAY) == true
    }

    /**
     * Sends a NIP-B1 BOLT12 zap to [recipientPubKey] over the default NWC wallet.
     *
     * Signs a kind 9737 intent, pays [offer] via the nwc#2 `pay` method with the
     * intent-bound `payer_note`, then — only if the wallet returns a payer proof that
     * validates — builds, self-consumes, and publishes the kind 9736 zap. Validation
     * is the fail-safe: a wallet that drops or misroutes the note yields a proof that
     * fails the binding check, so no invalid receipt is ever published (the payment
     * still happened; [onError] reports "paid, no receipt"). [zappedEvent] is null for
     * a profile zap. Requires an NWC wallet (see [hasNwcWallet]); BOLT12 zaps have no
     * external-wallet or LNURL fallback because only NWC returns the proof.
     */
    suspend fun sendBolt12Zap(
        zappedEvent: Event?,
        recipientPubKey: HexKey,
        offer: String,
        amountMillisats: Long,
        message: String,
        zapType: LnZapEvent.ZapType,
        // (messageResId, detail) — the caller localizes; detail carries a wallet error, if any.
        onError: (Int, String?) -> Unit,
        onProcessed: () -> Unit,
    ) {
        // NONZAP means "pay, but publish no receipt" — settle the offer without binding
        // a zap intent or emitting a 9736, matching the privacy of a bolt11 NONZAP.
        if (zapType == LnZapEvent.ZapType.NONZAP) {
            sendNwcRequest(PayMethod.create("bitcoin:?lno=$offer", amountMillisats)) { response ->
                account.scope.launch {
                    if (response is IErrorResponseLike) onError(R.string.bolt12_payment_failed, response.errorMessage())
                    onProcessed()
                }
            }
            return
        }

        val anonymous = zapType == LnZapEvent.ZapType.ANONYMOUS
        // The 9737 intent and the 9736 zap MUST be signed by the same key. An anonymous
        // zap uses a fresh ephemeral key so it carries no `P` tag and isn't traceable.
        val zapSigner = if (anonymous) NostrSignerInternal(KeyPair()) else account.signer

        val intent =
            if (zappedEvent == null) {
                Bolt12ZapBuilder.buildProfileIntent(zapSigner, recipientPubKey, amountMillisats, offer, message)
            } else {
                Bolt12ZapBuilder.buildIntent(zapSigner, recipientPubKey, amountMillisats, offer, EventHintBundle(zappedEvent), message)
            }

        val payerNote = Bolt12ZapBuilder.payerNote(intent)

        sendNwcRequest(PayMethod.create("bitcoin:?lno=$offer", amountMillisats, payerNote)) { response ->
            account.scope.launch {
                // try/finally so a failure while assembling/publishing the receipt (e.g. a
                // remote signer error) still steps progress and surfaces an error, instead
                // of vanishing as an uncaught coroutine exception. The payment already
                // settled at this point, so such a failure means "paid, no receipt".
                try {
                    when (response) {
                        is PaySuccessResponse -> {
                            val proof = response.result?.payer_proof
                            if (proof.isNullOrBlank()) {
                                onError(R.string.bolt12_zap_paid_no_receipt, null)
                            } else {
                                val zap = Bolt12ZapBuilder.buildZap(zapSigner, intent, proof, anonymous)
                                if (account.cache.bolt12ZapValidator.validate(zap, verifyEventSignature = false) is Bolt12ZapValidation.Valid) {
                                    account.cache.justConsumeMyOwnEvent(zap)
                                    account.client.publish(zap, account.broadcaster.computeRelayListToBroadcast(zap))
                                } else {
                                    onError(R.string.bolt12_zap_invalid_receipt, null)
                                }
                            }
                        }

                        is IErrorResponseLike -> onError(R.string.bolt12_payment_failed, response.errorMessage())

                        else -> onError(R.string.bolt12_zap_paid_no_receipt, null)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w("Account", "BOLT12 zap receipt assembly failed after payment", e)
                    onError(R.string.bolt12_zap_paid_no_receipt, null)
                } finally {
                    onProcessed()
                }
            }
        }
    }

    suspend fun createZapRequestFor(
        user: User,
        message: String = "",
        zapType: LnZapEvent.ZapType,
        amountMillisats: Long? = null,
        lnurl: String? = null,
    ): LnZapRequestEvent {
        val zapRequest =
            LnZapRequestEvent.create(
                userHex = user.pubkeyHex,
                relays = account.nip65RelayList.inboxFlow.value + (user.inboxRelays() ?: emptyList()),
                signer = account.signer,
                message = message,
                zapType = zapType,
                amountMillisats = amountMillisats,
                lnurl = lnurl,
            )

        account.cache.justConsumeMyOwnEvent(zapRequest)
        return zapRequest
    }

    private fun onchainBackendNotConfigured() =
        OnchainZapSendResult.Failure(
            OnchainZapSendStage.LOADING_UTXOS,
            OnchainZapSendError.BACKEND_NOT_CONFIGURED,
            ONCHAIN_BACKEND_NOT_CONFIGURED,
        )

    /**
     * Send a NIP-BC onchain zap: build a Bitcoin transaction paying the recipient's
     * derived Taproot address, sign it, broadcast it, and publish the kind:8333
     * zap receipt. Pass [zappedEvent] to attribute the zap to a specific event, or
     * leave it null for a profile zap.
     */
    suspend fun sendOnchainZap(
        recipientPubKey: HexKey,
        amountSats: Long,
        feeRateSatPerVByte: Double,
        comment: String = "",
        zappedEvent: EventHintBundle<out Event>? = null,
    ): OnchainZapSendResult {
        val backend =
            account.cache.onchainBackend
                ?: return onchainBackendNotConfigured()
        return OnchainZapSender.send(
            backend = backend,
            signer = account.signer,
            senderPubKey = account.signer.pubKey,
            recipientPubKey = recipientPubKey,
            amountSats = amountSats,
            feeRateSatPerVByte = feeRateSatPerVByte,
            comment = comment,
            zappedEvent = zappedEvent,
        ) { template -> account.broadcaster.signAndComputeBroadcast(template) }
    }

    /**
     * Pay an explicit Bitcoin address (e.g. a profile's NIP-A3 `bitcoin`
     * payment target) from the NIP-BC Taproot wallet. A plain wallet send —
     * no kind:8333 receipt is published. See [OnchainZapSender.sendToAddress].
     */
    suspend fun sendOnchainToAddress(
        recipientAddress: String,
        amountSats: Long,
        feeRateSatPerVByte: Double,
    ): OnchainZapSendResult {
        val backend =
            account.cache.onchainBackend
                ?: return onchainBackendNotConfigured()
        return OnchainZapSender.sendToAddress(
            backend = backend,
            signer = account.signer,
            senderPubKey = account.signer.pubKey,
            recipientAddress = recipientAddress,
            amountSats = amountSats,
            feeRateSatPerVByte = feeRateSatPerVByte,
        )
    }

    /**
     * Send a NIP-BC onchain split zap: a single Bitcoin transaction paying
     * each recipient their precomputed share, plus one kind:8333 receipt per
     * recipient. See [OnchainZapSender.sendSplit] for failure semantics.
     */
    suspend fun sendOnchainZapWithSplits(
        recipients: List<OnchainZapShare>,
        feeRateSatPerVByte: Double,
        comment: String = "",
        zappedEvent: EventHintBundle<out Event>? = null,
    ): OnchainZapSendResult {
        val backend =
            account.cache.onchainBackend
                ?: return onchainBackendNotConfigured()
        return OnchainZapSender.sendSplit(
            backend = backend,
            signer = account.signer,
            senderPubKey = account.signer.pubKey,
            recipients = recipients,
            feeRateSatPerVByte = feeRateSatPerVByte,
            comment = comment,
            zappedEvent = zappedEvent,
        ) { template -> account.broadcaster.signAndComputeBroadcast(template) }
    }
}
