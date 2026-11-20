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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
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

        /** When set, each upload reads this many bytes into the network, then waits for [serverAnswer]. */
        var bytes = 0L
        var serverAnswer: CompletableDeferred<Unit>? = null

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
            if (bytes > 0) {
                val report = progress.uploadProgress(0.2, 0.95)
                for (read in 0L..bytes step 64 * 1024) report(read, bytes)
                report(bytes, bytes)
                serverAnswer?.await()
            }
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
        ): UploadingState.UploadingFinalState {
            sent += Sent("enc:$uri", alt, contentWarningReason, compressionQuality, stripMetadata, server.name)
            if (uri.toString() in failing) return progress.error(UploadError.FAILED_TO_UPLOAD_MEDIA, "refused")
            return progress.finish(
                UploadOrchestrator.OrchestratorResult.ServerResult(FileHeader(mimeType, uri.toString(), 1, null, null), "https://blossom.example/enc$uri", null, null, mimeType, null),
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

        /** Paths whose crop cannot be written. */
        var uncroppable: Set<String> = emptySet()
        val discarded = ConcurrentLinkedQueue<String>()

        override suspend fun cropImage(
            uri: MediaUri,
            mimeType: String?,
            crop: ImageCrop,
        ): MediaCompressorResult? = if (uri.toString() in uncroppable) null else MediaCompressorResult(StringMediaUri("$uri.crop${crop.left}"), "image/jpeg", 1)

        /** Paths whose trim cannot be written. */
        var untrimmable: Set<String> = emptySet()

        override suspend fun trimVideo(
            uri: MediaUri,
            mimeType: String?,
            trim: VideoTrim,
        ): MediaCompressorResult? = if (uri.toString() in untrimmable) null else MediaCompressorResult(StringMediaUri("$uri.trim${trim.startMs}-${trim.endMs}"), "video/mp4", 1)

        override fun discardTempFile(uri: MediaUri) {
            discarded += uri.toString()
        }
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

    @Test
    fun encryptedChatUploadsUseEachFilesOptionsAndTheCaptionAsFallbackAlt() =
        runTest {
            val media = files("/a.jpg", "/b.jpg")
            media.get(0).options = media.get(0).options.copy(alt = "a boat", quality = CompressorQuality.LOW)
            media.get(1).options = media.get(1).options.copy(sensitive = true)
            val uploader = RecordingUploader()

            val result = media.uploadEncryptedOwnOptions({ AESGCM() }, server, account, uploader, altFallback = "my caption")

            assertTrue(result.allGood)
            val byPath = uploader.sent.associateBy { it.path }
            assertEquals("a boat", byPath["enc:/a.jpg"]!!.alt)
            assertEquals(CompressorQuality.LOW, byPath["enc:/a.jpg"]!!.quality)
            assertEquals("my caption", byPath["enc:/b.jpg"]!!.alt)
            assertEquals("", byPath["enc:/b.jpg"]!!.contentWarning)
            // Each result says which file it is, so a chat can read that file's options.
            assertEquals(
                listOf("/a.jpg", "/b.jpg"),
                result.successful.map {
                    it.item!!
                        .media.uri
                        .toString()
                },
            )
        }

    @Test
    fun aPlaintextRetryKeepsWhatAlreadyWentUpEncrypted() =
        runTest {
            val media = files("/a.jpg", "/b.jpg")
            val uploader = RecordingUploader(failing = setOf("/b.jpg"))
            assertFalse(media.uploadEncryptedOwnOptions({ AESGCM() }, server, account, uploader).allGood)

            uploader.failing = emptySet()
            uploader.sent.clear()
            val retry = media.uploadOwnOptions(server, account, uploader, keepEncrypted = true)

            assertTrue(retry.allGood)
            assertEquals(listOf("/b.jpg"), uploader.sent.map { it.path })
            assertEquals(
                listOf("/a.jpg"),
                retry.keptEncrypted.map {
                    it.item!!
                        .media.uri
                        .toString()
                },
            )
            assertEquals(
                listOf("/b.jpg"),
                retry.uploaded.map {
                    it.item.media.uri
                        .toString()
                },
            )
        }

    @Test
    fun aCroppedPictureSendsItsCropAndDeletesIt() =
        runTest {
            val media = files("/a.jpg", "/b.jpg")
            media.get(0).options = media.get(0).options.copy(crop = ImageCrop(0.25f, 0f, 0.75f, 1f))
            val uploader = RecordingUploader()

            assertTrue(media.uploadEach(server, account, uploader).allGood)

            assertEquals(setOf("/a.jpg.crop0.25", "/b.jpg"), uploader.sent.map { it.path }.toSet())
            assertEquals(listOf("/a.jpg.crop0.25"), uploader.discarded.toList(), "the crop's temp copy is deleted, the picked file is not")
        }

    @Test
    fun aCropThatCannotBeWrittenSendsNothing() =
        runTest {
            val media = files("/a.jpg")
            media.get(0).options = media.get(0).options.copy(crop = ImageCrop(0f, 0f, 0.5f, 0.5f))
            val uploader = RecordingUploader().apply { uncroppable = setOf("/a.jpg") }

            val result = media.uploadEach(server, account, uploader)

            assertFalse(result.allGood)
            assertEquals(UploadError.CROP_FAILED, result.errors.single().error)
            assertTrue(uploader.sent.isEmpty(), "the whole picture is not sent instead")
        }

    @Test
    fun aNewCropSendsAgain() =
        runTest {
            val media = files("/a.jpg")
            val uploader = RecordingUploader()
            media.uploadEach(server, account, uploader)
            media.get(0).options = media.get(0).options.copy(crop = ImageCrop(0.5f, 0f, 1f, 1f))

            media.uploadEach(server, account, uploader)

            assertEquals(listOf("/a.jpg", "/a.jpg.crop0.5"), uploader.sent.map { it.path })
        }

    @Test
    fun aGifLeftAloneGoesUpAsItIs() =
        runTest {
            val media = MultiOrchestrator(listOf(SelectedMedia(StringMediaUri("/a.gif"), "image/gif")))
            val uploader = RecordingUploader()

            media.uploadEach(server, account, uploader)

            assertEquals(listOf("/a.gif"), uploader.sent.map { it.path })
        }

    @Test
    fun anEditedGifIsNeverSentUnedited() =
        runTest {
            // This uploader can't edit a GIF (no editGif): the crop fails the file rather than
            // sending the whole GIF as if it had been cropped.
            val media = MultiOrchestrator(listOf(SelectedMedia(StringMediaUri("/a.gif"), "image/gif")))
            media.get(0).options = media.get(0).options.copy(crop = ImageCrop(0.5f, 0f, 1f, 1f))
            val uploader = RecordingUploader()

            val result = media.uploadEach(server, account, uploader)

            assertEquals(UploadError.CROP_FAILED, result.errors.single().error)
            assertTrue(uploader.sent.isEmpty())
        }

    @Test
    fun encryptedUploadsSendTheCrop() =
        runTest {
            val media = files("/a.jpg")
            media.get(0).options = media.get(0).options.copy(crop = ImageCrop(0.25f, 0f, 0.75f, 1f))
            val uploader = RecordingUploader()

            val result = media.uploadEncryptedOwnOptions({ AESGCM() }, server, account, uploader)

            assertTrue(result.allGood)
            assertEquals(listOf("enc:/a.jpg.crop0.25"), uploader.sent.map { it.path })
            assertEquals(1, result.successful.size)
        }

    @Test
    fun aTrimmedVideoSendsItsTrimAndDeletesIt() =
        runTest {
            val media = MultiOrchestrator(listOf(SelectedMedia(StringMediaUri("/v.mp4"), "video/mp4"), SelectedMedia(StringMediaUri("/a.jpg"), "image/jpeg")))
            media.get(0).options = media.get(0).options.copy(trim = VideoTrim(2_000, 9_500))
            // A trim on a picture means nothing and is ignored.
            media.get(1).options = media.get(1).options.copy(trim = VideoTrim(0, 1_000))
            val uploader = RecordingUploader()

            assertTrue(media.uploadEach(server, account, uploader).allGood)

            assertEquals(setOf("/v.mp4.trim2000-9500", "/a.jpg"), uploader.sent.map { it.path }.toSet())
            assertEquals(listOf("/v.mp4.trim2000-9500"), uploader.discarded.toList())
        }

    @Test
    fun aTrimThatCannotBeWrittenSendsNothing() =
        runTest {
            val media = MultiOrchestrator(listOf(SelectedMedia(StringMediaUri("/v.mp4"), "video/mp4")))
            media.get(0).options = media.get(0).options.copy(trim = VideoTrim(0, 3_000))
            val uploader = RecordingUploader().apply { untrimmable = setOf("/v.mp4") }

            val result = media.uploadEach(server, account, uploader)

            assertEquals(UploadError.TRIM_FAILED, result.errors.single().error)
            assertTrue(uploader.sent.isEmpty())
        }

    @Test
    fun encryptedUploadsSendTheTrim() =
        runTest {
            val media = MultiOrchestrator(listOf(SelectedMedia(StringMediaUri("/v.mp4"), "video/mp4")))
            media.get(0).options = media.get(0).options.copy(trim = VideoTrim(1_000, 4_000))
            val uploader = RecordingUploader()

            assertTrue(media.uploadEncryptedOwnOptions({ AESGCM() }, server, account, uploader).allGood)
            assertEquals(listOf("enc:/v.mp4.trim1000-4000"), uploader.sent.map { it.path })
        }

    @Test
    fun aWholeTrimIsNoTrim() {
        assertEquals(null, VideoTrim(20, 9_990).orNullIfWhole(10_000))
        assertEquals(VideoTrim(500, 10_000), VideoTrim(500, 10_000).orNullIfWhole(10_000))
    }

    @Test
    fun aDocumentOwesNoMetadataStrip() =
        runTest {
            // Neither platform can strip a PDF: owing it ends in "couldn't remove it, upload anyway?".
            val pdf = SelectedMedia(StringMediaUri("/doc.pdf"), "application/pdf")
            assertFalse(MediaUploadOptions.defaultFor(pdf, stripMetadata = true).stripMetadata)

            val media = MultiOrchestrator(listOf(pdf, SelectedMedia(StringMediaUri("/a.jpg"), "image/jpeg")))
            // Even when something set it on.
            media.get(0).options = media.get(0).options.copy(stripMetadata = true)
            val uploader = RecordingUploader()

            media.uploadEach(server, account, uploader)
            media.uploadEncryptedOwnOptions({ AESGCM() }, server, account, uploader)

            assertEquals(listOf(false, false), uploader.sent.filter { it.path.endsWith(".pdf") }.map { it.stripMetadata })
            assertTrue(uploader.sent.filter { it.path.endsWith(".jpg") }.all { it.stripMetadata })
        }

    @Test
    fun theBarFillsWithTheBytesReadThenWaitsWithoutANumber() {
        val orchestrator = UploadOrchestrator()
        val report = orchestrator.uploadProgress(0.2, 0.95)

        report(500, 1000)
        assertEquals(UploadingState.Uploading, orchestrator.progressState.value)
        assertEquals(0.575, orchestrator.status.value.progress, 1e-9)

        // The last byte is in the send buffer, not at the server: no "95% done" while it travels.
        report(1000, 1000)
        assertEquals(UploadingState.Sending, orchestrator.progressState.value)

        // A retry, or the next server, starts again from zero as a bar that fills.
        report(100, 1000)
        assertEquals(UploadingState.Uploading, orchestrator.progressState.value)
        assertEquals(0.275, orchestrator.status.value.progress, 1e-9)
    }

    @Test
    fun filesSentTogetherEachWaitForTheServerWithoutANumber() =
        runTest {
            val media = files("/a.jpg", "/b.jpg", "/c.jpg")
            val answer = CompletableDeferred<Unit>()
            val uploader =
                RecordingUploader().apply {
                    bytes = 3_000_000
                    serverAnswer = answer
                }

            val upload = async { media.uploadEach(server, account, uploader) }
            try {
                // Each file fills its own send buffer: every one of them waits, none stuck almost full.
                withContext(Dispatchers.Default) {
                    withTimeout(5_000) {
                        while (media.items().any { it.orchestrator.progressState.value != UploadingState.Sending }) delay(10)
                    }
                }
            } finally {
                // Answered even when the wait failed, so the upload ends and the test reports the failure.
                answer.complete(Unit)
            }
            assertTrue(upload.await().allGood)
            media.items().forEach {
                assertIs<UploadingState.Finished>(it.orchestrator.progressState.value)
                assertEquals(1.0, it.orchestrator.status.value.progress)
            }
        }

    @Test
    fun aViewNeverSeesTheLastBytesNumberWithoutItsStage() {
        val orchestrator = UploadOrchestrator()
        val seen = mutableListOf<UploadStatus>()
        val report = orchestrator.uploadProgress(0.2, 0.95)
        for (read in 0L..1000L step 10) {
            report(read, 1000)
            seen += orchestrator.status.value
        }
        report(100, 1000)
        seen += orchestrator.status.value

        // The bar's end comes with Sending, and a retry's restart with Uploading: never mixed.
        assertTrue(seen.none { it.state == UploadingState.Uploading && it.progress >= 0.95 }, "$seen")
        assertEquals(UploadStatus(UploadingState.Sending, 0.95), seen[seen.size - 2])
        assertEquals(UploadStatus(UploadingState.Uploading, 0.275), seen.last())
        assertEquals(UploadingState.Uploading, orchestrator.progressState.value)
    }
}
