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
package com.vitorpamplona.quartz.nipXXBolt12Zaps.verify

import com.vitorpamplona.quartz.nip01Core.crypto.verify
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip01Core.tags.events.ETag
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nipXXBolt12Zaps.bolt12.Bolt12Offer
import com.vitorpamplona.quartz.nipXXBolt12Zaps.bolt12.Bolt12PayerProof
import com.vitorpamplona.quartz.nipXXBolt12Zaps.tags.DescriptionTag
import com.vitorpamplona.quartz.nipXXBolt12Zaps.zap.Bolt12ZapEvent
import com.vitorpamplona.quartz.utils.Hex

/**
 * NIP-XX validator: runs the spec's validation steps over a [Bolt12ZapEvent] and
 * returns a [Bolt12ZapValidation]. A client MUST validate an event with this (and
 * deduplicate the results by [Bolt12ZapValidation.Valid.paymentHashHex]) before
 * counting a BOLT12 zap.
 *
 * The steps mirror the spec:
 *  1. kind 9736 and a valid event signature;
 *  2. zap-event structure (exactly one `description`/`p`, positive `amount`,
 *     canonical `offer`, `lnp` `proof`, at most one `e`/`a` and never both,
 *     `P` == `pubkey` when present);
 *  3. the embedded kind 9737 intent parses, is signed by the same pubkey, and is
 *     itself well-formed;
 *  4. the zap and intent match on `content`, `p`, `amount`, `offer`, `e`, `a`, `k`;
 *  5. the raw BOLT12 offer parses;
 *  6. the `lnp` payer proof decodes and its crypto checks pass (see
 *     [Bolt12ProofVerifier]);
 *  7. the proof binds to this zap: `invreq_payer_note == nostr:nipXX:<intent-id>`,
 *     `invoice_amount == amount`, and the proof matches the offer.
 */
