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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.ui.navigation.INav
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel

@Composable
fun CheckHiddenFeedWatchBlockAndReport(
    note: Note,
    modifier: Modifier = Modifier,
    showHiddenWarning: Boolean,
    ignoreAllBlocksAndReports: Boolean = false,
    accountViewModel: AccountViewModel,
    nav: INav,
    normalNote: @Composable (canPreview: Boolean) -> Unit,
) {
    if (ignoreAllBlocksAndReports) {
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
    nav: INav,
    normalNote: @Composable (canPreview: Boolean) -> Unit,
) {
    val isHidden by accountViewModel.createIsHiddenFlow(note).collectAsStateWithLifecycle()

    val showAnyway =
        remember {
            mutableStateOf(false)
        }

    if (showAnyway.value) {
        normalNote(true)
    } else if (!isHidden.isPostHidden) {
        if (isHidden.isAcceptable) {
            normalNote(isHidden.canPreview)
        } else {
            HiddenNote(
                isHidden.relevantReports,
                isHidden.isHiddenAuthor,
                accountViewModel,
                modifier,
                nav,
                onClick = { showAnyway.value = true },
            )
        }
    } else if (showHiddenWarning) {
        // if it is a quoted or boosted note, how the hidden warning.
        HiddenNoteByMe {
            showAnyway.value = true
        }
    }
}
