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

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.AddressableNote
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.ui.components.ClickableUrl
import com.vitorpamplona.amethyst.ui.components.SensitivityWarning
import com.vitorpamplona.amethyst.ui.components.TranslatableRichTextViewer
import com.vitorpamplona.amethyst.ui.note.LoadAddressableNote
import com.vitorpamplona.amethyst.ui.note.LoadDecryptedContent
import com.vitorpamplona.amethyst.ui.note.elements.DisplayUncitedHashtags
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.ui.theme.DoubleVertSpacer
import com.vitorpamplona.amethyst.ui.theme.QuoteBorder
import com.vitorpamplona.amethyst.ui.theme.Size10dp
import com.vitorpamplona.amethyst.ui.theme.Size5dp
import com.vitorpamplona.amethyst.ui.theme.StdHorzSpacer
import com.vitorpamplona.amethyst.ui.theme.placeholderText
import com.vitorpamplona.amethyst.ui.theme.replyModifier
import com.vitorpamplona.amethyst.ui.theme.subtleBorder
import com.vitorpamplona.quartz.events.EmptyTagList
import com.vitorpamplona.quartz.events.GitIssueEvent
import com.vitorpamplona.quartz.events.GitPatchEvent
import com.vitorpamplona.quartz.events.GitRepositoryEvent
import com.vitorpamplona.quartz.events.TextNoteEvent
import com.vitorpamplona.quartz.events.toImmutableListOfLists
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

@Composable
fun RenderGitPatchEvent(
    baseNote: Note,
    makeItShort: Boolean,
    canPreview: Boolean,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    val event = baseNote.event as? GitPatchEvent ?: return

    RenderGitPatchEvent(
        event,
        baseNote,
        makeItShort,
        canPreview,
        quotesLeft,
        backgroundColor,
        accountViewModel,
        nav,
    )
}

@Composable
private fun RenderShortRepositoryHeader(
    baseNote: AddressableNote,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    val noteState by baseNote.live().metadata.observeAsState()
    val noteEvent = noteState?.note?.event as? GitRepositoryEvent ?: return

    Column(
        modifier = MaterialTheme.colorScheme.replyModifier.padding(10.dp),
    ) {
        val title = remember(noteEvent) { noteEvent.name() ?: noteEvent.dTag() }
        Text(
            text = stringResource(id = R.string.git_repository, title),
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )

        noteEvent.description()?.let {
            Spacer(modifier = DoubleVertSpacer)
            Text(
                text = it,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun RenderGitPatchEvent(
    noteEvent: GitPatchEvent,
    note: Note,
    makeItShort: Boolean,
    canPreview: Boolean,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    val repository = remember(noteEvent) { noteEvent.repository() }

    if (repository != null) {
        LoadAddressableNote(aTag = repository, accountViewModel = accountViewModel) {
            if (it != null) {
                RenderShortRepositoryHeader(it, accountViewModel, nav)
                Spacer(modifier = DoubleVertSpacer)
            }
        }
    }

    LoadDecryptedContent(note, accountViewModel) { body ->
        val eventContent by
            remember(note.event) {
                derivedStateOf {
                    val subject = (note.event as? TextNoteEvent)?.subject()?.ifEmpty { null }

                    if (!subject.isNullOrBlank() && !body.split("\n")[0].contains(subject)) {
                        "### $subject\n$body"
                    } else {
                        body
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

@Composable
fun RenderGitIssueEvent(
    baseNote: Note,
    makeItShort: Boolean,
    canPreview: Boolean,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    val event = baseNote.event as? GitIssueEvent ?: return

    RenderGitIssueEvent(
        event,
        baseNote,
        makeItShort,
        canPreview,
        quotesLeft,
        backgroundColor,
        accountViewModel,
        nav,
    )
}

@Composable
private fun RenderGitIssueEvent(
    noteEvent: GitIssueEvent,
    note: Note,
    makeItShort: Boolean,
    canPreview: Boolean,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    val repository = remember(noteEvent) { noteEvent.repository() }

    if (repository != null) {
        LoadAddressableNote(aTag = repository, accountViewModel = accountViewModel) {
            if (it != null) {
                RenderShortRepositoryHeader(it, accountViewModel, nav)
                Spacer(modifier = DoubleVertSpacer)
            }
        }
    }

    LoadDecryptedContent(note, accountViewModel) { body ->
        val eventContent by
            remember(note.event) {
                derivedStateOf {
                    val subject = (note.event as? TextNoteEvent)?.subject()?.ifEmpty { null }

                    if (!subject.isNullOrBlank() && !body.split("\n")[0].contains(subject)) {
                        "### $subject\n$body"
                    } else {
                        body
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

@Composable
fun RenderGitRepositoryEvent(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    val event = baseNote.event as? GitRepositoryEvent ?: return

    RenderGitRepositoryEvent(event, baseNote, accountViewModel, nav)
}

@Composable
private fun RenderGitRepositoryEvent(
    noteEvent: GitRepositoryEvent,
    note: Note,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    val title = noteEvent.name() ?: noteEvent.dTag()
    val summary = noteEvent.description()
    val web = noteEvent.web()
    val clone = noteEvent.clone()

    Row(
        modifier =
            Modifier
                .clip(shape = QuoteBorder)
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.subtleBorder,
                    QuoteBorder,
                ).padding(Size10dp),
    ) {
        Column {
            Text(
                text = stringResource(id = R.string.git_repository, title),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )

            summary?.let {
                Text(
                    text = it,
                    modifier = Modifier.fillMaxWidth().padding(vertical = Size5dp),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            HorizontalDivider(thickness = DividerThickness)

            web?.let {
                Row(Modifier.fillMaxWidth().padding(top = Size5dp)) {
                    Text(
                        text = stringResource(id = R.string.git_web_address),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = StdHorzSpacer)
                    ClickableUrl(
                        url = it,
                        urlText = it.removePrefix("https://").removePrefix("http://"),
                    )
                }
            }

            clone?.let {
                Row(Modifier.fillMaxWidth().padding(top = Size5dp)) {
                    Text(
                        text = stringResource(id = R.string.git_clone_address),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = StdHorzSpacer)
                    ClickableUrl(
                        url = it,
                        urlText = it.removePrefix("https://").removePrefix("http://"),
                    )
                }
            }
        }
    }
}
