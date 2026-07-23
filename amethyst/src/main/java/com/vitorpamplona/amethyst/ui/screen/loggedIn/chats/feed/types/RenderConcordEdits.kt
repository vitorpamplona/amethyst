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
import com.vitorpamplona.amethyst.commons.model.concord.ConcordChannel
import com.vitorpamplona.amethyst.commons.model.toImmutableListOfLists
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.ui.components.TranslatableRichTextViewer
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.sample

/**
 * Observes the newest kind-1010 edit overlaying a Concord chat message [note],
 * recomposing when a new edit lands. Returns null for non-Concord messages or an
 * unedited one.
 *
 * Concord edits ride the encrypted channel plane (unlike public feed edits, there is
 * no relay subscription to start here — the session decrypts the wrap and lands the
 * kind-1010 rumor in [LocalCache] itself). Resolution then goes through the same
 * shared machinery the feed uses: [LocalCache.findLatestModificationForNote] keeps
 * only same-author modifications, so a member can't rewrite someone else's message,
 * and the newest one wins (last write). Runs off the main thread because
 * `findLatestModificationForNote` scans the cache.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Composable
fun observeConcordEdit(note: Note): Note? {
    // Key on note.event, not note: LocalCache mutates a Note in place, so keying on the Note instance
    // would cache a null gatherer taken before the event populated and never recompute for that row.
    val isConcord = remember(note.event) { note.inGatherers?.any { it is ConcordChannel } == true }
    if (!isConcord) return null

    val latest by
        produceState<Note?>(initialValue = null, note) {
            note
                .flow()
                .edits
                .stateFlow
                .sample(500)
                .mapLatest { LocalCache.findLatestModificationForNote(note).lastOrNull() }
                .distinctUntilChanged()
                .flowOn(Dispatchers.IO)
                .collect { value = it }
        }
    return latest
}

/**
 * A Concord chat message whose content has been superseded by a kind-1010 edit:
 * renders the NEWEST edit's content (never the stale original) plus an "(edited)"
 * marker, matching the feed's last-write-wins edit presentation.
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
