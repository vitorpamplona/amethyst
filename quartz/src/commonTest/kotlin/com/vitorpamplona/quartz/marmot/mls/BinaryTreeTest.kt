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
}
