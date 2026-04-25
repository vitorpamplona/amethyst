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
package com.vitorpamplona.quartz.marmot.mls

import com.vitorpamplona.quartz.marmot.mls.tree.BinaryTree
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for MLS left-balanced binary tree arithmetic (RFC 9420 Section 7.1).
 *
 * Test vectors match RFC 9420 Appendix C examples.
 */
class BinaryTreeTest {
    @Test
    fun testNodeCount() {
        assertEquals(1, BinaryTree.nodeCount(1))
        assertEquals(3, BinaryTree.nodeCount(2))
        assertEquals(5, BinaryTree.nodeCount(3))
        assertEquals(7, BinaryTree.nodeCount(4))
        assertEquals(9, BinaryTree.nodeCount(5))
        assertEquals(15, BinaryTree.nodeCount(8))
    }

    @Test
    fun testLeafToNode() {
        assertEquals(0, BinaryTree.leafToNode(0))
        assertEquals(2, BinaryTree.leafToNode(1))
        assertEquals(4, BinaryTree.leafToNode(2))
        assertEquals(6, BinaryTree.leafToNode(3))
    }

    @Test
    fun testIsLeaf() {
        assertTrue(BinaryTree.isLeaf(0))
        assertFalse(BinaryTree.isLeaf(1))
        assertTrue(BinaryTree.isLeaf(2))
        assertFalse(BinaryTree.isLeaf(3))
        assertTrue(BinaryTree.isLeaf(4))
    }

    @Test
    fun testLevel() {
        // Leaves at level 0
        assertEquals(0, BinaryTree.level(0))
        assertEquals(0, BinaryTree.level(2))
        assertEquals(0, BinaryTree.level(4))
        assertEquals(0, BinaryTree.level(6))

        // Level 1 parents
        assertEquals(1, BinaryTree.level(1))
        assertEquals(1, BinaryTree.level(5))

        // Level 2 parent (root of 4-leaf tree)
        assertEquals(2, BinaryTree.level(3))

        // Level 3
        assertEquals(3, BinaryTree.level(7))
    }

    @Test
    fun testLeftRight() {
        // For node 1 (level 1): left=0, right=2
        assertEquals(0, BinaryTree.left(1))
        assertEquals(2, BinaryTree.right(1))

        // For node 5 (level 1): left=4, right=6
        assertEquals(4, BinaryTree.left(5))
        assertEquals(6, BinaryTree.right(5))

        // For node 3 (level 2): left=1, right=5
        assertEquals(1, BinaryTree.left(3))
        assertEquals(5, BinaryTree.right(3))
    }

    @Test
    fun testParent4Leaves() {
        // Tree with 4 leaves (7 nodes):
        //        3
        //       / \
        //      1   5
        //     / \ / \
        //    0  2 4  6

        val n = BinaryTree.nodeCount(4) // 7
        assertEquals(1, BinaryTree.parent(0, n))
        assertEquals(1, BinaryTree.parent(2, n))
        assertEquals(5, BinaryTree.parent(4, n))
        assertEquals(5, BinaryTree.parent(6, n))
        assertEquals(3, BinaryTree.parent(1, n))
        assertEquals(3, BinaryTree.parent(5, n))
    }

    @Test
    fun testRoot() {
        assertEquals(0, BinaryTree.root(1))
        assertEquals(1, BinaryTree.root(2))
        assertEquals(3, BinaryTree.root(4))
        assertEquals(7, BinaryTree.root(8))
    }

    @Test
    fun testDirectPath4Leaves() {
        // Leaf 0 -> direct path: [1, 3]
        assertEquals(listOf(1, 3), BinaryTree.directPath(0, 4))
        // Leaf 1 -> direct path: [1, 3]
        assertEquals(listOf(1, 3), BinaryTree.directPath(1, 4))
        // Leaf 2 -> direct path: [5, 3]
        assertEquals(listOf(5, 3), BinaryTree.directPath(2, 4))
        // Leaf 3 -> direct path: [5, 3]
        assertEquals(listOf(5, 3), BinaryTree.directPath(3, 4))
    }

    @Test
    fun testCopath4Leaves() {
        // Leaf 0 copath: sibling of 0 is 2, sibling of 1 is 5 -> [2, 5]
        assertEquals(listOf(2, 5), BinaryTree.copath(0, 4))
        // Leaf 2 copath: sibling of 4 is 6, sibling of 5 is 1 -> [6, 1]
        assertEquals(listOf(6, 1), BinaryTree.copath(2, 4))
    }

    @Test
    fun testSibling() {
        val n = BinaryTree.nodeCount(4)
        assertEquals(2, BinaryTree.sibling(0, n))
        assertEquals(0, BinaryTree.sibling(2, n))
        assertEquals(6, BinaryTree.sibling(4, n))
        assertEquals(5, BinaryTree.sibling(1, n))
        assertEquals(1, BinaryTree.sibling(5, n))
    }

    @Test
    fun testSubtreeLeaves() {
        // Subtree of node 1 contains leaves 0, 1
        assertEquals(listOf(0, 1), BinaryTree.subtreeLeaves(1, 4))
        // Subtree of node 5 contains leaves 2, 3
        assertEquals(listOf(2, 3), BinaryTree.subtreeLeaves(5, 4))
        // Subtree of root 3 contains all leaves
        assertEquals(listOf(0, 1, 2, 3), BinaryTree.subtreeLeaves(3, 4))
        // Subtree of leaf 0 is just [0]
        assertEquals(listOf(0), BinaryTree.subtreeLeaves(0, 4))
    }

