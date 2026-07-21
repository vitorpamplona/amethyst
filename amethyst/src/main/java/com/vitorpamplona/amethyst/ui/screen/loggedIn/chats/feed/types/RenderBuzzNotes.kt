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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.model.EmptyTagList
import com.vitorpamplona.amethyst.commons.model.buzz.BuzzWorkspaceChannel
import com.vitorpamplona.amethyst.commons.model.toImmutableListOfLists
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.ui.components.TranslatableRichTextViewer
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.feed.layouts.ChatSystemMessage
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.buzz.stream.SystemMessageEvent

/**
 * The note's [BuzzWorkspaceChannel], when it was gathered by one. Buzz-only rendering
 * (edit overlays, system rows) keys off this; for every other chat surface it is null
 * and rendering falls through to the shared paths.
 */
fun Note.buzzWorkspaceChannel(): BuzzWorkspaceChannel? = inGatherers?.firstOrNull { it is BuzzWorkspaceChannel } as? BuzzWorkspaceChannel

/**
 * Observes the newest kind-40003 edit overlaying [note], recomposing when new edits
 * arrive. Returns null when the message is unedited (or not in a Buzz channel).
 */
@Composable
fun observeBuzzEdit(note: Note): Note? {
    val channel = remember(note) { note.buzzWorkspaceChannel() } ?: return null
    // Subscribing to the version counter is what re-runs editFor on new arrivals.
    val version by channel.editUpdates.collectAsState()
    return remember(note, version) { channel.editFor(note.idHex) }
}

/**
 * A Buzz stream message whose content has been superseded by a kind-40003 edit:
 * renders the NEWEST edit's content (never the stale original) plus an "(edited)"
 * marker, mirroring Buzz's own last-write-wins presentation.
 */
@Composable
fun RenderBuzzEditedNote(
    note: Note,
    editNote: Note,
    canPreview: Boolean,
    innerQuote: Boolean,
    bgColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val content = editNote.event?.content ?: return
    val tags = remember(note.event) { note.event?.tags?.toImmutableListOfLists() ?: EmptyTagList }

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
            text = stringRes(R.string.buzz_message_edited),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 10.sp,
        )
    }
}

/**
 * A Buzz kind-40099 system message ("X joined", "channel created", "topic changed"):
 * narrates the room rather than speaking in it, so it renders as a centered system
 * line like the NIP-28 admin events, from the relay-signed JSON payload.
 */
@Composable
fun RenderBuzzSystemMessage(note: Note) {
    val event = note.event as? SystemMessageEvent ?: return
    // Relay-emitted machine text (join/leave/topic); shown as-is rather than through
    // string resources — the payload vocabulary is Buzz's, not ours to translate yet.
    val text =
        remember(event) {
            val payload = event.payload()
            when (payload?.type) {
                "topic_changed" -> payload.topic?.let { "topic: $it" } ?: "topic changed"
                "purpose_changed" -> payload.purpose?.let { "purpose: $it" } ?: "purpose changed"
                else -> payload?.type?.replace('_', ' ')
            } ?: event.content.take(120)
        }

    ChatSystemMessage(text = text)
}
