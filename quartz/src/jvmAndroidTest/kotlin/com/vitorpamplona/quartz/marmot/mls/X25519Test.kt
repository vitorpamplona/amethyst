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

import com.vitorpamplona.quartz.marmot.mls.crypto.X25519
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Tests for X25519 Diffie-Hellman key exchange on JVM.
 * Verifies key generation, DH agreement, and interoperability.
 */
class X25519Test {
    @Test
    fun testKeyPairGeneration() {
        val kp = X25519.generateKeyPair()
        assertEquals(32, kp.privateKey.size, "Private key should be 32 bytes")
        assertEquals(32, kp.publicKey.size, "Public key should be 32 bytes")
    }

    @Test
    fun testDhAgreement() {
        val alice = X25519.generateKeyPair()
        val bob = X25519.generateKeyPair()

        val sharedAlice = X25519.dh(alice.privateKey, bob.publicKey)
        val sharedBob = X25519.dh(bob.privateKey, alice.publicKey)

        assertEquals(32, sharedAlice.size, "Shared secret should be 32 bytes")
        assertContentEquals(sharedAlice, sharedBob, "Both sides should derive the same shared secret")
    }

    @Test
    fun testDhWithDifferentKeys() {
        val alice = X25519.generateKeyPair()
        val bob = X25519.generateKeyPair()
        val carol = X25519.generateKeyPair()

        val sharedAB = X25519.dh(alice.privateKey, bob.publicKey)
        val sharedAC = X25519.dh(alice.privateKey, carol.publicKey)

        assertFalse(sharedAB.contentEquals(sharedAC), "Different key pairs should produce different shared secrets")
    }

    @Test
    fun testPublicFromPrivate() {
        val kp = X25519.generateKeyPair()
        val derived = X25519.publicFromPrivate(kp.privateKey)

        // The derived public key should produce the same DH result
        val bob = X25519.generateKeyPair()
        val shared1 = X25519.dh(kp.privateKey, bob.publicKey)
        // If publicFromPrivate works, using the derived key for the reverse DH should match
        val shared2 = X25519.dh(bob.privateKey, derived)
        assertContentEquals(shared1, shared2)
    }

    @Test
    fun testMultipleKeyPairsAreDistinct() {
        val kp1 = X25519.generateKeyPair()
        val kp2 = X25519.generateKeyPair()

        assertFalse(kp1.privateKey.contentEquals(kp2.privateKey), "Generated private keys should be distinct")
        assertFalse(kp1.publicKey.contentEquals(kp2.publicKey), "Generated public keys should be distinct")
    }

    private fun assertContentEquals(
        expected: ByteArray,
        actual: ByteArray,
        message: String = "",
    ) {
        kotlin.test.assertContentEquals(expected, actual, message)
    }
}