class Bolt12ZapValidator(
    private val proofVerifier: Bolt12ProofVerifier = Bolt12ProofVerifier(),
) {
    fun validate(event: Bolt12ZapEvent): Bolt12ZapValidation {
        // Step 1 — kind and event signature.
        if (event.kind != Bolt12ZapEvent.KIND) return invalid(Reason.WRONG_KIND)
        if (!event.verify()) return invalid(Reason.BAD_EVENT_SIGNATURE)

        // Step 2 — zap-event structure.
        if (event.tags.count(DescriptionTag::isTag) != 1) return invalid(Reason.NOT_EXACTLY_ONE_DESCRIPTION)
        if (event.description() == null) return invalid(Reason.MISSING_DESCRIPTION)

        val recipientCount = event.tags.count { PTag.parseKey(it) != null }
        if (recipientCount != 1) return invalid(Reason.NOT_EXACTLY_ONE_RECIPIENT)
        val recipient = event.recipient() ?: return invalid(Reason.MISSING_RECIPIENT)

        val amount = event.amount() ?: return invalid(Reason.MISSING_AMOUNT)
        if (amount <= 0) return invalid(Reason.NON_POSITIVE_AMOUNT)

        val offer = event.offer() ?: return invalid(Reason.MISSING_OR_INVALID_OFFER)
        val proofStr = event.payerProof() ?: return invalid(Reason.MISSING_OR_INVALID_PROOF)

        val cardinality = checkTargetCardinality(event.tags)
        if (cardinality != null) return invalid(cardinality)

        val payer = event.payer()
        if (payer != null && payer != event.pubKey) return invalid(Reason.PAYER_TAG_MISMATCH)

        // Step 3 — embedded intent.
        val intent = event.zapIntent ?: return invalid(Reason.MISSING_OR_INVALID_INTENT)
        if (!intent.verify()) return invalid(Reason.BAD_INTENT_SIGNATURE)
        if (intent.pubKey != event.pubKey) return invalid(Reason.INTENT_PUBKEY_MISMATCH)
        if (intent.zapId() == null) return invalid(Reason.INVALID_ZAP_ID)

        val intentRecipientCount = intent.tags.count { PTag.parseKey(it) != null }
        if (intentRecipientCount != 1) return invalid(Reason.INTENT_STRUCTURE_INVALID)
        val intentAmount = intent.amount() ?: return invalid(Reason.INTENT_STRUCTURE_INVALID)
        if (intentAmount <= 0) return invalid(Reason.INTENT_STRUCTURE_INVALID)
        if (intent.offer() == null) return invalid(Reason.INTENT_STRUCTURE_INVALID)
        if (checkTargetCardinality(intent.tags) != null) return invalid(Reason.INTENT_STRUCTURE_INVALID)

        // Step 4 — the zap and its intent must agree.
        if (event.content != intent.content) return invalid(Reason.CONTENT_MISMATCH)
        if (recipient != intent.recipient()) return invalid(Reason.RECIPIENT_MISMATCH)
        if (amount != intentAmount) return invalid(Reason.AMOUNT_MISMATCH)
        if (offer != intent.offer()) return invalid(Reason.OFFER_MISMATCH)
        if (event.zappedEvent() != intent.zappedEvent()) return invalid(Reason.TARGET_MISMATCH)
        if (event.zappedAddress() != intent.zappedAddress()) return invalid(Reason.TARGET_MISMATCH)
        if (event.zappedKind() != intent.zappedKind()) return invalid(Reason.TARGET_MISMATCH)

        // Step 5 — parse the raw offer.
        val offerParsed = Bolt12Offer.parse(offer) ?: return invalid(Reason.UNPARSEABLE_OFFER)

        // Step 6 — parse & decode the payer proof.
        val proof = Bolt12PayerProof.parse(proofStr) ?: return invalid(Reason.UNPARSEABLE_PROOF)

        // Step 7 — bind the proof to this zap.
        val expectedNote = NIP_URI_PREFIX + intent.id
        if (proof.invreqPayerNote() != expectedNote) return invalid(Reason.PROOF_NOTE_MISMATCH)

        val invoiceAmount = proof.invoiceAmount() ?: return invalid(Reason.MISSING_INVOICE_AMOUNT)
        if (invoiceAmount != amount) return invalid(Reason.PROOF_AMOUNT_MISMATCH)

        offerBindingFailure(offerParsed, proof)?.let { return invalid(it) }

        // Step 6 (crypto) — verify the payer proof signatures.
        val cryptoResult = proofVerifier.verify(proof)
        val cryptoVerified =
            when (cryptoResult) {
                is Bolt12ProofResult.Valid -> true
                is Bolt12ProofResult.Unsupported -> false
                is Bolt12ProofResult.Invalid -> return invalid(mapProofReason(cryptoResult.reason))
            }

        val paymentHash = proof.invoicePaymentHash() ?: return invalid(Reason.PROOF_MISSING_REQUIRED_FIELDS)

        return Bolt12ZapValidation.Valid(
            recipient = recipient,
            payer = payer,
            amountMillisats = amount,
            paymentHashHex = Hex.encode(paymentHash),
            zappedEventId = event.zappedEvent(),
            zappedAddress = event.zappedAddress(),
            zappedKind = event.zappedKind(),
            proofCryptoVerified = cryptoVerified,
        )
    }

    /** Returns the relevant reason when the `e`/`a` target cardinality is illegal, or null when fine. */
    private fun checkTargetCardinality(tags: Array<Array<String>>): Reason? {
        val eCount = tags.count(ETag::isTagged)
        val aCount = tags.count(ATag::isTagged)
        if (eCount > 1) return Reason.MULTIPLE_EVENT_TARGETS
        if (aCount > 1) return Reason.MULTIPLE_ADDRESS_TARGETS
        if (eCount >= 1 && aCount >= 1) return Reason.BOTH_EVENT_AND_ADDRESS_TARGET
        return null
    }

    /**
     * Soft binding of the proof to the offer without processing blinded paths:
     * when the offer publishes an `offer_issuer_id` and no blinded paths, the
     * invoice must be signed by that same node id, and any `offer_issuer_id`
     * copied into the proof must match. Offers that route through blinded paths
     * carry a per-path node id we can't check here, so they are left to the
     * signature verification alone.
     */
    private fun offerBindingFailure(
        offer: Bolt12Offer,
        proof: Bolt12PayerProof,
    ): Reason? {
        val issuerId = offer.issuerId() ?: return null

        proof.offerIssuerId()?.let { proofIssuer ->
            if (!proofIssuer.contentEquals(issuerId)) return Reason.OFFER_PROOF_MISMATCH
        }

        if (!offer.hasPaths()) {
            val nodeId = proof.invoiceNodeId() ?: return Reason.PROOF_MISSING_REQUIRED_FIELDS
            if (!nodeId.contentEquals(issuerId)) return Reason.OFFER_PROOF_MISMATCH
        }
        return null
    }

    private fun mapProofReason(reason: Bolt12ProofResult.Reason): Reason =
        when (reason) {
            Bolt12ProofResult.Reason.MISSING_REQUIRED_FIELDS -> Reason.PROOF_MISSING_REQUIRED_FIELDS
            Bolt12ProofResult.Reason.PREIMAGE_MISMATCH -> Reason.PROOF_PREIMAGE_MISMATCH
            Bolt12ProofResult.Reason.INVOICE_SIGNATURE_INVALID -> Reason.PROOF_INVOICE_SIGNATURE_INVALID
            Bolt12ProofResult.Reason.PROOF_SIGNATURE_INVALID -> Reason.PROOF_SIGNATURE_INVALID
            Bolt12ProofResult.Reason.MALFORMED_KEY -> Reason.PROOF_MALFORMED_KEY
            // A compressed proof never reaches here (it returns Unsupported, not Invalid).
            Bolt12ProofResult.Reason.COMPRESSED_PROOF_UNSUPPORTED -> Reason.PROOF_MISSING_REQUIRED_FIELDS
        }

    private fun invalid(reason: Reason) = Bolt12ZapValidation.Invalid(reason)

    companion object {
        /**
         * The NIP binds the Lightning payment to the signed intent through the
         * BOLT12 `invreq_payer_note`, which MUST equal this prefix followed by the
         * intent event id. The `nipXX` segment tracks the final NIP number.
         */
        const val NIP_URI_PREFIX = "nostr:nipXX:"
    }
}

private typealias Reason = Bolt12ZapValidation.Reason
