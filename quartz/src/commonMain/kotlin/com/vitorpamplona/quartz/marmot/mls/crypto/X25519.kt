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
 * X25519 Diffie-Hellman key exchange for MLS DHKEM (RFC 9180).
 *
 * Used in TreeKEM for path secret encryption via HPKE.
 *
 * Platform-specific implementations:
 * - JVM/Android: java.security XDH (Java 11+, Android API 31+)
 * - Native: expect/actual with platform crypto
 */
expect object X25519 {
    /**
     * Generate a new X25519 key pair.
     * @return pair of (privateKey: 32 bytes, publicKey: 32 bytes)
     */
    fun generateKeyPair(): X25519KeyPair

    /**
     * Perform X25519 Diffie-Hellman key agreement.
     * @param privateKey 32-byte private key
     * @param publicKey 32-byte public key
     * @return 32-byte shared secret
     */
    fun dh(
        privateKey: ByteArray,
        publicKey: ByteArray,
    ): ByteArray

    /**
     * Derive the public key from a private key.
     * @param privateKey 32-byte private key
     * @return 32-byte public key
     */
    fun publicFromPrivate(privateKey: ByteArray): ByteArray
}

data class X25519KeyPair(
    val privateKey: ByteArray,
    val publicKey: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is X25519KeyPair) return false
        return privateKey.contentEquals(other.privateKey) && publicKey.contentEquals(other.publicKey)
    }

    override fun hashCode(): Int {
        var result = privateKey.contentHashCode()
        result = 31 * result + publicKey.contentHashCode()
        return result
    }
}
