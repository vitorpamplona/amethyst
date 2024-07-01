/**
 * Copyright (c) 2024 Vitor Pamplona
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
package com.vitorpamplona.amethyst.ui.note.types

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.ui.components.GenericLoadable
import com.vitorpamplona.amethyst.ui.components.SensitivityWarning
import com.vitorpamplona.amethyst.ui.components.TranslatableRichTextViewer
import com.vitorpamplona.amethyst.ui.note.elements.DisplayUncitedHashtags
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.quartz.events.EmptyTagList
import com.vitorpamplona.quartz.events.NIP90ContentDiscoveryResponseEvent
import com.vitorpamplona.quartz.events.toImmutableListOfLists

@Composable
fun RenderNIP90ContentDiscoveryResponse(
    note: Note,
    makeItShort: Boolean,
    canPreview: Boolean,
    quotesLeft: Int,
    unPackReply: Boolean,
    backgroundColor: MutableState<Color>,
    editState: State<GenericLoadable<EditState>>,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    val noteEvent = note.event as? NIP90ContentDiscoveryResponseEvent ?: return

    SensitivityWarning(
        note = note,
        accountViewModel = accountViewModel,
    ) {
        val modifier = remember(note) { Modifier.fillMaxWidth() }
        val tags =
            remember(note) { note.event?.tags()?.toImmutableListOfLists() ?: EmptyTagList }

        TranslatableRichTextViewer(
            content = noteEvent.content,
            canPreview = canPreview && !makeItShort,
            quotesLeft = quotesLeft,
            modifier = modifier,
            tags = tags,
            backgroundColor = backgroundColor,
            id = note.idHex,
            callbackUri = note.toNostrUri(),
            accountViewModel = accountViewModel,
            nav = nav,
        )
    }

    if (noteEvent.hasHashtags()) {
        DisplayUncitedHashtags(noteEvent, noteEvent.content, nav)
    }
}
