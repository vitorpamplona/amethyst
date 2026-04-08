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

import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.spec.NamedParameterSpec
import java.security.spec.XECPrivateKeySpec
import java.security.spec.XECPublicKeySpec
import javax.crypto.KeyAgreement

/**
 * JVM/Android X25519 implementation using java.security XDH.
 *
 * Requires Java 11+ or Android API 31+.
 *
 * Key format: raw 32-byte Curve25519 keys (little-endian per RFC 7748).
 */
actual object X25519 {
    private const val ALGORITHM = "X25519"
    private const val KEY_LENGTH = 32

    actual fun generateKeyPair(): X25519KeyPair {
        val kpg = KeyPairGenerator.getInstance("XDH")
        kpg.initialize(NamedParameterSpec(ALGORITHM))
        val kp = kpg.generateKeyPair()

        val publicKey = extractPublicKeyBytes(kp.public as java.security.interfaces.XECPublicKey)
        val privateKey = extractPrivateKeyBytes(kp.private as java.security.interfaces.XECPrivateKey)

        return X25519KeyPair(privateKey, publicKey)
    }

    actual fun dh(
        privateKey: ByteArray,
        publicKey: ByteArray,
    ): ByteArray {
        require(privateKey.size == KEY_LENGTH) { "Private key must be 32 bytes" }
        require(publicKey.size == KEY_LENGTH) { "Public key must be 32 bytes" }

        val kf = KeyFactory.getInstance("XDH")

        // Build JCA private key from raw bytes
        val privKeySpec = XECPrivateKeySpec(NamedParameterSpec(ALGORITHM), privateKey)
        val jcaPrivateKey = kf.generatePrivate(privKeySpec)

        // Build JCA public key from raw bytes (u-coordinate as BigInteger)
        val u = bytesToBigInteger(publicKey)
        val pubKeySpec = XECPublicKeySpec(NamedParameterSpec(ALGORITHM), u)
        val jcaPublicKey = kf.generatePublic(pubKeySpec)

        val ka = KeyAgreement.getInstance("XDH")
        ka.init(jcaPrivateKey)
        ka.doPhase(jcaPublicKey, true)

        val secret = ka.generateSecret()

        // Check for small-subgroup attack (RFC 9180 Section 4.1)
        require(!secret.all { it == 0.toByte() }) {
            "DH produced all-zero shared secret (possible small-subgroup attack)"
        }

        // XDH returns big-endian, X25519 shared secret is 32 bytes
        // Pad or trim to KEY_LENGTH
        return if (secret.size == KEY_LENGTH) {
            secret
        } else if (secret.size < KEY_LENGTH) {
            ByteArray(KEY_LENGTH - secret.size) + secret
        } else {
            secret.copyOfRange(secret.size - KEY_LENGTH, secret.size)
        }
    }

    actual fun publicFromPrivate(privateKey: ByteArray): ByteArray {
        require(privateKey.size == KEY_LENGTH) { "Private key must be 32 bytes" }

        val kf = KeyFactory.getInstance("XDH")
        val privKeySpec = XECPrivateKeySpec(NamedParameterSpec(ALGORITHM), privateKey)
        val jcaPrivateKey = kf.generatePrivate(privKeySpec)

        // Generate key pair from the same seed to get the public key
        val kpg = KeyPairGenerator.getInstance("XDH")
        kpg.initialize(NamedParameterSpec(ALGORITHM))

        // Re-derive: use the private key to create a keypair, then extract public
        // Unfortunately JCA doesn't have a direct way to do this, so we use key factory
        // The JCA will compute the public key when creating the key pair
        // Workaround: generate a dummy keypair and use DH with basepoint
        // Actually, XECPrivateKeySpec can be used with KeyFactory to get the paired public key
        val kp = kf.generatePrivate(privKeySpec)
        // Get the public key by computing DH with the basepoint (9)
        val basepoint = ByteArray(KEY_LENGTH)
        basepoint[0] = 9

        return dh(privateKey, basepoint)
    }

    /**
     * Extract 32-byte raw X25519 public key from JCA XECPublicKey.
     * The u-coordinate is stored as a BigInteger, we convert to little-endian bytes.
     */
    private fun extractPublicKeyBytes(pubKey: java.security.interfaces.XECPublicKey): ByteArray {
        val u = pubKey.u
        return bigIntegerToBytes(u)
    }

    /**
     * Extract raw scalar bytes from JCA XECPrivateKey.
     * Scalar is in little-endian byte order per JCA spec.
     */
    private fun extractPrivateKeyBytes(privKey: java.security.interfaces.XECPrivateKey): ByteArray {
        val scalar = privKey.scalar.orElseThrow { IllegalStateException("No scalar in private key") }
        return if (scalar.size == KEY_LENGTH) {
            scalar
        } else if (scalar.size < KEY_LENGTH) {
            // Pad with trailing zeros (little-endian: high-order bytes at end)
            val result = ByteArray(KEY_LENGTH)
            scalar.copyInto(result, 0)
            result
        } else {
            // Take only the first KEY_LENGTH bytes (little-endian low-order bytes)
            scalar.copyOfRange(0, KEY_LENGTH)
        }
    }

    /**
     * Convert little-endian 32-byte X25519 key to BigInteger for JCA.
     * RFC 7748 uses little-endian, JCA uses BigInteger (unsigned).
     */
    private fun bytesToBigInteger(bytes: ByteArray): java.math.BigInteger {
        // Reverse to big-endian and create unsigned BigInteger
        val be = ByteArray(bytes.size + 1) // prepend 0 for positive
        for (i in bytes.indices) {
            be[bytes.size - i] = bytes[i]
        }
        return java.math.BigInteger(be)
    }

    /**
     * Convert BigInteger (u-coordinate) to 32-byte little-endian.
     */
    private fun bigIntegerToBytes(bi: java.math.BigInteger): ByteArray {
        val beBytes = bi.toByteArray()
        val result = ByteArray(KEY_LENGTH)
        // BigInteger is big-endian, possibly with a leading zero sign byte.
        // Reverse into little-endian, taking at most KEY_LENGTH bytes from the low end.
        val bytesToCopy = minOf(beBytes.size, KEY_LENGTH)
        for (i in 0 until bytesToCopy) {
            result[i] = beBytes[beBytes.size - 1 - i]
        }
        return result
    }
}
