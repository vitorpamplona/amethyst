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
 *      1. `H("LnLeaf", tlv)` — over the record's full `type || length || value`.
 *      2. `H("LnNonce" || first-tlv, type)` — over just the record's `type` field
 *         (its BigSize bytes), where `first-tlv` is the encoded bytes of the
 *         numerically-first signable record. (This is the subtle bit the BOLT
 *         spec's worked example pins down: the nonce leaf hashes the *type*, not
 *         the whole record.)
 *  - Inner nodes: `H("LnBranch", lesser || greater)` (children sorted by their
 *    32-byte value). Odd nodes are promoted unchanged to the next level.
 *  - The signature message digest is `H("lightning" || messagename || fieldname,
 *    merkle_root)`, verified with BIP-340 against the signing key.
 *
 * [rootHash] computes the root over a **fully-disclosed** record set.
 * [reconstructRoot] rebuilds it from a selectively-disclosed payer proof
 * (lightning/bolts#1346), pulling omitted subtrees from `proof_missing_hashes`.
 * Both are exercised byte-for-byte against the spec's `payer-proof-test.json`
 * vectors.
 */
object Bolt12Merkle {
    private val LN_NONCE = "LnNonce".encodeToByteArray()

    // The "LnLeaf" and "LnBranch" tags are constants, so their SHA-256 (the inner
    // hash of a tagged hash) is precomputed once instead of per leaf/branch. The
    // "LnNonce" tag isn't constant (it embeds the first TLV), so it is hashed once
    // per rootHash() call rather than once per record.
    private val LN_LEAF_TAG_HASH = sha256("LnLeaf".encodeToByteArray())
    private val LN_BRANCH_TAG_HASH = sha256("LnBranch".encodeToByteArray())

    /** Tagged hash `SHA256(SHA256(tag) || SHA256(tag) || msg)`. */
    fun taggedHash(
        tag: ByteArray,
        msg: ByteArray,
    ): ByteArray = taggedHashPrecomputed(sha256(tag), msg)

    /** Tagged hash when the caller already holds `SHA256(tag)` (the inner tag hash). */
    private fun taggedHashPrecomputed(
        tagHash: ByteArray,
        msg: ByteArray,
    ): ByteArray = sha256(tagHash + tagHash + msg)

    /**
     * Computes the merkle root over [signableRecords] — the caller must have
     * already excluded the signature elements (types 240..1000). Records must be
     * in ascending type order.
     */
    fun rootHash(signableRecords: List<TlvRecord>): ByteArray {
        require(signableRecords.isNotEmpty()) { "Cannot compute a merkle root over zero records" }

        val firstTlv = signableRecords.first().encoded
        val nonceTagHash = sha256(LN_NONCE + firstTlv)

        var nodes = ArrayList<ByteArray>(signableRecords.size * 2)
        for (record in signableRecords) {
            nodes.add(taggedHashPrecomputed(LN_LEAF_TAG_HASH, record.encoded))
            nodes.add(taggedHashPrecomputed(nonceTagHash, BigSize.encode(record.type)))
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

    /**
     * The `H("LnLeaf", tlv)` leaf hash of a single record (over its full encoded
     * `type || length || value`).
     */
    fun leafHash(encodedRecord: ByteArray): ByteArray = taggedHashPrecomputed(LN_LEAF_TAG_HASH, encodedRecord)

    /**
     * The `H("LnNonce" || first-tlv, type)` nonce leaf hash for a record of the
     * given [type], where [firstTlvEncoded] is the encoded numerically-first
     * signable record of the message.
     */
    fun nonceLeafHash(
        firstTlvEncoded: ByteArray,
        type: Long,
    ): ByteArray = taggedHash(LN_NONCE + firstTlvEncoded, BigSize.encode(type))

    /**
     * The per-field merkle node — `branch(H("LnLeaf", tlv), nonceLeafHash)` — the
     * hash that sits directly above a single TLV's leaf pair. A compressed proof
     * supplies [nonceLeafHash] (from `proof_leaf_hashes`) because it depends on
     * `first-tlv`, which the proof may have omitted.
     */
    fun fieldNode(
        encodedRecord: ByteArray,
        nonceLeafHash: ByteArray,
    ): ByteArray = branch(leafHash(encodedRecord), nonceLeafHash)

    /**
     * A node of the reconstruction/emission tree. A leaf carries a per-field node
     * hash ([leafHash], null only for a reader's omitted position) and whether it
     * was omitted; an inner node carries [left]/[right].
     */
    private class TreeNode private constructor(
        val left: TreeNode?,
        val right: TreeNode?,
        val leafHash: ByteArray?,
        val omitted: Boolean,
    ) {
        val isLeaf: Boolean get() = left == null

        companion object {
            fun leaf(
                hash: ByteArray?,
                omitted: Boolean,
            ) = TreeNode(null, null, hash, omitted)

            fun fork(
                left: TreeNode,
                right: TreeNode,
            ) = TreeNode(left, right, null, false)
        }
    }

    /** Rebuilds the pair-adjacent / promote-odd tree shape [rootHash] flattens. */
    private fun buildTree(leaves: List<TreeNode>): TreeNode {
        var level = leaves
        while (level.size > 1) {
            val next = ArrayList<TreeNode>((level.size + 1) / 2)
            var i = 0
            while (i < level.size) {
                if (i + 1 < level.size) {
                    next.add(TreeNode.fork(level[i], level[i + 1]))
                    i += 2
                } else {
                    next.add(level[i])
                    i += 1
                }
            }
            level = next
        }
        return level[0]
    }

    /**
     * Reconstructs the merkle root of a selectively-disclosed BOLT12 message
     * (lightning/bolts#1346). [leafNodeHashes] holds one entry per non-signature
     * leaf of the **original** message in ascending-type order: the per-field node
     * hash ([fieldNode]) for a disclosed field, or `null` for an omitted one. When
     * exactly one child of an inner node is entirely omitted, its hash is pulled
     * from [missingHashes] in the post-order depth-first (smallest-to-largest)
     * order the writer emitted them.
     *
     * Returns `null` if the tree cannot be closed with exactly the supplied
     * missing hashes (too few, too many, or an entirely-omitted root) — i.e. an
     * unverifiable proof.
     */
    fun reconstructRoot(
        leafNodeHashes: List<ByteArray?>,
        missingHashes: List<ByteArray>,
    ): ByteArray? {
        if (leafNodeHashes.isEmpty()) return null
        val tree = buildTree(leafNodeHashes.map { TreeNode.leaf(it, omitted = it == null) })
        var idx = 0
        var failed = false

        fun eval(node: TreeNode): ByteArray? {
            if (failed) return null
            if (node.isLeaf) return node.leafHash
            val a = eval(node.left!!)
            val b = eval(node.right!!)
            return when {
                failed -> null
                a == null && b == null -> null
                a != null && b != null -> branch(a, b)
                else -> {
                    if (idx >= missingHashes.size) {
                        failed = true
                        null
                    } else {
                        branch(a ?: b!!, missingHashes[idx++])
                    }
                }
            }
        }

        val root = eval(tree)
        return if (failed || root == null || idx != missingHashes.size) null else root
    }

    /**
     * The writer dual of [reconstructRoot]: given every non-signature leaf's
     * per-field node hash ([leafNodeHashes]) and whether each was omitted
     * ([leafOmitted]), emits the `proof_missing_hashes` — the hash of each subtree
     * that is the lone entirely-omitted child of an inner node — in post-order
     * depth-first (smallest-to-largest) order. Used to mint payer proofs (tests +
     * interop harness), never on the hot verification path.
     */
    fun emitMissingHashes(
        leafNodeHashes: List<ByteArray>,
        leafOmitted: List<Boolean>,
    ): List<ByteArray> {
        require(leafNodeHashes.size == leafOmitted.size) { "leaf hash / omitted-flag size mismatch" }
        if (leafNodeHashes.isEmpty()) return emptyList()
        val tree = buildTree(leafNodeHashes.indices.map { TreeNode.leaf(leafNodeHashes[it], leafOmitted[it]) })
        val missing = ArrayList<ByteArray>()

        // Returns the subtree hash and whether it is entirely omitted.
        fun emit(node: TreeNode): Pair<ByteArray, Boolean> {
            if (node.isLeaf) return node.leafHash!! to node.omitted
            val (a, aOmitted) = emit(node.left!!)
            val (b, bOmitted) = emit(node.right!!)
            if (aOmitted != bOmitted) missing.add(if (aOmitted) a else b)
            return branch(a, b) to (aOmitted && bOmitted)
        }

        emit(tree)
        return missing
    }

    private fun branch(
        a: ByteArray,
        b: ByteArray,
    ): ByteArray =
        if (compareUnsigned(a, b) <= 0) {
            taggedHashPrecomputed(LN_BRANCH_TAG_HASH, a + b)
        } else {
            taggedHashPrecomputed(LN_BRANCH_TAG_HASH, b + a)
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
