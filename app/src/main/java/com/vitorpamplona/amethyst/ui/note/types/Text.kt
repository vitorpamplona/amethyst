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
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.ui.components.GenericLoadable
import com.vitorpamplona.amethyst.ui.components.SensitivityWarning
import com.vitorpamplona.amethyst.ui.components.TranslatableRichTextViewer
import com.vitorpamplona.amethyst.ui.note.LoadDecryptedContent
import com.vitorpamplona.amethyst.ui.note.ReplyNoteComposition
import com.vitorpamplona.amethyst.ui.note.elements.DisplayUncitedHashtags
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.theme.StdVertSpacer
import com.vitorpamplona.amethyst.ui.theme.placeholderText
import com.vitorpamplona.quartz.events.BaseTextNoteEvent
import com.vitorpamplona.quartz.events.CommunityDefinitionEvent
import com.vitorpamplona.quartz.events.EmptyTagList
import com.vitorpamplona.quartz.events.TextNoteEvent
import com.vitorpamplona.quartz.events.toImmutableListOfLists
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

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
    nav: (String) -> Unit,
) {
    val noteEvent = note.event

    val showReply by
        remember(note) {
            derivedStateOf {
                noteEvent is BaseTextNoteEvent && !makeItShort && unPackReply && (note.replyTo != null || noteEvent.hasAnyTaggedUser())
            }
        }

    if (showReply) {
        val replyingDirectlyTo =
            remember(note) {
                if (noteEvent is BaseTextNoteEvent) {
                    val replyingTo = noteEvent.replyingToAddressOrEvent()
                    if (replyingTo != null) {
                        val newNote = accountViewModel.getNoteIfExists(replyingTo)
                        if (newNote != null && newNote.channelHex() == null && newNote.event?.kind() != CommunityDefinitionEvent.KIND) {
                            newNote
                        } else {
                            note.replyTo?.lastOrNull { it.event?.kind() != CommunityDefinitionEvent.KIND }
                        }
                    } else {
                        note.replyTo?.lastOrNull { it.event?.kind() != CommunityDefinitionEvent.KIND }
                    }
                } else {
                    note.replyTo?.lastOrNull { it.event?.kind() != CommunityDefinitionEvent.KIND }
                }
            }
        if (replyingDirectlyTo != null && unPackReply) {
            ReplyNoteComposition(replyingDirectlyTo, backgroundColor, accountViewModel, nav)
            Spacer(modifier = StdVertSpacer)
        }
    }

    LoadDecryptedContent(
        note,
        accountViewModel,
    ) { body ->
        val eventContent by
            remember(note.event) {
                derivedStateOf {
                    val subject = (note.event as? TextNoteEvent)?.subject()?.ifEmpty { null }
                    val newBody =
                        if (editState.value is GenericLoadable.Loaded) {
                            val state =
                                (editState.value as? GenericLoadable.Loaded)?.loaded?.modificationToShow
                            state?.value?.event?.content() ?: body
                        } else {
                            body
                        }

                    if (!subject.isNullOrBlank() && !newBody.split("\n")[0].contains(subject)) {
                        "### $subject\n$newBody"
                    } else {
                        newBody
                    }
                }
            }

        val isAuthorTheLoggedUser =
            remember(note.event) { accountViewModel.isLoggedUser(note.author) }

        if (makeItShort && isAuthorTheLoggedUser) {
            Text(
                text = eventContent,
                color = MaterialTheme.colorScheme.placeholderText,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        } else {
            SensitivityWarning(
                note = note,
                accountViewModel = accountViewModel,
            ) {
                val modifier = remember(note) { Modifier.fillMaxWidth() }
                val tags =
                    remember(note) { note.event?.tags()?.toImmutableListOfLists() ?: EmptyTagList }

                TranslatableRichTextViewer(
                    content = eventContent,
                    canPreview = canPreview && !makeItShort,
                    quotesLeft = quotesLeft,
                    modifier = modifier,
                    tags = tags,
                    backgroundColor = backgroundColor,
                    id = note.idHex,
                    accountViewModel = accountViewModel,
                    nav = nav,
                )
            }

            if (note.event?.hasHashtags() == true) {
                val hashtags =
                    remember(note.event) {
                        note.event?.hashtags()?.toImmutableList() ?: persistentListOf()
                    }
                DisplayUncitedHashtags(hashtags, eventContent, nav)
            }
        }
    }
}
