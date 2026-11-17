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
package com.vitorpamplona.amethyst.desktop.app

import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.mediaServers.ServerName
import com.vitorpamplona.amethyst.commons.service.upload.BlossomClient
import com.vitorpamplona.amethyst.commons.service.upload.FileHeader
import com.vitorpamplona.amethyst.commons.service.upload.video.FfmpegLocator
import com.vitorpamplona.amethyst.commons.service.upload.video.FfmpegProbe
import com.vitorpamplona.amethyst.commons.service.upload.video.FfmpegVideoCompressor
import com.vitorpamplona.amethyst.commons.service.uploads.CompressorQuality
import com.vitorpamplona.amethyst.commons.service.uploads.ImageCrop
import com.vitorpamplona.amethyst.commons.service.uploads.MediaUploader
import com.vitorpamplona.amethyst.commons.service.uploads.MediaUri
import com.vitorpamplona.amethyst.commons.service.uploads.MultiOrchestrator
import com.vitorpamplona.amethyst.commons.service.uploads.SelectedMedia
import com.vitorpamplona.amethyst.commons.service.uploads.StringMediaUri
import com.vitorpamplona.amethyst.commons.service.uploads.UploadOrchestrator
import com.vitorpamplona.amethyst.commons.service.uploads.UploadingState
import com.vitorpamplona.amethyst.commons.service.uploads.VideoTrim
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** GIFs in the media Studio on the desktop, with the real ffmpeg: left alone they go up as they are; cropped or trimmed, as an MP4. */
class DesktopGifEditTest {
    private val dir = Files.createTempDirectory("gif-edit").toFile()
    private val compressor = FfmpegVideoCompressor()
    private val uploader = DesktopMediaUploader(clientFor = { mockk<BlossomClient>() }, httpClients = mockk(relaxed = true), videoCompressor = compressor)

    @AfterTest
    fun cleanup() {
        dir.deleteRecursively()
    }

    private fun assumeFfmpeg() {
        assumeTrue("ffmpeg not installed", runCatching { ProcessBuilder("ffmpeg", "-version").start().waitFor(10, TimeUnit.SECONDS) }.getOrDefault(false))
        assumeTrue("ffmpeg not found", compressor.available)
    }

    /** Four seconds, 320x180, 8 frames a second. */
    private fun gif(): File {
        val frames = File(dir, "frames.mp4")
        ffmpeg("-f", "lavfi", "-i", "testsrc=size=320x180:rate=8", "-t", "4", frames.absolutePath)
        val palette = File(dir, "palette.png")
        ffmpeg("-i", frames.absolutePath, "-vf", "palettegen", palette.absolutePath)
        return File(dir, "anim.gif").also { ffmpeg("-i", frames.absolutePath, "-i", palette.absolutePath, "-lavfi", "paletteuse", "-loop", "0", it.absolutePath) }
    }

    @Test
    fun theTrimToolReadsAGif() {
        assumeFfmpeg()
        val uri = StringMediaUri(gif().absolutePath)
        val duration = assertNotNull(runBlocking { uploader.videoDurationMs(uri) })
        assertTrue(duration in 3_800L..4_200L, "about 4 s, got $duration")
        val frame = assertNotNull(runBlocking { uploader.videoFrame(uri, 2_000, 160) })
        assertEquals(160, ImageIO.read(ByteArrayInputStream(frame)).width)
    }

    @Test
    fun aCroppedAndTrimmedGifBecomesThatPartAsAVideo() {
        assumeFfmpeg()
        val uri = StringMediaUri(gif().absolutePath)
        // The left half, from 1 s to 3 s.
        val result = assertNotNull(runBlocking { uploader.editGif(uri, ImageCrop(0f, 0f, 0.5f, 1f), VideoTrim(1_000, 3_000)) })
        val mp4 = File(result.uri.toString())
        try {
            assertEquals("video/mp4", result.contentType)
            val probe = assertNotNull(runBlocking { FfmpegProbe(FfmpegLocator.default.ffprobe).probe(mp4) })
            assertEquals(160 to 180, probe.info.resolution.width to probe.info.resolution.height)
            val seconds = assertNotNull(probe.durationSeconds)
            assertTrue(seconds in 1.8..2.2, "about 2 s, got $seconds")
        } finally {
            mp4.delete()
        }
    }

    @Test
    fun anUntouchedGifGoesUpAsItIs() {
        assumeFfmpeg()
        val gif = gif()
        val recorder = RecordingUploader(uploader)
        val media = MultiOrchestrator(listOf(SelectedMedia(StringMediaUri(gif.absolutePath), "image/gif")))
        runBlocking { media.uploadEach(ServerName("one", "https://one.example"), mockk<Account>(), recorder) }
        assertEquals<List<Pair<String, String?>>>(listOf(gif.absolutePath to "image/gif"), recorder.sent)

        // Edited, the same file goes up as the video editGif made.
        media.get(0).options = media.get(0).options.copy(trim = VideoTrim(0, 2_000))
        runBlocking { media.uploadEach(ServerName("one", "https://one.example"), mockk<Account>(), recorder) }
        assertEquals("video/mp4", recorder.sent.last().second)
        assertTrue(!File(recorder.sent.last().first).exists(), "the edited copy is deleted after the upload")
    }

    /** The real desktop uploader for everything but the network: records what would be sent. */
    private class RecordingUploader(
        real: MediaUploader,
    ) : MediaUploader by real {
        val sent = mutableListOf<Pair<String, String?>>()

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
            sent += uri.toString() to mimeType
            return progress.finish(UploadOrchestrator.OrchestratorResult.ServerResult(FileHeader(mimeType, "h", 1, null, null), "https://x/a", null, null, mimeType, null))
        }
    }

    private fun ffmpeg(vararg args: String) {
        val process = ProcessBuilder(listOf("ffmpeg", "-loglevel", "error", "-y") + args).redirectErrorStream(true).start()
        check(process.waitFor(60, TimeUnit.SECONDS) && process.exitValue() == 0) { process.inputStream.bufferedReader().readText() }
    }
}
