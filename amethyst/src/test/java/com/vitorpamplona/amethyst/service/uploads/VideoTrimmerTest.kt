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
package com.vitorpamplona.amethyst.service.uploads

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.davotoula.lightcompressor.sanitizer.Mp4Sanitizer
import com.davotoula.lightcompressor.sanitizer.SanitizeResult
import com.davotoula.lightcompressor.sanitizer.TrimRange
import com.vitorpamplona.amethyst.commons.service.uploads.VideoTrim
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * The Studio trim through LightCompressor-enhanced's [Mp4Sanitizer]: the range it asks for, what a
 * failure or a cancellation leaves behind, and that its output is not remuxed again by the strip
 * before the upload.
 */
class VideoTrimmerTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var uploader: AndroidMediaUploader
    private lateinit var picked: Uri
    private val output = slot<File>()
    private val range = slot<TrimRange>()

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        val resolver: ContentResolver = mockk(relaxed = true)
        every { context.contentResolver } returns resolver
        every { context.cacheDir } returns tempFolder.root
        picked = mockk(relaxed = true) { every { scheme } returns "content" }

        // File.toUri() is Uri.fromFile, a stub on the JVM: hand back a Uri that remembers its path.
        mockkStatic(Uri::class)
        every { Uri.fromFile(any()) } answers { fileUri(firstArg()) }

        // Mp4Sanitizer's API is @JvmStatic, so mock the static bridge (as MetadataStripperTest does).
        mockkStatic(Mp4Sanitizer::class)
        every { Mp4Sanitizer.sanitize(any(), any(), capture(output)) } answers { SanitizeResult(output.captured, true) }

        uploader = AndroidMediaUploader(context)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun fileUri(file: File): Uri =
        mockk {
            every { scheme } returns "file"
            every { path } returns file.absolutePath
        }

    /** Stubs the trimming [Mp4Sanitizer.sanitize] to write [TRIMMED_BYTES] bytes and report [fastStart]. */
    private fun trimSucceeds(fastStart: Boolean = true) {
        every { Mp4Sanitizer.sanitize(context, picked, capture(output), capture(range)) } answers {
            output.captured.writeBytes(ByteArray(TRIMMED_BYTES))
            SanitizeResult(output.captured, fastStart)
        }
    }

    @Test
    fun `the trim asks the library for the kept range, both ends included`() =
        runTest {
            trimSucceeds()

            val trimmed = uploader.trimVideo(picked, "video/mp4", VideoTrim(2_500, 7_000))

            assertNotNull(trimmed)
            assertEquals(TrimRange(2_500, 7_000), range.captured)
            assertEquals(tempFolder.root, output.captured.parentFile)
            assertTrue(output.captured.name.startsWith("trim_"))
            assertEquals(output.captured.absolutePath, trimmed!!.uri.path)
            assertEquals("video/mp4", trimmed.contentType)
            assertEquals(TRIMMED_BYTES.toLong(), trimmed.size)
        }

    @Test
    fun `an uncompressed upload of the trim is not remuxed a second time`() =
        runTest {
            trimSucceeds()

            val trimmed = uploader.trimVideo(picked, "video/mp4", VideoTrim(0, 3_000))!!
            val sent = uploader.stripMetadata(trimmed.uri, trimmed.contentType)

            assertSame(trimmed.uri, sent)
            verify(exactly = 1) { Mp4Sanitizer.sanitize(any(), any(), any(), any<TrimRange>()) }
            verify(exactly = 0) { Mp4Sanitizer.sanitize(any(), any(), any()) }
        }

    @Test
    fun `a trim that is not fast-start still counts as stripped`() =
        runTest {
            trimSucceeds(fastStart = false)

            val trimmed = uploader.trimVideo(picked, "video/mp4", VideoTrim(0, 3_000))!!

            assertTrue(uploader.sanitizedTempFiles.isSanitized(trimmed.uri))
        }

    @Test
    fun `an audio-only file labelled video is trimmed and marked like any other`() =
        runTest {
            // The app does not look at the tracks: the library sets the clock from the audio when
            // there is no video, so an audio-only video/mp4 goes through the same call.
            trimSucceeds()

            val trimmed = uploader.trimVideo(picked, "video/mp4", VideoTrim(1_000, 4_000))!!

            assertEquals(TrimRange(1_000, 4_000), range.captured)
            assertTrue(uploader.sanitizedTempFiles.isSanitized(trimmed.uri))
        }

    @Test
    fun `a failed trim leaves no file and no mark`() =
        runTest {
            every { Mp4Sanitizer.sanitize(context, picked, capture(output), any<TrimRange>()) } throws
                IOException("Trim starts at 9000 ms, after the end of the video")

            assertNull(uploader.trimVideo(picked, "video/mp4", VideoTrim(9_000, 10_000)))

            assertFalse(output.captured.exists())
            assertEquals(emptyList<String>(), tempFolder.root.list()!!.toList())
        }

    @Test
    fun `a range that keeps nothing fails without calling the library`() =
        runTest {
            trimSucceeds()

            for ((start, end) in listOf(-1L to 1_000L, 5_000L to 5_000L, 5_000L to 4_000L)) {
                val out = tempFolder.newFile()
                assertFalse(VideoTrimmer.trim(context, picked, start, end, out))
            }
            assertNull(uploader.trimVideo(picked, "video/mp4", VideoTrim(3_000, 2_000)))

            verify(exactly = 0) { Mp4Sanitizer.sanitize(any(), any(), any(), any<TrimRange>()) }
            assertTrue(tempFolder.root.listFiles()!!.none { it.name.startsWith("trim_") })
        }

    @Test
    fun `cancelling the upload interrupts the trim and deletes its file`() {
        val started = CountDownLatch(1)
        every { Mp4Sanitizer.sanitize(context, picked, capture(output), any<TrimRange>()) } answers {
            started.countDown()
            Thread.sleep(30_000)
            fail("the trim was not interrupted")
            error("unreachable")
        }

        runBlocking {
            // Off this thread, which waits on the latch below.
            val trim = async(Dispatchers.IO) { uploader.trimVideo(picked, "video/mp4", VideoTrim(0, 3_000)) }
            assertTrue(started.await(10, TimeUnit.SECONDS))
            trim.cancel()
            try {
                trim.await()
                fail("a cancelled trim returned")
            } catch (_: CancellationException) {
            }
        }

        assertFalse(output.captured.exists())
    }

    private companion object {
        const val TRIMMED_BYTES = 128
    }
}
