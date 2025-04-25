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

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.event.observeNoteHasEvent
import com.vitorpamplona.amethyst.ui.actions.CrossfadeIfEnabled
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WatchNoteEvent(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    modifier: Modifier = Modifier,
    shortPreview: Boolean = false,
    onNoteEventFound: @Composable () -> Unit,
) {
    WatchNoteEvent(
        baseNote,
        onNoteEventFound,
        onBlank = {
            LongPressToQuickAction(
                baseNote = baseNote,
                accountViewModel = accountViewModel,
            ) { showPopup ->
                BlankNote(
                    remember {
                        modifier.combinedClickable(
                            onClick = {},
                            onLongClick = showPopup,
                        )
                    },
                    shortPreview = shortPreview,
                )
            }
        },
        accountViewModel = accountViewModel,
    )
}

@Composable
fun WatchNoteEvent(
    baseNote: Note,
    onNoteEventFound: @Composable () -> Unit,
    onBlank: @Composable () -> Unit,
    accountViewModel: AccountViewModel,
) {
    if (baseNote.event != null) {
        onNoteEventFound()
    } else {
        // avoid observing costs if already has an event.
        val hasEvent by observeNoteHasEvent(baseNote)
        CrossfadeIfEnabled(targetState = hasEvent, label = "Event presence", accountViewModel = accountViewModel) {
            if (it) {
                onNoteEventFound()
            } else {
                onBlank()
            }
        }
    }
}
