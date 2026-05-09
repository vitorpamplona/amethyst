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

import java.security.GeneralSecurityException
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * ChaCha20-Poly1305 AEAD via the JCA `ChaCha20-Poly1305` cipher
 * (Java 11+ / Android API 28+). Mirrors [JcaAesGcmAead]'s shape: caches
 * the [Cipher] + [SecretKeySpec] across calls and exposes the range
 * overloads ([sealRange] / [openRange] / [sealInto]) that pass
 * offsets straight to the JCA cipher's `doFinal(input, inOff, inLen,
 * output, outOff)` form, eliminating the per-packet slice
 * allocations the pure-Kotlin [ChaCha20Poly1305Aead] requires.
 *
 * Single-thread per direction (one PacketProtection per side, one
 * direction per side). Synchronization on a private monitor is
 * defence-in-depth — a future caller (test harness, key-update path)
 * sharing the instance across coroutines would otherwise corrupt
 * the cached `Cipher` state silently.
 */
class JcaChaCha20Poly1305Aead(
    key: ByteArray,
) : Aead() {
    override val keyLength = 32
    override val nonceLength = 12
    override val tagLength = 16

    private val keySpec = SecretKeySpec(key, "ChaCha20")
    private val encryptCipher: Cipher = Cipher.getInstance("ChaCha20-Poly1305")
    private val decryptCipher: Cipher = Cipher.getInstance("ChaCha20-Poly1305")

    /**
     * Last-N nonces successfully consumed by [seal] / [sealRange] /
     * [sealInto]. JCA's ChaCha20-Poly1305 (like AES-GCM) refuses to
     * encrypt under a (key, nonce) pair already used by THIS cipher
     * instance — even when the reuse is legitimate (Initial-padding
     * rebuild). When we detect a reuse against this history we fall
     * back to a fresh `Cipher.getInstance` to avoid fighting the
     * provider's safety check.
     */
    private val recentEncryptNonces = ArrayDeque<ByteArray>()

    override fun seal(
        key: ByteArray,
        nonce: ByteArray,
        aad: ByteArray,
        plaintext: ByteArray,
    ): ByteArray =
        synchronized(this) {
            sealCommon(nonce, aad, 0, aad.size, plaintext, 0, plaintext.size, output = null, outputOffset = 0)
                .first
        }

    override fun sealRange(
        key: ByteArray,
        nonce: ByteArray,
        aad: ByteArray,
        aadOffset: Int,
        aadLength: Int,
        plaintext: ByteArray,
        plaintextOffset: Int,
        plaintextLength: Int,
    ): ByteArray =
        synchronized(this) {
            sealCommon(nonce, aad, aadOffset, aadLength, plaintext, plaintextOffset, plaintextLength, output = null, outputOffset = 0)
                .first
        }

    override fun sealInto(
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
    ): Int =
        synchronized(this) {
            sealCommon(nonce, aad, aadOffset, aadLength, plaintext, plaintextOffset, plaintextLength, output, outputOffset)
                .second
        }

    /**
     * Shared seal path for all three entry points. Returns
     * `(freshOrEmpty, bytesWritten)`:
     *  - When [output] is null we allocate the result internally,
     *    return it as `freshOrEmpty`, and `bytesWritten` is its size.
     *  - When [output] is non-null we write into it at [outputOffset],
     *    return an empty array as `freshOrEmpty` (caller ignores), and
     *    `bytesWritten` is the number of bytes written.
     */
    private fun sealCommon(
        nonce: ByteArray,
        aad: ByteArray,
        aadOffset: Int,
        aadLength: Int,
        plaintext: ByteArray,
        plaintextOffset: Int,
        plaintextLength: Int,
        output: ByteArray?,
        outputOffset: Int,
    ): Pair<ByteArray, Int> {
        val reuse = recentEncryptNonces.any { it.contentEquals(nonce) }
        val cipher: Cipher
        if (reuse) {
            cipher = Cipher.getInstance("ChaCha20-Poly1305")
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, IvParameterSpec(nonce))
        } else {
            cipher = encryptCipher
            try {
                cipher.init(Cipher.ENCRYPT_MODE, keySpec, IvParameterSpec(nonce))
            } catch (t: Throwable) {
                if (recentEncryptNonces.lastOrNull()?.contentEquals(nonce) == true) {
                    recentEncryptNonces.removeLast()
                }
                throw t
            }
        }
        try {
            cipher.updateAAD(aad, aadOffset, aadLength)
            return if (output != null) {
                val written = cipher.doFinal(plaintext, plaintextOffset, plaintextLength, output, outputOffset)
                if (!reuse) rememberEncryptNonce(nonce)
                EMPTY_BYTES to written
            } else {
                val out = cipher.doFinal(plaintext, plaintextOffset, plaintextLength)
                if (!reuse) rememberEncryptNonce(nonce)
                out to out.size
            }
        } catch (t: Throwable) {
            if (!reuse && recentEncryptNonces.lastOrNull()?.contentEquals(nonce) == true) {
                recentEncryptNonces.removeLast()
            }
            throw t
        }
    }

    private fun rememberEncryptNonce(nonce: ByteArray) {
        recentEncryptNonces.addLast(nonce)
        while (recentEncryptNonces.size > NONCE_HISTORY_LIMIT) {
            recentEncryptNonces.removeFirst()
        }
    }

    override fun open(
        key: ByteArray,
        nonce: ByteArray,
        aad: ByteArray,
        ciphertext: ByteArray,
    ): ByteArray? =
        synchronized(this) {
            try {
                decryptCipher.init(Cipher.DECRYPT_MODE, keySpec, IvParameterSpec(nonce))
                decryptCipher.updateAAD(aad)
                decryptCipher.doFinal(ciphertext)
            } catch (_: GeneralSecurityException) {
                null
            }
        }

    override fun openRange(
        key: ByteArray,
        nonce: ByteArray,
        aad: ByteArray,
        aadOffset: Int,
        aadLength: Int,
        ciphertext: ByteArray,
        ciphertextOffset: Int,
        ciphertextLength: Int,
    ): ByteArray? =
        synchronized(this) {
            try {
                decryptCipher.init(Cipher.DECRYPT_MODE, keySpec, IvParameterSpec(nonce))
                decryptCipher.updateAAD(aad, aadOffset, aadLength)
                decryptCipher.doFinal(ciphertext, ciphertextOffset, ciphertextLength)
            } catch (_: GeneralSecurityException) {
                null
            }
        }

    private companion object {
        private const val NONCE_HISTORY_LIMIT = 8
        private val EMPTY_BYTES = ByteArray(0)
    }
}
