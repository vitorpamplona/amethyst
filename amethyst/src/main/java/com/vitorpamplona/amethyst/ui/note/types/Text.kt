/**
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
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.ui.components.GenericLoadable
import com.vitorpamplona.amethyst.ui.components.SensitivityWarning
import com.vitorpamplona.amethyst.ui.components.TranslatableRichTextViewer
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.note.LoadDecryptedContent
import com.vitorpamplona.amethyst.ui.note.ReplyNoteComposition
import com.vitorpamplona.amethyst.ui.note.elements.DisplayUncitedHashtags
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.theme.StdVertSpacer
import com.vitorpamplona.amethyst.ui.theme.placeholderText
import com.vitorpamplona.quartz.nip01Core.core.EmptyTagList
import com.vitorpamplona.quartz.nip01Core.core.toImmutableListOfLists
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.hasHashtags
import com.vitorpamplona.quartz.nip01Core.tags.people.hasAnyTaggedUser
import com.vitorpamplona.quartz.nip10Notes.BaseThreadedEvent
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip14Subject.subject
import com.vitorpamplona.quartz.nip72ModCommunities.definition.CommunityDefinitionEvent

@Composable
fun RenderTextEvent(
    note: Note,
    makeItShort: Boolean,
    canPreview: Boolean,
    quotesLeft: Int,
    unPackReply: Boolean,
    backgroundColor: MutableState<Color>,
    editState: State<GenericLoadable<EditState>>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val noteEvent = note.event ?: return

    val showReply by
        remember(note) {
            derivedStateOf {
                noteEvent is BaseThreadedEvent && !makeItShort && unPackReply && (note.replyTo != null || noteEvent.hasAnyTaggedUser())
            }
        }

    if (showReply) {
        val replyingDirectlyTo =
            remember(note) {
                if (noteEvent is BaseThreadedEvent) {
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
                } else {
                    note.replyTo?.lastOrNull { it.event?.kind != CommunityDefinitionEvent.KIND }
                }
            }
        if (replyingDirectlyTo != null && unPackReply) {
            ReplyNoteComposition(replyingDirectlyTo, backgroundColor, accountViewModel, nav)
            Spacer(modifier = StdVertSpacer)
        }
    }

    // Check if this is an audio-only event (content is just an audio URL with waveform IMeta)
    val isAudioOnly = remember(noteEvent) { noteEvent.isAudioOnlyContent() }
    if (isAudioOnly) {
        RenderAudioFromIMeta(note, accountViewModel, nav)
        return
    }

    LoadDecryptedContent(
        note,
        accountViewModel,
    ) { body ->
        val newBody =
            if (editState.value is GenericLoadable.Loaded) {
                (editState.value as? GenericLoadable.Loaded)
                    ?.loaded
                    ?.modificationToShow
                    ?.value
                    ?.event
                    ?.content ?: body
            } else {
                body
            }

        val eventContent =
            remember(newBody) {
                val subject = (note.event as? TextNoteEvent)?.subject()?.ifBlank { null }
                if (!subject.isNullOrBlank() && !newBody.contains(subject, ignoreCase = true)) {
                    "$subject\n\n$newBody"
                } else {
                    newBody
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
            val callbackUri = remember(note) { note.toNostrUri() }

            SensitivityWarning(
                note = note,
                accountViewModel = accountViewModel,
            ) {
                val tags =
                    remember(note) { note.event?.tags?.toImmutableListOfLists() ?: EmptyTagList }

                TranslatableRichTextViewer(
                    content = eventContent,
                    canPreview = canPreview && !makeItShort,
                    quotesLeft = quotesLeft,
                    modifier = Modifier.fillMaxWidth(),
                    tags = tags,
                    backgroundColor = backgroundColor,
                    id =
                        if (editState.value is GenericLoadable.Loaded) {
                            (editState.value as GenericLoadable.Loaded<EditState>)
                                .loaded.modificationToShow.value
                                ?.idHex ?: note.idHex
                        } else {
                            note.idHex
                        },
                    callbackUri = callbackUri,
                    accountViewModel = accountViewModel,
                    nav = nav,
                )
            }

            if (noteEvent.hasHashtags()) {
                DisplayUncitedHashtags(noteEvent, eventContent, callbackUri, accountViewModel, nav)
            }
        }
    }
}
