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
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** [MultiOrchestrator.uploadEach]: every file goes up with its own options, and a retry resends only what failed. */
class MultiOrchestratorUploadEachTest {
    private data class Sent(
        val path: String,
        val alt: String?,
        val contentWarning: String?,
        val quality: CompressorQuality,
        val stripMetadata: Boolean,
        val server: String,
    )

    private class RecordingUploader(
        var failing: Set<String> = emptySet(),
    ) : MediaUploader {
        val sent = ConcurrentLinkedQueue<Sent>()

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
        ): UploadingState.UploadingFinalState {
            sent += Sent(uri.toString(), alt, contentWarningReason, compressionQuality, stripMetadata, server.name)
            if (uri.toString() in failing) return progress.error(UploadError.FAILED_TO_UPLOAD_MEDIA, "refused")
            return progress.finish(
                UploadOrchestrator.OrchestratorResult.ServerResult(FileHeader(mimeType, uri.toString(), 1, null, null), "https://blossom.example$uri", null, null, mimeType, null),
            )
        }

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
        ): UploadingState.UploadingFinalState = error("not used")

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

    private val server = ServerName("one", "https://one.example")
    private val other = ServerName("two", "https://two.example")
    private val account = mockk<Account>()

    private fun files(vararg paths: String) = MultiOrchestrator(paths.map { SelectedMedia(StringMediaUri(it), "image/jpeg") })

    @Test
    fun eachFileGoesUpWithItsOwnOptions() =
        runTest {
            val media = files("/a.jpg", "/b.jpg")
            media.get(0).options = media.get(0).options.copy(alt = "  a boat  ", quality = CompressorQuality.LOW)
            media.get(1).options = media.get(1).options.copy(sensitive = true, quality = CompressorQuality.UNCOMPRESSED, stripMetadata = false)
            val uploader = RecordingUploader()

            val result = media.uploadEach(server, account, uploader)

            assertTrue(result.allGood)
            val byPath = uploader.sent.associateBy { it.path }
            assertEquals(Sent("/a.jpg", "a boat", null, CompressorQuality.LOW, true, "one"), byPath["/a.jpg"])
            assertEquals(Sent("/b.jpg", null, "", CompressorQuality.UNCOMPRESSED, false, "one"), byPath["/b.jpg"])
            // In the files' order, each paired with its own file, so the composer builds the right imeta.
            assertEquals(
                listOf("/a.jpg", "/b.jpg"),
                result.successful.map {
                    it.item.media.uri
                        .toString()
                },
            )
        }

    @Test
    fun aRetryResendsOnlyTheFileThatFailed() =
        runTest {
            val media = files("/a.jpg", "/b.jpg")
            val uploader = RecordingUploader(failing = setOf("/b.jpg"))

            val first = media.uploadEach(server, account, uploader)
            assertFalse(first.allGood)
            assertEquals(1, first.errors.size)

            uploader.failing = emptySet()
            uploader.sent.clear()
            val second = media.uploadEach(server, account, uploader)

            assertTrue(second.allGood)
            assertEquals(listOf("/b.jpg"), uploader.sent.map { it.path })
            assertEquals(2, second.successful.size)
        }

    @Test
    fun changingOptionsOrServerSendsTheFileAgain() =
        runTest {
            val media = files("/a.jpg")
            val uploader = RecordingUploader()
            media.uploadEach(server, account, uploader)

            media.get(0).options = media.get(0).options.copy(quality = CompressorQuality.VERY_LOW)
            media.uploadEach(server, account, uploader)
            media.uploadEach(other, account, uploader)

            assertEquals(3, uploader.sent.size)
            assertEquals(listOf("one", "one", "two"), uploader.sent.map { it.server })
        }

    @Test
    fun nonMediaStartsUncompressed() {
        val media = MultiOrchestrator(listOf(SelectedMedia(StringMediaUri("/doc.pdf"), "application/pdf")))
        assertEquals(CompressorQuality.UNCOMPRESSED, media.get(0).options.quality)
    }
}
