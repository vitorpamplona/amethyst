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

import com.vitorpamplona.quartz.marmot.mls.crypto.MlsCryptoProvider
import com.vitorpamplona.quartz.nip44Encryption.crypto.ChaCha20Poly1305
import com.vitorpamplona.quartz.utils.RandomInstance
import com.vitorpamplona.quartz.utils.sha256.sha256

/**
 * MIP-04 Encrypted Media (Version 2) implementation.
 *
 * Encrypts/decrypts media files for Marmot groups using ChaCha20-Poly1305 AEAD
 * with keys derived from MLS exporter secrets.
 *
 * Key derivation:
 *   exporter_secret = MLS-Exporter("marmot", "encrypted-media", 32)
 *   file_key = HKDF-Expand(exporter_secret, context, 32)
 *   nonce = Random(12)
 *
 * Context layout:
 *   "mip04-v2" || 0x00 || file_hash(32) || 0x00 || mime_type || 0x00 || filename || 0x00 || "key"
 *
 * AAD layout:
 *   "mip04-v2" || 0x00 || file_hash(32) || 0x00 || mime_type || 0x00 || filename
 */
object Mip04MediaEncryption {
    const val VERSION = "mip04-v2"
    const val EXPORTER_LABEL = "marmot"
    const val EXPORTER_CONTEXT = "encrypted-media"
    const val EXPORTER_KEY_LENGTH = 32

    private const val KEY_LENGTH = 32
    private const val NONCE_LENGTH = 12
    private val SCHEME_BYTES = VERSION.encodeToByteArray()
    private val KEY_SUFFIX = "key".encodeToByteArray()
    private val NULL_SEPARATOR = byteArrayOf(0x00)

    /**
     * Derive the MIP-04 file encryption key from the MLS exporter secret.
     *
     * @param exporterSecret 32-byte MLS-Exporter("marmot", "encrypted-media", 32)
     * @param originalFileHash SHA256 hash of the original (unencrypted) file content (32 bytes)
     * @param mimeType canonical MIME type (e.g. "image/jpeg")
     * @param filename original filename (e.g. "photo.jpg")
     * @return 32-byte file encryption key
     */
    fun deriveFileKey(
        exporterSecret: ByteArray,
        originalFileHash: ByteArray,
        mimeType: String,
        filename: String,
    ): ByteArray {
        require(exporterSecret.size == EXPORTER_KEY_LENGTH) {
            "Exporter secret must be $EXPORTER_KEY_LENGTH bytes"
        }
        require(originalFileHash.size == 32) {
            "File hash must be 32 bytes"
        }

        val info = buildContext(originalFileHash, mimeType, filename, KEY_SUFFIX)
        return MlsCryptoProvider.hkdfExpand(exporterSecret, info, KEY_LENGTH)
    }

    /**
     * Encrypt a file's bytes using MIP-04 v2.
     *
     * @param plaintext original file bytes
     * @param exporterSecret 32-byte MLS exporter secret
     * @param mimeType canonical MIME type
     * @param filename original filename
     * @return encryption result with ciphertext, nonce, and file hash
     */
    fun encrypt(
        plaintext: ByteArray,
        exporterSecret: ByteArray,
        mimeType: String,
        filename: String,
    ): Mip04EncryptionResult {
        val originalFileHash = sha256(plaintext)
        val fileKey = deriveFileKey(exporterSecret, originalFileHash, mimeType, filename)
        val nonce = RandomInstance.bytes(NONCE_LENGTH)
        val aad = buildAad(originalFileHash, mimeType, filename)
        val ciphertextWithTag = ChaCha20Poly1305.encrypt(plaintext, aad, nonce, fileKey)

        return Mip04EncryptionResult(
            ciphertext = ciphertextWithTag,
            nonce = nonce,
            originalFileHash = originalFileHash,
        )
    }

