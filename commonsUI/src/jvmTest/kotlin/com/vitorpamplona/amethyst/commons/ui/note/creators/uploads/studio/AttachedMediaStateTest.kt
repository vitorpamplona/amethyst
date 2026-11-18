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
package com.vitorpamplona.amethyst.commons.ui.note.creators.uploads.studio

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import com.vitorpamplona.amethyst.commons.model.mediaServers.ServerName
import com.vitorpamplona.amethyst.commons.model.mediaServers.ServerType
import com.vitorpamplona.amethyst.commons.service.uploads.MediaMarkers
import com.vitorpamplona.amethyst.commons.service.uploads.SelectedMedia
import com.vitorpamplona.amethyst.commons.service.uploads.StringMediaUri
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AttachedMediaStateTest {
    private fun attached(vararg names: String): Pair<TextFieldState, AttachedMediaState> {
        val text = TextFieldState("Look")
        text.setTextAndPlaceCursorAtEnd("Look")
        val state = AttachedMediaState(text)
        state.pick(names.map { SelectedMedia(StringMediaUri("/tmp/$it.jpg"), "image/jpeg") }, stripMetadata = true)
        state.attachPicked(chosenServer = ServerName("Test", "https://test.example/", ServerType.Blossom))
        return text to state
    }

    @Test
    fun aPastedCopyOfAMarkerIsStillOneFile() {
        val (text, state) = attached("a", "b")
        val first = state.inOrder().first()
        // Copy the first marker and paste it at the end.
        text.edit { append(MediaMarkers.of(1)) }
        assertEquals(2, MediaMarkers.idsIn(text.text).count { it == 1 }, "two copies in the text")
        assertEquals(
            listOf("a", "b"),
            state.inOrder().map {
                it.media.uri
                    .toString()
                    .substringAfterLast('/')
                    .removeSuffix(".jpg")
            },
        )
        assertTrue(state.inOrder().count { it === first } == 1, "sent once")
    }

    @Test
    fun undoAfterRemoveBringsTheFileBack() {
        val (text, state) = attached("a")
        val before = text.text.toString()
        state.remove(state.inOrder().single())
        assertEquals(emptyList(), state.inOrder())
        assertTrue(!state.hasMedia())
        // What undo does: the text as it was.
        text.setTextAndPlaceCursorAtEnd(before)
        assertEquals(1, state.inOrder().size, "the file is back with its marker")
    }
}
