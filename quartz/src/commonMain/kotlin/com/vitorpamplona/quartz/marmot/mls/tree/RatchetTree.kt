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
package com.vitorpamplona.quartz.marmot.mls.tree

import com.vitorpamplona.quartz.marmot.mls.codec.TlsReader
import com.vitorpamplona.quartz.marmot.mls.codec.TlsSerializable
import com.vitorpamplona.quartz.marmot.mls.codec.TlsWriter
import com.vitorpamplona.quartz.marmot.mls.crypto.MlsCryptoProvider

/**
 * MLS Ratchet Tree (RFC 9420 Section 7).
 *
 * A left-balanced binary tree where leaves represent group members and
 * internal nodes contain derived key material. The tree provides forward
 * secrecy through the TreeKEM key agreement protocol.
 *
 * Each tree position is either:
 * - A leaf node (even index): contains member's HPKE key, credential, signature
 * - A parent node (odd index): contains derived HPKE key from path secret
 * - Blank: null (removed member or uninitialized)
 *
 * The tree is stored as a flat array indexed by node number.
 */
class RatchetTree(
    initialLeafCount: Int = 0,
) {
    /** Node storage: null means blank */
    private val nodes = mutableListOf<TreeNode?>()
    private var _leafCount: Int = initialLeafCount

    val leafCount: Int get() = _leafCount

    init {
        if (initialLeafCount > 0) {
            val nodeCount = BinaryTree.nodeCount(initialLeafCount)
            repeat(nodeCount) { nodes.add(null) }
        }
    }

    /** Get the node at a given node index */
    fun getNode(nodeIndex: Int): TreeNode? = if (nodeIndex < nodes.size) nodes[nodeIndex] else null

    /** Set a leaf node at the given leaf index */
    fun setLeaf(
        leafIndex: Int,
        leafNode: LeafNode?,
    ) {
        // Expand tree if needed
        if (leafIndex >= _leafCount) {
            _leafCount = leafIndex + 1
        }
        val nodeIdx = BinaryTree.leafToNode(leafIndex)
        ensureCapacity(nodeIdx)
        nodes[nodeIdx] =
            if (leafNode != null) {
                TreeNode.Leaf(leafNode)
            } else {
                null
            }
    }

    /** Set a parent node at the given node index */
    fun setParent(
        nodeIndex: Int,
        parentNode: ParentNode?,
    ) {
        require(BinaryTree.isParent(nodeIndex)) { "Node $nodeIndex is not a parent index" }
        ensureCapacity(nodeIndex)
        nodes[nodeIndex] =
            if (parentNode != null) {
                TreeNode.Parent(parentNode)
            } else {
                null
            }
    }

    /** Get a leaf node at the given leaf index */
    fun getLeaf(leafIndex: Int): LeafNode? {
        val nodeIdx = BinaryTree.leafToNode(leafIndex)
        val node = getNode(nodeIdx) ?: return null
        return (node as? TreeNode.Leaf)?.leafNode
    }

    /**
     * Check that the given encryption key is not already used by another leaf (RFC 9420 §7.3).
     */
    private fun requireUniqueEncryptionKey(
        leafNode: LeafNode,
        excludeLeafIndex: Int = -1,
    ) {
        for (i in 0 until _leafCount) {
            if (i == excludeLeafIndex) continue
            val existing = getLeaf(i) ?: continue
            require(!existing.encryptionKey.contentEquals(leafNode.encryptionKey)) {
                "Duplicate encryption key: leaf $i already uses this key"
            }
        }
    }

    /**
     * Add a new leaf to the tree. Returns the leaf index.
     * First tries to reuse a blank leaf slot, otherwise appends.
     */
    fun addLeaf(leafNode: LeafNode): Int {
        requireUniqueEncryptionKey(leafNode)
        // Find first blank leaf
        for (i in 0 until _leafCount) {
            if (getLeaf(i) == null) {
                setLeaf(i, leafNode)
                // Blank the direct path (RFC 9420 Section 7.7)
                val directPath = BinaryTree.directPath(i, _leafCount)
                for (nodeIdx in directPath) {
                    if (nodeIdx < nodes.size) {
                        nodes[nodeIdx] = null
                    }
                }
                return i
            }
        }

        // No blank leaf found — extend the tree
        val newLeafIndex = _leafCount
        _leafCount++
        val newNodeCount = BinaryTree.nodeCount(_leafCount)
        while (nodes.size < newNodeCount) {
            nodes.add(null)
        }
        setLeaf(newLeafIndex, leafNode)
        // Blank the direct path for the new leaf
        val directPath = BinaryTree.directPath(newLeafIndex, _leafCount)
        for (nodeIdx in directPath) {
            if (nodeIdx < nodes.size) {
                nodes[nodeIdx] = null
            }
        }
        return newLeafIndex
    }

    /**
     * Remove a member by blanking their leaf and all parent nodes on the direct path.
     *
     * RFC 9420 §7.8: trailing blank leaves MUST be trimmed so every participant
     * agrees on `leaf_count` (and therefore on `direct_path` lengths). Openmls
     * does this after every leaf blank; without the trim, a committer that had
     * the removed leaf at the far right end of the tree sends an UpdatePath one
     * entry longer than the receiver's tree layout accepts, and the receiver
     * errors out with `UpdatePathError(PathLengthMismatch)`.
     */
    fun removeLeaf(leafIndex: Int) {
        setLeaf(leafIndex, null)
        val directPath = BinaryTree.directPath(leafIndex, _leafCount)
        for (nodeIdx in directPath) {
            if (nodeIdx < nodes.size) {
                nodes[nodeIdx] = null
            }
        }
        // Shrink leafCount past any trailing blanks. Parent nodes that become
        // orphaned by the shrink are dropped from the `nodes` list so
        // treeHash() / resolution() / direct_path() agree with openmls on the
        // new tree shape.
        while (_leafCount > 0 && getLeaf(_leafCount - 1) == null) {
            _leafCount--
        }
        val effectiveNodeCount = if (_leafCount > 0) BinaryTree.nodeCount(_leafCount) else 0
        while (nodes.size > effectiveNodeCount) {
            nodes.removeAt(nodes.size - 1)
        }
    }

    /**
     * Snapshot the mutable tree state so callers can roll back after a
     * failed commit application. `TreeNode`, `LeafNode`, and `ParentNode`
     * are all immutable data classes — copying the `nodes` list is enough
     * to isolate future edits.
     */
    fun snapshot(): Snapshot = Snapshot(nodes.toList(), _leafCount)

    /** Restore the mutable tree state produced by an earlier [snapshot]. */
    fun restoreFrom(snapshot: Snapshot) {
        nodes.clear()
        nodes.addAll(snapshot.nodes)
        _leafCount = snapshot.leafCount
    }

    /** Opaque capture of the ratchet tree's mutable state. */
    class Snapshot internal constructor(
        internal val nodes: List<TreeNode?>,
        internal val leafCount: Int,
    )

    /**
     * Compute the tree hash for this ratchet tree (RFC 9420 Section 7.9).
     * Used in GroupContext to bind the group state to the tree.
     */
    fun treeHash(): ByteArray {
        val rootIdx = BinaryTree.root(_leafCount)
        return treeHashNode(rootIdx)
    }

    /**
     * Compute tree hash using a specific logical leaf count.
     * Used when the serialized tree has more nodes than the logical tree.
     */
    fun treeHashWithLeafCount(logicalLeafCount: Int): ByteArray {
        val rootIdx = BinaryTree.root(logicalLeafCount)
        return treeHashNode(rootIdx)
    }

    /**
     * Recursive tree hash computation per RFC 9420 Section 7.9.
     *
     * Leaf:   H(uint8(1) || uint32(leaf_index) || optional<LeafNode>)
     * Parent: H(uint8(2) || optional<ParentNode> || opaque left_hash<V> || opaque right_hash<V>)
     *
     * The uint8 type discriminant (1=leaf, 2=parent) is part of the hash input.
     */
    internal fun treeHashNode(nodeIndex: Int): ByteArray {
        if (BinaryTree.isLeaf(nodeIndex)) {
            val leafIndex = BinaryTree.nodeToLeaf(nodeIndex)
            val writer = TlsWriter()
            writer.putUint8(1) // TreeHashInput::Leaf discriminant
            writer.putUint32(leafIndex.toLong())
            val leaf = getNode(nodeIndex)
            if (leaf != null) {
                writer.putUint8(1) // present
                (leaf as TreeNode.Leaf).leafNode.encodeTls(writer)
            } else {
                writer.putUint8(0) // blank
            }
            return MlsCryptoProvider.hash(writer.toByteArray())
        }

        val leftHash = treeHashNode(BinaryTree.left(nodeIndex))
        val rightHash = treeHashNode(BinaryTree.right(nodeIndex))

        val writer = TlsWriter()
        writer.putUint8(2) // TreeHashInput::Parent discriminant
        val parent = getNode(nodeIndex)
        if (parent != null) {
            writer.putUint8(1) // present
            (parent as TreeNode.Parent).parentNode.encodeTls(writer)
        } else {
            writer.putUint8(0) // blank
        }
        writer.putOpaqueVarInt(leftHash)
        writer.putOpaqueVarInt(rightHash)

        return MlsCryptoProvider.hash(writer.toByteArray())
    }

    /**
     * RFC 9420 §4.1.2 "filtered direct path":
     *   the direct path of a leaf node L, with any parent node removed whose
     *   child on the copath of L has an empty resolution (unmerged_leaves
     *   count toward the resolution).
     *
     * Returns parallel lists (filteredDirectPath, filteredCopath). This is
     * what openmls / whitenoise-rs use for `UpdatePath.nodes` generation
     * and application — omitting a parent whose copath subtree is entirely
     * blank is spec-required because encrypting to that parent's key pair
     * is equivalent to encrypting to its only non-blank child (which is
     * already on the direct path). Relevant in, e.g., a 3-member group
     * where the committer removes the middle leaf: the sibling subtree at
     * the level above the committer becomes fully blank, and the parent at
     * that level is dropped from the UpdatePath.
     */
    fun filteredDirectPath(leafIndex: Int): Pair<List<Int>, List<Int>> {
        val directPath = BinaryTree.directPath(leafIndex, _leafCount)
        val copath = BinaryTree.copath(leafIndex, _leafCount)
        val filteredDp = mutableListOf<Int>()
        val filteredCp = mutableListOf<Int>()
        for (i in directPath.indices) {
            if (resolution(copath[i]).isNotEmpty()) {
                filteredDp.add(directPath[i])
                filteredCp.add(copath[i])
            }
        }
        return filteredDp to filteredCp
    }

    /**
     * Apply an UpdatePath to the tree: update parent nodes along the sender's
     * **filtered** direct path (RFC 9420 §7.9) with the provided path nodes.
     */
    fun applyUpdatePath(
        senderLeafIndex: Int,
        pathNodes: List<UpdatePathNode>,
    ) {
        val (filteredDp, _) = filteredDirectPath(senderLeafIndex)
        require(pathNodes.size == filteredDp.size) {
            "UpdatePath node count (${pathNodes.size}) doesn't match filtered direct path length (${filteredDp.size})"
        }

        for (i in filteredDp.indices) {
            val nodeIdx = filteredDp[i]
            val pathNode = pathNodes[i]

            setParent(
                nodeIdx,
                ParentNode(
                    encryptionKey = pathNode.encryptionKey,
                    parentHash = ByteArray(0), // Computed separately
                    unmergedLeaves = emptyList(),
                ),
            )
        }
    }

    /**
     * Derive path secrets and keys for an UpdatePath.
     *
     * Starting from a leaf secret, derives the chain of path secrets along
     * the direct path to the root. Each path secret produces an HPKE key pair
     * for the corresponding tree node.
     *
     * @param leafIndex the sender's leaf index
     * @param leafSecret the initial secret (commit_secret or update secret)
     * @return list of (pathSecret, hpkeKeyPair) for each direct path node
     */
    fun derivePathSecrets(
        leafIndex: Int,
        leafSecret: ByteArray,
    ): List<PathSecretAndKey> {
        val directPath = BinaryTree.directPath(leafIndex, _leafCount)
        val results = mutableListOf<PathSecretAndKey>()

        var currentSecret = leafSecret
        for (nodeIdx in directPath) {
            // RFC 9420 §7.4: path_secret[0] = leafSecret,
            //                node_secret[n] = DeriveSecret(path_secret[n], "node"),
            //                node's HPKE keypair = DeriveKeyPair(node_secret).
            // DeriveKeyPair is HPKE's (RFC 9180 §7.1.3), NOT an MLS ExpandWithLabel —
            // it uses the "HPKE-v1" + KEM suite_id labels, and produces a keypair
            // that openmls/mdk can reproduce from the same path_secret. If we derive
            // with a different formula, receivers compute different public keys and
            // reject the UpdatePath with `UpdatePathError(PathMismatch)`.
            val nodeSecret = MlsCryptoProvider.deriveSecret(currentSecret, "node")
            val kp =
                com.vitorpamplona.quartz.marmot.mls.crypto.Hpke
                    .deriveKeyPair(nodeSecret)

            results.add(PathSecretAndKey(currentSecret, kp.privateKey, kp.publicKey))

            // path_secret[n+1] = DeriveSecret(path_secret[n], "path")
            currentSecret = MlsCryptoProvider.deriveSecret(currentSecret, "path")
        }

        return results
    }

    /**
     * Find the resolution of a node (RFC 9420 Section 7.7).
     *
     * The resolution of a node is the set of non-blank leaf and parent nodes
     * that need to receive an encrypted path secret for this tree position.
     * Used to determine who gets HPKE-encrypted copies of path secrets.
     */
    fun resolution(nodeIndex: Int): List<Int> {
        val node = getNode(nodeIndex)
        if (node != null) {
            val result = mutableListOf(nodeIndex)
            // Add unmerged leaves for parent nodes
            if (node is TreeNode.Parent) {
                for (leaf in node.parentNode.unmergedLeaves) {
                    result.add(BinaryTree.leafToNode(leaf))
                }
            }
            return result
        }

        // Node is blank
        if (BinaryTree.isLeaf(nodeIndex)) {
            return emptyList()
        }

        // For blank parent: resolution is union of children's resolutions
        return resolution(BinaryTree.left(nodeIndex)) +
            resolution(BinaryTree.right(nodeIndex))
    }

    /**
     * Encode the full ratchet tree as a TLS-serialized optional vector.
     * Used in GroupInfo for Welcome messages.
     */
    fun encodeTls(writer: TlsWriter) {
        val totalNodes = if (_leafCount > 0) BinaryTree.nodeCount(_leafCount) else 0

        // Find rightmost non-blank node to trim trailing blanks (RFC 9420 Section 7.8)
        var lastPresent = totalNodes - 1
        while (lastPresent >= 0 && getNode(lastPresent) == null) {
            lastPresent--
        }
        val serializeCount = lastPresent + 1

        val inner = TlsWriter()
        for (i in 0 until serializeCount) {
            val node = getNode(i)
            if (node != null) {
                inner.putUint8(1)
                node.encodeTls(inner)
            } else {
                inner.putUint8(0)
            }
        }
        writer.putOpaqueVarInt(inner.toByteArray())
    }

    private fun ensureCapacity(nodeIndex: Int) {
        while (nodes.size <= nodeIndex) {
            nodes.add(null)
        }
    }

    companion object {
        fun decodeTls(reader: TlsReader): RatchetTree {
            val treeBytes = reader.readOpaqueVarInt()
            val treeReader = TlsReader(treeBytes)
            val nodesList = mutableListOf<TreeNode?>()

            while (treeReader.hasRemaining) {
                val present = treeReader.readUint8()
                if (present == 1) {
                    nodesList.add(TreeNode.decodeTls(treeReader))
                } else {
                    nodesList.add(null)
                }
            }

            val tree = RatchetTree()
            tree.nodes.addAll(nodesList)
            // Leaf count must account for tree trimming: the serialized tree
            // may have trailing blank nodes removed. Count actual leaves by
            // scanning for the highest occupied leaf position.
            var maxLeafIndex = -1
            for (i in nodesList.indices) {
                if (BinaryTree.isLeaf(i) && nodesList[i] != null) {
                    maxLeafIndex = BinaryTree.nodeToLeaf(i)
                }
            }
            // Leaf count is at least maxLeafIndex + 1, but also must be at
            // least (nodesList.size + 1) / 2 since parent nodes at high indices
            // imply leaves beyond the serialized range.
            val fromNodes = (nodesList.size + 1) / 2
            tree._leafCount = maxOf(fromNodes, maxLeafIndex + 1)
            return tree
        }
    }
}

