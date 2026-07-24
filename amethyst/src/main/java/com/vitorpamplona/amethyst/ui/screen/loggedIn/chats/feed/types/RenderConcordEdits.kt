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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.feed.types

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.model.EmptyTagList
import com.vitorpamplona.amethyst.commons.model.toImmutableListOfLists
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.latestConcordEdit
import com.vitorpamplona.amethyst.ui.components.TranslatableRichTextViewer
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes

/**
 * Observes the newest kind-3302 Concord edit overlaying a Concord chat message [note],
 * recomposing when a new edit lands. Returns null for non-Concord messages or an
 * unedited one.
 *
 * Concord edits ride the encrypted channel plane (unlike public feed edits, there is no
 * relay subscription to start — the session decrypts the wrap and lands the kind-3302
 * rumor in [LocalCache] itself). Each edit is attached to the message it edits ([Note.edits],
 * like a reaction to its target), so it is held for exactly as long as the channel-retained
 * message — a Concord rumor is decrypted once per session and can't be re-downloaded, so it
 * must not be left orphaned in the soft cache. We recompute from that list whenever it changes.
 * Only edits authored by the original message's author are applied — so a member can't rewrite
 * someone else's message — and the latest by CORD-02 §4 send time wins.
 */
@Composable
fun observeConcordEdit(note: Note): Note? {
    // `addEdit` invalidates this flow, so collecting it re-runs the fold on each new edit. A non-Concord
    // message simply has no kind-3302 edits, so [Note.latestConcordEdit] returns null for it.
    val latest by
        produceState<Note?>(initialValue = null, note.idHex) {
            note.flow().edits.stateFlow.collect {
                value = note.latestConcordEdit()
            }
        }
    return latest
}

/**
 * A Concord chat message whose content has been superseded by a kind-3302 edit:
 * renders the NEWEST edit's content (never the stale original) plus an "(edited)"
 * marker, matching the Concord reference client's last-write-wins presentation.
 */
@Composable
fun RenderConcordEditedNote(
    note: Note,
    editNote: Note,
    canPreview: Boolean,
    innerQuote: Boolean,
    bgColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    // The edit note may still be loading; fall back to the original rendering rather
    // than committing to an edited branch that would show a blank row.
    val content = editNote.event?.content
    if (content == null) {
        RenderRegularTextNote(note, canPreview, innerQuote, bgColor, accountViewModel, nav)
        return
    }
    // Custom emoji + mentions live on the edit's own tags, so render against those.
    val tags = remember(editNote.event) { editNote.event?.tags?.toImmutableListOfLists() ?: EmptyTagList }

    Column {
        TranslatableRichTextViewer(
            content = content,
            canPreview = canPreview,
            quotesLeft = if (innerQuote) 0 else 1,
            modifier = Modifier,
            tags = tags,
            backgroundColor = bgColor,
            id = note.idHex,
            callbackUri = note.toNostrUri(),
            authorPubKey = note.author?.pubkeyHex,
            accountViewModel = accountViewModel,
            nav = nav,
        )
        Text(
            text = stringRes(R.string.message_edited),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 10.sp,
        )
    }
}
