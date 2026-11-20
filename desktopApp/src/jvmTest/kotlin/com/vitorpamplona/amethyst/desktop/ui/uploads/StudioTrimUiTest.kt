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
package com.vitorpamplona.amethyst.desktop.ui.uploads

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import com.vitorpamplona.amethyst.commons.model.mediaServers.ServerName
import com.vitorpamplona.amethyst.commons.service.uploads.MediaUploader
import com.vitorpamplona.amethyst.commons.service.uploads.MediaUri
import com.vitorpamplona.amethyst.commons.service.uploads.MultiOrchestrator
import com.vitorpamplona.amethyst.commons.service.uploads.SelectedMedia
import com.vitorpamplona.amethyst.commons.service.uploads.StringMediaUri
import com.vitorpamplona.amethyst.commons.service.uploads.UnavailableMediaUploader
import com.vitorpamplona.amethyst.commons.service.uploads.VideoTrim
import com.vitorpamplona.amethyst.commons.ui.note.creators.uploads.studio.MediaStudio
import com.vitorpamplona.amethyst.commons.ui.note.creators.uploads.studio.StudioTool
import com.vitorpamplona.amethyst.commons.ui.theme.AmethystPreviewTheme
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** The Studio's Trim filmstrip dragged the way a finger drags it. */
class StudioTrimUiTest {
    @get:Rule
    val compose = createComposeRule()

    /** Times a file without reading it, as a platform that can trim GIFs and videos does. */
    private class TimingUploader(
        private val durationMs: (MediaUri) -> Long,
    ) : MediaUploader by UnavailableMediaUploader {
        constructor(durationMs: Long) : this({ durationMs })

        override val videoTrim = true
        override val gifTrim = true

        override suspend fun videoDurationMs(uri: MediaUri): Long = durationMs(uri)
    }

    private val servers = listOf(ServerName("blossom.primal.net", "https://blossom.primal.net"))

    private fun show(
        mimeType: String,
        durationMs: Long,
    ): MultiOrchestrator = show(listOf(SelectedMedia(StringMediaUri("/tmp/trim-test-$durationMs.${mimeType.substringAfter('/')}"), mimeType)), TimingUploader(durationMs))

    private fun show(
        files: List<SelectedMedia>,
        uploader: MediaUploader,
    ): MultiOrchestrator {
        val media = MultiOrchestrator(files)
        compose.setContent {
            AmethystPreviewTheme(dark = true) {
                MediaStudio(
                    media = media,
                    servers = servers,
                    defaultServer = servers[0],
                    isUploading = false,
                    uploader = uploader,
                    altSuggester = null,
                    onAdd = {},
                    onRemove = {},
                    onCancel = {},
                    initialTool = StudioTool.TRIM,
                )
            }
        }
        compose.waitForIdle()
        return media
    }

    // The filmstrip runs the width of the window, 16 px in, near the bottom of the default 1024x768.
    private val startHandle = Offset(21f, 628f)

    @Test
    fun aOneSecondGifTrimsFromItsStartWhenDraggedSlowly() {
        val media = show("image/gif", durationMs = 1_000)
        // A slow drag: many moves of a few pixels, each a few milliseconds of a one-second GIF.
        compose.onRoot().performTouchInput { down(startHandle) }
        repeat(60) {
            compose.onRoot().performTouchInput { moveBy(Offset(5f, 0f)) }
            compose.waitForIdle()
        }
        compose.onRoot().performTouchInput { up() }
        compose.waitForIdle()

        val trim = assertNotNull(media.get(0).options.trim, "the start moved")
        assertTrue(trim.startMs in 250L..350L, "about 300 px of 992 kept out: ${trim.startMs}")
        assertTrue(trim.durationMs >= VideoTrim.MIN_GIF_MS)
    }

    @Test
    fun aHandleGrabbedAndPulledInMovesThatEdge() {
        val media = show("video/mp4", durationMs = 60_000)
        // Past the touch slop the pointer is beyond the handle's reach: it still moves that handle.
        compose.onRoot().performTouchInput { swipe(startHandle, startHandle + Offset(400f, 0f), 300) }
        compose.waitForIdle()

        val trim = assertNotNull(media.get(0).options.trim, "the start moved")
        assertTrue(trim.startMs > 10_000L, "not moved as a whole: ${trim.startMs}")
        assertTrue(trim.endMs == 60_000L, "the end stayed")
    }

    @Test
    fun theSecondFileTrimsByItsOwnLength() {
        val short = SelectedMedia(StringMediaUri("/tmp/trim-test-short.mp4"), "video/mp4")
        val long = SelectedMedia(StringMediaUri("/tmp/trim-test-long.mp4"), "video/mp4")
        val media = show(listOf(short, long), TimingUploader { if (it.toString().contains("long")) 60_000L else 6_000L })

        // Trim opens on the 6 s video; then the 60 s one is picked from the strip.
        compose.onNodeWithContentDescription("2 of 2").performClick()
        compose.waitForIdle()
        compose.onRoot().performTouchInput { swipe(startHandle, startHandle + Offset(400f, 0f), 300) }
        compose.waitForIdle()

        val trim = assertNotNull(media.get(1).options.trim, "the start moved")
        assertEquals(60_000L, trim.endMs, "the end is the 60 s video's, not the first file's 6 s")
    }
}
