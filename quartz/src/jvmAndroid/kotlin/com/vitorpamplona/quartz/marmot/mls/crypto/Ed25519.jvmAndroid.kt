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
import java.security.Provider
import java.security.Security
import java.security.Signature
import java.security.spec.EdECPrivateKeySpec
import java.security.spec.EdECPublicKeySpec
import java.security.spec.NamedParameterSpec

/**
 * JVM/Android Ed25519 implementation using java.security EdDSA.
 *
 * Requires Java 15+ or Android API 33+.
 *
 * Private key format: 32-byte seed + 32-byte public key (64 bytes total).
 * Public key format: 32-byte compressed Edwards point.
 */
actual object Ed25519 {
    private const val ALGORITHM = "Ed25519"
    private const val SEED_LENGTH = 32
    private const val PUBLIC_KEY_LENGTH = 32

    /**
     * Picks a non-AndroidKeyStore provider for the given JCA service.
     *
     * On Android, `KeyPairGenerator.getInstance("Ed25519")` can resolve to
     * `AndroidKeyStoreKeyPairGeneratorSpi`, which rejects `NamedParameterSpec`
     * and requires `KeyGenParameterSpec` instead. We need a software provider
     * (Conscrypt / SunEC) that supports the standard JCA Ed25519 interface.
     */
    private fun findProvider(service: String): Provider? =
        Security
            .getProviders("$service.$ALGORITHM")
            ?.firstOrNull { !it.name.contains("AndroidKeyStore", ignoreCase = true) }

    private val keyPairGeneratorProvider: Provider? = findProvider("KeyPairGenerator")
    private val keyFactoryProvider: Provider? = findProvider("KeyFactory")
    private val signatureProvider: Provider? = findProvider("Signature")

    private fun keyPairGenerator(): KeyPairGenerator =
        keyPairGeneratorProvider?.let { KeyPairGenerator.getInstance(ALGORITHM, it) }
            ?: KeyPairGenerator.getInstance(ALGORITHM)

    private fun keyFactory(): KeyFactory =
        keyFactoryProvider?.let { KeyFactory.getInstance(ALGORITHM, it) }
            ?: KeyFactory.getInstance(ALGORITHM)

    private fun signatureInstance(): Signature =
        signatureProvider?.let { Signature.getInstance(ALGORITHM, it) }
            ?: Signature.getInstance(ALGORITHM)

    actual fun generateKeyPair(): Ed25519KeyPair {
        // Ed25519 is fully specified by its algorithm name, so no initialize()
        // call is required. Calling initialize(NamedParameterSpec) would fail
        // on Android's keystore provider (which requires KeyGenParameterSpec).
        val kpg = keyPairGenerator()
        val kp = kpg.generateKeyPair()

        val publicKey = extractPublicKeyBytes(kp.public as java.security.interfaces.EdECPublicKey)
        val seed = extractPrivateKeyBytes(kp.private as java.security.interfaces.EdECPrivateKey)
        val privateKey = seed + publicKey

        return Ed25519KeyPair(privateKey, publicKey)
    }

    actual fun sign(
        message: ByteArray,
        privateKey: ByteArray,
    ): ByteArray {
        require(privateKey.size == SEED_LENGTH * 2) { "Private key must be 64 bytes (seed + public)" }

        val seed = privateKey.copyOfRange(0, SEED_LENGTH)
        val pubBytes = privateKey.copyOfRange(SEED_LENGTH, SEED_LENGTH * 2)

        val kf = keyFactory()
        val privKeySpec = EdECPrivateKeySpec(NamedParameterSpec(ALGORITHM), seed)
        val jcaPrivateKey = kf.generatePrivate(privKeySpec)

        val sig = signatureInstance()
        sig.initSign(jcaPrivateKey)
        sig.update(message)
        return sig.sign()
    }

    actual fun verify(
        message: ByteArray,
        signature: ByteArray,
        publicKey: ByteArray,
    ): Boolean {
        require(publicKey.size == PUBLIC_KEY_LENGTH) { "Public key must be 32 bytes" }

        val kf = keyFactory()
        val point = bytesToEdECPoint(publicKey)
        val pubKeySpec = EdECPublicKeySpec(NamedParameterSpec(ALGORITHM), point)
        val jcaPublicKey = kf.generatePublic(pubKeySpec)

        val sig = signatureInstance()
        sig.initVerify(jcaPublicKey)
        sig.update(message)
        return sig.verify(signature)
    }

    actual fun publicFromPrivate(privateKey: ByteArray): ByteArray {
        require(privateKey.size == SEED_LENGTH * 2) { "Private key must be 64 bytes (seed + public)" }
        return privateKey.copyOfRange(SEED_LENGTH, SEED_LENGTH * 2)
    }

    /**
     * Extract 32-byte compressed Edwards point from JCA EdECPublicKey.
     * The point encoding follows RFC 8032 Section 5.1.2.
     */
    private fun extractPublicKeyBytes(pubKey: java.security.interfaces.EdECPublicKey): ByteArray {
        val point = pubKey.point
        val yBytes = point.y.toByteArray()
        val result = ByteArray(PUBLIC_KEY_LENGTH)

        // BigInteger is big-endian, Edwards encoding is little-endian
        for (i in yBytes.indices) {
            val targetIdx = yBytes.size - 1 - i
            if (targetIdx < PUBLIC_KEY_LENGTH) {
                result[targetIdx] = yBytes[i]
            }
        }

        // Set high bit of last byte if x is odd
        if (point.isXOdd) {
            result[PUBLIC_KEY_LENGTH - 1] = (result[PUBLIC_KEY_LENGTH - 1].toInt() or 0x80).toByte()
        }

        return result
    }

    /**
     * Extract seed bytes from JCA EdECPrivateKey.
     */
    private fun extractPrivateKeyBytes(privKey: java.security.interfaces.EdECPrivateKey): ByteArray {
        val bytes = privKey.bytes.orElseThrow { IllegalStateException("No seed in private key") }
        return bytes.copyOf()
    }

    /**
     * Convert 32-byte compressed Edwards point to JCA EdECPoint.
     */
    private fun bytesToEdECPoint(publicKey: ByteArray): java.security.spec.EdECPoint {
        // RFC 8032: last bit of last byte encodes x parity
        val xOdd = (publicKey[PUBLIC_KEY_LENGTH - 1].toInt() and 0x80) != 0

        // Clear the high bit and reverse to big-endian for BigInteger
        val yLE = publicKey.copyOf()
        yLE[PUBLIC_KEY_LENGTH - 1] = (yLE[PUBLIC_KEY_LENGTH - 1].toInt() and 0x7F).toByte()

        // Reverse to big-endian
        val yBE = ByteArray(PUBLIC_KEY_LENGTH)
        for (i in 0 until PUBLIC_KEY_LENGTH) {
            yBE[i] = yLE[PUBLIC_KEY_LENGTH - 1 - i]
        }

        val y = java.math.BigInteger(1, yBE)
        return java.security.spec.EdECPoint(xOdd, y)
    }
}
