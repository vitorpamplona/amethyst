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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.event.observeNote
import com.vitorpamplona.amethyst.ui.actions.EditPostView
import com.vitorpamplona.amethyst.ui.components.GenericLoadable
import com.vitorpamplona.amethyst.ui.components.LoadNote
import com.vitorpamplona.amethyst.ui.components.TranslatableRichTextViewer
import com.vitorpamplona.amethyst.ui.navigation.INav
import com.vitorpamplona.amethyst.ui.navigation.routeFor
import com.vitorpamplona.amethyst.ui.note.NoteBody
import com.vitorpamplona.amethyst.ui.note.observeEdits
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.Size10dp
import com.vitorpamplona.amethyst.ui.theme.StdVertSpacer
import com.vitorpamplona.amethyst.ui.theme.imageModifier
import com.vitorpamplona.amethyst.ui.theme.innerPostModifier
import com.vitorpamplona.quartz.experimental.edits.TextNoteModificationEvent
import com.vitorpamplona.quartz.nip02FollowList.EmptyTagList
import kotlinx.collections.immutable.persistentListOf

@Composable
fun RenderTextModificationEvent(
    note: Note,
    makeItShort: Boolean,
    canPreview: Boolean,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val noteEvent = note.event as? TextNoteModificationEvent ?: return
    val noteAuthor = note.author ?: return

    val wantsToEditPost =
        remember {
            mutableStateOf(false)
        }

    val isAuthorTheLoggedUser =
        remember {
            val authorOfTheOriginalNote =
                noteEvent.editedNote()?.let { accountViewModel.getNoteIfExists(it.eventId)?.author?.pubkeyHex ?: it.author }

            mutableStateOf(accountViewModel.isLoggedUser(authorOfTheOriginalNote))
        }

    noteEvent.editedNote()?.let {
        LoadNote(baseNoteHex = it.eventId, accountViewModel = accountViewModel) { baseOriginalNote ->
            baseOriginalNote?.let {
            }
        }
    }

    Card(
        modifier = MaterialTheme.colorScheme.imageModifier,
    ) {
        Column(Modifier.fillMaxWidth().padding(Size10dp)) {
            Text(
                text = stringRes(id = R.string.proposal_to_edit),
                style =
                    TextStyle(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    ),
            )

            Spacer(modifier = StdVertSpacer)

            noteEvent.summary()?.let {
                TranslatableRichTextViewer(
                    content = it,
                    canPreview = canPreview && !makeItShort,
                    quotesLeft = quotesLeft,
                    modifier = Modifier.fillMaxWidth(),
                    tags = EmptyTagList,
                    backgroundColor = backgroundColor,
                    id = note.idHex,
                    callbackUri = note.toNostrUri(),
                    accountViewModel = accountViewModel,
                    nav = nav,
                )
                Spacer(modifier = StdVertSpacer)
            }

            noteEvent.editedNote()?.let {
                LoadNote(baseNoteHex = it.eventId, accountViewModel = accountViewModel) { baseNote ->
                    baseNote?.let {
                        val noteState by observeNote(baseNote)

                        val editStateOriginalNote =
                            observeEdits(baseNote = baseNote, accountViewModel = accountViewModel)

                        val editState =
                            remember(note) {
                                derivedStateOf {
                                    val loadable =
                                        editStateOriginalNote.value as? GenericLoadable.Loaded<EditState>

                                    val state = EditState()

                                    val latestChangeByAuthor =
                                        if (loadable != null && loadable.loaded.hasModificationsToShow()) {
                                            loadable.loaded.latestBefore(note.createdAt())
                                        } else {
                                            null
                                        }

                                    state.updateModifications(
                                        listOfNotNull(
                                            latestChangeByAuthor,
                                            note,
                                        ),
                                    )

                                    GenericLoadable.Loaded(state)
                                }
                            }

                        LaunchedEffect(key1 = noteState) {
                            val newAuthor = accountViewModel.isLoggedUser(noteState?.note?.author)

                            if (isAuthorTheLoggedUser.value != newAuthor) {
                                isAuthorTheLoggedUser.value = newAuthor
                            }
                        }

                        Column(
                            modifier =
                                MaterialTheme.colorScheme.innerPostModifier
                                    .padding(Size10dp)
                                    .clickable {
                                        routeFor(
                                            baseNote,
                                            accountViewModel.userProfile(),
                                        )?.let { nav.nav(it) }
                                    },
                        ) {
                            NoteBody(
                                baseNote = baseNote,
                                showAuthorPicture = true,
                                unPackReply = false,
                                makeItShort = false,
                                canPreview = true,
                                showSecondRow = false,
                                quotesLeft = quotesLeft,
                                backgroundColor = backgroundColor,
                                editState = editState,
                                accountViewModel = accountViewModel,
                                nav = nav,
                            )

                            if (wantsToEditPost.value) {
                                EditPostView(
                                    onClose = {
                                        wantsToEditPost.value = false
                                    },
                                    edit = baseNote,
                                    versionLookingAt = note,
                                    accountViewModel = accountViewModel,
                                    nav = nav,
                                )
                            }
                        }
                    }
                }
            }

            if (isAuthorTheLoggedUser.value) {
                Spacer(modifier = StdVertSpacer)

                Button(
                    onClick = { wantsToEditPost.value = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = stringRes(id = R.string.accept_the_suggestion))
                }
            }
        }
    }
}

val EmptyState = mutableStateOf<GenericLoadable<EditState>>(GenericLoadable.Empty())

@Stable
class EditState {
    private var modificationsList: List<Note> = persistentListOf()
    private var modificationToShowIndex: Int = -1

    val modificationToShow: MutableState<Note?> = mutableStateOf(null)
    val showingVersion: MutableState<Int> = mutableIntStateOf(0)

    fun hasModificationsToShow(): Boolean = modificationsList.isNotEmpty()

    fun isOriginal(): Boolean = modificationToShowIndex < 0

    fun isLatest(): Boolean = modificationToShowIndex == modificationsList.lastIndex

    fun originalVersionId() = 0

    fun lastVersionId() = modificationsList.size

    fun versionId() = modificationToShowIndex + 1

    fun latest() = modificationsList.lastOrNull()

    fun latestBefore(createdAt: Long?): Note? {
        if (createdAt == null) return latest()
        return modificationsList.lastOrNull { (it.createdAt() ?: Long.MAX_VALUE) < createdAt }
    }

    fun nextModification() {
        if (modificationToShowIndex < 0) {
            modificationToShowIndex = 0
            modificationToShow.value = modificationsList.getOrNull(0)
        } else {
            modificationToShowIndex++
            if (modificationToShowIndex >= modificationsList.size) {
                modificationToShowIndex = -1
                modificationToShow.value = null
            } else {
                modificationToShow.value = modificationsList.getOrNull(modificationToShowIndex)
            }
        }

        showingVersion.value = versionId()
    }

    fun updateModifications(newModifications: List<Note>) {
        if (modificationsList != newModifications) {
            modificationsList = newModifications

            if (newModifications.isEmpty()) {
                modificationToShow.value = null
                modificationToShowIndex = -1
            } else {
                modificationToShowIndex = newModifications.lastIndex
                modificationToShow.value = newModifications.last()
            }
        }

        showingVersion.value = versionId()
    }
}
