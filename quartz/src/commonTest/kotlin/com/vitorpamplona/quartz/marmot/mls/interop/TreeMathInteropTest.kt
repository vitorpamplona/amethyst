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
package com.vitorpamplona.quartz.marmot.mls.interop

import com.vitorpamplona.quartz.TestResourceLoader
import com.vitorpamplona.quartz.marmot.mls.tree.BinaryTree
import com.vitorpamplona.quartz.nip01Core.core.JsonMapper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Interop tests for MLS left-balanced binary tree arithmetic against IETF RFC 9420
 * test vectors from github.com/mlswg/mls-implementations (tree-math.json).
 *
 * Tree math is cipher-suite independent, so all vectors are tested.
 */
class TreeMathInteropTest {
    private val vectors: List<TreeMathVector> =
        JsonMapper.jsonInstance.decodeFromString<List<TreeMathVector>>(
            TestResourceLoader().loadString("mls/tree-math.json"),
        )

    @Test
    fun testNodeCount() {
        assertTrue(vectors.isNotEmpty(), "No tree-math vectors found")

        for (v in vectors) {
            assertEquals(
                v.nNodes,
                BinaryTree.nodeCount(v.nLeaves),
                "nodeCount mismatch for n_leaves=${v.nLeaves}",
            )
        }
    }

    @Test
    fun testRoot() {
        for (v in vectors) {
            assertEquals(
                v.root,
                BinaryTree.root(v.nLeaves),
                "root mismatch for n_leaves=${v.nLeaves}",
            )
        }
    }

    @Test
    fun testLeft() {
        for (v in vectors) {
            for ((nodeIndex, expected) in v.left.withIndex()) {
                if (expected != null) {
                    assertEquals(
                        expected,
                        BinaryTree.left(nodeIndex),
                        "left mismatch for node=$nodeIndex, n_leaves=${v.nLeaves}",
                    )
                }
            }
        }
    }

    @Test
    fun testRight() {
        for (v in vectors) {
            for ((nodeIndex, expected) in v.right.withIndex()) {
                if (expected != null) {
                    assertEquals(
                        expected,
                        BinaryTree.right(nodeIndex),
                        "right mismatch for node=$nodeIndex, n_leaves=${v.nLeaves}",
                    )
                }
            }
        }
    }

    @Test
    fun testParent() {
        for (v in vectors) {
            val nNodes = BinaryTree.nodeCount(v.nLeaves)
            for ((nodeIndex, expected) in v.parent.withIndex()) {
                if (expected != null) {
                    assertEquals(
                        expected,
                        BinaryTree.parent(nodeIndex, nNodes),
                        "parent mismatch for node=$nodeIndex, n_leaves=${v.nLeaves}",
                    )
                }
            }
        }
    }

    @Test
    fun testSibling() {
        for (v in vectors) {
            val nNodes = BinaryTree.nodeCount(v.nLeaves)
            for ((nodeIndex, expected) in v.sibling.withIndex()) {
                if (expected != null) {
                    assertEquals(
                        expected,
                        BinaryTree.sibling(nodeIndex, nNodes),
                        "sibling mismatch for node=$nodeIndex, n_leaves=${v.nLeaves}",
                    )
                }
            }
        }
    }
}
