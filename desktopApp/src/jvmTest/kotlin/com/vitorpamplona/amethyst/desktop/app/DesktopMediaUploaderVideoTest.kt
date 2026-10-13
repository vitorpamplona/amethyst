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

import com.vitorpamplona.amethyst.commons.notices.UploadNotice
import com.vitorpamplona.amethyst.commons.notices.UserNotice
import com.vitorpamplona.amethyst.commons.notices.UserNoticeSink
import com.vitorpamplona.amethyst.commons.service.http.IRoleBasedHttpClientBuilder
import com.vitorpamplona.amethyst.commons.service.upload.FileHeader
import com.vitorpamplona.amethyst.commons.service.upload.video.VideoCompressionResult
import com.vitorpamplona.amethyst.commons.service.upload.video.VideoCompressor
import com.vitorpamplona.amethyst.commons.service.uploads.CompressorQuality
import com.vitorpamplona.amethyst.commons.service.uploads.MediaUri
import com.vitorpamplona.amethyst.commons.service.uploads.StringMediaUri
import com.vitorpamplona.amethyst.commons.service.uploads.UploadError
import com.vitorpamplona.amethyst.commons.service.uploads.UploadOrchestrator
import com.vitorpamplona.amethyst.commons.service.uploads.UploadingState
import com.vitorpamplona.amethyst.commons.service.uploads.VideoCompressionSupport
import com.vitorpamplona.amethyst.commons.service.uploads.video.VideoInfo
import com.vitorpamplona.amethyst.commons.service.uploads.video.VideoResolution
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DesktopMediaUploaderVideoTest {
    private lateinit var tempDir: File
    private lateinit var src: File

    @BeforeTest
    fun setUp() {
        tempDir = Files.createTempDirectory("desktop-uploader").toFile()
        src = File(tempDir, "source.mov").apply { writeBytes(ByteArray(1000)) }
    }

    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    private class FakeVideoCompressor(
        override val available: Boolean,
        val result: (File) -> VideoCompressionResult,
        val onCompress: (File, CompressorQuality, Boolean) -> Unit,
        val onGif: (File) -> Unit,
        val progressEmits: List<Double>,
        val stripResult: () -> File?,
        val probeResult: VideoInfo? = VideoInfo(VideoResolution(1920, 1080), 30f),
        val compressDelayMs: Long = 0,
    ) : VideoCompressor {
        override suspend fun probe(source: File): VideoInfo? = probeResult

        override suspend fun compress(
            source: File,
            quality: CompressorQuality,
            useH265: Boolean,
            maxBytes: Long?,
            onProgress: (Double) -> Unit,
        ): VideoCompressionResult {
            onCompress(source, quality, useH265)
            delay(compressDelayMs)
            progressEmits.forEach(onProgress)
            return result(source)
        }

        override suspend fun convertGif(
            source: File,
            onProgress: (Double) -> Unit,
        ): VideoCompressionResult {
            onGif(source)
            return result(source)
        }

        override suspend fun strip(source: File): File? = stripResult()
    }

    private class RecordingSink : UserNoticeSink {
        val notices = mutableListOf<UserNotice>()

        override fun notify(notice: UserNotice) {
            notices += notice
        }
    }

    private object StubHttpClients : IRoleBasedHttpClientBuilder {
        override fun proxyPortForVideo(url: String): Int? = null

        override fun okHttpClientForNip05(url: String) = OkHttpClient()

        override fun okHttpClientForUploads(url: String) = OkHttpClient()

        override fun okHttpClientForImage(url: String) = OkHttpClient()

        override fun okHttpClientForVideo(url: String) = OkHttpClient()

        override fun okHttpClientForMoney(url: String) = OkHttpClient()

        override fun okHttpClientForPreview(url: String) = OkHttpClient()

        override fun okHttpClientForPushRegistration(url: String) = OkHttpClient()
    }

    private fun uploader(
        available: Boolean = true,
        result: (File) -> VideoCompressionResult = { VideoCompressionResult.Original(null) },
        onCompress: (File, CompressorQuality, Boolean) -> Unit = { _, _, _ -> },
        onGif: (File) -> Unit = {},
        progressEmits: List<Double> = emptyList(),
        stripResult: () -> File? = { null },
        sink: RecordingSink = RecordingSink(),
        compressDelayMs: Long = 0,
    ) = DesktopMediaUploader(
        clientFor = { error("no blossom client in this test") },
        httpClients = StubHttpClients,
        notices = sink,
        videoCompressor = FakeVideoCompressor(available, result, onCompress, onGif, progressEmits, stripResult, compressDelayMs = compressDelayMs),
    )

    private fun uri(file: File): MediaUri = StringMediaUri(file.absolutePath)

    private fun tempVideo(bytes: Int): File = File.createTempFile("amethyst_video_", ".mp4", tempDir).apply { writeBytes(ByteArray(bytes)) }

    /** What a block hands back once it has "uploaded". */
    private fun finished(): UploadingState.UploadingFinalState = UploadingState.Finished(UploadOrchestrator.OrchestratorResult.NIP95Result(FileHeader("video/mp4", "0", 0, null, null), ByteArray(0), null, null))

    private fun errorOf(state: UploadingState.UploadingFinalState): UploadError = (state as UploadingState.Error).error

    @Test
    fun supportMirrorsTheCompressor() {
        assertEquals(VideoCompressionSupport.Supported, uploader(available = true).videoCompression)
        assertEquals(VideoCompressionSupport.NeedsFfmpeg, uploader(available = false).videoCompression)
    }

    @Test
    fun compressIfNeededReturnsMp4TempForVideo() =
        runTest {
            val out = tempVideo(10)
            val r = uploader(result = { VideoCompressionResult.Compressed(out) }).compressIfNeeded(UploadOrchestrator(), uri(src), "video/quicktime", CompressorQuality.HIGH, false, false)
            assertEquals(out.absolutePath, r.uri.toString())
            assertEquals("video/mp4", r.contentType)
            assertEquals(10L, r.size)
        }

    @Test
    fun compressIfNeededReturnsStrippedOriginal() =
        runTest {
            val s = tempVideo(10)
            val r = uploader(result = { VideoCompressionResult.Original(s) }).compressIfNeeded(UploadOrchestrator(), uri(src), "video/mp4", CompressorQuality.HIGH, false, false)
            assertEquals(s.absolutePath, r.uri.toString())
        }

    @Test
    fun uncompressedAndUnavailablePassThrough() =
        runTest {
            assertEquals(src.absolutePath, uploader().compressIfNeeded(UploadOrchestrator(), uri(src), "video/mp4", CompressorQuality.UNCOMPRESSED, false, false).uri.toString())
            assertEquals(src.absolutePath, uploader(available = false).compressIfNeeded(UploadOrchestrator(), uri(src), "video/mp4", CompressorQuality.HIGH, false, false).uri.toString())
        }

    @Test
    fun imagesNeverReachTheVideoCompressor() =
        runTest {
            var called = false
            uploader(result = {
                called = true
                VideoCompressionResult.Original(null)
            }).compressIfNeeded(UploadOrchestrator(), uri(src), "image/jpeg", CompressorQuality.HIGH, false, false)
            assertFalse(called)
        }

    @Test
    fun gifIsConvertedOnlyWhenAsked() =
        runTest {
            var gifCalls = 0
            val u = uploader(result = { VideoCompressionResult.Original(null) }, onGif = { gifCalls++ })
            u.compressIfNeeded(UploadOrchestrator(), uri(src), "image/gif", CompressorQuality.UNCOMPRESSED, false, convertGifToMp4 = false)
            assertEquals(0, gifCalls)
            u.compressIfNeeded(UploadOrchestrator(), uri(src), "image/gif", CompressorQuality.UNCOMPRESSED, false, convertGifToMp4 = true)
            assertEquals(1, gifCalls) // even at UNCOMPRESSED, as Android
        }

    @Test
    fun noticesAreForwarded() =
        runTest {
            val sink = RecordingSink()
            uploader(result = { VideoCompressionResult.Original(null, listOf(UploadNotice.HdrSource)) }, sink = sink)
                .compressIfNeeded(UploadOrchestrator(), uri(src), "video/mp4", CompressorQuality.HIGH, false, false)
            assertEquals(listOf<UserNotice>(UploadNotice.HdrSource), sink.notices)
        }

    @Test
    fun withVideoPreparedDeletesTempOnSuccess() =
        runTest {
            val out = tempVideo(10)
            val r =
                uploader(result = { VideoCompressionResult.Compressed(out) }).withVideoPrepared(UploadOrchestrator(), uri(src), "video/mp4", CompressorQuality.HIGH, false, true, false, 0.2) { f, prepared ->
                    assertEquals(out, f)
                    assertTrue(prepared)
                    finished()
                }
            assertIs<UploadingState.Finished>(r)
            assertFalse(out.exists())
            assertTrue(src.exists())
        }

    @Test
    fun withVideoPreparedDeletesTempEvenWhenBlockThrows() =
        runTest {
            val out = tempVideo(10)
            assertFailsWith<IllegalStateException> {
                uploader(result = { VideoCompressionResult.Compressed(out) }).withVideoPrepared(UploadOrchestrator(), uri(src), "video/mp4", CompressorQuality.HIGH, false, true, false, 0.2) { _, _ -> error("upload failed") }
            }
            assertFalse(out.exists())
        }

    @Test
    fun withVideoPreparedDeletesTempWhenCancelledInsideTheBlock() =
        runTest {
            val out = tempVideo(10)
            val job =
                launch {
                    uploader(result = { VideoCompressionResult.Compressed(out) }).withVideoPrepared(UploadOrchestrator(), uri(src), "video/mp4", CompressorQuality.HIGH, false, true, false, 0.2) { _, _ ->
                        delay(10_000)
                        finished()
                    }
                }
            advanceTimeBy(100)
            job.cancelAndJoin()
            assertFalse(out.exists()) // deleted under NonCancellable
        }

    @Test
    fun cancellationDuringCompressionLeavesNothingToDelete() =
        runTest {
            var blockRan = false
            val job =
                launch {
                    uploader(result = { VideoCompressionResult.Compressed(tempVideo(10)) }, compressDelayMs = 10_000)
                        .withVideoPrepared(UploadOrchestrator(), uri(src), "video/mp4", CompressorQuality.HIGH, false, true, false, 0.2) { _, _ ->
                            blockRan = true
                            finished()
                        }
                }
            advanceTimeBy(100)
            job.cancelAndJoin()
            assertFalse(blockRan)
            assertTrue(src.exists())
        }

    @Test
    fun withVideoPreparedReportsCompressingUpToTheCeiling() =
        runTest {
            val progress = UploadOrchestrator()
            uploader(result = { VideoCompressionResult.Compressed(tempVideo(10)) }, progressEmits = listOf(0.5))
                .withVideoPrepared(progress, uri(src), "video/mp4", CompressorQuality.HIGH, false, true, false, progressCeiling = 0.4) { _, prepared ->
                    assertTrue(prepared)
                    finished()
                }
            assertEquals(0.21, progress.progress.value, 1e-9) // 0.02 + 0.38 * 0.5
            assertEquals(UploadingState.Compressing, progress.progressState.value)
        }

    @Test
    fun uncompressedVideoWithStripOnIsRemuxed() =
        runTest {
            val s = tempVideo(10)
            uploader(stripResult = { s }).withVideoPrepared(UploadOrchestrator(), uri(src), "video/mp4", CompressorQuality.UNCOMPRESSED, false, stripMetadata = true, convertGifToMp4 = false, progressCeiling = 0.2) { f, prepared ->
                assertEquals(s, f)
                assertTrue(prepared)
                finished()
            }
        }

    @Test
    fun strippingOwedButImpossibleAsksTheUser() =
        runTest {
            // Uncompressed + strip on, remux failed: the user decides, as on Android.
            val declined = uploader(stripResult = { null }).withVideoPrepared(UploadOrchestrator(), uri(src), "video/mp4", CompressorQuality.UNCOMPRESSED, false, stripMetadata = true, convertGifToMp4 = false, progressCeiling = 0.2, onStrippingFailed = { false }) { _, _ -> finished() }
            assertEquals(UploadError.UPLOAD_CANCELLED, errorOf(declined))
            var ran = false
            uploader(stripResult = { null }).withVideoPrepared(UploadOrchestrator(), uri(src), "video/mp4", CompressorQuality.UNCOMPRESSED, false, stripMetadata = true, convertGifToMp4 = false, progressCeiling = 0.2, onStrippingFailed = { true }) { f, prepared ->
                assertEquals(src, f)
                assertFalse(prepared)
                ran = true
                finished()
            }
            assertTrue(ran)
            // Every encoder failed and the remux too, with the toggle hidden on the promise of a strip: ask as well.
            val allFailed = uploader(result = { VideoCompressionResult.Original(null) }).withVideoPrepared(UploadOrchestrator(), uri(src), "video/mp4", CompressorQuality.HIGH, false, stripMetadata = true, convertGifToMp4 = false, progressCeiling = 0.2, onStrippingFailed = { false }) { _, _ -> finished() }
            assertEquals(UploadError.UPLOAD_CANCELLED, errorOf(allFailed))
        }

    @Test
    fun stripNotOwedNeverAsks() =
        runTest {
            var asked = false
            uploader(result = { VideoCompressionResult.Original(null) }).withVideoPrepared(UploadOrchestrator(), uri(src), "video/mp4", CompressorQuality.HIGH, false, stripMetadata = false, convertGifToMp4 = false, progressCeiling = 0.2, onStrippingFailed = {
                asked = true
                false
            }) { _, _ -> finished() }
            uploader(available = false).withVideoPrepared(UploadOrchestrator(), uri(src), "video/mp4", CompressorQuality.HIGH, false, stripMetadata = true, convertGifToMp4 = false, progressCeiling = 0.2, onStrippingFailed = {
                asked = true
                false
            }) { _, _ -> finished() }
            assertFalse(asked)
        }

    @Test
    fun tooBigIsTheNip95SizeError() =
        runTest {
            val r = uploader(result = { VideoCompressionResult.TooBig }).withVideoPrepared(UploadOrchestrator(), uri(src), "video/mp4", CompressorQuality.HIGH, false, true, false, 0.4, maxBytes = 80_000) { _, _ -> finished() }
            assertEquals(UploadError.MEDIA_TOO_BIG_FOR_NIP95, errorOf(r))
        }

    @Test
    fun stripMetadataRemuxesVideo() =
        runTest {
            val s = tempVideo(10)
            assertEquals(s.absolutePath, uploader(stripResult = { s }).stripMetadata(uri(src), "video/mp4").toString())
            assertEquals(uri(src), uploader(stripResult = { null }).stripMetadata(uri(src), "video/mp4"))
        }

    @Test
    fun probeVideoReturnsInfo() =
        runTest {
            assertEquals(VideoResolution(1920, 1080), uploader().probeVideo(uri(src))!!.resolution)
        }

    @Test
    fun probeVideoIsNullForAnUnparseableUri() =
        runTest {
            // A file: URI with a bare space fails to parse; the slider's probe must see null, not an exception.
            assertNull(uploader().probeVideo(StringMediaUri("file:///tmp/has space.mov")))
        }
}
