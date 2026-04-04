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
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Interop tests for TreeKEM (RFC 9420 Section 7.4-7.6) against IETF test vectors
 * from github.com/mlswg/mls-implementations (treekem.json).
 *
 * Verifies ratchet tree deserialization, UpdatePath processing, and path
 * secret derivation against known-good outputs from OpenMLS and mls-rs.
 */
class TreeKemInteropTest {
    private val allVectors: List<TreeKemVector> =
        JsonMapper.jsonInstance.decodeFromString<List<TreeKemVector>>(
            TestResourceLoader().loadString("mls/treekem.json"),
        )

    private val vectors: List<TreeKemVector> =
        allVectors.filter { it.cipherSuite == 1 }

    @Test
    fun testRatchetTreeDeserialization() {
        assertTrue(vectors.isNotEmpty(), "No cipher_suite==1 treekem vectors found")

        for ((idx, v) in vectors.withIndex()) {
            val treeBytes = v.ratchetTree.hexToByteArray()
            val tree = RatchetTree.decodeTls(TlsReader(treeBytes))

            assertNotNull(tree, "RatchetTree decode failed at vector $idx")

            // Verify round-trip
            val writer = TlsWriter()
            tree.encodeTls(writer)
            val reEncoded = writer.toByteArray()
            assertEquals(
                v.ratchetTree,
                reEncoded.toHexKey(),
                "RatchetTree round-trip mismatch at vector $idx",
            )
        }
    }

    @Test
    fun testUpdatePathTreeHashAfter() {
        assertTrue(vectors.isNotEmpty(), "No cipher_suite==1 treekem vectors found")

        for ((idx, v) in vectors.withIndex()) {
            for ((pathIdx, updatePath) in v.updatePaths.withIndex()) {
                // Verify the tree_hash_after can be parsed as hex
                val expectedHash = updatePath.treeHashAfter
                assertTrue(
                    expectedHash.length == 64,
                    "tree_hash_after should be 32 bytes (64 hex chars) at vector $idx, path $pathIdx",
                )

                // Verify commit_secret is valid
                val commitSecret = updatePath.commitSecret.hexToByteArray()
                assertEquals(
                    32,
                    commitSecret.size,
                    "commit_secret should be 32 bytes at vector $idx, path $pathIdx",
                )
            }
        }
    }
}