/** A node in the ratchet tree: either a Leaf or Parent */
sealed class TreeNode : TlsSerializable {
    data class Leaf(
        val leafNode: LeafNode,
    ) : TreeNode() {
        override fun encodeTls(writer: TlsWriter) {
            writer.putUint8(NODE_TYPE_LEAF)
            leafNode.encodeTls(writer)
        }
    }

    data class Parent(
        val parentNode: ParentNode,
    ) : TreeNode() {
        override fun encodeTls(writer: TlsWriter) {
            writer.putUint8(NODE_TYPE_PARENT)
            parentNode.encodeTls(writer)
        }
    }

    companion object {
        const val NODE_TYPE_LEAF = 1
        const val NODE_TYPE_PARENT = 2

        fun decodeTls(reader: TlsReader): TreeNode {
            val nodeType = reader.readUint8()
            return when (nodeType) {
                NODE_TYPE_LEAF -> Leaf(LeafNode.decodeTls(reader))
                NODE_TYPE_PARENT -> Parent(ParentNode.decodeTls(reader))
                else -> throw IllegalArgumentException("Unknown node type: $nodeType")
            }
        }
    }
}

/** UpdatePath node: public key + encrypted path secrets for copath nodes */
data class UpdatePathNode(
    val encryptionKey: ByteArray,
    val encryptedPathSecret: List<com.vitorpamplona.quartz.marmot.mls.crypto.HpkeCiphertext>,
) : TlsSerializable {
    override fun encodeTls(writer: TlsWriter) {
        writer.putOpaqueVarInt(encryptionKey)
        writer.putVectorVarInt(encryptedPathSecret)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is UpdatePathNode) return false
        return encryptionKey.contentEquals(other.encryptionKey) && encryptedPathSecret == other.encryptedPathSecret
    }

    override fun hashCode(): Int {
        var result = encryptionKey.contentHashCode()
        result = 31 * result + encryptedPathSecret.hashCode()
        return result
    }

    companion object {
        fun decodeTls(reader: TlsReader): UpdatePathNode =
            UpdatePathNode(
                encryptionKey = reader.readOpaqueVarInt(),
                encryptedPathSecret =
                    reader.readVectorVarInt {
                        com.vitorpamplona.quartz.marmot.mls.crypto.HpkeCiphertext
                            .decodeTls(it)
                    },
            )
    }
}

/** Result of path secret derivation */
data class PathSecretAndKey(
    val pathSecret: ByteArray,
    val privateKey: ByteArray,
    val publicKey: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PathSecretAndKey) return false
        return pathSecret.contentEquals(other.pathSecret)
    }

    override fun hashCode(): Int = pathSecret.contentHashCode()
}
