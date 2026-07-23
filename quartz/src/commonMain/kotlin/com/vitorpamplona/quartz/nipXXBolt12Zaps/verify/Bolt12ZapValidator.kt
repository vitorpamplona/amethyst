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
    /**
     * @param verifyEventSignature verify the zap event's own signature. Defaults to
     *   true for standalone callers. The `LocalCache` ingest path passes false
     *   because the relay-client pipeline already verified it before dispatch —
     *   avoiding a redundant schnorr check on the hot path.
     *
     * Checks are ordered cheap-to-expensive: all structural, cross-event, and
     * binding checks (tag reads, string/number compares) run first, and the
     * expensive signature/crypto verifications run only once an event has passed
     * them — so a malformed or mismatched event is rejected without paying for a
     * schnorr verification. This cannot change an accept/reject outcome (a
     * badly-signed event still fails the later verify), only which reason a
     * doubly-invalid event reports.
     */
    fun validate(
        event: Bolt12ZapEvent,
        verifyEventSignature: Boolean = true,
    ): Bolt12ZapValidation {
        // --- Cheap checks (no crypto) --------------------------------------------

        // Zap-event kind + structure.
        if (event.kind != Bolt12ZapEvent.KIND) return invalid(Reason.WRONG_KIND)
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

        // Embedded intent — parse + structure (signature verified later).
        val intent = event.zapIntent ?: return invalid(Reason.MISSING_OR_INVALID_INTENT)
        if (intent.pubKey != event.pubKey) return invalid(Reason.INTENT_PUBKEY_MISMATCH)
        if (intent.zapId() == null) return invalid(Reason.INVALID_ZAP_ID)

        val intentRecipientCount = intent.tags.count { PTag.parseKey(it) != null }
        if (intentRecipientCount != 1) return invalid(Reason.INTENT_STRUCTURE_INVALID)
        val intentAmount = intent.amount() ?: return invalid(Reason.INTENT_STRUCTURE_INVALID)
        if (intentAmount <= 0) return invalid(Reason.INTENT_STRUCTURE_INVALID)
        if (intent.offer() == null) return invalid(Reason.INTENT_STRUCTURE_INVALID)
        if (checkTargetCardinality(intent.tags) != null) return invalid(Reason.INTENT_STRUCTURE_INVALID)

        // The zap and its intent must agree.
        if (event.content != intent.content) return invalid(Reason.CONTENT_MISMATCH)
        if (recipient != intent.recipient()) return invalid(Reason.RECIPIENT_MISMATCH)
        if (amount != intentAmount) return invalid(Reason.AMOUNT_MISMATCH)
        if (offer != intent.offer()) return invalid(Reason.OFFER_MISMATCH)
        if (event.zappedEvent() != intent.zappedEvent()) return invalid(Reason.TARGET_MISMATCH)
        if (event.zappedAddress() != intent.zappedAddress()) return invalid(Reason.TARGET_MISMATCH)
        if (event.zappedKind() != intent.zappedKind()) return invalid(Reason.TARGET_MISMATCH)

        // Parse the raw offer + payer proof.
        val offerParsed = Bolt12Offer.parse(offer) ?: return invalid(Reason.UNPARSEABLE_OFFER)
        val proof = Bolt12PayerProof.parse(proofStr) ?: return invalid(Reason.UNPARSEABLE_PROOF)

        // Bind the proof to this zap. Uses intent.id — a forged id can't survive the
        // intent signature check below, so binding-before-verify is safe.
        val expectedNote = NIP_URI_PREFIX + intent.id
        if (proof.invreqPayerNote() != expectedNote) return invalid(Reason.PROOF_NOTE_MISMATCH)

        val invoiceAmount = proof.invoiceAmount() ?: return invalid(Reason.MISSING_INVOICE_AMOUNT)
        if (invoiceAmount != amount) return invalid(Reason.PROOF_AMOUNT_MISMATCH)

        offerBindingHardFailure(offerParsed, proof)?.let { return invalid(it) }

        // --- Expensive checks (signatures + proof crypto) ------------------------

        if (verifyEventSignature && !event.verify()) return invalid(Reason.BAD_EVENT_SIGNATURE)
        if (!intent.verify()) return invalid(Reason.BAD_INTENT_SIGNATURE)

        val cryptoResult = proofVerifier.verify(proof)
        val cryptoOk =
            when (cryptoResult) {
                is Bolt12ProofResult.Valid -> true
                is Bolt12ProofResult.Unsupported -> false
                is Bolt12ProofResult.Invalid -> return invalid(mapProofReason(cryptoResult.reason))
            }

        // "Crypto verified" requires BOTH that the invoice signature was checked AND
        // that the signed invoice is provably the offer's — i.e. signed by the offer's
        // `offer_issuer_id`. When the offer hides its destination behind blinded paths,
        // or publishes no issuer id, the invoice node key is one the payer chose and
        // can't be tied to the offer here, so "paid this offer" is NOT proven; such a
        // proof is downgraded to unverified rather than asserted verified.
        //
        // NB: even a bound proof only proves payment to the *embedded* offer. The NIP
        // has no offer↔recipient-identity binding, so nothing here proves the offer
        // belongs to the p-tagged recipient — see
        // quartz/plans/2026-07-23-bolt12-zap-interop-vectors.md.
        val cryptoVerified = cryptoOk && isInvoiceBoundToOffer(offerParsed, proof)

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
     * A definite contradiction between the proof and the offer — the proof either
     * copies a different `offer_issuer_id`, or (for a directly-addressed offer)
     * carries an `invoice_node_id` that isn't the offer's issuer. These are hard
     * rejects. Note the *absence* of a check is NOT a pass here — that only means
     * we can't bind, which [isInvoiceBoundToOffer] reports separately.
     */
    private fun offerBindingHardFailure(
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

    /**
     * True only when the settled invoice can be cryptographically tied to the offer:
     * the offer publishes an `offer_issuer_id`, uses no blinded paths, and the proof's
     * `invoice_node_id` equals that issuer (so the invoice signature the verifier
     * checks was made by the offer's own node). Blinded-path or issuer-less offers
     * expose a payer-chosen node key that can't be bound to the offer here, so a
     * self-consistent proof against such an offer proves nothing about paying it.
     */
    private fun isInvoiceBoundToOffer(
        offer: Bolt12Offer,
        proof: Bolt12PayerProof,
    ): Boolean {
        val issuerId = offer.issuerId() ?: return false
        if (offer.hasPaths()) return false
        val nodeId = proof.invoiceNodeId() ?: return false
        return nodeId.contentEquals(issuerId)
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
