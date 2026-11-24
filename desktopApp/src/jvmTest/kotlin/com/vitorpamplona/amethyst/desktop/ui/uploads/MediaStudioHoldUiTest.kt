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
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import com.vitorpamplona.amethyst.commons.model.mediaServers.ServerName
import com.vitorpamplona.amethyst.commons.service.uploads.CompressorQuality
import com.vitorpamplona.amethyst.commons.service.uploads.ImageCompressionPreview
import com.vitorpamplona.amethyst.commons.service.uploads.ImageFileStats
import com.vitorpamplona.amethyst.commons.service.uploads.MediaUploader
import com.vitorpamplona.amethyst.commons.service.uploads.MediaUri
import com.vitorpamplona.amethyst.commons.service.uploads.MultiOrchestrator
import com.vitorpamplona.amethyst.commons.service.uploads.SelectedMedia
import com.vitorpamplona.amethyst.commons.service.uploads.StringMediaUri
import com.vitorpamplona.amethyst.commons.service.uploads.UnavailableMediaUploader
import com.vitorpamplona.amethyst.commons.ui.note.creators.uploads.studio.MediaStudio
import com.vitorpamplona.amethyst.commons.ui.theme.AmethystPreviewTheme
import org.junit.Rule
import kotlin.test.Test

/** Pressing and holding the picture shows the original, by finger, by mouse and by screen reader. */
class MediaStudioHoldUiTest {
    @get:Rule
    val compose = createComposeRule()

    private val server = ServerName("blossom.primal.net", "https://blossom.primal.net")

    /** Every level previews at once: a 4000×3000 original scaled to the level's edge. */
    private object PreviewingUploader : MediaUploader by UnavailableMediaUploader {
        override suspend fun previewImageCompression(
            uri: MediaUri,
            mimeType: String?,
            compressionQuality: CompressorQuality,
        ): ImageCompressionPreview? {
            val edge = compressionQuality.imageMaxDimension ?: return null
            return ImageCompressionPreview(
                original = ImageFileStats(4000, 3000, 1_600_000),
                compressed = ImageFileStats(edge, edge * 3 / 4, 100_000),
                compressedUri = StringMediaUri("/tmp/studio-hold-$edge.jpg"),
            )
        }
    }

    private val picture = SemanticsMatcher.keyIsDefined(SemanticsActions.OnLongClick)

    private fun show() {
        compose.setContent {
            AmethystPreviewTheme(dark = true) {
                MediaStudio(
                    media = MultiOrchestrator(listOf(SelectedMedia(StringMediaUri("/tmp/studio-hold.jpg"), "image/jpeg"))),
                    servers = listOf(server),
                    defaultServer = server,
                    isUploading = false,
                    uploader = PreviewingUploader,
                    altSuggester = null,
                    onAdd = {},
                    onRemove = {},
                    onCancel = {},
                )
            }
        }
        showsCompressed()
    }

    /** The pill on the picture: "Original · 4000×3000 · …" (the level cards only say "Original"). */
    private fun pillSays(prefix: String) = compose.onAllNodes(hasText("$prefix · ", substring = true)).fetchSemanticsNodes().isNotEmpty()

    private fun shows(prefix: String) = compose.waitUntil(timeoutMillis = 5_000) { pillSays(prefix) }

    private fun showsOriginal() = shows("Original")

    /** The level a new file starts on. */
    private fun showsCompressed() = shows("High")

    private fun hold() = compose.mainClock.advanceTimeBy(1_000)

    @Test
    fun holdingAFingerShowsTheOriginalUntilItLetsGo() {
        show()
        compose.onNode(picture).performTouchInput { down(center) }
        hold()
        showsOriginal()
        compose.onNode(picture).performTouchInput { up() }
        showsCompressed()
    }

    @Test
    fun holdingTheMouseShowsTheOriginalUntilItLetsGo() {
        show()
        compose.onNode(picture).performMouseInput {
            moveTo(center)
            press()
        }
        hold()
        showsOriginal()
        compose.onNode(picture).performMouseInput { release() }
        showsCompressed()
    }

    @Test
    fun aDragIsAPanNotAHold() {
        show()
        compose.onNode(picture).performTouchInput {
            down(center)
            moveBy(Offset(200f, 0f))
        }
        hold()
        compose.waitForIdle()
        check(!pillSays("Original")) { "a drag showed the original" }
        compose.onNode(picture).performTouchInput { up() }
    }

    @Test
    fun aScreenReaderFlipsWithTheLongPressAction() {
        show()
        compose.onNode(picture).performSemanticsAction(SemanticsActions.OnLongClick)
        showsOriginal()
        compose.onNode(picture).performSemanticsAction(SemanticsActions.OnLongClick)
        showsCompressed()
    }
}
