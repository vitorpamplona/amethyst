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
package com.vitorpamplona.amethyst.commons.service.uploads

import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.mediaServers.ServerName
import com.vitorpamplona.amethyst.commons.service.http.IRoleBasedHttpClientBuilder
import com.vitorpamplona.amethyst.commons.service.upload.FileHeader
import com.vitorpamplona.crypto.SymmetricCipher
import com.vitorpamplona.crypto.aes.AESGCM
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MultiOrchestratorEncryptedTest {
    /** Encrypts the file's name as its bytes, the way the platform uploaders encrypt the file once. */
    private object EncryptingUploader : MediaUploader {
        override suspend fun upload(
            progress: UploadOrchestrator,
            uri: MediaUri,
            mimeType: String?,
            alt: String?,
            contentWarningReason: String?,
            compressionQuality: CompressorQuality,
            server: ServerName,
            account: Account,
            useH265: Boolean,
            stripMetadata: Boolean,
            onStrippingFailed: suspend () -> Boolean,
            convertGifToMp4: Boolean,
            forcedSigner: NostrSigner?,
        ): UploadingState.UploadingFinalState = error("not used")

        override suspend fun uploadEncrypted(
            progress: UploadOrchestrator,
            uri: MediaUri,
            mimeType: String?,
            alt: String?,
            contentWarningReason: String?,
            compressionQuality: CompressorQuality,
            encrypt: SymmetricCipher,
            server: ServerName,
            account: Account,
            useH265: Boolean,
            stripMetadata: Boolean,
            onStrippingFailed: suspend () -> Boolean,
            convertGifToMp4: Boolean,
            forcedSigner: NostrSigner?,
        ): UploadingState.UploadingFinalState {
            val encrypted = encrypt.encrypt(uri.toString().encodeToByteArray())
            return progress.finish(
                UploadOrchestrator.OrchestratorResult.ServerResult(
                    FileHeader(mimeType, encrypted.size.toString(), encrypted.size, null, null),
                    "https://blossom.example/$uri",
                    null,
                    null,
                    mimeType,
                    null,
                ),
            )
        }

        override suspend fun compressIfNeeded(
            progress: UploadOrchestrator,
            uri: MediaUri,
            mimeType: String?,
            compressionQuality: CompressorQuality,
            useH265: Boolean,
            convertGifToMp4: Boolean,
        ): MediaCompressorResult = MediaCompressorResult(uri, mimeType, null)

        override suspend fun remoteFileHeader(
            url: String,
            httpClients: IRoleBasedHttpClientBuilder,
        ): FileHeader? = null
    }

    @Test
    fun everyFileIsSealedUnderItsOwnCipher() =
        runTest {
            val files =
                MultiOrchestrator(
                    listOf(
                        SelectedMedia(StringMediaUri("/tmp/a.jpg"), "image/jpeg"),
                        SelectedMedia(StringMediaUri("/tmp/b.jpg"), "image/jpeg"),
                        SelectedMedia(StringMediaUri("/tmp/c.mp4"), "video/mp4"),
                    ),
                )

            val result =
                files.uploadEncrypted(
                    alt = null,
                    contentWarningReason = null,
                    mediaQuality = CompressorQuality.UNCOMPRESSED,
                    newCipher = { AESGCM() },
                    server = ServerName("test", "https://blossom.example"),
                    account = mockk(),
                    uploader = EncryptingUploader,
                )

            assertTrue(result.allGood, "errors: ${result.errors}")
            assertEquals(3, result.successful.size)
            assertEquals(
                3,
                result.successful
                    .map { it.cipher }
                    .toSet()
                    .size,
            )
            assertEquals(
                3,
                result.successful
                    .map { it.cipher.nonce.toList() }
                    .toSet()
                    .size,
            )
        }
}
