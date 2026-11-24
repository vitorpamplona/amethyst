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
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * The uploader's metadata strip leaves a file it marked sanitized alone, and remuxes everything
 * else: an unmarked file, a marked one changed since, a forgotten one, or the same path reached
 * through anything but a `file://` uri.
 */
class SanitizedTempFilesTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var uploader: AndroidMediaUploader

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        val resolver: ContentResolver = mockk(relaxed = true)
        every { context.contentResolver } returns resolver
        every { context.cacheDir } returns tempFolder.root

        // File.toUri() is Uri.fromFile, a stub on the JVM: hand back a Uri that remembers its path.
        mockkStatic(Uri::class)
        every { Uri.fromFile(any()) } answers { fileUri(firstArg()) }

        // Mp4Sanitizer's API is @JvmStatic, so mock the static bridge (as MetadataStripperTest does).
        mockkStatic(Mp4Sanitizer::class)
        val output = slot<File>()
        every { Mp4Sanitizer.sanitize(any(), any(), capture(output)) } answers { SanitizeResult(output.captured, true) }

        uploader = AndroidMediaUploader(context)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun fileUri(
        file: File,
        scheme: String = "file",
    ): Uri =
        mockk {
            every { this@mockk.scheme } returns scheme
            every { path } returns file.absolutePath
        }

    /** A trim's output: a temp file with some bytes in it. */
    private fun trimmedFile(): File = tempFolder.newFile("trim_${System.nanoTime()}.mp4").apply { writeBytes(ByteArray(64) { it.toByte() }) }

    @Test
    fun `a marked file goes up as it is`() =
        runTest {
            val file = trimmedFile()
            val uri = fileUri(file)
            uploader.sanitizedTempFiles.mark(file)

            assertSame(uri, uploader.stripMetadata(uri, "video/mp4"))
            val result = uploader.strip(uri, "video/mp4")
            assertSame(uri, result.uri)
            assertTrue(result.stripped)

            verify(exactly = 0) { Mp4Sanitizer.sanitize(any(), any(), any()) }
        }

    @Test
    fun `an unmarked file is sanitized`() =
        runTest {
            val uri = fileUri(trimmedFile())

            val stripped = uploader.stripMetadata(uri, "video/mp4")

            verify(exactly = 1) { Mp4Sanitizer.sanitize(any(), uri, any()) }
            assertTrue(stripped.path!!.substringAfterLast('/').startsWith("stripped_video_"))
        }

    @Test
    fun `a marked file changed since is sanitized`() =
        runTest {
            val file = trimmedFile()
            val uri = fileUri(file)
            uploader.sanitizedTempFiles.mark(file)

            file.appendBytes(ByteArray(8))

            uploader.stripMetadata(uri, "video/mp4")
            verify(exactly = 1) { Mp4Sanitizer.sanitize(any(), uri, any()) }
        }

    @Test
    fun `a marked file touched since is sanitized`() =
        runTest {
            val file = trimmedFile()
            val uri = fileUri(file)
            uploader.sanitizedTempFiles.mark(file)

            assertTrue(file.setLastModified(file.lastModified() - 10_000))

            uploader.stripMetadata(uri, "video/mp4")
            verify(exactly = 1) { Mp4Sanitizer.sanitize(any(), uri, any()) }
        }

    @Test
    fun `discarding the file drops its mark`() =
        runTest {
            val file = trimmedFile()
            val uri = fileUri(file)
            uploader.sanitizedTempFiles.mark(file)
            val markedAt = file.lastModified()

            uploader.discardTempFile(uri)
            assertFalse(file.exists())

            // A new file at the same path, even with the same bytes and time, is not the one marked.
            file.writeBytes(ByteArray(64) { it.toByte() })
            assertTrue(file.setLastModified(markedAt))
            uploader.stripMetadata(uri, "video/mp4")
            verify(exactly = 1) { Mp4Sanitizer.sanitize(any(), uri, any()) }
        }

    @Test
    fun `the same path through a content uri is sanitized`() =
        runTest {
            val file = trimmedFile()
            uploader.sanitizedTempFiles.mark(file)
            val contentUri = fileUri(file, scheme = "content")

            uploader.stripMetadata(contentUri, "video/mp4")
            verify(exactly = 1) { Mp4Sanitizer.sanitize(any(), contentUri, any()) }
        }

    @Test
    fun `a marked file that is gone is not trusted`() {
        val file = trimmedFile()
        uploader.sanitizedTempFiles.mark(file)
        assertTrue(file.delete())

        assertFalse(uploader.sanitizedTempFiles.isSanitized(fileUri(file)))
    }

    @Test
    fun `marks are per file`() {
        val marked = trimmedFile()
        val other = trimmedFile()
        uploader.sanitizedTempFiles.mark(marked)

        assertTrue(uploader.sanitizedTempFiles.isSanitized(fileUri(marked)))
        assertFalse(uploader.sanitizedTempFiles.isSanitized(fileUri(other)))
    }
}
