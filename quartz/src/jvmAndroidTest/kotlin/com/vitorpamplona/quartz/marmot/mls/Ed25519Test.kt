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

import com.vitorpamplona.quartz.marmot.mls.crypto.Ed25519
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for Ed25519 signature operations on JVM.
 * Verifies key generation, signing, verification, and interoperability.
 */
class Ed25519Test {
    @Test
    fun testKeyPairGeneration() {
        val kp = Ed25519.generateKeyPair()
        assertEquals(64, kp.privateKey.size, "Private key should be 64 bytes (seed + public)")
        assertEquals(32, kp.publicKey.size, "Public key should be 32 bytes")
    }

    @Test
    fun testPublicFromPrivate() {
        val kp = Ed25519.generateKeyPair()
        val derived = Ed25519.publicFromPrivate(kp.privateKey)
        assertContentEquals(kp.publicKey, derived)
    }

    @Test
    fun testSignAndVerify() {
        val kp = Ed25519.generateKeyPair()
        val message = "Hello MLS!".encodeToByteArray()

        val signature = Ed25519.sign(message, kp.privateKey)
        assertEquals(64, signature.size, "Ed25519 signature should be 64 bytes")

        assertTrue(Ed25519.verify(message, signature, kp.publicKey), "Signature should verify")
    }

    @Test
    fun testSignatureFailsWithWrongKey() {
        val kp1 = Ed25519.generateKeyPair()
        val kp2 = Ed25519.generateKeyPair()
        val message = "Hello MLS!".encodeToByteArray()

        val signature = Ed25519.sign(message, kp1.privateKey)

        assertFalse(Ed25519.verify(message, signature, kp2.publicKey), "Signature should not verify with wrong key")
    }

    @Test
    fun testSignatureFailsWithWrongMessage() {
        val kp = Ed25519.generateKeyPair()
        val message = "Hello MLS!".encodeToByteArray()
        val wrongMessage = "Hello Wrong!".encodeToByteArray()

        val signature = Ed25519.sign(message, kp.privateKey)

        assertFalse(Ed25519.verify(wrongMessage, signature, kp.publicKey), "Signature should not verify with wrong message")
    }

    @Test
    fun testDeterministicSignatures() {
        val kp = Ed25519.generateKeyPair()
        val message = "Deterministic test".encodeToByteArray()

        val sig1 = Ed25519.sign(message, kp.privateKey)
        val sig2 = Ed25519.sign(message, kp.privateKey)

        // Ed25519 signatures are deterministic (RFC 8032)
        assertContentEquals(sig1, sig2)
    }

    @Test
    fun testEmptyMessage() {
        val kp = Ed25519.generateKeyPair()
        val message = ByteArray(0)

        val signature = Ed25519.sign(message, kp.privateKey)
        assertTrue(Ed25519.verify(message, signature, kp.publicKey))
    }

    @Test
    fun testLargeMessage() {
        val kp = Ed25519.generateKeyPair()
        val message = ByteArray(10000) { it.toByte() }

        val signature = Ed25519.sign(message, kp.privateKey)
        assertTrue(Ed25519.verify(message, signature, kp.publicKey))
    }

    private fun assertContentEquals(
        expected: ByteArray,
        actual: ByteArray,
    ) {
        kotlin.test.assertContentEquals(expected, actual)
    }
}
