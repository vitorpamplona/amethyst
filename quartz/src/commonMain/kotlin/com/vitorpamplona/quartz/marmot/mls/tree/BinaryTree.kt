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

/**
 * Left-balanced binary tree index arithmetic for MLS ratchet trees (RFC 9420 Section 7.1).
 *
 * The tree uses a flat array representation where:
 * - Leaves are at even indices: 0, 2, 4, 6, ...
 * - Parent nodes are at odd indices: 1, 3, 5, 7, ...
 * - For n leaves, the tree has 2n-1 total nodes.
 *
 * Node numbering for 4 leaves:
 * ```
 *        3
 *       / \
 *      1   5
 *     / \ / \
 *    0  2 4  6
 * ```
 */
object BinaryTree {
    /** Total number of nodes for a tree with [leafCount] leaves */
    fun nodeCount(leafCount: Int): Int {
        require(leafCount > 0) { "leafCount must be positive" }
        return 2 * leafCount - 1
    }

    /** Convert a leaf index (0, 1, 2, ...) to a node index (0, 2, 4, ...) */
    fun leafToNode(leafIndex: Int): Int = 2 * leafIndex

    /** Convert a node index (0, 2, 4, ...) to a leaf index (0, 1, 2, ...) */
    fun nodeToLeaf(nodeIndex: Int): Int {
        require(isLeaf(nodeIndex)) { "Node $nodeIndex is not a leaf" }
        return nodeIndex / 2
    }

    /** Whether a node index represents a leaf (even index) */
    fun isLeaf(nodeIndex: Int): Boolean = nodeIndex % 2 == 0

    /** Whether a node index represents a parent (odd index) */
    fun isParent(nodeIndex: Int): Boolean = nodeIndex % 2 == 1

    /** Level of a node in the tree (leaves are level 0) */
    fun level(nodeIndex: Int): Int {
        if (nodeIndex % 2 == 0) return 0
        var x = nodeIndex
        var k = 0
        while (x % 2 == 1) {
            x = x shr 1
            k++
        }
        return k
    }

    /** Left child of a parent node */
    fun left(nodeIndex: Int): Int {
        val k = level(nodeIndex)
        require(k > 0) { "Leaves have no children" }
        return nodeIndex xor (1 shl (k - 1))
    }

    /** Right child of a parent node */
    fun right(nodeIndex: Int): Int {
        val k = level(nodeIndex)
        require(k > 0) { "Leaves have no children" }
        return nodeIndex xor (3 shl (k - 1))
    }

    /**
     * Parent of a node in a tree with [nodeCount] total nodes.
     * Uses the left-balanced tree parent formula per RFC 9420 Appendix C.
     *
     * The root and any index ≥ [nodeCount] have no defined parent — the
     * left-balanced formula keeps walking up through virtual parents that
     * never come back into range, integer-overflows after ~31 doublings,
     * and either returns garbage or OOMs the call site that's storing the
     * result. Both modes are signs of a tree-accounting bug upstream
     * (e.g., calling directPath with a leafIndex past the post-Remove
     * leafCount); fail loudly here so callers can't silently corrupt
     * the rest of the commit-processing pipeline.
     */
    fun parent(
        nodeIndex: Int,
        nodeCount: Int,
    ): Int {
        require(nodeCount > 0) { "nodeCount must be positive, got $nodeCount" }
        require(nodeIndex in 0 until nodeCount) {
            "nodeIndex $nodeIndex out of range [0, $nodeCount)"
        }
        // The structural root of a left-balanced tree with `leafCount`
        // leaves sits at (1 << ceil(log2(leafCount))) - 1, derivable from
        // nodeCount via leafCount = (nodeCount + 1) / 2.
        val leafCount = (nodeCount + 1) / 2
        require(nodeIndex != root(leafCount)) {
            "nodeIndex $nodeIndex is the root of a $leafCount-leaf tree and has no parent"
        }
        return parentRec(nodeIndex, nodeCount)
    }

