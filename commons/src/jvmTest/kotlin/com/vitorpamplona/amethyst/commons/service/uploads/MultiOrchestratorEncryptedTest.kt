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
import java.util.concurrent.ConcurrentHashMap
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MultiOrchestratorEncryptedTest {
    /**
     * Encrypts the file's path as its bytes, once, the way the platform uploaders do, and serves the
     * ciphertext at the returned URL. Fails the paths in [failing]; runs [during] before encrypting.
     */
    private class EncryptingUploader(
        val failing: Set<String> = emptySet(),
        val during: (MediaUri) -> Unit = {},
    ) : MediaUploader {
        val blobs = ConcurrentHashMap<String, ByteArray>()

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
            during(uri)
            if (uri.toString() in failing) return progress.error(UploadError.FAILED_TO_UPLOAD_MEDIA, "refused")
            val encrypted = encrypt.encrypt(uri.toString().encodeToByteArray())
            val url = "https://blossom.example$uri"
            blobs[url] = encrypted
            return progress.finish(
                UploadOrchestrator.OrchestratorResult.ServerResult(
                    FileHeader(mimeType, encrypted.size.toString(), encrypted.size, null, null),
                    url,
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

    private fun files(vararg paths: String) = MultiOrchestrator(paths.map { SelectedMedia(StringMediaUri(it), "image/jpeg") })

    private suspend fun MultiOrchestrator.uploadAll(uploader: MediaUploader) =
        uploadEncrypted(
            alt = null,
            contentWarningReason = null,
            mediaQuality = CompressorQuality.UNCOMPRESSED,
            newCipher = { AESGCM() },
            server = ServerName("test", "https://blossom.example"),
            account = mockk(),
            uploader = uploader,
        )

    private val MultiOrchestrator.EncryptedUpload<AESGCM>.url get() = (finished.result as UploadOrchestrator.OrchestratorResult.ServerResult).url

    @Test
    fun everyFileDecryptsUnderTheCipherPairedWithIt() =
        runTest {
            val uploader = EncryptingUploader()
            val result = files("/tmp/a.jpg", "/tmp/b.jpg", "/tmp/c.jpg").uploadAll(uploader)

            assertTrue(result.allGood, "errors: ${result.errors}")
            assertEquals(3, result.successful.size)
            assertEquals(
                3,
                result.successful
                    .map { it.cipher.nonce.toList() }
                    .toSet()
                    .size,
            )
            result.successful.forEach {
                val path = it.url.removePrefix("https://blossom.example")
                assertContentEquals(path.encodeToByteArray(), it.cipher.decrypt(uploader.blobs.getValue(it.url)))
            }
        }

    @Test
    fun aFailedFileIsReportedAndTheOthersKeepTheirCiphers() =
        runTest {
            val uploader = EncryptingUploader(failing = setOf("/tmp/b.jpg"))
            val result = files("/tmp/a.jpg", "/tmp/b.jpg", "/tmp/c.jpg").uploadAll(uploader)

            assertFalse(result.allGood)
            assertEquals(1, result.errors.size)
            assertEquals(listOf("https://blossom.example/tmp/a.jpg", "https://blossom.example/tmp/c.jpg"), result.successful.map { it.url })
            result.successful.forEach { it.cipher.decrypt(uploader.blobs.getValue(it.url)) }
        }

    @Test
    fun aFileRemovedDuringTheUploadIsLeftOut() =
        runTest {
            val files = files("/tmp/a.jpg", "/tmp/b.jpg")
            val b = files.get(1)
            val result = files.uploadAll(EncryptingUploader(during = { if (it == b.media.uri) files.remove(b) }))

            assertTrue(result.allGood)
            assertEquals(listOf("https://blossom.example/tmp/a.jpg"), result.successful.map { it.url })
        }
}
