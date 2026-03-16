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
package com.vitorpamplona.amethyst.desktop.service.media

import com.vitorpamplona.amethyst.desktop.service.upload.DesktopBlossomClient
import com.vitorpamplona.amethyst.desktop.service.upload.DesktopMediaMetadata
import com.vitorpamplona.quartz.utils.ciphers.AESGCM
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

/**
 * Handles encryption/decryption of media files for NIP-17 DMs.
 * Uses AESGCM cipher from quartz (commonMain).
 */
object EncryptedMediaService {
    private val httpClient = OkHttpClient()
    private val blossomClient = DesktopBlossomClient()

    data class EncryptedUploadResult(
        val url: String,
        val cipher: AESGCM,
        val mimeType: String?,
        val hash: String?,
        val size: Int,
        val dimensions: Pair<Int, Int>?,
        val blurhash: String?,
    )

    /**
     * Encrypt a file and upload to Blossom.
     * Returns the encrypted upload result with cipher details.
     */
    suspend fun encryptAndUpload(
        file: File,
        serverBaseUrl: String,
        authHeader: String?,
    ): EncryptedUploadResult =
        withContext(Dispatchers.IO) {
            val metadata = DesktopMediaMetadata.compute(file)
            val cipher = AESGCM()

            // Read file bytes and encrypt
            val plainBytes = file.readBytes()
            val encryptedBytes = cipher.encrypt(plainBytes)

            // Write encrypted bytes to temp file for upload
            val tempFile = File.createTempFile("encrypted_", ".enc")
            tempFile.deleteOnExit()
            tempFile.writeBytes(encryptedBytes)

            try {
                val result =
                    blossomClient.upload(
                        file = tempFile,
                        contentType = "application/octet-stream",
                        serverBaseUrl = serverBaseUrl,
                        authHeader = authHeader,
                    )

                EncryptedUploadResult(
                    url = result.url ?: throw IllegalStateException("No URL in upload result"),
                    cipher = cipher,
                    mimeType = metadata.mimeType,
                    hash = metadata.sha256,
                    size = plainBytes.size,
                    dimensions =
                        if (metadata.width != null && metadata.height != null) {
                            metadata.width to metadata.height
                        } else {
                            null
                        },
                    blurhash = metadata.blurhash,
                )
            } finally {
                tempFile.delete()
            }
        }

    /**
     * Download and decrypt an encrypted file from a URL.
     * Returns the decrypted bytes.
     */
    suspend fun downloadAndDecrypt(
        url: String,
        keyBytes: ByteArray,
        nonce: ByteArray,
    ): ByteArray =
        withContext(Dispatchers.IO) {
            val request = Request.Builder().url(url).build()
            val response = httpClient.newCall(request).execute()
            val encryptedBytes =
                response.use {
                    if (!it.isSuccessful) throw RuntimeException("Download failed: ${it.code}")
                    it.body.bytes()
                }

            val cipher = AESGCM(keyBytes, nonce)
            cipher.decrypt(encryptedBytes)
        }
}
