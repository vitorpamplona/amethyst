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

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A GIF's trimmed copy is written once and shared by its previews and its upload, and deleted when nothing holds it. */
class TrimmedGifCopyTest {
    /** Writes a numbered "copy" per edit and records what was deleted. */
    private class CountingUploader : MediaUploader by UnavailableMediaUploader {
        val edits = mutableListOf<Pair<ImageCrop?, VideoTrim?>>()
        val deleted = mutableListOf<String>()

        override suspend fun editGif(
            uri: MediaUri,
            crop: ImageCrop?,
            trim: VideoTrim?,
        ): MediaCompressorResult {
            edits += crop to trim
            return MediaCompressorResult(StringMediaUri("/tmp/copy-${edits.size}.gif"), "image/gif", 1)
        }

        override fun discardTempFile(uri: MediaUri) {
            deleted += uri.toString()
        }
    }

    private fun gif() = SelectedMediaProcessing(SelectedMedia(StringMediaUri("/tmp/picked.gif"), "image/gif"))

    private val trim = VideoTrim(300, 1000)

    @Test
    fun twoPreviewsShareOneCopy() =
        runTest {
            val uploader = CountingUploader()
            val item = gif()
            item.holdTrimmedGif()
            item.holdTrimmedGif()
            val first = item.trimmedGif(uploader, trim)
            val second = item.trimmedGif(uploader, trim)
            assertEquals(first, second)
            assertEquals(1, uploader.edits.size, "written once")

            item.releaseTrimmedGif()
            assertTrue(uploader.deleted.isEmpty(), "the other preview still shows it")
            item.releaseTrimmedGif()
            assertEquals(listOf("/tmp/copy-1.gif"), uploader.deleted)
            assertNull(item.trimmedGifNow(trim))
        }

    @Test
    fun aNewTrimReplacesAndDeletesTheOldCopy() =
        runTest {
            val uploader = CountingUploader()
            val item = gif()
            item.holdTrimmedGif()
            item.trimmedGif(uploader, trim)
            item.trimmedGif(uploader, VideoTrim(0, 500))
            assertEquals(listOf("/tmp/copy-1.gif"), uploader.deleted)
            assertNull(item.trimmedGifNow(trim))
            assertNotNull(item.trimmedGifNow(VideoTrim(0, 500)))
        }

    @Test
    fun aCopyWrittenForNobodyIsDeletedAtOnce() =
        runTest {
            val uploader = CountingUploader()
            val item = gif()
            // The preview that asked left while it was being written.
            assertNull(item.trimmedGif(uploader, trim))
            assertEquals(listOf("/tmp/copy-1.gif"), uploader.deleted)
        }

    @Test
    fun theUploadSendsThePreviewsCopyWithoutWritingItAgain() =
        runTest {
            val uploader = CountingUploader()
            val item = gif()
            item.options = item.options.copy(trim = trim)
            item.holdTrimmedGif()
            val shown = item.trimmedGif(uploader, trim)

            var sent: String? = null
            item.withEdits(uploader, onFailed = { error("failed: $it") }) { uri, type ->
                sent = uri.toString()
                assertEquals("image/gif", type)
            }
            assertEquals(shown.toString(), sent)
            assertEquals(1, uploader.edits.size, "no second encode")
            assertTrue(uploader.deleted.isEmpty(), "the preview still shows it")
        }

    @Test
    fun aCroppedUploadCutsTheTrimmedCopyAndDeletesOnlyItsOwnFile() =
        runTest {
            val uploader = CountingUploader()
            val item = gif()
            val crop = ImageCrop(0.1f, 0.1f, 0.5f, 0.5f)
            item.options = item.options.copy(trim = trim, crop = crop)
            item.holdTrimmedGif()
            item.trimmedGif(uploader, trim)

            item.withEdits(uploader, onFailed = { error("failed: $it") }) { uri, _ -> assertEquals("/tmp/copy-2.gif", uri.toString()) }
            assertEquals(listOf<Pair<ImageCrop?, VideoTrim?>>(null to trim, crop to null), uploader.edits)
            assertEquals(listOf("/tmp/copy-2.gif"), uploader.deleted, "the crop's file, not the shared copy")
        }
}
