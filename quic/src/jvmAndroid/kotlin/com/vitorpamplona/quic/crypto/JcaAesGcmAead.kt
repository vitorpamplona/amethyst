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
 * loop OR the send loop, never both. Locking would be needed if that ever
 * changes.
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
    private var lastEncryptNonce: ByteArray? = null

    override fun seal(
        key: ByteArray,
        nonce: ByteArray,
        aad: ByteArray,
        plaintext: ByteArray,
    ): ByteArray {
        // Detect IV reuse — happens on the Initial-padding rebuild path. Fall
        // back to a one-shot fresh Cipher for that case rather than fighting
        // JCA's safety check.
        val reuse = lastEncryptNonce?.contentEquals(nonce) == true
        return if (reuse) {
            val fresh = Cipher.getInstance("AES/GCM/NoPadding")
            fresh.init(Cipher.ENCRYPT_MODE, keySpec, GCMParameterSpec(128, nonce))
            fresh.updateAAD(aad)
            fresh.doFinal(plaintext)
        } else {
            encryptCipher.init(Cipher.ENCRYPT_MODE, keySpec, GCMParameterSpec(128, nonce))
            encryptCipher.updateAAD(aad)
            val out = encryptCipher.doFinal(plaintext)
            lastEncryptNonce = nonce
            out
        }
    }

    override fun open(
        key: ByteArray,
        nonce: ByteArray,
        aad: ByteArray,
        ciphertext: ByteArray,
    ): ByteArray? =
        try {
            decryptCipher.init(Cipher.DECRYPT_MODE, keySpec, GCMParameterSpec(128, nonce))
            decryptCipher.updateAAD(aad)
            decryptCipher.doFinal(ciphertext)
        } catch (_: GeneralSecurityException) {
            null
        }
}
