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
package com.vitorpamplona.quartz.nipB1Bolt12Zaps.bolt12

/**
 * Mints a canonical BOLT12 `lnp1...` payer proof from a full invoice, per
 * lightning/bolts#1346 — the writer dual of [Bolt12ProofVerifier]. Production never
 * *creates* payer proofs (a paying wallet does); this exists for the self-consistent
 * test fixtures and the interop harness, and to prove the reader against
 * writer-produced streams. It reproduces the spec `payer-proof-test.json` vectors'
 * `proof_omitted_tlvs` / `proof_missing_hashes` / `proof_leaf_hashes` byte-for-byte.
 */
object Bolt12ProofBuilder {
    /**
     * One non-signature invoice TLV. [include] flags whether it is disclosed in the
     * proof; `invreq_metadata` (type 0) is always omitted regardless (it is the
     * hashing nonce and must never be revealed).
     */
    class InvoiceField(
        val type: Long,
        val value: ByteArray,
        val include: Boolean,
    )

    /**
     * @param invoiceFields ALL non-signature invoice TLVs in ascending type order,
     *   including `invreq_metadata` (type 0).
     * @param preimage the `proof_preimage`; the caller ensures the invoice's
     *   `invoice_payment_hash` (type 168) is its SHA-256 (or deliberately corrupt).
     * @param signInvoiceDigest signs the 32-byte invoice merkle digest (node key).
     * @param signProofDigest signs the 32-byte proof merkle digest (payer key).
     */
    fun build(
        invoiceFields: List<InvoiceField>,
        preimage: ByteArray,
        proofNote: String? = null,
        signInvoiceDigest: (ByteArray) -> ByteArray,
        signProofDigest: (ByteArray) -> ByteArray,
    ): String {
        val firstTlv = TlvRecord(invoiceFields.first().type, invoiceFields.first().value).encoded

        // Invoice signature (240) over the full invoice merkle root.
        val invoiceRecords = invoiceFields.map { TlvRecord(it.type, it.value) }
        val invoiceRoot = Bolt12Merkle.rootHash(invoiceRecords)
        val invoiceSig = signInvoiceDigest(Bolt12Merkle.signatureDigest("invoice", INVOICE_SIG_FIELD, invoiceRoot))

        // Disclosed invoice fields (type 0 is always withheld).
        val disclosed = invoiceFields.filter { it.include && it.type != 0L }
        val leafHashes = disclosed.map { Bolt12Merkle.nonceLeafHash(firstTlv, it.type) }
        val markers = renumberOmitted(invoiceFields)

        val leafNodeHashes =
            invoiceFields.map { Bolt12Merkle.fieldNode(TlvRecord(it.type, it.value).encoded, Bolt12Merkle.nonceLeafHash(firstTlv, it.type)) }
        val leafOmitted = invoiceFields.map { !(it.include && it.type != 0L) }
        val missingHashes = Bolt12Merkle.emitMissingHashes(leafNodeHashes, leafOmitted)

        // Records the proof signature (241) commits to: everything but the 240..1000
        // signature elements — disclosed invoice fields plus the proof-specific fields.
        val proofSignable =
            buildList {
                disclosed.forEach { add(TlvRecord(it.type, it.value)) }
                add(TlvRecord(Bolt12PayerProof.TYPE_PROOF_PREIMAGE, preimage))
                if (markers.isNotEmpty()) add(TlvRecord(Bolt12PayerProof.TYPE_PROOF_OMITTED_TLVS, encodeMarkers(markers)))
                add(TlvRecord(Bolt12PayerProof.TYPE_PROOF_MISSING_HASHES, concat(missingHashes)))
                add(TlvRecord(Bolt12PayerProof.TYPE_PROOF_LEAF_HASHES, concat(leafHashes)))
                if (proofNote != null) add(TlvRecord(Bolt12PayerProof.TYPE_PROOF_NOTE, proofNote.encodeToByteArray()))
            }.sortedBy { it.type }
        val proofRoot = Bolt12Merkle.rootHash(proofSignable)
        val proofSig = signProofDigest(Bolt12Merkle.signatureDigest("payer_proof", PROOF_SIG_FIELD, proofRoot))

        val all =
            (
                proofSignable +
                    TlvRecord(Bolt12PayerProof.TYPE_SIGNATURE, invoiceSig) +
                    TlvRecord(Bolt12PayerProof.TYPE_PROOF_SIGNATURE, proofSig)
            ).sortedBy { it.type }
        return Bolt12Bech32.encode(Bolt12Bech32.PAYER_PROOF_HRP, TlvStream(all).encode())
    }

    /**
     * Minimal renumbering of omitted fields into `proof_omitted_tlvs` markers
     * (lightning/bolts#1346): `invreq_metadata` (type 0) is implied and never
     * emitted; every other omitted field takes the previous included type + 1, or
     * the next value after the previous marker (starting at 1), with the
     * 1_000_000_000 jump once the low range (≤239) is exhausted.
     */
    private fun renumberOmitted(invoiceFields: List<InvoiceField>): List<Long> {
        val markers = ArrayList<Long>()
        var prevIncludedType: Long? = null
        for (f in invoiceFields) {
            if (f.include && f.type != 0L) {
                prevIncludedType = f.type
                continue
            }
            if (f.type == 0L) {
                prevIncludedType = null
                continue
            }
            markers.add(
                when {
                    prevIncludedType != null -> prevIncludedType + 1
                    markers.isEmpty() -> 1L
                    markers.last() == 239L -> 1_000_000_000L
                    else -> markers.last() + 1
                },
            )
            prevIncludedType = null
        }
        return markers
    }

    private fun encodeMarkers(markers: List<Long>): ByteArray {
        var size = 0
        for (m in markers) size += BigSize.encodedSize(m)
        val out = ByteArray(size)
        var offset = 0
        for (m in markers) {
            val enc = BigSize.encode(m)
            enc.copyInto(out, offset)
            offset += enc.size
        }
        return out
    }

    private fun concat(hashes: List<ByteArray>): ByteArray {
        val out = ByteArray(hashes.size * 32)
        var offset = 0
        for (h in hashes) {
            h.copyInto(out, offset)
            offset += 32
        }
        return out
    }

    private const val INVOICE_SIG_FIELD = "signature"
    private const val PROOF_SIG_FIELD = "proof_signature"
}
