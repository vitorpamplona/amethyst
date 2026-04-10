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
package com.vitorpamplona.amethyst.ios.service.upload

import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nipB7Blossom.BlossomUploadResult
import com.vitorpamplona.quartz.utils.sha256.sha256
import platform.Foundation.NSData

data class IosUploadResult(
    val blossom: BlossomUploadResult,
    val sha256: String,
    val size: Long,
    val mimeType: String,
)

/**
 * Default Blossom server for uploads when user hasn't configured one.
 */
const val DEFAULT_BLOSSOM_SERVER = "https://blossom.primal.net"

class IosUploadOrchestrator(
    private val client: IosBlossomClient = IosBlossomClient(),
) {
    /**
     * Upload image data to a Blossom server.
     *
     * @param imageData Raw bytes of the image as NSData
     * @param mimeType MIME type (e.g. "image/jpeg")
     * @param alt Alt text for the upload auth
     * @param serverBaseUrl Blossom server URL
     * @param signer Nostr signer for auth
     */
    suspend fun upload(
        imageData: NSData,
        mimeType: String,
        alt: String?,
        serverBaseUrl: String = DEFAULT_BLOSSOM_SERVER,
        signer: NostrSigner,
    ): IosUploadResult {
        // 1. Convert NSData to ByteArray and compute hash
        val bytes = nsDataToByteArray(imageData)
        val hash = sha256(bytes).toHexKey()
        val size = bytes.size.toLong()

        // 2. Create auth header
        val authHeader =
            IosBlossomAuth.createUploadAuth(
                hash = hash,
                size = size,
                alt = alt ?: "Upload from Amethyst iOS",
                signer = signer,
            )

        // 3. Upload via Blossom (using ByteArray with Ktor)
        val result =
            client.upload(
                bytes = bytes,
                contentType = mimeType,
                serverBaseUrl = serverBaseUrl,
                authHeader = authHeader,
            )

        return IosUploadResult(
            blossom = result,
            sha256 = hash,
            size = size,
            mimeType = mimeType,
        )
    }
}
