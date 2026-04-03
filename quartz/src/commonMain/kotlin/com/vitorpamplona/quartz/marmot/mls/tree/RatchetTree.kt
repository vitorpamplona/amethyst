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
import com.vitorpamplona.quartz.marmot.mls.crypto.X25519

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
     * Add a new leaf to the tree. Returns the leaf index.
     * First tries to reuse a blank leaf slot, otherwise appends.
     */
    fun addLeaf(leafNode: LeafNode): Int {
        // Find first blank leaf
        for (i in 0 until _leafCount) {
            if (getLeaf(i) == null) {
                setLeaf(i, leafNode)
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
        return newLeafIndex
    }

    /**
     * Remove a member by blanking their leaf and all parent nodes on the direct path.
     */
    fun removeLeaf(leafIndex: Int) {
        setLeaf(leafIndex, null)
        // Blank the direct path
        val directPath = BinaryTree.directPath(leafIndex, _leafCount)
        for (nodeIdx in directPath) {
            if (nodeIdx < nodes.size) {
                nodes[nodeIdx] = null
            }
        }
    }

    /**
     * Compute the tree hash for this ratchet tree (RFC 9420 Section 7.9).
     * Used in GroupContext to bind the group state to the tree.
     */
    fun treeHash(): ByteArray {
        val rootIdx = BinaryTree.root(_leafCount)
        return treeHashNode(rootIdx)
    }

    /**
     * Recursive tree hash computation per RFC 9420 Section 7.9.
     *
     * Leaf:   H(uint32(leaf_index) || optional<LeafNode>)
     * Parent: H(optional<ParentNode> || opaque left_hash<V> || opaque right_hash<V>)
     */
    private fun treeHashNode(nodeIndex: Int): ByteArray {
        if (BinaryTree.isLeaf(nodeIndex)) {
            val leafIndex = BinaryTree.nodeToLeaf(nodeIndex)
            val writer = TlsWriter()
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
     * Apply an UpdatePath to the tree: update parent nodes along the sender's
     * direct path with the provided path nodes.
     */
    fun applyUpdatePath(
        senderLeafIndex: Int,
        pathNodes: List<UpdatePathNode>,
    ) {
        val directPath = BinaryTree.directPath(senderLeafIndex, _leafCount)

        require(pathNodes.size == directPath.size) {
            "UpdatePath node count (${pathNodes.size}) doesn't match direct path length (${directPath.size})"
        }

        for (i in directPath.indices) {
            val nodeIdx = directPath[i]
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
            // path_secret[n] = DeriveSecret(path_secret[n-1], "path")
            val pathSecret = MlsCryptoProvider.deriveSecret(currentSecret, "path")

            // node_secret = DeriveSecret(path_secret, "node")
            val nodeSecret = MlsCryptoProvider.deriveSecret(pathSecret, "node")

            // Derive HPKE key pair from node_secret
            val privateKey = MlsCryptoProvider.expandWithLabel(nodeSecret, "hpke", ByteArray(0), 32)
            val publicKey = X25519.publicFromPrivate(privateKey)

            results.add(PathSecretAndKey(pathSecret, privateKey, publicKey))
            currentSecret = pathSecret
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
        val inner = TlsWriter()
        for (i in 0 until totalNodes) {
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
            // Compute leaf count from node count: nodes = 2*leaves - 1
            tree._leafCount = (nodesList.size + 1) / 2
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
