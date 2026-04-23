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
package com.vitorpamplona.amethyst.commons.service.upload

import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nipB7Blossom.BlossomUploadResult
import com.vitorpamplona.quartz.utils.ciphers.AESGCM
import com.vitorpamplona.quartz.utils.sha256.sha256
import java.io.File

data class UploadResult(
    val blossom: BlossomUploadResult,
    val metadata: MediaMetadata,
)

data class EncryptedUploadResult(
    val blossom: BlossomUploadResult,
    val metadata: MediaMetadata,
    val encryptedHash: String,
    val encryptedSize: Int,
)

class UploadOrchestrator(
    private val client: BlossomClient = BlossomClient(),
) {
    suspend fun upload(
        file: File,
        alt: String?,
        serverBaseUrl: String,
        signer: NostrSigner,
        stripExif: Boolean = true,
    ): UploadResult {
        // 1. Strip EXIF if requested (JPEG only)
        val processedFile =
            if (stripExif) {
                MediaCompressor.stripExif(file)
            } else {
                file
            }

        // 2. Compute metadata (hash, dimensions, blurhash)
        val metadata = MediaMetadataReader.compute(processedFile)

        // 3. Create auth header
        val authHeader =
            BlossomAuth.createUploadAuth(
                hash = metadata.sha256,
                size = metadata.size,
                alt = alt ?: "Uploading ${file.name}",
                signer = signer,
            )

        // 4. Upload
        val result =
            client.upload(
                file = processedFile,
                contentType = metadata.mimeType,
                serverBaseUrl = serverBaseUrl,
                authHeader = authHeader,
            )

        // 5. Clean up temp file if we stripped EXIF
        if (processedFile != file) {
            processedFile.delete()
        }

        return UploadResult(blossom = result, metadata = metadata)
    }

    /**
     * Upload a file encrypted with AES-GCM for NIP-17 DM file sharing.
     * Computes pre-encryption metadata (dimensions, blurhash), encrypts bytes,
     * then uploads the encrypted blob to Blossom.
     */
    suspend fun uploadEncrypted(
        file: File,
        cipher: AESGCM,
        serverBaseUrl: String,
        signer: NostrSigner,
    ): EncryptedUploadResult {
        // 1. Compute pre-encryption metadata (dimensions, blurhash, mime, originalHash)
        val metadata = MediaMetadataReader.compute(file)

        // 2. Read file bytes and encrypt
        val plaintext = file.readBytes()
        val encrypted = cipher.encrypt(plaintext)

        // 3. Compute SHA256 of ENCRYPTED blob (not plaintext)
        val encryptedHash = sha256(encrypted).toHexKey()
        val encryptedSize = encrypted.size

        // 4. Create Blossom auth with encrypted hash and size
        val authHeader =
            BlossomAuth.createUploadAuth(
                hash = encryptedHash,
                size = encryptedSize.toLong(),
                alt = "Encrypted upload",
                signer = signer,
            )

        // 5. Upload encrypted blob as opaque binary
        val result =
            client.upload(
                bytes = encrypted,
                contentType = "application/octet-stream",
                serverBaseUrl = serverBaseUrl,
                authHeader = authHeader,
            )

        return EncryptedUploadResult(
            blossom = result,
            metadata = metadata,
            encryptedHash = encryptedHash,
            encryptedSize = encryptedSize,
        )
    }
}