    /**
     * Decrypt a file's bytes using MIP-04 v2.
     *
     * @param ciphertextWithTag encrypted bytes (ciphertext + 16-byte Poly1305 tag)
     * @param exporterSecret 32-byte MLS exporter secret
     * @param nonce 12-byte nonce from the imeta tag
     * @param originalFileHash SHA256 hash of the original file (from imeta "x" field)
     * @param mimeType canonical MIME type (from imeta "m" field)
     * @param filename original filename (from imeta "filename" field)
     * @return decrypted file bytes
     * @throws IllegalStateException if authentication fails
     */
    fun decrypt(
        ciphertextWithTag: ByteArray,
        exporterSecret: ByteArray,
        nonce: ByteArray,
        originalFileHash: ByteArray,
        mimeType: String,
        filename: String,
    ): ByteArray {
        require(nonce.size == NONCE_LENGTH) { "Nonce must be $NONCE_LENGTH bytes" }
        require(originalFileHash.size == 32) { "File hash must be 32 bytes" }

        val fileKey = deriveFileKey(exporterSecret, originalFileHash, mimeType, filename)
        val aad = buildAad(originalFileHash, mimeType, filename)
        val decrypted = ChaCha20Poly1305.decrypt(ciphertextWithTag, aad, nonce, fileKey)

        // Integrity verification: SHA256(decrypted) must match originalFileHash
        val actualHash = sha256(decrypted)
        check(actualHash.contentEquals(originalFileHash)) {
            "Integrity verification failed: decrypted file hash does not match original"
        }

        return decrypted
    }

    /**
     * Build the HKDF-Expand context for key derivation:
     * "mip04-v2" || 0x00 || file_hash(32) || 0x00 || mime_type || 0x00 || filename || 0x00 || suffix
     */
    private fun buildContext(
        fileHash: ByteArray,
        mimeType: String,
        filename: String,
        suffix: ByteArray,
    ): ByteArray {
        val mimeBytes = canonicalizeMimeType(mimeType).encodeToByteArray()
        val filenameBytes = filename.encodeToByteArray()

        val size = SCHEME_BYTES.size + 1 + fileHash.size + 1 + mimeBytes.size + 1 + filenameBytes.size + 1 + suffix.size
        val result = ByteArray(size)
        var offset = 0

        SCHEME_BYTES.copyInto(result, offset)
        offset += SCHEME_BYTES.size
        NULL_SEPARATOR.copyInto(result, offset)
        offset += 1
        fileHash.copyInto(result, offset)
        offset += fileHash.size
        NULL_SEPARATOR.copyInto(result, offset)
        offset += 1
        mimeBytes.copyInto(result, offset)
        offset += mimeBytes.size
        NULL_SEPARATOR.copyInto(result, offset)
        offset += 1
        filenameBytes.copyInto(result, offset)
        offset += filenameBytes.size
        NULL_SEPARATOR.copyInto(result, offset)
        offset += 1
        suffix.copyInto(result, offset)

        return result
    }

    /**
     * Build the AAD (Additional Authenticated Data):
     * "mip04-v2" || 0x00 || file_hash(32) || 0x00 || mime_type || 0x00 || filename
     */
    private fun buildAad(
        fileHash: ByteArray,
        mimeType: String,
        filename: String,
    ): ByteArray {
        val mimeBytes = canonicalizeMimeType(mimeType).encodeToByteArray()
        val filenameBytes = filename.encodeToByteArray()

        val size = SCHEME_BYTES.size + 1 + fileHash.size + 1 + mimeBytes.size + 1 + filenameBytes.size
        val result = ByteArray(size)
        var offset = 0

        SCHEME_BYTES.copyInto(result, offset)
        offset += SCHEME_BYTES.size
        NULL_SEPARATOR.copyInto(result, offset)
        offset += 1
        fileHash.copyInto(result, offset)
        offset += fileHash.size
        NULL_SEPARATOR.copyInto(result, offset)
        offset += 1
        mimeBytes.copyInto(result, offset)
        offset += mimeBytes.size
        NULL_SEPARATOR.copyInto(result, offset)
        offset += 1
        filenameBytes.copyInto(result, offset)

        return result
    }

    /**
     * Canonicalize a MIME type per MIP-04:
     * - Lowercase
     * - Trim whitespace
     * - Strip parameters (e.g., "; charset=utf-8")
     */
    fun canonicalizeMimeType(mimeType: String): String =
        mimeType
            .trim()
            .lowercase()
            .substringBefore(";")
            .trim()
}

class Mip04EncryptionResult(
    val ciphertext: ByteArray,
    val nonce: ByteArray,
    val originalFileHash: ByteArray,
)
