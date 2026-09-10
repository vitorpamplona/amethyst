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
package com.vitorpamplona.quartz.marmot.appComponents

import com.vitorpamplona.quartz.utils.ciphers.NostrCipher

/**
 * `encrypted-media-v2` as a [NostrCipher], so the existing upload pipeline can
 * encrypt with it without knowing anything about Marmot.
 *
 * The pipeline compresses and strips metadata before handing bytes over, and
 * that matters here: v2 derives its key from the hash of the bytes it actually
 * encrypts, so the hash has to be taken at this point in the chain and not from
 * the file the user picked. [plaintextSha256] and [ciphertextSha256] are
 * therefore populated by [encrypt] and read afterwards to build the reference.
 *
 * A single-use object. The nonce is fresh per [encrypt] call, which is required
 * — the key is deterministic in (plaintext hash, media type, filename, epoch),
 * so re-encrypting the same file in the same epoch under a repeated nonce would
 * break ChaCha20-Poly1305 outright — but it also means the fields below always
 * describe the LAST call.
 */
class EncryptedMediaV2Cipher(
    private val mediaSecret: ByteArray,
    /** Canonical media type — the `m` field, byte-for-byte. */
    val mediaType: String,
    val filename: String,
) : NostrCipher {
    var nonce: ByteArray = ByteArray(0)
        private set

    var plaintextSha256: ByteArray = ByteArray(0)
        private set

    var ciphertextSha256: ByteArray = ByteArray(0)
        private set

    /**
     * The RECEIVING side, where the nonce and the plaintext hash arrive in the
     * `imeta` tag instead of being produced by [encrypt].
     *
     * Without this the object could only ever decrypt what the same instance
     * had just encrypted, which is the sender's case and nobody else's — the
     * primary constructor leaves both fields empty and [decrypt] would fail on
     * the length check.
     */
    constructor(
        mediaSecret: ByteArray,
        reference: EncryptedMediaReferenceV2,
    ) : this(mediaSecret, reference.mediaType, reference.filename) {
        nonce = reference.nonce
        plaintextSha256 = reference.plaintextSha256
        ciphertextSha256 = reference.ciphertextSha256
    }

    override fun name(): String = EncryptedMediaV2.VERSION

    override fun encrypt(bytesToEncrypt: ByteArray): ByteArray {
        val result = EncryptedMediaV2.encrypt(bytesToEncrypt, mediaSecret, mediaType, filename)
        nonce = result.nonce
        plaintextSha256 = result.plaintextSha256
        ciphertextSha256 = result.ciphertextSha256
        return result.ciphertext
    }

    override fun decrypt(bytesToDecrypt: ByteArray): ByteArray =
        EncryptedMediaV2.decrypt(
            ciphertext = bytesToDecrypt,
            mediaSecret = mediaSecret,
            nonce = nonce,
            plaintextSha256 = plaintextSha256,
            mediaType = mediaType,
            filename = filename,
        )

    override fun decryptOrNull(bytesToDecrypt: ByteArray): ByteArray? =
        try {
            decrypt(bytesToDecrypt)
        } catch (_: Exception) {
            null
        }
}
