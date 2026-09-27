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
package com.vitorpamplona.quartz.cordn.appEncryptedMedia

import com.vitorpamplona.quartz.utils.ciphers.NostrCipher

/**
 * cordn's encrypted media as a [NostrCipher], so the ordinary media pipeline
 * can open it.
 *
 * Registering one of these against a blob URL in the encryption key cache is
 * what lets `EncryptedBlobInterceptor` decrypt the download in flight, which in
 * turn lets a cordn attachment go through `ZoomableContentView` like every
 * other image, video and voice note in the app. Before this existed the cordn
 * chat fetched and decrypted blobs by hand and drew its own `Image`, an "Open
 * <filename>" button and its own audio player — so the one chat with encrypted
 * media was the one chat whose media did not look like the rest of the app.
 *
 * The same shape as `Mip04Cipher` and `EncryptedMediaV2Cipher`: a sending
 * constructor that produces the nonce, and a receiving one that is handed the
 * nonce and hash off the `imeta` tag.
 */
class CordnMediaCipher(
    /** The group's epoch media key — see [CordnMediaEncryption.mediaKey]. */
    private val mediaKey: ByteArray,
    /** Canonical media type: the `m` field, byte-for-byte, because it is in the AAD. */
    val mimeType: String,
    /** The `filename` field, byte-for-byte, for the same reason. */
    val filename: String,
) : NostrCipher {
    var nonce: ByteArray = ByteArray(0)
        private set

    var plaintextHash: ByteArray = ByteArray(0)
        private set

    /**
     * The RECEIVING side, where the nonce and the plaintext hash come off the
     * tag rather than out of [encrypt].
     *
     * Without it the object could only decrypt what the same instance had just
     * encrypted, which is the sender's case and nobody else's.
     */
    constructor(
        mediaKey: ByteArray,
        attachment: CordnMediaAttachment,
    ) : this(mediaKey, attachment.mimeType, attachment.filename) {
        nonce = attachment.nonceBytes
        plaintextHash = attachment.hashBytes
    }

    override fun name(): String = CordnMediaTag.VERSION_V1

    override fun encrypt(bytesToEncrypt: ByteArray): ByteArray {
        val sealed = CordnMediaEncryption.encrypt(bytesToEncrypt, mediaKey, mimeType, filename)
        nonce = sealed.nonce
        plaintextHash = sealed.plaintextHash
        return sealed.ciphertext
    }

    override fun decrypt(bytesToDecrypt: ByteArray): ByteArray =
        CordnMediaEncryption.decrypt(
            ciphertext = bytesToDecrypt,
            fileKey = mediaKey,
            nonce = nonce,
            plaintextHash = plaintextHash,
            mimeType = mimeType,
            filename = filename,
        )

    override fun decryptOrNull(bytesToDecrypt: ByteArray): ByteArray? =
        try {
            decrypt(bytesToDecrypt)
        } catch (_: Exception) {
            null
        }
}
