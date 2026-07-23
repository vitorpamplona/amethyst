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

import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.crypto.Nip01Crypto
import com.vitorpamplona.quartz.nipXXBolt12Zaps.bolt12.Bolt12Bech32
import com.vitorpamplona.quartz.nipXXBolt12Zaps.bolt12.Bolt12Merkle
import com.vitorpamplona.quartz.nipXXBolt12Zaps.bolt12.Bolt12Offer
import com.vitorpamplona.quartz.nipXXBolt12Zaps.bolt12.Bolt12PayerProof
import com.vitorpamplona.quartz.nipXXBolt12Zaps.bolt12.Bolt12Values
import com.vitorpamplona.quartz.nipXXBolt12Zaps.bolt12.TlvRecord
import com.vitorpamplona.quartz.nipXXBolt12Zaps.bolt12.TlvStream
import com.vitorpamplona.quartz.utils.sha256.sha256

/**
 * Builds matched BOLT12 offers and payer proofs for tests, self-signing them with
 * Quartz's own secp256k1 so the whole merkle + BIP-340 path is exercised. This is
 * a self-consistent construction, not a CLN/LDK interop vector — see
 * [Bolt12ProofVerifier].
 */
object Bolt12ProofFixture {
    /** A 33-byte compressed point (even parity) wrapping an x-only key. */
    private fun point(xOnly: ByteArray) = byteArrayOf(0x02) + xOnly

    fun buildOffer(
        nodeKey: KeyPair,
        amountMillisats: Long,
    ): String {
        val records =
            listOf(
                TlvRecord(Bolt12Offer.TYPE_AMOUNT, Bolt12Values.tu64ToBytes(amountMillisats)),
                TlvRecord(Bolt12Offer.TYPE_DESCRIPTION, "zap".encodeToByteArray()),
                TlvRecord(Bolt12Offer.TYPE_ISSUER_ID, point(nodeKey.pubKey)),
            )
        return Bolt12Bech32.encode(Bolt12Bech32.OFFER_HRP, TlvStream(records).encode())
    }

    fun buildProof(
        nodeKey: KeyPair,
        payerLightningKey: KeyPair,
        preimage: ByteArray,
        amountMillisats: Long,
        payerNote: String,
        compressed: Boolean = false,
        breakProofSignature: Boolean = false,
    ): String {
        val nodePoint = point(nodeKey.pubKey)
        val payerPoint = point(payerLightningKey.pubKey)
        val paymentHash = sha256(preimage)

        // The invoice's signed records (types < 240), in ascending order.
        val invoiceRecords =
            listOf(
                TlvRecord(Bolt12PayerProof.TYPE_OFFER_ISSUER_ID, nodePoint),
                TlvRecord(Bolt12PayerProof.TYPE_INVREQ_AMOUNT, Bolt12Values.tu64ToBytes(amountMillisats)),
                TlvRecord(Bolt12PayerProof.TYPE_INVREQ_PAYER_ID, payerPoint),
                TlvRecord(Bolt12PayerProof.TYPE_INVREQ_PAYER_NOTE, payerNote.encodeToByteArray()),
                TlvRecord(Bolt12PayerProof.TYPE_INVOICE_PAYMENT_HASH, paymentHash),
                TlvRecord(Bolt12PayerProof.TYPE_INVOICE_AMOUNT, Bolt12Values.tu64ToBytes(amountMillisats)),
                TlvRecord(Bolt12PayerProof.TYPE_INVOICE_NODE_ID, nodePoint),
            )
        val invoiceRoot = Bolt12Merkle.rootHash(invoiceRecords)
        val invoiceSig =
            Nip01Crypto.sign(
                Bolt12Merkle.signatureDigest(Bolt12ProofVerifier.INVOICE_MESSAGE, Bolt12ProofVerifier.SIGNATURE_FIELD, invoiceRoot),
                nodeKey.privKey!!,
            )

        val preimageRecord = TlvRecord(Bolt12PayerProof.TYPE_PROOF_PREIMAGE, preimage)

        // The payer proof signs everything but the 240..1000 signature elements.
        val proofSignable = invoiceRecords + preimageRecord
        val proofRoot = Bolt12Merkle.rootHash(proofSignable)
        val proofSigningKey = if (breakProofSignature) nodeKey else payerLightningKey
        val proofSig =
            Nip01Crypto.sign(
                Bolt12Merkle.signatureDigest(Bolt12ProofVerifier.PROOF_MESSAGE, Bolt12ProofVerifier.SIGNATURE_FIELD, proofRoot),
                proofSigningKey.privKey!!,
            )

        val records =
            buildList {
                addAll(invoiceRecords)
                add(TlvRecord(Bolt12PayerProof.TYPE_SIGNATURE, invoiceSig))
                add(TlvRecord(Bolt12PayerProof.TYPE_PROOF_SIGNATURE, proofSig))
                add(preimageRecord)
                if (compressed) {
                    // A non-empty proof_missing_hashes marks the proof as compressed.
                    add(TlvRecord(Bolt12PayerProof.TYPE_PROOF_MISSING_HASHES, ByteArray(32) { 9 }))
                }
            }
        return Bolt12Bech32.encode(Bolt12Bech32.PAYER_PROOF_HRP, TlvStream(records).encode())
    }
}
