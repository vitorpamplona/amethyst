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
 * plus the invoice's `signature`, and adds the payer's own `proof_signature`, the
 * `proof_preimage`, and the merkle-reconstruction fields `proof_missing_hashes` /
 * `proof_leaf_hashes` / `proof_omitted_tlvs` that let [Bolt12ProofVerifier] rebuild
 * the invoice root even though `invreq_metadata` (and optionally other fields) are
 * withheld for privacy.
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

    /** The optional free-text `proof_note` (1005) a challenge-response verifier may request. */
    fun proofNote(): String? = tlv.value(TYPE_PROOF_NOTE)?.decodeToString()

    fun proofOmittedTlvs(): ByteArray? = tlv.value(TYPE_PROOF_OMITTED_TLVS)

    fun proofMissingHashes(): ByteArray? = tlv.value(TYPE_PROOF_MISSING_HASHES)

    fun proofLeafHashes(): ByteArray? = tlv.value(TYPE_PROOF_LEAF_HASHES)

    /**
     * The `proof_omitted_tlvs` marker numbers (BigSize-decoded), empty when the
     * field is absent. Returns null if the bytes don't decode as a BigSize list —
     * a malformed proof the verifier must reject.
     */
    fun omittedTlvMarkers(): List<Long>? {
        val bytes = proofOmittedTlvs() ?: return emptyList()
        return try {
            val reader = TlvReader(bytes)
            buildList { while (reader.remaining() > 0) add(reader.readBigSize()) }
        } catch (_: Exception) {
            null
        }
    }

    /** `proof_leaf_hashes` split into 32-byte hashes, or null if not a whole multiple of 32. */
    fun leafHashList(): List<ByteArray>? = split32(proofLeafHashes())

    /** `proof_missing_hashes` split into 32-byte hashes, or null if not a whole multiple of 32. */
    fun missingHashList(): List<ByteArray>? = split32(proofMissingHashes())

    /**
     * The disclosed invoice (offer / invoice-request / invoice) records — the
     * fields whose types fall in the invoice ranges 1..239 and
     * 1_000_000_000..3_999_999_999 (lightning/bolts#1346), excluding the signature
     * elements and the proof-specific fields (240..999_999_999).
     */
    fun invoiceIncludedRecords(): List<TlvRecord> = tlv.records.filter { isInvoiceField(it.type) }

    /**
     * True when the proof omits some of the original invoice's TLV fields (i.e.
     * carries `proof_omitted_tlvs` markers). Every proof reconstructs the invoice
     * root through the merkle machinery — `invreq_metadata` (type 0) is always
     * omitted — but this flags the extra selective disclosure for display.
     */
    fun isCompressed(): Boolean = omittedTlvMarkers()?.isNotEmpty() == true

    /** The signable proof records (everything but the 240..1000 signature elements) — used for the payer proof signature. */
    fun proofSignableRecords(): List<TlvRecord> = tlv.records.filter { !it.isSignatureElement() }

    /**
     * True when every field the BOLT12 crypto verification requires is present and
     * well-sized (lightning/bolts#1346 reader rules). `invreq_payer_note` is *not*
     * required here — the NIP-XX zap binding checks it separately in the validator.
     */
    fun hasAllCryptoFields(): Boolean =
        invreqPayerId() != null &&
            invoicePaymentHash()?.size == 32 &&
            invoiceNodeId() != null &&
            invoiceSignature()?.size == 64 &&
            proofSignature()?.size == 64 &&
            proofPreimage()?.size == 32 &&
            tlv.has(TYPE_PROOF_MISSING_HASHES) &&
            tlv.has(TYPE_PROOF_LEAF_HASHES)

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

        /**
         * The invoice TLV type ranges that participate in the invoice merkle tree,
         * per lightning/bolts#1346: 1..239 (offer/invreq/invoice) and
         * 1_000_000_000..3_999_999_999 (high/unknown invoice fields). Excludes the
         * signature range 240..1000 and the proof-specific fields 1001..999_999_999.
         */
        fun isInvoiceField(type: Long): Boolean = type in 1L..239L || type in 1_000_000_000L..3_999_999_999L

        private fun split32(bytes: ByteArray?): List<ByteArray>? {
            if (bytes == null) return null
            if (bytes.size % 32 != 0) return null
            return (0 until bytes.size / 32).map { bytes.copyOfRange(it * 32, it * 32 + 32) }
        }

        fun parse(canonicalProof: String): Bolt12PayerProof? {
            val bytes = Bolt12Bech32.decodeToBytesOrNull(canonicalProof, Bolt12Bech32.PAYER_PROOF_HRP) ?: return null
            val tlv = TlvStream.readOrNull(bytes) ?: return null
            return Bolt12PayerProof(tlv)
        }
    }
}
