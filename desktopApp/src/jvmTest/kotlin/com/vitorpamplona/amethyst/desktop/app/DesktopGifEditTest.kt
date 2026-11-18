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
import java.awt.image.BufferedImage
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

/** GIFs in the media Studio on the desktop, checked with the real ffmpeg: left alone they go up as they are; cropped or trimmed, still as GIFs. */
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
    fun aCroppedAndTrimmedGifIsThatPartPixelForPixel() {
        assumeFfmpeg()
        val source = gif()
        // The left half, from 1 s to 3 s: frames 8 to 23 of 8 a second.
        val result = assertNotNull(runBlocking { uploader.editGif(StringMediaUri(source.absolutePath), ImageCrop(0f, 0f, 0.5f, 1f), VideoTrim(1_000, 3_000)) })
        val edited = File(result.uri.toString())
        try {
            assertEquals("image/gif", result.contentType)
            assertEquals("GIF89a", edited.readBytes().copyOf(6).decodeToString())
            val probe = assertNotNull(runBlocking { FfmpegProbe(FfmpegLocator.default.ffprobe).probe(edited) })
            assertEquals(160 to 180, probe.info.resolution.width to probe.info.resolution.height)
            val seconds = assertNotNull(probe.durationSeconds)
            assertTrue(seconds in 1.9..2.1, "about 2 s, got $seconds")

            // ffmpeg, decoding both on its own, sees the same pixels.
            val before = decodeFrames(source, "src")
            val after = decodeFrames(edited, "out")
            assertEquals(16, after.size)
            after.forEachIndexed { n, frame ->
                val expected = before[8 + n].getSubimage(0, 0, 160, 180)
                for (y in 0 until 180) {
                    for (x in 0 until 160) {
                        assertEquals(expected.getRGB(x, y), frame.getRGB(x, y), "frame $n at $x,$y")
                    }
                }
            }
        } finally {
            edited.delete()
        }
    }

    /** Every frame of [gif] as ffmpeg composes it. */
    private fun decodeFrames(
        gif: File,
        name: String,
    ): List<BufferedImage> {
        val out = File(dir, name).apply { mkdirs() }
        // Every frame as it is, none dropped or doubled: -fps_mode since ffmpeg 5.1, -vsync before it
        // (ffmpeg 9 removed -vsync).
        val target = File(out, "%03d.png").absolutePath
        runCatching { ffmpeg("-i", gif.absolutePath, "-fps_mode", "passthrough", target) }
            .recover { if ("fps_mode" in it.message.orEmpty()) ffmpeg("-i", gif.absolutePath, "-vsync", "0", target) else throw it }
            .getOrThrow()
        return out.listFiles()!!.sortedBy { it.name }.map { ImageIO.read(it) }
    }

    @Test
    fun anUntouchedGifGoesUpAsItIs() {
        assumeFfmpeg()
        val gif = gif()
        val recorder = RecordingUploader(uploader)
        val media = MultiOrchestrator(listOf(SelectedMedia(StringMediaUri(gif.absolutePath), "image/gif")))
        runBlocking { media.uploadEach(ServerName("one", "https://one.example"), mockk<Account>(), recorder) }
        assertEquals<List<Pair<String, String?>>>(listOf(gif.absolutePath to "image/gif"), recorder.sent)

        // Edited, it goes up as the GIF editGif made, still a GIF.
        media.get(0).options = media.get(0).options.copy(trim = VideoTrim(0, 2_000))
        runBlocking { media.uploadEach(ServerName("one", "https://one.example"), mockk<Account>(), recorder) }
        assertEquals("image/gif", recorder.sent.last().second)
        assertTrue(recorder.sent.last().first != gif.absolutePath, "the edited copy, not the original")
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
