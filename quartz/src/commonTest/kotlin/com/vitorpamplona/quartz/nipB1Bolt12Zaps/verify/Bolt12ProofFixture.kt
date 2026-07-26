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

import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.crypto.Nip01Crypto
import com.vitorpamplona.quartz.nipB1Bolt12Zaps.bolt12.Bolt12Bech32
import com.vitorpamplona.quartz.nipB1Bolt12Zaps.bolt12.Bolt12Offer
import com.vitorpamplona.quartz.nipB1Bolt12Zaps.bolt12.Bolt12PayerProof
import com.vitorpamplona.quartz.nipB1Bolt12Zaps.bolt12.Bolt12ProofBuilder
import com.vitorpamplona.quartz.nipB1Bolt12Zaps.bolt12.Bolt12Values
import com.vitorpamplona.quartz.nipB1Bolt12Zaps.bolt12.TlvRecord
import com.vitorpamplona.quartz.nipB1Bolt12Zaps.bolt12.TlvStream
import com.vitorpamplona.quartz.utils.sha256.sha256

/**
 * Builds matched BOLT12 offers and payer proofs for tests, minting spec-compliant
 * (lightning/bolts#1346) proofs through [Bolt12ProofBuilder] and self-signing them
 * with Quartz's own secp256k1 so the whole reconstruction + merkle + BIP-340 path
 * is exercised. This is a self-consistent construction; byte-exact CLN/LDK interop
 * is covered separately by [Bolt12PayerProofVectorTest].
 */
object Bolt12ProofFixture {
    /** A 33-byte compressed point (even parity) wrapping an x-only key. */
    private fun point(xOnly: ByteArray) = byteArrayOf(0x02) + xOnly

    fun buildOffer(
        nodeKey: KeyPair,
        amountMillisats: Long,
        withIssuerId: Boolean = true,
    ): String {
        // TLV types must be strictly ascending: amount(8), description(10), issuer_id(22).
        val records =
            buildList {
                add(TlvRecord(Bolt12Offer.TYPE_AMOUNT, Bolt12Values.tu64ToBytes(amountMillisats)))
                add(TlvRecord(Bolt12Offer.TYPE_DESCRIPTION, "zap".encodeToByteArray()))
                if (withIssuerId) add(TlvRecord(Bolt12Offer.TYPE_ISSUER_ID, point(nodeKey.pubKey)))
            }
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
        breakInvoiceSignature: Boolean = false,
        corruptPaymentHash: Boolean = false,
    ): String {
        val nodePoint = point(nodeKey.pubKey)
        val payerPoint = point(payerLightningKey.pubKey)
        // A corrupt hash still yields a valid signature over the corrupted records —
        // the preimage check (SHA256(preimage) != invoice_payment_hash) is what rejects it.
        val paymentHash = sha256(preimage).also { if (corruptPaymentHash) it[0] = (it[0] + 1).toByte() }

        // The full invoice's non-signature TLVs, ascending. invreq_metadata (type 0)
        // is always present and always withheld; invreq_amount (82) is the field the
        // `compressed` flag selectively omits.
        val invoiceFields =
            listOf(
                Bolt12ProofBuilder.InvoiceField(TYPE_INVREQ_METADATA, ByteArray(16), include = false),
                Bolt12ProofBuilder.InvoiceField(Bolt12PayerProof.TYPE_OFFER_ISSUER_ID, nodePoint, include = true),
                Bolt12ProofBuilder.InvoiceField(Bolt12PayerProof.TYPE_INVREQ_AMOUNT, Bolt12Values.tu64ToBytes(amountMillisats), include = !compressed),
                Bolt12ProofBuilder.InvoiceField(Bolt12PayerProof.TYPE_INVREQ_PAYER_ID, payerPoint, include = true),
                Bolt12ProofBuilder.InvoiceField(Bolt12PayerProof.TYPE_INVREQ_PAYER_NOTE, payerNote.encodeToByteArray(), include = true),
                Bolt12ProofBuilder.InvoiceField(Bolt12PayerProof.TYPE_INVOICE_PAYMENT_HASH, paymentHash, include = true),
                Bolt12ProofBuilder.InvoiceField(Bolt12PayerProof.TYPE_INVOICE_AMOUNT, Bolt12Values.tu64ToBytes(amountMillisats), include = true),
                Bolt12ProofBuilder.InvoiceField(Bolt12PayerProof.TYPE_INVOICE_NODE_ID, nodePoint, include = true),
            )

        val invoiceSigningKey = if (breakInvoiceSignature) payerLightningKey else nodeKey
        val proofSigningKey = if (breakProofSignature) nodeKey else payerLightningKey

        return Bolt12ProofBuilder.build(
            invoiceFields = invoiceFields,
            preimage = preimage,
            signInvoiceDigest = { digest -> Nip01Crypto.sign(digest, invoiceSigningKey.privKey!!) },
            signProofDigest = { digest -> Nip01Crypto.sign(digest, proofSigningKey.privKey!!) },
        )
    }

    private const val TYPE_INVREQ_METADATA = 0L
}