    /**
     * Inner walker that assumes [nodeIndex] has at least one valid parent
     * within [nodeCount]. Public callers go through [parent] which performs
     * the bounds check above.
     */
    private fun parentRec(
        nodeIndex: Int,
        nodeCount: Int,
    ): Int {
        val k = level(nodeIndex)
        val b = (nodeIndex shr (k + 1)) and 1
        val p =
            if (b == 0) {
                nodeIndex + (1 shl k)
            } else {
                nodeIndex - (1 shl k)
            }
        return if (p < nodeCount) p else parentRec(p, nodeCount)
    }

    /** Root node index for a tree with [leafCount] leaves */
    fun root(leafCount: Int): Int {
        if (leafCount <= 1) return 0
        // Root of a left-balanced tree: (1 << ceil(log2(n))) - 1
        val ceilLog2 = if (leafCount and (leafCount - 1) == 0) log2(leafCount) else log2(leafCount) + 1
        return (1 shl ceilLog2) - 1
    }

    /**
     * Direct path from a leaf to the root (excluding the leaf itself).
     * These are the parent nodes along the path from leaf to root.
     *
     * [leafIndex] must be in `[0, leafCount)`. Calling with `leafIndex >=
     * leafCount` is an upstream bug — most often a stale local index that
     * was valid before the tree shrank under a Remove proposal — and used
     * to OOM here because `parent()` would loop on the out-of-range start.
     */
    fun directPath(
        leafIndex: Int,
        leafCount: Int,
    ): List<Int> {
        require(leafCount > 0) { "leafCount must be positive, got $leafCount" }
        require(leafIndex in 0 until leafCount) {
            "leafIndex $leafIndex out of range [0, $leafCount)"
        }
        val nodeIdx = leafToNode(leafIndex)
        val n = nodeCount(leafCount)
        val rootIdx = root(leafCount)

        val path = mutableListOf<Int>()
        var current = nodeIdx
        while (current != rootIdx) {
            current = parent(current, n)
            path.add(current)
        }
        return path
    }

    /**
     * Copath of a leaf: the siblings of each node on the direct path.
     * The copath determines which nodes need to receive encrypted path secrets.
     *
     * Same range contract as [directPath]: `leafIndex` must be in
     * `[0, leafCount)`.
     */
    fun copath(
        leafIndex: Int,
        leafCount: Int,
    ): List<Int> {
        require(leafCount > 0) { "leafCount must be positive, got $leafCount" }
        require(leafIndex in 0 until leafCount) {
            "leafIndex $leafIndex out of range [0, $leafCount)"
        }
        val nodeIdx = leafToNode(leafIndex)
        val n = nodeCount(leafCount)
        val rootIdx = root(leafCount)

        val result = mutableListOf<Int>()
        var current = nodeIdx

        while (current != rootIdx) {
            result.add(sibling(current, n))
            current = parent(current, n)
        }
        return result
    }

    /**
     * Sibling of a node (the other child of the same parent).
     */
    fun sibling(
        nodeIndex: Int,
        nodeCount: Int,
    ): Int {
        val p = parent(nodeIndex, nodeCount)
        val l = left(p)
        val r = right(p)
        return if (nodeIndex == l) r else l
    }

    /**
     * All leaf indices that are in the subtree rooted at [nodeIndex].
     */
    fun subtreeLeaves(
        nodeIndex: Int,
        leafCount: Int,
    ): List<Int> {
        if (isLeaf(nodeIndex)) {
            return listOf(nodeToLeaf(nodeIndex))
        }

        val result = mutableListOf<Int>()
        collectLeaves(nodeIndex, leafCount, result)
        return result
    }

    private fun collectLeaves(
        nodeIndex: Int,
        leafCount: Int,
        result: MutableList<Int>,
    ) {
        if (isLeaf(nodeIndex)) {
            val leafIdx = nodeToLeaf(nodeIndex)
            if (leafIdx < leafCount) {
                result.add(leafIdx)
            }
            return
        }
        collectLeaves(left(nodeIndex), leafCount, result)
        collectLeaves(right(nodeIndex), leafCount, result)
    }

    /** Floor of log2(n) */
    private fun log2(n: Int): Int {
        var result = 0
        var value = n
        while (value > 1) {
            value = value shr 1
            result++
        }
        return result
    }
}
