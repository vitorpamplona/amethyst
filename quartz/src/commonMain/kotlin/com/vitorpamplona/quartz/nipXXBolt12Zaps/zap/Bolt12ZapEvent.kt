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
package com.vitorpamplona.quartz.nipXXBolt12Zaps.zap

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.hints.AddressHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.EventHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.PubKeyHintProvider
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip01Core.tags.events.ETag
import com.vitorpamplona.quartz.nip01Core.tags.kinds.KindTag
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip50Search.SearchableEvent
import com.vitorpamplona.quartz.nipXXBolt12Zaps.intent.Bolt12ZapIntentEvent
import com.vitorpamplona.quartz.nipXXBolt12Zaps.tags.AmountTag
import com.vitorpamplona.quartz.nipXXBolt12Zaps.tags.DescriptionTag
import com.vitorpamplona.quartz.nipXXBolt12Zaps.tags.OfferTag
import com.vitorpamplona.quartz.nipXXBolt12Zaps.tags.PayerTag
import com.vitorpamplona.quartz.nipXXBolt12Zaps.tags.ProofTag
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * NIP-XX: BOLT12 Zaps — the **zap event** (kind 9736).
 *
 * A public, self-verifying proof that a BOLT12 payment was made to the author of
 * a profile, event, or addressable event. It carries:
 *  - the serialized kind 9737 zap intent in its `description` tag,
 *  - the recipient / amount / offer copied from that intent, and
 *  - the settled BOLT12 `lnp` payer proof in its `proof` tag.
 *
 * This is the only event counted as a BOLT12 zap. Counting clients MUST run it
 * through the validator (structure + intent match + payer-proof binding) and
 * deduplicate by the proof's `invoice_payment_hash` before adding its amount.
 */
@Immutable
class Bolt12ZapEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig),
    EventHintProvider,
    AddressHintProvider,
    PubKeyHintProvider,
    SearchableEvent {
    // The public zap comment; it mirrors the embedded intent's content.
    override fun indexableContent() = content

    override fun pubKeyHints() = tags.mapNotNull(PTag::parseAsHint)

    override fun linkedPubKeys() = tags.mapNotNull(PTag::parseKey)

    override fun eventHints() = tags.mapNotNull(ETag::parseAsHint)

    override fun linkedEventIds() = tags.mapNotNull(ETag::parseId)

    override fun addressHints() = tags.mapNotNull(ATag::parseAsHint)

    override fun linkedAddressIds() = tags.mapNotNull(ATag::parseAddressId)

    /** The raw serialized zap intent JSON from the `description` tag. */
    fun description() = tags.firstNotNullOfOrNull(DescriptionTag::parse)

    /** The parsed & typed embedded zap intent, or null if it isn't a valid kind 9737 event. */
    val zapIntent: Bolt12ZapIntentEvent? by lazy { containedIntent() }

    private fun containedIntent(): Bolt12ZapIntentEvent? =
        try {
            description()?.ifBlank { null }?.let { Event.fromJson(it) } as? Bolt12ZapIntentEvent
        } catch (e: Exception) {
            Log.w("Bolt12ZapEvent", "Failed to parse embedded zap intent in event $id", e)
            null
        }

    /** The recipient pubkey (`p` tag). */
    fun recipient() = tags.firstNotNullOfOrNull(PTag::parseKey)

    /** The claimed amount in millisatoshis (`amount` tag). Verified against the payer proof. */
    fun amount() = tags.firstNotNullOfOrNull(AmountTag::parse)

    /** The canonical raw BOLT12 offer (`offer` tag). */
    fun offer() = tags.firstNotNullOfOrNull(OfferTag::parse)

    /** The bech32 `lnp` payer proof (`proof` tag). */
    fun payerProof() = tags.firstNotNullOfOrNull(ProofTag::parse)

    /** The payer pubkey (`P` tag), present only for publicly-attributed zaps. */
    fun payer() = tags.firstNotNullOfOrNull(PayerTag::parse)

    /** The event being zapped, if any (`e` tag). */
    fun zappedEvent() = tags.firstNotNullOfOrNull(ETag::parseId)

    /** The addressable event being zapped, if any (`a` tag). */
    fun zappedAddress() = tags.firstNotNullOfOrNull(ATag::parseAddressId)

    /** The kind of the target event, if declared (`k` tag). */
    fun zappedKind() = tags.firstNotNullOfOrNull(KindTag::parse)

    /** True when neither `e` nor `a` is present — the zap targets the recipient's profile. */
    fun isProfileZap() = zappedEvent() == null && zappedAddress() == null

    /** True when the zap is anonymous: it carries no `P` tag. */
    fun isAnonymous() = payer() == null

    companion object {
        const val KIND = 9736

        /**
         * Assemble a kind 9736 zap event from a **signed** zap intent and a settled
         * payer proof. The recipient, amount, offer, and target (`e`/`a`/`k`) tags
         * and the content are copied from the intent so they match, as the NIP
         * requires.
         *
         * @param payerPubKey when non-null, added as the uppercase `P` tag (a
         *   publicly-attributed zap). It MUST be the same key that will sign this
         *   event. Anonymous zaps pass null and sign with an ephemeral key.
         */
        fun build(
            signedIntent: Bolt12ZapIntentEvent,
            payerProof: String,
            payerPubKey: HexKey? = null,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<Bolt12ZapEvent>.() -> Unit = {},
        ): EventTemplate<Bolt12ZapEvent> {
            val recipient = requireNotNull(signedIntent.recipient()) { "zap intent is missing its p tag" }
            val amount = requireNotNull(signedIntent.amount()) { "zap intent is missing its amount tag" }
            val offer = requireNotNull(signedIntent.offer()) { "zap intent is missing its offer tag" }

            return eventTemplate(KIND, signedIntent.content, createdAt) {
                description(signedIntent.toJson())
                recipient(recipient)
                amountInMillisats(amount)
                offer(offer)
                proof(payerProof)
                payerPubKey?.let { payer(it) }
                // Copy the target tags verbatim so the zap and intent match exactly.
                signedIntent.tags.firstOrNull { ETag.isTagged(it) }?.let { add(it) }
                signedIntent.tags.firstOrNull { ATag.isTagged(it) }?.let { add(it) }
                signedIntent.tags.firstOrNull { KindTag.match(it) }?.let { add(it) }
                initializer()
            }
        }
    }
}
