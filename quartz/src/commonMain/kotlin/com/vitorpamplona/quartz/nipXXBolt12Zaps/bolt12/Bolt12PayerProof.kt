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
package com.vitorpamplona.quartz.nipXXBolt12Zaps.bolt12

/**
 * A parsed BOLT12 payer proof (`lnp1...`), per lightning/bolts#1346.
 *
 * A payer proof copies the relevant offer / invoice-request / invoice TLV fields,
 * plus the invoice's `signature`, and adds the payer's own `proof_signature`,
 * the `proof_preimage`, and (for compressed proofs) the merkle-reconstruction
 * fields `proof_missing_hashes` / `proof_leaf_hashes` / `proof_omitted_tlvs`.
 *
 * The type numbers below are the ones proposed in lightning/bolts#1346 and MUST
 * be reconciled against the final merged BOLT if they change.
 */
class Bolt12PayerProof(
    val tlv: TlvStream,
) {
    fun offerIssuerId(): ByteArray? = tlv.value(TYPE_OFFER_ISSUER_ID)

    fun invreqPayerId(): ByteArray? = tlv.value(TYPE_INVREQ_PAYER_ID)

    fun invreqPayerNote(): String? = tlv.value(TYPE_INVREQ_PAYER_NOTE)?.decodeToString()

    fun invreqAmount(): Long? = tlv.tu64(TYPE_INVREQ_AMOUNT)

    fun invoicePaymentHash(): ByteArray? = tlv.value(TYPE_INVOICE_PAYMENT_HASH)

    fun invoiceAmount(): Long? = tlv.tu64(TYPE_INVOICE_AMOUNT)

    fun invoiceNodeId(): ByteArray? = tlv.value(TYPE_INVOICE_NODE_ID)

    fun invoiceSignature(): ByteArray? = tlv.value(TYPE_SIGNATURE)

    fun proofSignature(): ByteArray? = tlv.value(TYPE_PROOF_SIGNATURE)

    fun proofPreimage(): ByteArray? = tlv.value(TYPE_PROOF_PREIMAGE)

    fun proofOmittedTlvs(): ByteArray? = tlv.value(TYPE_PROOF_OMITTED_TLVS)

    fun proofMissingHashes(): ByteArray? = tlv.value(TYPE_PROOF_MISSING_HASHES)

    fun proofLeafHashes(): ByteArray? = tlv.value(TYPE_PROOF_LEAF_HASHES)

    /**
     * True when the proof omits some of the original invoice's TLV fields and
     * relies on `proof_missing_hashes` to reconstruct the merkle tree. Such
     * proofs need the compressed-tree reconstruction to verify the invoice
     * signature (not yet implemented — see [Bolt12ProofVerifier]).
     */
    fun isCompressed(): Boolean {
        if (tlv.has(TYPE_PROOF_OMITTED_TLVS)) return true
        val missing = proofMissingHashes()
        return missing != null && missing.isNotEmpty()
    }

    /** The signable invoice records (types < 240) — used to recompute the invoice merkle root when fully disclosed. */
    fun invoiceSignableRecords(): List<TlvRecord> = tlv.records.filter { it.type < TlvRecord.SIGNATURE_TYPE_MIN }

    /** The signable proof records (everything but the 240..1000 signature elements) — used for the payer proof signature. */
    fun proofSignableRecords(): List<TlvRecord> = tlv.records.filter { !it.isSignatureElement() }

    /** True when every field NIP-XX validation requires is present. */
    fun hasAllRequiredFields(): Boolean =
        invreqPayerId() != null &&
            invreqPayerNote() != null &&
            invoicePaymentHash() != null &&
            invoiceNodeId() != null &&
            invoiceSignature()?.size == 64 &&
            proofSignature()?.size == 64 &&
            proofPreimage()?.size == 32

    companion object {
        // Offer / invoice-request fields copied into the proof.
        const val TYPE_INVREQ_CHAIN = 80L
        const val TYPE_INVREQ_AMOUNT = 82L
        const val TYPE_INVREQ_FEATURES = 84L
        const val TYPE_INVREQ_QUANTITY = 86L
        const val TYPE_INVREQ_PAYER_ID = 88L
        const val TYPE_INVREQ_PAYER_NOTE = 89L
        const val TYPE_INVREQ_PATHS = 90L
        const val TYPE_INVREQ_BIP353_NAME = 91L
        const val TYPE_OFFER_ISSUER_ID = 22L

        // Invoice fields copied into the proof.
        const val TYPE_INVOICE_PATHS = 160L
        const val TYPE_INVOICE_BLINDEDPAY = 162L
        const val TYPE_INVOICE_CREATED_AT = 164L
        const val TYPE_INVOICE_RELATIVE_EXPIRY = 166L
        const val TYPE_INVOICE_PAYMENT_HASH = 168L
        const val TYPE_INVOICE_AMOUNT = 170L
        const val TYPE_INVOICE_FALLBACKS = 172L
        const val TYPE_INVOICE_FEATURES = 174L
        const val TYPE_INVOICE_NODE_ID = 176L

        // Signature elements (240..1000).
        const val TYPE_SIGNATURE = 240L
        const val TYPE_PROOF_SIGNATURE = 241L

        // Payer-proof-specific fields (> 1000).
        const val TYPE_PROOF_PREIMAGE = 1001L
        const val TYPE_PROOF_OMITTED_TLVS = 1002L
        const val TYPE_PROOF_MISSING_HASHES = 1003L
        const val TYPE_PROOF_LEAF_HASHES = 1004L
        const val TYPE_PROOF_NOTE = 1005L

        fun parse(canonicalProof: String): Bolt12PayerProof? {
            val bytes = Bolt12Bech32.decodeToBytesOrNull(canonicalProof, Bolt12Bech32.PAYER_PROOF_HRP) ?: return null
            val tlv = TlvStream.readOrNull(bytes) ?: return null
            return Bolt12PayerProof(tlv)
        }
    }
}
