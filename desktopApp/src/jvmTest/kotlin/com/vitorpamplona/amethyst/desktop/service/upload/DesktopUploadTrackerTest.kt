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
package com.vitorpamplona.amethyst.desktop.service.upload

import com.vitorpamplona.quartz.nipB7Blossom.BlossomUploadResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopUploadTrackerTest {
    @Test
    fun initialStateIsIdle() {
        val tracker = DesktopUploadTracker()
        val state = tracker.state.value

        assertFalse(state.isUploading)
        assertNull(state.fileName)
        assertNull(state.error)
        assertNull(state.result)
    }

    @Test
    fun startUploadSetsUploadingState() {
        val tracker = DesktopUploadTracker()

        tracker.startUpload("photo.jpg")

        val state = tracker.state.value
        assertTrue(state.isUploading)
        assertEquals("photo.jpg", state.fileName)
        assertNull(state.error)
        assertNull(state.result)
    }

    @Test
    fun onSuccessStoresResult() {
        val tracker = DesktopUploadTracker()
        val metadata = MediaMetadata(sha256 = "abc123", size = 1024, mimeType = "image/png")
        val blossom = BlossomUploadResult(url = "https://blossom.example.com/abc123.png")
        val result = UploadResult(blossom = blossom, metadata = metadata)

        tracker.startUpload("test.png")
        tracker.onSuccess(result)

        val state = tracker.state.value
        assertFalse(state.isUploading)
        assertNotNull(state.result)
        assertEquals("https://blossom.example.com/abc123.png", state.result!!.blossom.url)
        assertNull(state.error)
    }

    @Test
    fun onErrorStoresErrorMessage() {
        val tracker = DesktopUploadTracker()

        tracker.startUpload("test.png")
        tracker.onError("Connection refused")

        val state = tracker.state.value
        assertFalse(state.isUploading)
        assertEquals("Connection refused", state.error)
        assertNull(state.result)
    }

    @Test
    fun resetReturnsToInitialState() {
        val tracker = DesktopUploadTracker()

        tracker.startUpload("test.png")
        tracker.onError("failed")
        tracker.reset()

        val state = tracker.state.value
        assertFalse(state.isUploading)
        assertNull(state.fileName)
        assertNull(state.error)
        assertNull(state.result)
    }

    @Test
    fun stateFlowEmitsLatestValue() =
        runTest {
            val tracker = DesktopUploadTracker()

            // Initial emission
            val initial = tracker.state.first()
            assertFalse(initial.isUploading)

            tracker.startUpload("file.mp4")
            val uploading = tracker.state.first()
            assertTrue(uploading.isUploading)
            assertEquals("file.mp4", uploading.fileName)
        }

    @Test
    fun multipleUploadsOverwriteState() {
        val tracker = DesktopUploadTracker()

        tracker.startUpload("first.jpg")
        tracker.startUpload("second.png")

        assertEquals("second.png", tracker.state.value.fileName)
    }
}
