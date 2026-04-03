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

import com.vitorpamplona.quartz.utils.ciphers.AESGCM

/**
 * HPKE (Hybrid Public Key Encryption) implementation for MLS (RFC 9180).
 *
 * Mode: Base (0x00) — no PSK, no authentication
 * KEM: DHKEM(X25519, HKDF-SHA256) — KEM ID 0x0020
 * KDF: HKDF-SHA256 — KDF ID 0x0001
 * AEAD: AES-128-GCM — AEAD ID 0x0001
 *
 * This matches MLS ciphersuite 0x0001.
 *
 * Reference: RFC 9180 Section 4 (Base Mode), Section 4.1 (DHKEM)
 */
object Hpke {
    // Suite IDs per RFC 9180
    private val KEM_SUITE_ID = "KEM".encodeToByteArray() + byteArrayOf(0x00, 0x20) // DHKEM(X25519)
    private val KDF_ID = byteArrayOf(0x00, 0x01) // HKDF-SHA256
    private val AEAD_ID = byteArrayOf(0x00, 0x01) // AES-128-GCM
    private val HPKE_SUITE_ID = "HPKE".encodeToByteArray() + byteArrayOf(0x00, 0x20) + KDF_ID + AEAD_ID

    private const val N_SECRET = 32 // KEM shared secret length
    private const val N_ENC = 32 // KEM encapsulated key length
    private const val N_K = 16 // AES-128-GCM key length
    private const val N_N = 12 // AES-128-GCM nonce length
    private const val N_H = 32 // HKDF-SHA256 output length

    /**
     * HPKE single-shot seal (encrypt).
     *
     * Generates an ephemeral X25519 key pair, performs DHKEM encapsulation,
     * derives an AEAD key, and encrypts the plaintext.
     *
     * @param recipientPub 32-byte X25519 public key of the recipient
     * @param info context info for key derivation
     * @param aad associated authenticated data
     * @param plaintext data to encrypt
     * @return HpkeCiphertext containing KEM output (ephemeral public key) and encrypted data
     */
    fun seal(
        recipientPub: ByteArray,
        info: ByteArray,
        aad: ByteArray,
        plaintext: ByteArray,
    ): HpkeCiphertext {
        val (sharedSecret, enc) = encap(recipientPub)
        val (key, baseNonce) = keySchedule(sharedSecret, info)
        val ciphertext = aeadSeal(key, baseNonce, aad, plaintext)
        return HpkeCiphertext(enc, ciphertext)
    }

    /**
     * HPKE single-shot open (decrypt).
     *
     * Performs DHKEM decapsulation using the recipient's private key,
     * derives the AEAD key, and decrypts the ciphertext.
     *
     * @param recipientPriv 32-byte X25519 private key
     * @param enc 32-byte KEM output (ephemeral public key from sender)
     * @param info context info for key derivation
     * @param aad associated authenticated data
     * @param ciphertext encrypted data + tag
     * @return decrypted plaintext
     */
    fun open(
        recipientPriv: ByteArray,
        enc: ByteArray,
        info: ByteArray,
        aad: ByteArray,
        ciphertext: ByteArray,
    ): ByteArray {
        val sharedSecret = decap(enc, recipientPriv)
        val (key, baseNonce) = keySchedule(sharedSecret, info)
        return aeadOpen(key, baseNonce, aad, ciphertext)
    }

    // --- DHKEM(X25519) per RFC 9180 Section 4.1 ---

    /**
     * Encapsulate: generate ephemeral key, compute shared secret.
     * Returns (shared_secret, enc) where enc is the ephemeral public key.
     */
    private fun encap(recipientPub: ByteArray): Pair<ByteArray, ByteArray> {
        val ephemeral = X25519.generateKeyPair()
        val dh = X25519.dh(ephemeral.privateKey, recipientPub)
        val enc = ephemeral.publicKey

        val kemContext = enc + recipientPub
        val sharedSecret = extractAndExpand(dh, kemContext)
        return Pair(sharedSecret, enc)
    }

    /**
     * Decapsulate: compute shared secret from ephemeral public and recipient private.
     */
    private fun decap(
        enc: ByteArray,
        recipientPriv: ByteArray,
    ): ByteArray {
        val dh = X25519.dh(recipientPriv, enc)
        val recipientPub = X25519.publicFromPrivate(recipientPriv)
        val kemContext = enc + recipientPub
        return extractAndExpand(dh, kemContext)
    }

    /**
     * ExtractAndExpand per RFC 9180 Section 4.1:
     * shared_secret = ExtractAndExpand(dh, kem_context)
     */
    private fun extractAndExpand(
        dh: ByteArray,
        kemContext: ByteArray,
    ): ByteArray {
        val suiteId = KEM_SUITE_ID
        val prk = labeledExtract(suiteId, ByteArray(0), "shared_secret", dh)
        return labeledExpand(suiteId, prk, "shared_secret", kemContext, N_SECRET)
    }

    // --- Key Schedule per RFC 9180 Section 5.1 ---

    /**
     * HPKE Key Schedule (Base mode, no PSK):
     * Derives key and base_nonce from shared_secret and info.
     */
    private fun keySchedule(
        sharedSecret: ByteArray,
        info: ByteArray,
    ): Pair<ByteArray, ByteArray> {
        val mode = byteArrayOf(0x00) // Base mode
        val suiteId = HPKE_SUITE_ID

        val pskIdHash = labeledExtract(suiteId, ByteArray(0), "psk_id_hash", ByteArray(0))
        val infoHash = labeledExtract(suiteId, ByteArray(0), "info_hash", info)

        val ksContext = mode + pskIdHash + infoHash

        val secret = labeledExtract(suiteId, sharedSecret, "secret", ByteArray(N_H)) // default PSK = zeros

        val key = labeledExpand(suiteId, secret, "key", ksContext, N_K)
        val baseNonce = labeledExpand(suiteId, secret, "base_nonce", ksContext, N_N)

        return Pair(key, baseNonce)
    }

    // --- Labeled Extract/Expand per RFC 9180 Section 4 ---

    private fun labeledExtract(
        suiteId: ByteArray,
        salt: ByteArray,
        label: String,
        ikm: ByteArray,
    ): ByteArray {
        val labeledIkm = "HPKE-v1".encodeToByteArray() + suiteId + label.encodeToByteArray() + ikm
        val effectiveSalt = if (salt.isEmpty()) ByteArray(N_H) else salt
        return MlsCryptoProvider.hkdfExtract(effectiveSalt, labeledIkm)
    }

    private fun labeledExpand(
        suiteId: ByteArray,
        prk: ByteArray,
        label: String,
        info: ByteArray,
        length: Int,
    ): ByteArray {
        val labeledInfo =
            byteArrayOf((length shr 8).toByte(), length.toByte()) +
                "HPKE-v1".encodeToByteArray() + suiteId + label.encodeToByteArray() + info
        return MlsCryptoProvider.hkdfExpand(prk, labeledInfo, length)
    }

    // --- AEAD (AES-128-GCM) ---

    private fun aeadSeal(
        key: ByteArray,
        nonce: ByteArray,
        aad: ByteArray,
        plaintext: ByteArray,
    ): ByteArray = AESGCM(key, nonce).encrypt(plaintext, aad)

    private fun aeadOpen(
        key: ByteArray,
        nonce: ByteArray,
        aad: ByteArray,
        ciphertext: ByteArray,
    ): ByteArray = AESGCM(key, nonce).decrypt(ciphertext, aad)
}
