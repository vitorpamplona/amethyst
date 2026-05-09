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

    /**
     * Range-based [seal] — same semantics as [seal] but reads `aad` and
     * `plaintext` from sub-ranges of larger backing arrays. Default impl
     * slices and delegates; concrete impls (notably the JCA-backed
     * [com.vitorpamplona.quic.crypto.JcaAesGcmAead]) override to skip
     * the slice allocations entirely by passing the offsets through to
     * `Cipher.updateAAD` / `Cipher.doFinal`.
     *
     * Saves ~2 ByteArray allocations per outbound packet on the hot
     * path (the header `aad` and the payload `plaintext` no longer
     * need to be carved out of the in-progress packet buffer).
     */
    open fun sealRange(
        key: ByteArray,
        nonce: ByteArray,
        aad: ByteArray,
        aadOffset: Int,
        aadLength: Int,
        plaintext: ByteArray,
        plaintextOffset: Int,
        plaintextLength: Int,
    ): ByteArray {
        val a =
            if (aadOffset == 0 && aadLength == aad.size) aad else aad.copyOfRange(aadOffset, aadOffset + aadLength)
        val p =
            if (plaintextOffset == 0 && plaintextLength == plaintext.size) {
                plaintext
            } else {
                plaintext.copyOfRange(plaintextOffset, plaintextOffset + plaintextLength)
            }
        return seal(key, nonce, a, p)
    }

    /**
     * Range + in-place [seal]: read `aad` and `plaintext` from sub-ranges
     * and write ciphertext+tag DIRECTLY into [output] at [outputOffset].
     * Returns the number of bytes written (== plaintextLength + tagLength).
     *
     * Saves another ByteArray allocation per outbound packet vs.
     * [sealRange] — the build path can pre-allocate one final packet
     * buffer and have the ciphertext land in-place rather than copying
     * a fresh `seal()` result. Combined with the AAD+plaintext range
     * inputs already handled by [sealRange], a complete outbound packet
     * goes from ~4 allocations (headerBytes / paddedPlaintext /
     * ciphertext / final concat) down to ~2 (the final packet buffer
     * and the AEAD provider's internal scratch).
     *
     * Default impl falls back to [sealRange] + copy; the JCA-backed
     * [com.vitorpamplona.quic.crypto.JcaAesGcmAead] overrides to use
     * `Cipher.doFinal(input, inOff, inLen, output, outOff)`.
     */
    open fun sealInto(
        key: ByteArray,
        nonce: ByteArray,
        aad: ByteArray,
        aadOffset: Int,
        aadLength: Int,
        plaintext: ByteArray,
        plaintextOffset: Int,
        plaintextLength: Int,
        output: ByteArray,
        outputOffset: Int,
    ): Int {
        val ct = sealRange(key, nonce, aad, aadOffset, aadLength, plaintext, plaintextOffset, plaintextLength)
        ct.copyInto(output, outputOffset)
        return ct.size
    }

    /**
     * Range-based [open] — same semantics as [open] but reads `aad` and
     * `ciphertext` from sub-ranges. Default impl slices and delegates.
     */
    open fun openRange(
        key: ByteArray,
        nonce: ByteArray,
        aad: ByteArray,
        aadOffset: Int,
        aadLength: Int,
        ciphertext: ByteArray,
        ciphertextOffset: Int,
        ciphertextLength: Int,
    ): ByteArray? {
        val a =
            if (aadOffset == 0 && aadLength == aad.size) aad else aad.copyOfRange(aadOffset, aadOffset + aadLength)
        val c =
            if (ciphertextOffset == 0 && ciphertextLength == ciphertext.size) {
                ciphertext
            } else {
                ciphertext.copyOfRange(ciphertextOffset, ciphertextOffset + ciphertextLength)
            }
        return open(key, nonce, a, c)
    }
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
