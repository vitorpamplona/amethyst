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
package com.vitorpamplona.quartz.nipB1Bolt12Zaps.verify

import com.vitorpamplona.quartz.nip01Core.crypto.Nip01Crypto
import com.vitorpamplona.quartz.nipB1Bolt12Zaps.bolt12.Bolt12Merkle
import com.vitorpamplona.quartz.nipB1Bolt12Zaps.bolt12.Bolt12PayerProof
import com.vitorpamplona.quartz.nipB1Bolt12Zaps.bolt12.TlvRecord
import com.vitorpamplona.quartz.utils.sha256.sha256

/**
 * Cryptographic verification of a BOLT12 `lnp` payer proof, per lightning/bolts#1346:
 *
 *  1. `SHA256(proof_preimage) == invoice_payment_hash` — proves the payment settled.
 *  2. The invoice `signature` (240) is valid over the **reconstructed** invoice
 *     merkle root, signed by `invoice_node_id`. The proof discloses only some of
 *     the invoice's TLV fields (`invreq_metadata` is always withheld); the omitted
 *     branches are rebuilt from `proof_missing_hashes` / `proof_leaf_hashes` /
 *     `proof_omitted_tlvs` via [Bolt12Merkle.reconstructRoot].
 *  3. The `proof_signature` (241) is valid over the proof's own (fully-disclosed)
 *     merkle root, signed by `invreq_payer_id`.
 *
 * The full reader path — reconstruction and both BIP-340 checks — is exercised
 * byte-for-byte against the spec's `payer-proof-test.json` vectors
 * ([Bolt12PayerProofVectorTest]). The BOLT signature message/field names below
 * still track the unmerged draft and must be reconciled if it changes on merge.
 */
class Bolt12ProofVerifier {
    fun verify(proof: Bolt12PayerProof): Bolt12ProofResult {
        if (!proof.hasAllCryptoFields()) {
            return Bolt12ProofResult.Invalid(Bolt12ProofResult.Reason.MISSING_REQUIRED_FIELDS)
        }

        val preimage = proof.proofPreimage()!!
        val paymentHash = proof.invoicePaymentHash()!!

        // 1. Settlement proof: the preimage must hash to the invoice payment hash.
        if (!sha256(preimage).contentEquals(paymentHash)) {
            return Bolt12ProofResult.Invalid(Bolt12ProofResult.Reason.PREIMAGE_MISMATCH)
        }

        // 2. Invoice signature over the reconstructed invoice merkle root.
        val invoiceRoot =
            reconstructInvoiceRoot(proof)
                ?: return Bolt12ProofResult.Invalid(Bolt12ProofResult.Reason.RECONSTRUCTION_FAILED)
        val invoiceSig = proof.invoiceSignature()!!
        val nodeId = xOnly(proof.invoiceNodeId()!!) ?: return Bolt12ProofResult.Invalid(Bolt12ProofResult.Reason.MALFORMED_KEY)
        val invoiceDigest = Bolt12Merkle.signatureDigest(INVOICE_MESSAGE, INVOICE_SIG_FIELD, invoiceRoot)
        if (!Nip01Crypto.verify(invoiceSig, invoiceDigest, nodeId)) {
            return Bolt12ProofResult.Invalid(Bolt12ProofResult.Reason.INVOICE_SIGNATURE_INVALID)
        }

        // 3. Payer proof signature over the proof's own (disclosed) records.
        val proofSig = proof.proofSignature()!!
        val payerId = xOnly(proof.invreqPayerId()!!) ?: return Bolt12ProofResult.Invalid(Bolt12ProofResult.Reason.MALFORMED_KEY)
        val proofRoot = Bolt12Merkle.rootHash(proof.proofSignableRecords())
        val proofDigest = Bolt12Merkle.signatureDigest(PROOF_MESSAGE, PROOF_SIG_FIELD, proofRoot)
        if (!Nip01Crypto.verify(proofSig, proofDigest, payerId)) {
            return Bolt12ProofResult.Invalid(Bolt12ProofResult.Reason.PROOF_SIGNATURE_INVALID)
        }

        return Bolt12ProofResult.Valid(paymentHash = paymentHash, invoiceAmountMillisats = proof.invoiceAmount())
    }

