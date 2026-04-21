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
 * NostrCipher adapter for MIP-04 encrypted media decryption.
 *
 * This wraps the MIP-04 decryption parameters so encrypted media can be
 * decrypted by the existing [EncryptedBlobInterceptor] OkHttp interceptor.
 * The interceptor calls [decrypt]/[decryptOrNull] on downloaded bytes,
 * and this cipher delegates to [Mip04MediaEncryption.decrypt].
 *
 * Note: [encrypt] is not used by the interceptor path but is provided
 * for completeness.
 */
class Mip04Cipher(
    val exporterSecret: ByteArray,
    val nonce: ByteArray,
    val originalFileHash: ByteArray,
    val mimeType: String,
    val filename: String,
) : NostrCipher {
    override fun name(): String = Mip04MediaEncryption.VERSION

    override fun encrypt(bytesToEncrypt: ByteArray): ByteArray {
        val result = Mip04MediaEncryption.encrypt(bytesToEncrypt, exporterSecret, mimeType, filename)
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
