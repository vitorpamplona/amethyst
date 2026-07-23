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
package com.vitorpamplona.quartz.nipXXBolt12Zaps.builder

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nipXXBolt12Zaps.intent.Bolt12ZapIntentEvent
import com.vitorpamplona.quartz.nipXXBolt12Zaps.verify.Bolt12ZapValidator
import com.vitorpamplona.quartz.nipXXBolt12Zaps.zap.Bolt12ZapEvent
import com.vitorpamplona.quartz.utils.Hex
import com.vitorpamplona.quartz.utils.RandomInstance
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * Assembles the two NIP-XX events in the order the payment flow requires:
 *
 *  1. [buildIntent] — sign a kind 9737 zap intent *before* paying.
 *  2. Pay the offer, putting [payerNote] in the BOLT12 `invreq_payer_note`, and
 *     collect a settled `lnp` payer proof from the wallet.
 *  3. [buildZap] — wrap the signed intent and the proof into a kind 9736 zap.
 *
 * This is the on-Nostr assembly only; requesting the BOLT12 invoice, paying it,
 * and obtaining the payer proof are the wallet's job (no Amethyst payment rail
 * exposes `lnp` proofs yet).
 */
object Bolt12ZapBuilder {
    /** A fresh 128-bit `zap_id` as lowercase hex. */
    fun randomZapId(): String = Hex.encode(RandomInstance.bytes(16))

    /**
     * The value the payer MUST place in the BOLT12 `invreq_payer_note` when paying
     * the offer, binding the settled payment to [intent].
     */
    fun payerNote(intent: Bolt12ZapIntentEvent): String = Bolt12ZapValidator.NIP_URI_PREFIX + intent.id

    /** Sign a zap intent targeting a specific event. */
    suspend fun buildIntent(
        signer: NostrSigner,
        recipientPubKey: HexKey,
        amountInMillisats: Long,
        offer: String,
        zappedEvent: EventHintBundle<out Event>,
        comment: String = "",
        zapId: String = randomZapId(),
        createdAt: Long = TimeUtils.now(),
    ): Bolt12ZapIntentEvent =
        signer.sign(
            Bolt12ZapIntentEvent.build(recipientPubKey, amountInMillisats, offer, zapId, zappedEvent, comment, createdAt),
        )

    /** Sign a zap intent targeting a recipient's profile (no event / address). */
    suspend fun buildProfileIntent(
        signer: NostrSigner,
        recipientPubKey: HexKey,
        amountInMillisats: Long,
        offer: String,
        comment: String = "",
        zapId: String = randomZapId(),
        createdAt: Long = TimeUtils.now(),
    ): Bolt12ZapIntentEvent =
        signer.sign(
            Bolt12ZapIntentEvent.buildProfileZap(recipientPubKey, amountInMillisats, offer, zapId, comment, createdAt),
        )

    /**
     * Sign the final kind 9736 zap from a [signedIntent] and a settled [payerProof].
     *
     * @param anonymous when true, no `P` tag is added; the caller MUST pass an
     *   ephemeral [signer] (the same key that signed [signedIntent]). When false,
     *   the signer's pubkey is added as `P`, publicly attributing the zap.
     */
    suspend fun buildZap(
        signer: NostrSigner,
        signedIntent: Bolt12ZapIntentEvent,
        payerProof: String,
        anonymous: Boolean = false,
        createdAt: Long = TimeUtils.now(),
    ): Bolt12ZapEvent {
        val payerPubKey = if (anonymous) null else signer.pubKey
        return signer.sign(
            Bolt12ZapEvent.build(signedIntent, payerProof, payerPubKey, createdAt),
        )
    }
}
