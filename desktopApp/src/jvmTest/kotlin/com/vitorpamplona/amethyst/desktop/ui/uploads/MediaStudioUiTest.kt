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

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.vitorpamplona.amethyst.commons.model.mediaServers.ServerName
import com.vitorpamplona.amethyst.commons.service.uploads.CompressorQuality
import com.vitorpamplona.amethyst.commons.service.uploads.MultiOrchestrator
import com.vitorpamplona.amethyst.commons.service.uploads.SelectedMedia
import com.vitorpamplona.amethyst.commons.service.uploads.SelectedMediaProcessing
import com.vitorpamplona.amethyst.commons.service.uploads.StringMediaUri
import com.vitorpamplona.amethyst.commons.service.uploads.UnavailableMediaUploader
import com.vitorpamplona.amethyst.commons.ui.note.creators.uploads.studio.MediaStudio
import com.vitorpamplona.amethyst.commons.ui.theme.AmethystPreviewTheme
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** The media studio driven the way a person drives it: every choice lands on the file on screen only. */
class MediaStudioUiTest {
    @get:Rule
    val compose = createComposeRule()

    private val servers = listOf(ServerName("blossom.primal.net", "https://blossom.primal.net"), ServerName("nostr.download", "https://nostr.download"))
    private val media =
        MultiOrchestrator(
            listOf(
                SelectedMedia(StringMediaUri("/tmp/studio-a.jpg"), "image/jpeg"),
                SelectedMedia(StringMediaUri("/tmp/studio-b.jpg"), "image/jpeg"),
            ),
        )
    private var added: ServerName? = null
    private var removed: SelectedMediaProcessing? = null

    private fun show() =
        compose.setContent {
            AmethystPreviewTheme(dark = true) {
                MediaStudio(
                    media = media,
                    servers = servers,
                    defaultServer = servers[0],
                    isUploading = false,
                    uploader = UnavailableMediaUploader,
                    altSuggester = null,
                    onAdd = { added = it },
                    onRemove = { removed = it },
                    onCancel = {},
                )
            }
        }

    @Test
    fun choicesApplyToTheFileOnScreenOnly() {
        show()
        val first = media.get(0)
        val second = media.get(1)
        val defaultQuality = second.options.quality

        compose.onNodeWithText("Low").performClick()
        compose.onNodeWithContentDescription("2 of 2").performClick()
        compose.onNodeWithText("Alt text").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("A forest at dusk")
        compose.onNodeWithText("Privacy").performClick()
        compose.onNodeWithText("Sensitive").performClick()
        compose.waitForIdle()

        assertEquals(CompressorQuality.LOW, first.options.quality)
        assertEquals("", first.options.alt)
        assertFalse(first.options.sensitive)

        assertEquals(defaultQuality, second.options.quality)
        assertEquals("A forest at dusk", second.options.alt)
        assertTrue(second.options.sensitive)
    }

    @Test
    fun addSendsThePickedServerAndRemoveTheFileOnScreen() {
        show()
        compose.onNodeWithText("blossom.primal.net").performClick()
        compose.onNodeWithText("nostr.download").performClick()
        compose.onNodeWithText("Add").performClick()
        compose.waitForIdle()
        assertEquals(servers[1], added)

        compose.onNodeWithContentDescription("2 of 2").performClick()
        compose.onNodeWithContentDescription("Remove").performClick()
        compose.waitForIdle()
        assertSame(media.get(1), removed)
    }
}
