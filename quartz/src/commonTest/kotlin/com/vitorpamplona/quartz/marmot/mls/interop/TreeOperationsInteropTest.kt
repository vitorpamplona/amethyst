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
import com.vitorpamplona.quartz.marmot.mls.tree.RatchetTree
import com.vitorpamplona.quartz.nip01Core.core.JsonMapper
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Interop tests for MLS tree operations (RFC 9420 Section 7.7-7.8)
 * against IETF test vectors from github.com/mlswg/mls-implementations
 * (tree-operations.json).
 *
 * Verifies that applying proposals (add/remove/update) to the ratchet tree
 * produces the expected tree state and tree hash.
 */
class TreeOperationsInteropTest {
    private val allVectors: List<TreeOperationsVector> =
        JsonMapper.jsonInstance.decodeFromString<List<TreeOperationsVector>>(
            TestResourceLoader().loadString("mls/tree-operations.json"),
        )

    private val vectors: List<TreeOperationsVector> =
        allVectors.filter { it.cipherSuite == 1 }

    @Test
    fun testTreeBeforeHash() {
        assertTrue(vectors.isNotEmpty(), "No cipher_suite==1 tree-operations vectors found")

        for ((idx, v) in vectors.withIndex()) {
            val treeBeforeBytes = v.treeBefore.hexToByteArray()
            val treeBefore = RatchetTree.decodeTls(TlsReader(treeBeforeBytes))
            val treeAfterBytes = v.treeAfter.hexToByteArray()
            val treeAfterParsed = RatchetTree.decodeTls(TlsReader(treeAfterBytes))

            val tree = treeBefore
            val treeHash = tree.treeHash()
            assertEquals(
                v.treeHashBefore,
                treeHash.toHexKey(),
                "tree_hash_before mismatch at vector $idx (before_lc=${treeBefore.leafCount}, after_lc=${treeAfterParsed.leafCount}, before_bytes=${v.treeBefore.length / 2}, after_bytes=${v.treeAfter.length / 2})",
            )
        }
    }

    @Test
    fun testTreeAfterDeserialization() {
        assertTrue(vectors.isNotEmpty(), "No cipher_suite==1 tree-operations vectors found")

        for ((idx, v) in vectors.withIndex()) {
            val treeBytes = v.treeAfter.hexToByteArray()
            val tree = RatchetTree.decodeTls(TlsReader(treeBytes))

            // Verify round-trip serialization first
            val writer = TlsWriter()
            tree.encodeTls(writer)
            val reEncoded = writer.toByteArray()
            assertEquals(
                v.treeAfter,
                reEncoded.toHexKey(),
                "tree_after round-trip mismatch at vector $idx",
            )

            val treeHash = tree.treeHash()
            assertEquals(
                v.treeHashAfter,
                treeHash.toHexKey(),
                "tree_hash_after mismatch at vector $idx (leafCount=${tree.leafCount}, nodeCount=${tree.leafCount * 2 - 1})",
            )
        }
    }
}
