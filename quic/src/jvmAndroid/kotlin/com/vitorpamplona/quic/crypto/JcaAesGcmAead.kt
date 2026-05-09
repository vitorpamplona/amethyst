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
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-128-GCM AEAD with the JCA `Cipher` and `SecretKeySpec` cached per
 * direction. Audits 1 + 3 flagged that going through Quartz's AESGCM (which
 * internally calls `Cipher.getInstance("AES/GCM/NoPadding")` per call) is
 * the dominant per-packet cost on the steady-state path. This wrapper
 * builds the Cipher + key spec ONCE; each seal/open does only `Cipher.init`
 * (which is much cheaper than `getInstance`) plus the AEAD math itself.
 *
 * Single-thread per direction: one PacketProtection feeds either the read
 * loop OR the send loop, never both. The class still synchronizes on a
 * private monitor as a defence-in-depth: the lock-split refactor in
 * `QuicConnectionDriver` keeps each side single-threaded by design, but
 * a future caller (test harness, key-update path) sharing the instance
 * across coroutines would otherwise corrupt the cached `Cipher` state
 * silently. The JCA `Cipher` itself is documented as not thread-safe.
 */
class JcaAesGcmAead(
    key: ByteArray,
) : Aead() {
    override val keyLength = 16
    override val nonceLength = 12
    override val tagLength = 16

    private val keySpec = SecretKeySpec(key, "AES")

    // Separate ciphers per direction. JCA's AES-GCM tracks the (key, iv) pair
    // across encrypt calls and rejects IV reuse — even when the IV reuse is
    // legitimate (our Initial-padding rebuild path re-encrypts the same PN).
    // Toggling DECRYPT_MODE between seals is fragile; using two ciphers
    // (one always-ENCRYPT, one always-DECRYPT) avoids the check entirely
    // since the encrypt-side IVs come from monotonic packet numbers and
    // never legitimately repeat OUTSIDE the rebuild edge case.
    //
    // For the rebuild edge case specifically, we fall back to a fresh
    // Cipher.getInstance — slow but rare (once per Initial datagram).
    private val encryptCipher: Cipher = Cipher.getInstance("AES/GCM/NoPadding")
    private val decryptCipher: Cipher = Cipher.getInstance("AES/GCM/NoPadding")

    /**
     * Last nonce successfully consumed by [seal]. We use a fresh
     * [Cipher.getInstance] when the caller asks us to seal under the
     * SAME nonce a second time (RFC 9000 §14 Initial-padding rebuild).
     *
     * Recent-history set rather than just the most-recent nonce: a
     * single intermediate seal between two rebuilds could otherwise
     * mask a duplicate against the SECOND-most-recent nonce, which
     * some JCA providers (Conscrypt) reject with
     * `InvalidAlgorithmParameterException` while others (SunJCE)
     * silently allow. Bounded at [NONCE_HISTORY_LIMIT] entries — far
     * more than any legitimate rebuild path needs (typically 1–2),
     * but cheap to keep.
     */
    private val recentEncryptNonces = ArrayDeque<ByteArray>()

    override fun seal(
        key: ByteArray,
        nonce: ByteArray,
        aad: ByteArray,
        plaintext: ByteArray,
    ): ByteArray =
        synchronized(this) {
            val reuse = recentEncryptNonces.any { it.contentEquals(nonce) }
            if (reuse) {
                val fresh = Cipher.getInstance("AES/GCM/NoPadding")
                fresh.init(Cipher.ENCRYPT_MODE, keySpec, GCMParameterSpec(128, nonce))
                fresh.updateAAD(aad)
                fresh.doFinal(plaintext)
            } else {
                try {
                    encryptCipher.init(Cipher.ENCRYPT_MODE, keySpec, GCMParameterSpec(128, nonce))
                    encryptCipher.updateAAD(aad)
                    val out = encryptCipher.doFinal(plaintext)
                    rememberEncryptNonce(nonce)
                    out
                } catch (t: Throwable) {
                    // Any throw mid-`init`/`updateAAD`/`doFinal` leaves the
                    // cached cipher in a provider-defined state. The next
                    // legitimate call's `init` should reset it, but if a
                    // crafted input triggers a partial init the next seal
                    // could conceivably reuse residual state. Drop the
                    // most recent nonce from the history so a retry with
                    // the same nonce takes the safe fresh-Cipher path.
                    if (recentEncryptNonces.lastOrNull()?.contentEquals(nonce) == true) {
                        recentEncryptNonces.removeLast()
                    }
                    throw t
                }
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
                decryptCipher.init(Cipher.DECRYPT_MODE, keySpec, GCMParameterSpec(128, nonce))
                decryptCipher.updateAAD(aad)
                decryptCipher.doFinal(ciphertext)
            } catch (_: GeneralSecurityException) {
                null
            }
        }

    /**
     * JCA-native range overload: `Cipher.updateAAD(byte[], offset, len)`
     * and `Cipher.doFinal(byte[], inputOffset, inputLen)` accept
     * sub-ranges directly, so we skip the two `copyOfRange` calls the
     * default impl would perform. Saves ~2 KB allocation per inbound
     * packet on the audio-rooms hot path (one for `aad`, one for
     * `ciphertext`, both sliced from a packet buffer that the caller
     * already owns).
     */
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
                decryptCipher.init(Cipher.DECRYPT_MODE, keySpec, GCMParameterSpec(128, nonce))
                decryptCipher.updateAAD(aad, aadOffset, aadLength)
                decryptCipher.doFinal(ciphertext, ciphertextOffset, ciphertextLength)
            } catch (_: GeneralSecurityException) {
                null
            }
        }

    /**
     * JCA-native range overload for [seal]. Same nonce-reuse history +
     * fresh-Cipher fallback as the whole-array [seal] path; the only
     * difference is the offset+length pass-through to `updateAAD` /
     * `doFinal`. Saves the slice allocations on the outbound hot path.
     */
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
            val reuse = recentEncryptNonces.any { it.contentEquals(nonce) }
            if (reuse) {
                val fresh = Cipher.getInstance("AES/GCM/NoPadding")
                fresh.init(Cipher.ENCRYPT_MODE, keySpec, GCMParameterSpec(128, nonce))
                fresh.updateAAD(aad, aadOffset, aadLength)
                fresh.doFinal(plaintext, plaintextOffset, plaintextLength)
            } else {
                try {
                    encryptCipher.init(Cipher.ENCRYPT_MODE, keySpec, GCMParameterSpec(128, nonce))
                    encryptCipher.updateAAD(aad, aadOffset, aadLength)
                    val out = encryptCipher.doFinal(plaintext, plaintextOffset, plaintextLength)
                    rememberEncryptNonce(nonce)
                    out
                } catch (t: Throwable) {
                    if (recentEncryptNonces.lastOrNull()?.contentEquals(nonce) == true) {
                        recentEncryptNonces.removeLast()
                    }
                    throw t
                }
            }
        }

    private companion object {
        private const val NONCE_HISTORY_LIMIT = 8
    }
}
