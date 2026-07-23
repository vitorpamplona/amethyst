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

import com.vitorpamplona.quartz.nip01Core.crypto.Nip01Crypto
import com.vitorpamplona.quartz.nipXXBolt12Zaps.bolt12.Bolt12Merkle
import com.vitorpamplona.quartz.nipXXBolt12Zaps.bolt12.Bolt12PayerProof
import com.vitorpamplona.quartz.utils.sha256.sha256

/**
 * Cryptographic verification of a BOLT12 `lnp` payer proof, per lightning/bolts#1346:
 *
 *  1. `SHA256(proof_preimage) == invoice_payment_hash` — proves the payment settled.
 *  2. The invoice `signature` (240) is valid over the invoice merkle root, signed
 *     by `invoice_node_id`.
 *  3. The `proof_signature` (241) is valid over the proof merkle root, signed by
 *     `invreq_payer_id`.
 *
 * The merkle machinery ([Bolt12Merkle]) and the BIP-340 checks ([Nip01Crypto.verify])
 * are exercised end-to-end by the round-trip tests. **Interop caveat:** the exact
 * signature field names and, especially, the compressed-proof merkle
 * reconstruction (`proof_missing_hashes` / `proof_leaf_hashes` / `proof_omitted_tlvs`)
 * have not been checked against lightning/bolts#1346's `payer-proof-test.json`
 * vectors — that spec is still an unmerged draft. Until then, this verifier only
 * fully validates signatures for **fully-disclosed** proofs and reports
 * compressed proofs as [Bolt12ProofResult.Unsupported]. Callers decide whether an
 * unsupported crypto check may still be surfaced (labeled unverified) or dropped.
 */
class Bolt12ProofVerifier {
    fun verify(proof: Bolt12PayerProof): Bolt12ProofResult {
        if (!proof.hasAllRequiredFields()) {
            return Bolt12ProofResult.Invalid(Bolt12ProofResult.Reason.MISSING_REQUIRED_FIELDS)
        }

        val preimage = proof.proofPreimage() ?: return Bolt12ProofResult.Invalid(Bolt12ProofResult.Reason.MISSING_REQUIRED_FIELDS)
        val paymentHash = proof.invoicePaymentHash() ?: return Bolt12ProofResult.Invalid(Bolt12ProofResult.Reason.MISSING_REQUIRED_FIELDS)

        // 1. Settlement proof: the preimage must hash to the invoice payment hash.
        if (!sha256(preimage).contentEquals(paymentHash)) {
            return Bolt12ProofResult.Invalid(Bolt12ProofResult.Reason.PREIMAGE_MISMATCH)
        }

        // 2/3. Signature checks require reconstructing the invoice merkle root; for a
        // compressed proof that needs the (unverified) missing-hash reconstruction.
        if (proof.isCompressed()) {
            return Bolt12ProofResult.Unsupported(Bolt12ProofResult.Reason.COMPRESSED_PROOF_UNSUPPORTED)
        }

        val invoiceSig = proof.invoiceSignature()!!
        val nodeId = xOnly(proof.invoiceNodeId()!!) ?: return Bolt12ProofResult.Invalid(Bolt12ProofResult.Reason.MALFORMED_KEY)
        val invoiceRoot = Bolt12Merkle.rootHash(proof.invoiceSignableRecords())
        val invoiceDigest = Bolt12Merkle.signatureDigest(INVOICE_MESSAGE, SIGNATURE_FIELD, invoiceRoot)
        if (!Nip01Crypto.verify(invoiceSig, invoiceDigest, nodeId)) {
            return Bolt12ProofResult.Invalid(Bolt12ProofResult.Reason.INVOICE_SIGNATURE_INVALID)
        }

        val proofSig = proof.proofSignature()!!
        val payerId = xOnly(proof.invreqPayerId()!!) ?: return Bolt12ProofResult.Invalid(Bolt12ProofResult.Reason.MALFORMED_KEY)
        val proofRoot = Bolt12Merkle.rootHash(proof.proofSignableRecords())
        val proofDigest = Bolt12Merkle.signatureDigest(PROOF_MESSAGE, SIGNATURE_FIELD, proofRoot)
        if (!Nip01Crypto.verify(proofSig, proofDigest, payerId)) {
            return Bolt12ProofResult.Invalid(Bolt12ProofResult.Reason.PROOF_SIGNATURE_INVALID)
        }

        return Bolt12ProofResult.Valid(paymentHash = paymentHash, invoiceAmountMillisats = proof.invoiceAmount())
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
        const val SIGNATURE_FIELD = "signature"
    }
}
