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
package com.vitorpamplona.quartz.nipXXBolt12Zaps.bolt12

import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.crypto.Nip01Crypto
import com.vitorpamplona.quartz.utils.sha256.sha256
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class Bolt12MerkleTest {
    @Test
    fun taggedHashMatchesTheDefinition() {
        val tag = "LnLeaf".encodeToByteArray()
        val msg = byteArrayOf(1, 2, 3, 4)
        val tagHash = sha256(tag)
        val expected = sha256(tagHash + tagHash + msg)
        assertContentEquals(expected, Bolt12Merkle.taggedHash(tag, msg))
    }

    @Test
    fun rootHashIsDeterministicAndDependsOnEveryRecord() {
        val records =
            listOf(
                TlvRecord(22, ByteArray(33) { 2 }),
                TlvRecord(170, Bolt12Values.tu64ToBytes(21_000)),
                TlvRecord(176, ByteArray(33) { 3 }),
            )
        val root = Bolt12Merkle.rootHash(records)
        assertEquals(32, root.size)
        assertContentEquals(root, Bolt12Merkle.rootHash(records))

        val altered =
            records.toMutableList().also {
                it[1] = TlvRecord(170, Bolt12Values.tu64ToBytes(21_001))
            }
        assertFalse(root.contentEquals(Bolt12Merkle.rootHash(altered)))
    }

    /**
     * End-to-end check of the tagged-hash → merkle-root → signature-digest → BIP-340
     * pipeline: a signature made over the digest of a record set verifies, and any
     * tampering with the records (which changes the root) makes it fail. This
     * validates the composition; byte-exact interop with CLN/LDK proofs additionally
     * needs the lightning/bolts#1346 test vectors.
     */
    @Test
    fun signatureOverTheMerkleRootVerifiesAndTamperingBreaksIt() {
        val key = KeyPair()
        val records =
            listOf(
                TlvRecord(88, ByteArray(33) { 2 }),
                TlvRecord(168, ByteArray(32) { it.toByte() }),
                TlvRecord(176, ByteArray(33) { 3 }),
            )

        val root = Bolt12Merkle.rootHash(records)
        val digest = Bolt12Merkle.signatureDigest("invoice", "signature", root)
        val sig = Nip01Crypto.sign(digest, key.privKey!!)

        assertTrue(Nip01Crypto.verify(sig, digest, key.pubKey))

        val tamperedRoot = Bolt12Merkle.rootHash(records.dropLast(1))
        val tamperedDigest = Bolt12Merkle.signatureDigest("invoice", "signature", tamperedRoot)
        assertFalse(Nip01Crypto.verify(sig, tamperedDigest, key.pubKey))
    }
}