    // ----- Non-power-of-2 tree shapes ----------------------------------------
    //
    // RFC 9420 Appendix C only worked example is the 4-leaf tree, but every
    // group with ≠ a power-of-2 members exercises the parentInRange branch in
    // BinaryTree.parent. Those branches were entirely uncovered before, and a
    // bug there (infinite recursion / infinite loop in directPath) is what
    // OOM'd amy when wn removed her in marmot-interop test 14.

    @Test
    fun testRoot_nonPowerOfTwo() {
        // root = (1 << ceil(log2(n))) - 1
        assertEquals(3, BinaryTree.root(3)) // ceil(log2(3))=2, 2^2-1 = 3
        assertEquals(7, BinaryTree.root(5)) // ceil(log2(5))=3, 2^3-1 = 7
        assertEquals(7, BinaryTree.root(6))
        assertEquals(7, BinaryTree.root(7))
        assertEquals(15, BinaryTree.root(9))
    }

    @Test
    fun testDirectPath3Leaves() {
        // 3 leaves, n=5, root=3:
        //          3
        //         / \
        //        1   4
        //       / \   \
        //      0   2   (leaf 2 sits at node 4; node 5 doesn't exist)
        // Node 4 is a leaf node here because the tree is not full — its
        // "parent slot" 5 would be ≥ nodeCount and is collapsed away by
        // parentInRange.
        assertEquals(listOf(1, 3), BinaryTree.directPath(0, 3))
        assertEquals(listOf(1, 3), BinaryTree.directPath(1, 3))
        assertEquals(listOf(3), BinaryTree.directPath(2, 3))
    }

    @Test
    fun testDirectPath5Leaves() {
        // 5 leaves, n=9, root=7:
        //              7
        //           /     \
        //          3       8 ← leaf 4 collapsed up
        //         / \
        //        1   5
        //       / \ / \
        //      0  2 4  6
        assertEquals(listOf(1, 3, 7), BinaryTree.directPath(0, 5))
        assertEquals(listOf(1, 3, 7), BinaryTree.directPath(1, 5))
        assertEquals(listOf(5, 3, 7), BinaryTree.directPath(2, 5))
        assertEquals(listOf(5, 3, 7), BinaryTree.directPath(3, 5))
        assertEquals(listOf(7), BinaryTree.directPath(4, 5))
    }

    @Test
    fun testDirectPathTerminatesForAllLeafCountsUpTo32() {
        // Property: every valid leaf in any tree size 1..32 has a directPath
        // that ends at root(leafCount) and has length == log2-ish. We don't
        // assert exact lengths — we just want to catch any future regression
        // where some (leafCount, leafIndex) triggers the parent() infinite
        // loop. Each call is wrapped in a generous timeout via the kotlin
        // test runner (default test timeout is fine: a non-terminating call
        // would OOM long before any test deadline).
        for (leafCount in 1..32) {
            val rootIdx = BinaryTree.root(leafCount)
            for (leafIndex in 0 until leafCount) {
                val dp = BinaryTree.directPath(leafIndex, leafCount)
                if (leafCount == 1) {
                    assertTrue(dp.isEmpty(), "leafCount=1 has no path")
                } else {
                    assertEquals(rootIdx, dp.last(), "directPath($leafIndex, $leafCount) should end at root")
                    // copath must align with the directPath one-for-one
                    assertEquals(dp.size, BinaryTree.copath(leafIndex, leafCount).size)
                }
            }
        }
    }

    // ----- Out-of-range inputs -----------------------------------------------
    //
    // Marmot-interop test 14 fails with a Java OOM because amy calls
    // `BinaryTree.directPath(myLeafIndex, tree.leafCount)` with `myLeafIndex
    // == tree.leafCount` (her own leaf was just removed and the tree shrank
    // past it). `parent()` then walks node indices ≥ nodeCount forever and
    // the result list explodes.
    //
    // These tests pin down that boundary so the failure becomes a clean
    // IllegalArgumentException instead of an OOM, AND so the MLS code path
    // that hits it has a deterministic regression.

    @Test
    fun testDirectPathRejectsOutOfRangeLeafIndex() {
        // leafIndex == leafCount — the exact shape the post-Remove path hits.
        assertFails { BinaryTree.directPath(2, 2) }
        // leafIndex > leafCount.
        assertFails { BinaryTree.directPath(5, 3) }
    }

    @Test
    fun testDirectPathRejectsNegativeLeafIndex() {
        assertFails { BinaryTree.directPath(-1, 4) }
    }

    @Test
    fun testCopathRejectsOutOfRangeLeafIndex() {
        assertFails { BinaryTree.copath(2, 2) }
        assertFails { BinaryTree.copath(-1, 4) }
    }

    @Test
    fun testParentRejectsRootOrAbove() {
        // The root has no parent. Asking anyway is the symptom of a tree
        // accounting bug — surface it loudly instead of looping.
        val n = BinaryTree.nodeCount(4)
        assertFails { BinaryTree.parent(BinaryTree.root(4), n) }
        // Index strictly above nodeCount also has no defined parent.
        assertFails { BinaryTree.parent(n, n) }
        assertFails { BinaryTree.parent(n + 5, n) }
    }
}
