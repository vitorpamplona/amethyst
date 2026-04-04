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
import com.vitorpamplona.quartz.marmot.mls.schedule.SecretTree
import com.vitorpamplona.quartz.nip01Core.core.JsonMapper
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Interop tests for MLS Secret Tree (RFC 9420 Section 9) against IETF test vectors
 * from github.com/mlswg/mls-implementations (secret-tree.json).
 *
 * Verifies per-sender key/nonce derivation from the epoch's encryption_secret,
 * for both handshake and application ratchets at specific generations.
 */
class SecretTreeInteropTest {
    private val allVectors: List<SecretTreeVector> =
        JsonMapper.jsonInstance.decodeFromString<List<SecretTreeVector>>(
            TestResourceLoader().loadString("mls/secret-tree.json"),
        )

    private val vectors: List<SecretTreeVector> =
        allVectors.filter { it.cipherSuite == 1 }

    @Test
    fun testApplicationKeys() {
        assertTrue(vectors.isNotEmpty(), "No cipher_suite==1 secret-tree vectors found")

        for ((vectorIdx, v) in vectors.withIndex()) {
            val leafCount = v.leaves.size
            val secretTree = SecretTree(v.encryptionSecret.hexToByteArray(), leafCount)

            for ((leafIdx, leafGens) in v.leaves.withIndex()) {
                for (gen in leafGens) {
                    val result =
                        if (gen.generation == 0) {
                            secretTree.nextApplicationKeyNonce(leafIdx)
                        } else {
                            secretTree.applicationKeyNonceForGeneration(leafIdx, gen.generation)
                        }

                    assertEquals(
                        gen.applicationKey,
                        result.key.toHexKey(),
                        "application_key mismatch: vector=$vectorIdx, leaf=$leafIdx, gen=${gen.generation}",
                    )
                    assertEquals(
                        gen.applicationNonce,
                        result.nonce.toHexKey(),
                        "application_nonce mismatch: vector=$vectorIdx, leaf=$leafIdx, gen=${gen.generation}",
                    )
                }
            }
        }
    }

    @Test
    fun testHandshakeKeys() {
        assertTrue(vectors.isNotEmpty(), "No cipher_suite==1 secret-tree vectors found")

        for ((vectorIdx, v) in vectors.withIndex()) {
            val leafCount = v.leaves.size
            val secretTree = SecretTree(v.encryptionSecret.hexToByteArray(), leafCount)

            for ((leafIdx, leafGens) in v.leaves.withIndex()) {
                var currentHandshakeGen = 0

                for (gen in leafGens) {
                    // Advance the handshake ratchet to the target generation
                    while (currentHandshakeGen < gen.generation) {
                        secretTree.nextHandshakeKeyNonce(leafIdx)
                        currentHandshakeGen++
                    }

                    val result = secretTree.nextHandshakeKeyNonce(leafIdx)
                    currentHandshakeGen++

                    assertEquals(
                        gen.handshakeKey,
                        result.key.toHexKey(),
                        "handshake_key mismatch: vector=$vectorIdx, leaf=$leafIdx, gen=${gen.generation}",
                    )
                    assertEquals(
                        gen.handshakeNonce,
                        result.nonce.toHexKey(),
                        "handshake_nonce mismatch: vector=$vectorIdx, leaf=$leafIdx, gen=${gen.generation}",
                    )
                }
            }
        }
    }
}
