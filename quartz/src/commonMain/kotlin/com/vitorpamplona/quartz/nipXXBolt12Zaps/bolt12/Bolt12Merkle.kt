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

import com.vitorpamplona.quartz.utils.sha256.sha256

/**
 * BOLT12 "Signature Calculation" merkle root and the message digest that BOLT12
 * signatures (invoice `signature`, payer `proof_signature`) are computed over.
 *
 * Definitions (from 12-offer-encoding.md, matching the CLN/LDK reference
 * implementations):
 *
 *  - Tagged hash: `H(tag, msg) = SHA256(SHA256(tag) || SHA256(tag) || msg)`.
 *  - For each signable TLV record (types outside the 240..1000 signature range),
 *    two leaves are produced, in TLV-ascending order:
 *      1. `H("LnLeaf", tlv)`
 *      2. `H("LnNonce" || first-tlv, tlv)` where `first-tlv` is the encoded bytes
 *         of the numerically-first signable record.
 *  - Inner nodes: `H("LnBranch", lesser || greater)` (children sorted by their
 *    32-byte value). Odd nodes are promoted unchanged to the next level.
 *  - The signature message digest is `H("lightning" || messagename || fieldname,
 *    merkle_root)`, verified with BIP-340 against the signing key.
 *
 * NOTE: this computes the root over a **fully-disclosed** record set. Compressed
 * payer proofs (which omit some invoice TLVs and supply `proof_missing_hashes` /
 * `proof_leaf_hashes` to reconstruct the tree) are not reconstructed here; the
 * verifier reports those as unverifiable pending validation against the
 * lightning/bolts#1346 test vectors.
 */
object Bolt12Merkle {
    private val LN_LEAF = "LnLeaf".encodeToByteArray()
    private val LN_NONCE = "LnNonce".encodeToByteArray()
    private val LN_BRANCH = "LnBranch".encodeToByteArray()

    /** Tagged hash `SHA256(SHA256(tag) || SHA256(tag) || msg)`. */
    fun taggedHash(
        tag: ByteArray,
        msg: ByteArray,
    ): ByteArray {
        val tagHash = sha256(tag)
        return sha256(tagHash + tagHash + msg)
    }

    /**
     * Computes the merkle root over [signableRecords] — the caller must have
     * already excluded the signature elements (types 240..1000). Records must be
     * in ascending type order.
     */
    fun rootHash(signableRecords: List<TlvRecord>): ByteArray {
        require(signableRecords.isNotEmpty()) { "Cannot compute a merkle root over zero records" }

        val firstTlv = signableRecords.first().encoded
        val nonceTag = LN_NONCE + firstTlv

        var nodes = ArrayList<ByteArray>(signableRecords.size * 2)
        for (record in signableRecords) {
            nodes.add(taggedHash(LN_LEAF, record.encoded))
            nodes.add(taggedHash(nonceTag, record.encoded))
        }

        while (nodes.size > 1) {
            val next = ArrayList<ByteArray>((nodes.size + 1) / 2)
            var i = 0
            while (i < nodes.size) {
                if (i + 1 < nodes.size) {
                    next.add(branch(nodes[i], nodes[i + 1]))
                    i += 2
                } else {
                    next.add(nodes[i])
                    i += 1
                }
            }
            nodes = next
        }
        return nodes[0]
    }

    private fun branch(
        a: ByteArray,
        b: ByteArray,
    ): ByteArray =
        if (compareUnsigned(a, b) <= 0) {
            taggedHash(LN_BRANCH, a + b)
        } else {
            taggedHash(LN_BRANCH, b + a)
        }

    /**
     * The 32-byte BIP-340 message digest a BOLT12 signature signs:
     * `H("lightning" || messagename || fieldname, merkleRoot)`.
     */
    fun signatureDigest(
        messageName: String,
        fieldName: String,
        merkleRoot: ByteArray,
    ): ByteArray = taggedHash("lightning$messageName$fieldName".encodeToByteArray(), merkleRoot)

    private fun compareUnsigned(
        a: ByteArray,
        b: ByteArray,
    ): Int {
        val min = minOf(a.size, b.size)
        for (i in 0 until min) {
            val ai = a[i].toInt() and 0xff
            val bi = b[i].toInt() and 0xff
            if (ai != bi) return ai - bi
        }
        return a.size - b.size
    }
}
