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
package com.vitorpamplona.amethyst.ui.note

import androidx.compose.animation.Crossfade
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.distinctUntilChanged
import androidx.lifecycle.map
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel

@Composable
fun CheckHiddenFeedWatchBlockAndReport(
    note: Note,
    modifier: Modifier = Modifier,
    showHiddenWarning: Boolean,
    showHidden: Boolean = false,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
    normalNote: @Composable (canPreview: Boolean) -> Unit,
) {
    if (showHidden) {
        // Ignores reports as well
        normalNote(true)
    } else {
        WatchBlockAndReport(note, showHiddenWarning, modifier, accountViewModel, nav) { canPreview ->
            normalNote(canPreview)
        }
    }
}

@Composable
fun WatchBlockAndReport(
    note: Note,
    showHiddenWarning: Boolean,
    modifier: Modifier = Modifier,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
    normalNote: @Composable (canPreview: Boolean) -> Unit,
) {
    val isHiddenState by remember(note) {
        accountViewModel.account.liveHiddenUsers
            .map { note.isHiddenFor(it) }
            .distinctUntilChanged()
    }
        .observeAsState(accountViewModel.isNoteHidden(note))

    val showAnyway =
        remember {
            mutableStateOf(false)
        }

    Crossfade(targetState = isHiddenState, label = "CheckHiddenNoteCompose") { isHidden ->
        if (showAnyway.value) {
            normalNote(true)
        } else if (!isHidden) {
            LoadReportsNoteCompose(note, modifier, accountViewModel, nav) { canPreview ->
                normalNote(canPreview)
            }
        } else if (showHiddenWarning) {
            // if it is a quoted or boosted note, how the hidden warning.
            HiddenNoteByMe {
                showAnyway.value = true
            }
        }
    }
}

@Composable
private fun LoadReportsNoteCompose(
    note: Note,
    modifier: Modifier = Modifier,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
    normalNote: @Composable (canPreview: Boolean) -> Unit,
) {
    var state by
        remember(note) {
            mutableStateOf(
                AccountViewModel.NoteComposeReportState(),
            )
        }

    WatchForReports(note, accountViewModel) { newState ->
        if (state != newState) {
            state = newState
        }
    }

    Crossfade(targetState = state, label = "LoadedNoteCompose") {
        RenderReportState(state = it, note = note, modifier = modifier, accountViewModel = accountViewModel, nav = nav) { canPreview ->
            normalNote(canPreview)
        }
    }
}

@Composable
private fun RenderReportState(
    state: AccountViewModel.NoteComposeReportState,
    note: Note,
    modifier: Modifier = Modifier,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
    normalNote: @Composable (canPreview: Boolean) -> Unit,
) {
    var showReportedNote by remember(note) { mutableStateOf(false) }

    Crossfade(targetState = !state.isAcceptable && !showReportedNote, label = "RenderReportState") { showHiddenNote ->
        if (showHiddenNote) {
            HiddenNote(
                state.relevantReports,
                state.isHiddenAuthor,
                accountViewModel,
                modifier,
                nav,
                onClick = { showReportedNote = true },
            )
        } else {
            val canPreview = (!state.isAcceptable && showReportedNote) || state.canPreview

            normalNote(canPreview)
        }
    }
}

@Composable
fun WatchForReports(
    note: Note,
    accountViewModel: AccountViewModel,
    onChange: (AccountViewModel.NoteComposeReportState) -> Unit,
) {
    val userFollowsState by accountViewModel.userFollows.observeAsState()
    val noteReportsState by note.live().reports.observeAsState()
    val userBlocks by accountViewModel.account.flowHiddenUsers.collectAsStateWithLifecycle()

    LaunchedEffect(key1 = noteReportsState, key2 = userFollowsState, userBlocks) {
        accountViewModel.isNoteAcceptable(note, onChange)
    }
}
