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
package com.vitorpamplona.amethyst.desktop.service.upload

import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nipB7Blossom.BlossomUploadResult
import java.io.File

data class UploadResult(
    val blossom: BlossomUploadResult,
    val metadata: MediaMetadata,
)

class DesktopUploadOrchestrator(
    private val client: DesktopBlossomClient = DesktopBlossomClient(),
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
                DesktopMediaCompressor.stripExif(file)
            } else {
                file
            }

        // 2. Compute metadata (hash, dimensions, blurhash)
        val metadata = DesktopMediaMetadata.compute(processedFile)

        // 3. Create auth header
        val authHeader =
            DesktopBlossomAuth.createUploadAuth(
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
}
