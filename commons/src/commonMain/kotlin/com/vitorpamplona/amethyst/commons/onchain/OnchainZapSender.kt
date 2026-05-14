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
package com.vitorpamplona.amethyst.commons.onchain

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nipBCOnchainZaps.build.OnchainZapBuilder
import com.vitorpamplona.quartz.nipBCOnchainZaps.chain.OnchainBackend
import com.vitorpamplona.quartz.nipBCOnchainZaps.psbt.Psbt
import com.vitorpamplona.quartz.nipBCOnchainZaps.psbt.PsbtFinalizer
import com.vitorpamplona.quartz.nipBCOnchainZaps.taproot.TaprootAddress
import com.vitorpamplona.quartz.nipBCOnchainZaps.zap.OnchainZapEvent
import kotlin.coroutines.cancellation.CancellationException

/** The stage a NIP-BC onchain zap send reached before it finished or failed. */
enum class OnchainZapSendStage {
    /** Querying the chain backend for the sender's spendable UTXOs. */
    LOADING_UTXOS,

    /** Selecting coins and assembling the unsigned PSBT. */
    BUILDING,

    /** Signing the PSBT inputs and finalizing the transaction. */
    SIGNING,

    /** Broadcasting the signed transaction to the network. */
    BROADCASTING,

    /** Publishing the kind:8333 zap receipt to relays. */
    PUBLISHING,
}

/** Outcome of an [OnchainZapSender.send] attempt. */
sealed interface OnchainZapSendResult {
    /**
     * The transaction was broadcast and the zap receipt was published.
     *
     * @property txid The broadcast Bitcoin transaction id.
     * @property receiptEventId The id of the published kind:8333 event.
     * @property feeSats Miner fee paid.
     * @property changeSats Change returned to the sender (0 if none).
     */
    data class Success(
        val txid: String,
        val receiptEventId: HexKey,
        val feeSats: Long,
        val changeSats: Long,
    ) : OnchainZapSendResult

    /**
     * The send failed at [stage]. The transaction was NOT broadcast unless
     * [stage] is [OnchainZapSendStage.PUBLISHING], in which case the payment
     * went through but the receipt could not be published.
     */
    data class Failure(
        val stage: OnchainZapSendStage,
        val message: String,
        val cause: Throwable? = null,
        /** Non-null when the payment was broadcast but a later stage failed. */
        val broadcastTxid: String? = null,
    ) : OnchainZapSendResult
}

/**
 * Orchestrates a NIP-BC onchain zap end to end:
 * load UTXOs → build PSBT → sign → finalize → broadcast → publish kind:8333.
 *
 * Stateless and platform-agnostic — it takes the chain backend, the signer,
 * and a publish callback, so the same pipeline works from the Android app, the
 * CLI, or tests. Per-stage failures are reported as
 * [OnchainZapSendResult.Failure] rather than thrown, except [CancellationException]
 * which always propagates.
 */
object OnchainZapSender {
    /**
     * @param backend Chain data source (UTXOs + broadcast).
     * @param signer The sender's signer; must support `signPsbt`.
     * @param senderPubKey The sender's x-only Nostr pubkey (hex).
     * @param recipientPubKey The recipient's x-only Nostr pubkey (hex).
     * @param amountSats Amount to pay the recipient.
     * @param feeRateSatPerVByte Target fee rate.
     * @param comment Optional human-readable comment for the receipt's content.
     * @param zappedEvent The event being zapped, or null for a profile zap.
     * @param publish Publishes the signed kind:8333 receipt and returns the event.
     */
    suspend fun send(
        backend: OnchainBackend,
        signer: NostrSigner,
        senderPubKey: HexKey,
        recipientPubKey: HexKey,
        amountSats: Long,
        feeRateSatPerVByte: Double,
        comment: String,
        zappedEvent: EventHintBundle<out Event>?,
        publish: suspend (EventTemplate<OnchainZapEvent>) -> Event,
    ): OnchainZapSendResult {
        // 1. Load the sender's UTXOs.
        val utxos =
            try {
                val address = TaprootAddress.fromPubKey(senderPubKey)
                backend.getUtxosForAddress(address)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                return fail(OnchainZapSendStage.LOADING_UTXOS, "Could not load your Bitcoin balance", e)
            }

        // 2. Coin-select and assemble the unsigned PSBT.
        val built =
            try {
                OnchainZapBuilder.build(
                    senderPubKey = senderPubKey,
                    recipientPubKey = recipientPubKey,
                    amountSats = amountSats,
                    feeRateSatPerVByte = feeRateSatPerVByte,
                    availableUtxos = utxos,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                return fail(OnchainZapSendStage.BUILDING, e.message ?: "Could not build the transaction", e)
            }

        // 3. Sign and finalize.
        val rawTxHex =
            try {
                val signedHex = signer.signPsbt(built.psbt.toHex())
                val signedPsbt = Psbt.parse(signedHex)
                if (!PsbtFinalizer.isFullySigned(signedPsbt)) {
                    return fail(
                        OnchainZapSendStage.SIGNING,
                        "The signer did not sign every input",
                    )
                }
                PsbtFinalizer.finalizeToHex(signedPsbt)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                return fail(OnchainZapSendStage.SIGNING, e.message ?: "Could not sign the transaction", e)
            }

        // 4. Broadcast.
        val txid =
            try {
                backend.broadcast(rawTxHex)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                return fail(OnchainZapSendStage.BROADCASTING, "Could not broadcast the transaction", e)
            }

        // 5. Publish the kind:8333 receipt. The payment is already on-chain at
        // this point, so a failure here keeps the txid for retry/diagnostics.
        val receiptId =
            try {
                val template =
                    if (zappedEvent != null) {
                        OnchainZapEvent.build(txid, recipientPubKey, amountSats, zappedEvent, comment)
                    } else {
                        OnchainZapEvent.buildProfileZap(txid, recipientPubKey, amountSats, comment)
                    }
                publish(template).id
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                return OnchainZapSendResult.Failure(
                    stage = OnchainZapSendStage.PUBLISHING,
                    message = "Payment sent, but the zap receipt could not be published",
                    cause = e,
                    broadcastTxid = txid,
                )
            }

        return OnchainZapSendResult.Success(
            txid = txid,
            receiptEventId = receiptId,
            feeSats = built.feeSats,
            changeSats = built.changeSats,
        )
    }

    private fun fail(
        stage: OnchainZapSendStage,
        message: String,
        cause: Throwable? = null,
    ) = OnchainZapSendResult.Failure(stage, message, cause)
}
