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

import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.placeCursorAtEnd
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.text.TextRange
import com.vitorpamplona.amethyst.commons.service.uploads.MediaMarkers
import com.vitorpamplona.amethyst.commons.service.uploads.SelectedMedia
import com.vitorpamplona.amethyst.commons.service.uploads.StringMediaUri
import com.vitorpamplona.amethyst.commons.ui.actions.rememberUrlUserTagOutputTransformation
import com.vitorpamplona.amethyst.commons.ui.note.creators.uploads.studio.rememberMediaMarkerChips
import com.vitorpamplona.amethyst.commons.ui.text.MentionPreservingInputTransformation
import com.vitorpamplona.amethyst.commons.ui.theme.AmethystPreviewTheme
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A composer's media markers in a real text field, with the field's own transformations: each draws
 * as a chip named like its tile, and a key press removes a whole marker, never part of one.
 */
@OptIn(ExperimentalTestApi::class)
class MediaMarkerFieldUiTest {
    @get:Rule
    val compose = createComposeRule()

    private val photo = SelectedMedia(StringMediaUri("/tmp/a.jpg"), "image/jpeg")
    private val video = SelectedMedia(StringMediaUri("/tmp/b.mp4"), "video/mp4")
    private val state = TextFieldState("Look\n${MediaMarkers.of(4)}\nand\n${MediaMarkers.of(9)}\n")

    private fun show() =
        compose.setContent {
            AmethystPreviewTheme(dark = true) {
                val chips =
                    rememberMediaMarkerChips { id ->
                        if (id == 4) {
                            photo
                        } else if (id == 9) {
                            video
                        } else {
                            null
                        }
                    }
                BasicTextField(
                    state = state,
                    inputTransformation = MentionPreservingInputTransformation,
                    outputTransformation = rememberUrlUserTagOutputTransformation(mediaMarkers = chips),
                )
            }
        }

    @Test
    fun markersDrawAsNumberedChips() {
        show()
        // What the field draws; the stored text keeps the markers for the upload to replace.
        val drawn =
            compose
                .onNode(hasSetTextAction())
                .fetchSemanticsNode()
                .config[SemanticsProperties.EditableText]
                .text
        assertEquals("Look\n\u00A0Photo 1\u00A0\nand\n\u00A0Video 2\u00A0\n", drawn)
        assertEquals(listOf(4, 9), MediaMarkers.idsIn(state.text))
    }

    @Test
    fun backspaceAfterAChipRemovesTheWholeMarker() {
        show()
        compose.onNode(hasSetTextAction()).performClick()
        val end = state.text.indexOf(MediaMarkers.of(4)) + MediaMarkers.of(4).length
        state.edit { selection = TextRange(end) }
        compose.onNode(hasSetTextAction()).performKeyInput { pressKey(Key.Backspace) }
        compose.waitForIdle()
        assertEquals("Look\n\nand\n${MediaMarkers.of(9)}\n", state.text.toString())
    }

    @Test
    fun typingRightAfterAChipKeepsTheMarkerWhole() {
        show()
        compose.onNode(hasSetTextAction()).performClick()
        state.edit { placeCursorAtEnd() }
        val end = state.text.indexOf(MediaMarkers.of(4)) + MediaMarkers.of(4).length
        state.edit { selection = TextRange(end) }
        compose.onNode(hasSetTextAction()).performKeyInput { pressKey(Key.A) }
        compose.waitForIdle()
        assertEquals(listOf(4, 9), MediaMarkers.idsIn(state.text))
    }
}