    /**
     * Rebuilds the invoice merkle root from a selectively-disclosed proof. Returns
     * null when the proof's compression fields are malformed or don't describe a
     * closable tree — an unverifiable proof the caller must not count.
     */
    private fun reconstructInvoiceRoot(proof: Bolt12PayerProof): ByteArray? {
        val markers = proof.omittedTlvMarkers() ?: return null
        val leafHashes = proof.leafHashList() ?: return null
        val missingHashes = proof.missingHashList() ?: return null

        val included = proof.invoiceIncludedRecords().sortedBy { it.type }
        if (leafHashes.size != included.size) return null
        if (!markersValid(markers, included)) return null

        // Ordered non-signature leaves of the original invoice: the implied
        // `invreq_metadata` (type 0) plus every omitted marker plus every disclosed
        // field, merged in ascending numeric order. Disclosed fields carry their
        // per-field node hash; omitted positions are null (filled from missing hashes).
        val positions = ArrayList<Pair<Long, TlvRecord?>>(included.size + markers.size + 1)
        positions.add(0L to null) // type 0 (invreq_metadata) is always omitted
        for (m in markers) positions.add(m to null)
        for (r in included) positions.add(r.type to r)
        positions.sortBy { it.first }

        var leafIdx = 0
        val leafNodeHashes =
            positions.map { (_, record) ->
                if (record == null) {
                    null
                } else {
                    Bolt12Merkle.fieldNode(record.encoded, leafHashes[leafIdx++])
                }
            }

        return Bolt12Merkle.reconstructRoot(leafNodeHashes, missingHashes)
    }

    /**
     * The lightning/bolts#1346 reader rules for `proof_omitted_tlvs`: strictly
     * ascending, non-zero, within the invoice ranges, never the number of a
     * disclosed field, and each a valid *minimal renumbering* successor — one more
     * than a disclosed field, one more than the previous marker (or 0 for the
     * first), or the 1_000_000_000 jump after 239.
     */
    private fun markersValid(
        markers: List<Long>,
        included: List<TlvRecord>,
    ): Boolean {
        val includedTypes = included.mapTo(HashSet()) { it.type }
        var prev = 0L
        for ((i, m) in markers.withIndex()) {
            if (i > 0 && m <= markers[i - 1]) return false // strict ascending, no duplicates
            if (m == 0L) return false
            if (!Bolt12PayerProof.isInvoiceField(m)) return false
            if (m in includedTypes) return false
            val validSuccessor =
                (m - 1) in includedTypes ||
                    m == prev + 1 ||
                    (m == 1_000_000_000L && prev == 239L)
            if (!validSuccessor) return false
            prev = m
        }
        return true
    }

    /**
     * A BOLT12 `point` is a 33-byte compressed secp256k1 key; BIP-340 uses the
     * 32-byte x-only form. Drop the parity prefix. (Already-x-only 32-byte input
     * is passed through for convenience in tests.)
     */
    private fun xOnly(point: ByteArray): ByteArray? =
        when (point.size) {
            33 -> point.copyOfRange(1, 33)
            32 -> point
            else -> null
        }

    companion object {
        // BOLT12 signature digest tags are "lightning" || messagename || fieldname.
        // These strings track lightning/bolts#1346 and must be reconciled on merge.
        const val INVOICE_MESSAGE = "invoice"
        const val PROOF_MESSAGE = "payer_proof"

        /** The invoice's own signature field is named `signature`. */
        const val INVOICE_SIG_FIELD = "signature"

        /** The payer proof's signature field is named `proof_signature`. */
        const val PROOF_SIG_FIELD = "proof_signature"
    }
}
