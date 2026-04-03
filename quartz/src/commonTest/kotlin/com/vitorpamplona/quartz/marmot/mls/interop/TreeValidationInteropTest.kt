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
import com.vitorpamplona.quartz.marmot.mls.codec.TlsReader
import com.vitorpamplona.quartz.marmot.mls.codec.TlsWriter
import com.vitorpamplona.quartz.marmot.mls.tree.BinaryTree
import com.vitorpamplona.quartz.marmot.mls.tree.RatchetTree
import com.vitorpamplona.quartz.nip01Core.core.JsonMapper
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Interop tests for MLS ratchet tree validation (RFC 9420 Section 7)
 * against IETF test vectors from github.com/mlswg/mls-implementations
 * (tree-validation.json).
 *
 * Verifies tree hash computation and resolution for ratchet trees
 * produced by other MLS implementations.
 */
class TreeValidationInteropTest {
    private val allVectors: List<TreeValidationVector> =
        JsonMapper.jsonInstance.decodeFromString<List<TreeValidationVector>>(
            TestResourceLoader().loadString("mls/tree-validation.json"),
        )

    private val vectors: List<TreeValidationVector> =
        allVectors.filter { it.cipherSuite == 1 }

    @Test
    fun testTreeDeserialization() {
        assertTrue(vectors.isNotEmpty(), "No cipher_suite==1 tree-validation vectors found")

        for ((idx, v) in vectors.withIndex()) {
            val treeBytes = v.tree.hexToByteArray()
            val tree = RatchetTree.decodeTls(TlsReader(treeBytes))

            // Verify round-trip serialization
            val writer = TlsWriter()
            tree.encodeTls(writer)
            val reEncoded = writer.toByteArray()
            assertEquals(
                v.tree,
                reEncoded.toHexKey(),
                "Tree round-trip mismatch at vector $idx",
            )
        }
    }

    @Test
    fun testTreeHash() {
        assertTrue(vectors.isNotEmpty(), "No cipher_suite==1 tree-validation vectors found")

        for ((idx, v) in vectors.withIndex()) {
            val treeBytes = v.tree.hexToByteArray()
            val tree = RatchetTree.decodeTls(TlsReader(treeBytes))

            // The root tree hash should match the last entry in tree_hashes
            val rootHash = tree.treeHash()
            val rootIdx = BinaryTree.root(tree.leafCount)
            assertEquals(
                v.treeHashes[rootIdx],
                rootHash.toHexKey(),
                "Root tree hash mismatch at vector $idx",
            )
        }
    }

    @Test
    fun testResolution() {
        assertTrue(vectors.isNotEmpty(), "No cipher_suite==1 tree-validation vectors found")

        for ((idx, v) in vectors.withIndex()) {
            val treeBytes = v.tree.hexToByteArray()
            val tree = RatchetTree.decodeTls(TlsReader(treeBytes))

            val nodeCount = BinaryTree.nodeCount(tree.leafCount)
            for (nodeIdx in 0 until nodeCount) {
                if (nodeIdx < v.resolutions.size) {
                    val expected = v.resolutions[nodeIdx]
                    val actual = tree.resolution(nodeIdx)
                    assertEquals(
                        expected,
                        actual,
                        "Resolution mismatch at vector $idx, node $nodeIdx",
                    )
                }
            }
        }
    }
}
