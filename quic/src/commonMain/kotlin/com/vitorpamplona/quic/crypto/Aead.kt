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
package com.vitorpamplona.quic.crypto

import com.vitorpamplona.quartz.nip44Encryption.crypto.ChaCha20Poly1305
import com.vitorpamplona.quartz.utils.ciphers.AESGCM

/**
 * AEAD selector, parameterised by TLS cipher-suite identifier.
 *
 * Implementations may be stateless singletons (the historical pattern;
 * `Aes128Gcm` and `ChaCha20Poly1305Aead` below) or stateful instances that
 * cache the underlying cipher / key spec across calls (the JVM-only
 * `JcaAesGcmAead` does this for the AES-GCM hot path). The `key` parameter
 * is included in seal/open for the singleton pattern; cached instances may
 * ignore it and use their bound key.
 */
abstract class Aead {
    abstract val keyLength: Int
    abstract val nonceLength: Int
    abstract val tagLength: Int

    abstract fun seal(
        key: ByteArray,
        nonce: ByteArray,
        aad: ByteArray,
        plaintext: ByteArray,
    ): ByteArray

    /** Returns null on auth-tag failure. */
    abstract fun open(
        key: ByteArray,
        nonce: ByteArray,
        aad: ByteArray,
        ciphertext: ByteArray,
    ): ByteArray?
}

/** AES-128-GCM AEAD via Quartz's AESGCM (which uses JCA underneath on JVM/Android). */
object Aes128Gcm : Aead() {
    override val keyLength = 16
    override val nonceLength = 12
    override val tagLength = 16

    override fun seal(
        key: ByteArray,
        nonce: ByteArray,
        aad: ByteArray,
        plaintext: ByteArray,
    ): ByteArray {
        require(key.size == keyLength) { "AES-128-GCM key must be 16 bytes" }
        require(nonce.size == nonceLength) { "AES-128-GCM nonce must be 12 bytes" }
        return AESGCM(key, nonce).encrypt(plaintext, aad)
    }

    override fun open(
        key: ByteArray,
        nonce: ByteArray,
        aad: ByteArray,
        ciphertext: ByteArray,
    ): ByteArray? {
        require(key.size == keyLength) { "AES-128-GCM key must be 16 bytes" }
        require(nonce.size == nonceLength) { "AES-128-GCM nonce must be 12 bytes" }
        return try {
            AESGCM(key, nonce).decrypt(ciphertext, aad)
        } catch (_: Throwable) {
            null
        }
    }
}

/** ChaCha20-Poly1305 AEAD via Quartz's pure-Kotlin implementation. */
object ChaCha20Poly1305Aead : Aead() {
    override val keyLength = 32
    override val nonceLength = 12
    override val tagLength = 16

    override fun seal(
        key: ByteArray,
        nonce: ByteArray,
        aad: ByteArray,
        plaintext: ByteArray,
    ): ByteArray {
        require(key.size == keyLength) { "ChaCha20-Poly1305 key must be 32 bytes" }
        require(nonce.size == nonceLength) { "ChaCha20-Poly1305 nonce must be 12 bytes" }
        return ChaCha20Poly1305.encrypt(plaintext, aad, nonce, key)
    }

    override fun open(
        key: ByteArray,
        nonce: ByteArray,
        aad: ByteArray,
        ciphertext: ByteArray,
    ): ByteArray? {
        require(key.size == keyLength) { "ChaCha20-Poly1305 key must be 32 bytes" }
        require(nonce.size == nonceLength) { "ChaCha20-Poly1305 nonce must be 12 bytes" }
        return try {
            ChaCha20Poly1305.decrypt(ciphertext, aad, nonce, key)
        } catch (_: Throwable) {
            null
        }
    }
}

/**
 * Build a QUIC AEAD nonce from a static IV and a packet number.
 *
 * RFC 9001 §5.3: nonce = static_iv XOR (packet_number padded to nonce length, big-endian).
 */
fun aeadNonce(
    staticIv: ByteArray,
    packetNumber: Long,
): ByteArray {
    val nonce = staticIv.copyOf()
    val len = nonce.size
    for (i in 0 until 8) {
        nonce[len - 1 - i] = (nonce[len - 1 - i].toInt() xor ((packetNumber ushr (i * 8)).toInt() and 0xFF)).toByte()
    }
    return nonce
}
