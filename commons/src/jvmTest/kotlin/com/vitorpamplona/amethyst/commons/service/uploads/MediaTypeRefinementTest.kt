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
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/** A voice memo saved as `.mp4` is labelled audio before the Studio offers tools or anything uploads. */
class MediaTypeRefinementTest {
    private fun box(
        type: String,
        vararg children: ByteArray,
    ): ByteArray {
        val payload = children.fold(ByteArray(0)) { acc, b -> acc + b }
        val size = 8 + payload.size
        return byteArrayOf((size ushr 24).toByte(), (size ushr 16).toByte(), (size ushr 8).toByte(), size.toByte()) + type.encodeToByteArray() + payload
    }

    private fun trak(handler: String) = box("trak", box("mdia", box("hdlr", ByteArray(8) + handler.encodeToByteArray() + ByteArray(12))))

    private fun mp4(vararg handlers: String) = box("ftyp", "mp42".encodeToByteArray() + ByteArray(4)) + box("moov", *handlers.map { trak(it) }.toTypedArray()) + box("mdat", ByteArray(64))

    /** Files by path; records the type each upload was sent with. */
    private class FileUploader(
        val files: Map<String, ByteArray>,
    ) : MediaUploader {
        // uploadEach sends each file from a coroutine of its own on Dispatchers.IO: two appends to a
        // plain list at once can lose one.
        val sentTypes = CopyOnWriteArrayList<String?>()

        override suspend fun readRange(
            uri: MediaUri,
            offset: Long,
            length: Int,
        ): ByteArray? {
            val file = files[uri.toString()] ?: return null
            if (offset >= file.size) return ByteArray(0)
            return file.copyOfRange(offset.toInt(), minOf(file.size, offset.toInt() + length))
        }

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
            sentTypes += mimeType
            return progress.finish(UploadOrchestrator.OrchestratorResult.ServerResult(FileHeader(mimeType, "h", 1, null, null), "https://x/$uri", null, null, mimeType, null))
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
        ) = MediaCompressorResult(uri, mimeType, null)

        override suspend fun remoteFileHeader(
            url: String,
            httpClients: IRoleBasedHttpClientBuilder,
        ): FileHeader? = null
    }

    private val uploader =
        FileUploader(
            mapOf(
                "/memo.mp4" to mp4("soun"),
                "/clip.mp4" to mp4("vide", "soun"),
                "/song.m4a" to mp4("soun"),
                "/broken.mp4" to ByteArray(10),
                "/anim.webp" to PictureHeadersTest().webp(animated = true),
                "/still.webp" to PictureHeadersTest().webp(animated = false),
                "/anim.gif" to PictureHeadersTest().gif(10, 10),
            ),
        )

    @Test
    fun soundOnlyMp4IsAudio() =
        runTest {
            assertEquals("audio/mp4", uploader.refineMediaType(SelectedMedia(StringMediaUri("/memo.mp4"), "video/mp4")).mimeType)
        }

    @Test
    fun othersStayAsTheyAre() =
        runTest {
            val clip = SelectedMedia(StringMediaUri("/clip.mp4"), "video/mp4")
            assertSame(clip, uploader.refineMediaType(clip))
            // Declared audio is taken at its word; an unreadable or unknown file is left alone.
            val song = SelectedMedia(StringMediaUri("/song.m4a"), "audio/mp4")
            assertSame(song, uploader.refineMediaType(song))
            val broken = SelectedMedia(StringMediaUri("/broken.mp4"), "video/mp4")
            assertSame(broken, uploader.refineMediaType(broken))
            val photo = SelectedMedia(StringMediaUri("/a.jpg"), "image/jpeg")
            assertSame(photo, uploader.refineMediaType(photo))
        }

    @Test
    fun theStudioRelabelsTheFileOnce() =
        runTest {
            val item = SelectedMediaProcessing(SelectedMedia(StringMediaUri("/memo.mp4"), "video/mp4"))
            item.checkType(uploader)
            assertEquals("audio/mp4", item.media.mimeType)
            assertEquals(false, item.media.isVideo())
        }

    @Test
    fun anUploadSendsItAsAudioEvenWithoutTheStudio() =
        runTest {
            val media = MultiOrchestrator(listOf(SelectedMedia(StringMediaUri("/memo.mp4"), "video/mp4"), SelectedMedia(StringMediaUri("/clip.mp4"), "video/mp4")))

            media.uploadEach(ServerName("one", "https://one.example"), mockk<Account>(), uploader)

            assertEquals(listOf("audio/mp4", "video/mp4"), uploader.sentTypes.sortedBy { it })
        }

    @Test
    fun anAnimatedWebpIsNeverCroppedOrReencoded() =
        runTest {
            val anim = uploader.refineMediaType(SelectedMedia(StringMediaUri("/anim.webp"), "image/webp"))
            assertEquals(true, anim.animated)
            assertEquals(false, anim.canCrop())
            assertEquals(false, anim.isCompressible())

            val still = uploader.refineMediaType(SelectedMedia(StringMediaUri("/still.webp"), "image/webp"))
            assertEquals(false, still.animated)
            assertEquals(true, still.canCrop())
        }

    @Test
    fun aMovingPictureTakesItsShapeFromItsHeader() =
        runTest {
            val webp = SelectedMediaProcessing(SelectedMedia(StringMediaUri("/anim.webp"), "image/webp"))
            webp.checkType(uploader)
            assertEquals(640f / 360f, webp.displayAspectRatio)

            val gif = SelectedMediaProcessing(SelectedMedia(StringMediaUri("/anim.gif"), "image/gif"))
            gif.checkType(uploader)
            assertEquals(2f, gif.displayAspectRatio)
        }
}
