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
package com.vitorpamplona.quartz.marmot.mip04EncryptedMedia

import com.vitorpamplona.quartz.utils.ciphers.NostrCipher

/**
 * NostrCipher implementation for MIP-04 media encryption.
 *
 * Used as an adapter to integrate MIP-04 encryption into the existing
 * [EncryptFiles] upload pipeline. After calling [encrypt], the [nonce]
 * and [originalFileHash] fields are populated and can be read to build
 * the imeta tag.
 *
 * @param exporterSecret 32-byte MLS-Exporter("marmot", "encrypted-media", 32)
 * @param mimeType canonical MIME type for key derivation context
 * @param filename original filename for key derivation context
 */
class Mip04NostrCipher(
    val exporterSecret: ByteArray,
    val mimeType: String,
    val filename: String,
) : NostrCipher {
    var nonce: ByteArray = ByteArray(0)
        private set

    var originalFileHash: ByteArray = ByteArray(0)
        private set

    override fun name(): String = Mip04MediaEncryption.VERSION

    override fun encrypt(bytesToEncrypt: ByteArray): ByteArray {
        val result = Mip04MediaEncryption.encrypt(bytesToEncrypt, exporterSecret, mimeType, filename)
        nonce = result.nonce
        originalFileHash = result.originalFileHash
        return result.ciphertext
    }

    override fun decrypt(bytesToDecrypt: ByteArray): ByteArray =
        Mip04MediaEncryption.decrypt(
            ciphertextWithTag = bytesToDecrypt,
            exporterSecret = exporterSecret,
            nonce = nonce,
            originalFileHash = originalFileHash,
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
