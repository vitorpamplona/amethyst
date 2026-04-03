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
package com.vitorpamplona.quartz.marmot.mls.crypto

/**
 * Ed25519 digital signature operations for MLS ciphersuite 0x0001.
 *
 * Platform-specific implementations:
 * - JVM/Android: java.security EdDSA (Java 15+, Android API 33+)
 * - Native: expect/actual with kotlinx-crypto or platform crypto
 */
expect object Ed25519 {
    /**
     * Generate a new Ed25519 key pair.
     * @return pair of (privateKey: 64 bytes seed+public, publicKey: 32 bytes)
     */
    fun generateKeyPair(): Ed25519KeyPair

    /**
     * Sign a message using Ed25519.
     * @param message the data to sign
     * @param privateKey 64-byte private key (seed + public key)
     * @return 64-byte Ed25519 signature
     */
    fun sign(
        message: ByteArray,
        privateKey: ByteArray,
    ): ByteArray

    /**
     * Verify an Ed25519 signature.
     * @param message the signed data
     * @param signature 64-byte signature
     * @param publicKey 32-byte public key
     * @return true if signature is valid
     */
    fun verify(
        message: ByteArray,
        signature: ByteArray,
        publicKey: ByteArray,
    ): Boolean

    /**
     * Derive the public key from a private key.
     * @param privateKey 64-byte private key (seed + public key)
     * @return 32-byte public key
     */
    fun publicFromPrivate(privateKey: ByteArray): ByteArray
}

data class Ed25519KeyPair(
    val privateKey: ByteArray,
    val publicKey: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Ed25519KeyPair) return false
        return privateKey.contentEquals(other.privateKey) && publicKey.contentEquals(other.publicKey)
    }

    override fun hashCode(): Int {
        var result = privateKey.contentHashCode()
        result = 31 * result + publicKey.contentHashCode()
        return result
    }
}
