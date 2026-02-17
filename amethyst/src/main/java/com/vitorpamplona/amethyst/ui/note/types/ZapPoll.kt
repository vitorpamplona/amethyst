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
package com.vitorpamplona.amethyst.ui.note.types

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import com.vitorpamplona.amethyst.commons.model.EmptyTagList
import com.vitorpamplona.amethyst.commons.model.toImmutableListOfLists
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.ui.components.SensitivityWarning
import com.vitorpamplona.amethyst.ui.components.TranslatableRichTextViewer
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.note.ReplyNoteComposition
import com.vitorpamplona.amethyst.ui.note.ZapPollNote
import com.vitorpamplona.amethyst.ui.note.elements.DisplayUncitedHashtags
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.theme.StdVertSpacer
import com.vitorpamplona.amethyst.ui.theme.placeholderText
import com.vitorpamplona.quartz.experimental.zapPolls.PollNoteEvent
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.hasHashtags
import com.vitorpamplona.quartz.nip01Core.tags.people.hasAnyTaggedUser
import com.vitorpamplona.quartz.nip72ModCommunities.definition.CommunityDefinitionEvent

@Composable
fun RenderZapPoll(
    note: Note,
    makeItShort: Boolean,
    canPreview: Boolean,
    quotesLeft: Int,
    unPackReply: Boolean,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val noteEvent = note.event as? PollNoteEvent ?: return
    val eventContent = noteEvent.content

    val showReply by
        remember(note) {
            derivedStateOf {
                !makeItShort && unPackReply && (note.replyTo != null || noteEvent.hasAnyTaggedUser())
            }
        }

    if (showReply) {
        val replyingDirectlyTo =
            remember(note) {
                val replyingTo = noteEvent.replyingToAddressOrEvent()
                if (replyingTo != null) {
                    val newNote = accountViewModel.getNoteIfExists(replyingTo)
                    if (newNote != null && LocalCache.getAnyChannel(newNote) == null && newNote.event?.kind != CommunityDefinitionEvent.KIND) {
                        newNote
                    } else {
                        note.replyTo?.lastOrNull { it.event?.kind != CommunityDefinitionEvent.KIND }
                    }
                } else {
                    note.replyTo?.lastOrNull { it.event?.kind != CommunityDefinitionEvent.KIND }
                }
            }
        if (replyingDirectlyTo != null) {
            ReplyNoteComposition(replyingDirectlyTo, backgroundColor, accountViewModel, nav)
            Spacer(modifier = StdVertSpacer)
        }
    }

    if (makeItShort && accountViewModel.isLoggedUser(note.author)) {
        Text(
            text = eventContent,
            color = MaterialTheme.colorScheme.placeholderText,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    } else {
        val tags = remember(note) { note.event?.tags?.toImmutableListOfLists() ?: EmptyTagList }
        val callbackUri = remember(note) { note.toNostrUri() }

        SensitivityWarning(
            note = note,
            accountViewModel = accountViewModel,
        ) {
            TranslatableRichTextViewer(
                content = eventContent,
                canPreview = canPreview && !makeItShort,
                quotesLeft = quotesLeft,
                modifier = Modifier.fillMaxWidth(),
                tags = tags,
                backgroundColor = backgroundColor,
                id = note.idHex,
                callbackUri = callbackUri,
                accountViewModel = accountViewModel,
                nav = nav,
            )

            ZapPollNote(
                note,
                canPreview = canPreview && !makeItShort,
                backgroundColor,
                accountViewModel,
                nav,
            )
        }

        if (noteEvent.hasHashtags()) {
            DisplayUncitedHashtags(noteEvent, eventContent, callbackUri, accountViewModel, nav)
        }
    }
}
