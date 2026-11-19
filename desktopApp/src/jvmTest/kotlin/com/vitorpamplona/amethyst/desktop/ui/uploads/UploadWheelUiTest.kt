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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.service.uploads.MultiOrchestrator
import com.vitorpamplona.amethyst.commons.service.uploads.SelectedMedia
import com.vitorpamplona.amethyst.commons.service.uploads.StringMediaUri
import com.vitorpamplona.amethyst.commons.service.uploads.UploadingState
import com.vitorpamplona.amethyst.commons.ui.note.creators.uploads.studio.AttachedMediaCard
import com.vitorpamplona.amethyst.commons.ui.theme.AmethystPreviewTheme
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals

/** Each file of a batch shows its own upload: a ring that fills while its bytes are counted, one that spins once they are all on their way. */
class UploadWheelUiTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun eachCardOfABatchShowsItsOwnUpload() {
        val batch =
            MultiOrchestrator(
                listOf(
                    SelectedMedia(StringMediaUri("/tmp/wheel-a.jpg"), "image/jpeg"),
                    SelectedMedia(StringMediaUri("/tmp/wheel-b.jpg"), "image/jpeg"),
                ),
            )
        val (sending, uploading) = batch.items()
        sending.orchestrator.updateState(0.95, UploadingState.Sending)
        uploading.orchestrator.updateState(0.5, UploadingState.Uploading)

        compose.setContent {
            AmethystPreviewTheme(dark = true) {
                Column {
                    batch.items().forEach {
                        AttachedMediaCard(it, selected = false, locked = true, onOpen = {}, onRemove = {}, modifier = Modifier.width(300.dp).height(200.dp))
                    }
                }
            }
        }

        // The file whose bytes are all on their way spins: how far they got is not known.
        assertEquals(1, compose.onAllNodes(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)).fetchSemanticsNodes().size)
        // The other still fills with its bytes.
        assertEquals(1, compose.onAllNodes(hasProgressBarRangeInfo(ProgressBarRangeInfo(0.5f, 0f..1f))).fetchSemanticsNodes().size)
        // Both are uploading, in the same word.
        assertEquals(2, compose.onAllNodesWithText("Uploading", substring = true).fetchSemanticsNodes().size)

        // The server answers the first: no longer spinning.
        compose.runOnIdle { sending.orchestrator.updateState(0.6, UploadingState.ServerProcessing) }
        compose.waitForIdle()
        assertEquals(0, compose.onAllNodes(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)).fetchSemanticsNodes().size)
    }
}
